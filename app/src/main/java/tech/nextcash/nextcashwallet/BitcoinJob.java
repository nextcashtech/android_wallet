package tech.nextcash.nextcashwallet;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;


// This is the periodic job for synchronizing the Bitcoin SPV node.
public class BitcoinJob extends JobService
{
    private static final String logTag = "BitcoinJob";

    public static final int SYNC_JOB_ID = 92; // Sync to latest block and exit (background only)

    Bitcoin mBitcoin;
    JobParameters mJobParameters;
    Thread mWaitForStopThread;

    private Runnable mWaitForStopRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            while(mBitcoin.isRunning())
            {
                try
                {
                    Thread.sleep(100);
                }
                catch(InterruptedException pException)
                {
                    Log.d(logTag, String.format("Wait for stop sleep exception : %s", pException.toString()));
                }
            }

            jobFinished(mJobParameters, false);
        }
    };

    @Override
    public boolean onStartJob(JobParameters pParams)
    {
        Log.i(logTag, "Starting job");
        mBitcoin = ((MainApp)getApplication()).bitcoin;
        if(mBitcoin.start(pParams.getJobId()))
            return true;
        else
            return false;
    }

    @Override
    public boolean onStopJob(JobParameters pParams)
    {
        Log.i(logTag, "Stopping job");
        mJobParameters = pParams;
        if(mBitcoin.requestStop())
        {
            mWaitForStopThread = new Thread(mWaitForStopRunnable, "BitcoinJobWaitForStopThread");
            mWaitForStopThread.start();
            return true;
        }
        else
            return false;
    }
}
