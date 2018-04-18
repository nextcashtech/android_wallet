package tech.nextcash.nextcashwallet;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.util.Log;


// This is the periodic job for synchronizing the Bitcoin SPV node.
public class BitcoinJob extends JobService
{
    private static final String logTag = "BitcoinJob";
    private static Thread sUpdateThread;

    public static final int SYNC_JOB_ID = 92; // Sync to latest block and exit (background only)

    Bitcoin mBitcoin;
    Bitcoin.CallBacks mBitcoinCallBacks;
    JobParameters mJobParameters;

    private void backgroundUpdate()
    {
        boolean started = false;
        Context context = getApplicationContext();
        int notStartedCount = 0;

        while(true)
        {
            if(started)
            {
                if(!mBitcoin.isRunning())
                    break;
            }
            else
            {
                if(mBitcoin.isLoaded())
                    started = true;
                else
                {
                    notStartedCount++;
                    if(notStartedCount > 100)
                    {
                        Log.d(logTag, "Bitcoin failed to start for update thread");
                        break;
                    }
                }
            }

            mBitcoin.update(context, false);

            try
            {
                Thread.sleep(2000);
            }
            catch(InterruptedException pException)
            {
                Log.d(logTag, String.format("Bitcoin update sleep exception : %s", pException.toString()));
            }
        }
    }

    private Runnable mUpdateRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            backgroundUpdate();
        }
    };

    private synchronized Thread getUpdateThread()
    {
        if((sUpdateThread == null || !sUpdateThread.isAlive()))
            sUpdateThread = new Thread(mUpdateRunnable, "BitcoinUpdate");
        return sUpdateThread;
    }

    public synchronized boolean startUpdate()
    {
        boolean result = true;
        Thread thread = getUpdateThread();
        if(!thread.isAlive())
            thread.start();
        else
        {
            Log.v(logTag, "Bitcoin update thread already running");
            result = false;
        }

        return result;
    }

    @Override
    public boolean onStartJob(JobParameters pParams)
    {
        Log.i(logTag, "Starting job");
        mJobParameters = pParams;
        mBitcoin = ((MainApp)getApplication()).bitcoin;

        mBitcoinCallBacks = new Bitcoin.CallBacks()
        {
            @Override
            public void onLoad()
            {
            }

            @Override
            public boolean onTransactionUpdate(int pWalletOffset, Transaction pTransaction)
            {
                return false;
            }

            @Override
            public void onFinish()
            {
                mBitcoin.clearProgress(getApplicationContext());
                jobFinished(mJobParameters, false);
                mBitcoin.clearCallBacks(mBitcoinCallBacks);
            }
        };

        if(mBitcoin.start(Bitcoin.FINISH_ON_SYNC))
        {
            startUpdate();
            mBitcoin.setCallBacks(mBitcoinCallBacks);
            return true;
        }
        else
        {
            Log.i(logTag, "Job never started. Already running.");
            jobFinished(pParams, false);
            return false;
        }
    }

    @Override
    public boolean onStopJob(JobParameters pParams)
    {
        Log.i(logTag, "Stopping job");
        if(mBitcoin.requestStop())
            return true;
        else
            return false;
    }
}
