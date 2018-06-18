package tech.nextcash.nextcashwallet;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;


// This is the periodic job for synchronizing the Bitcoin SPV node.
public class BitcoinJob extends JobService
{
    private static final String logTag = "BitcoinJob";

    public static final int SYNC_JOB_ID = 92; // Sync to latest block and exit (background only)

    private Bitcoin mBitcoin;
    private BitcoinService.CallBacks mServiceCallBacks;
    private BitcoinService mService;
    private boolean mServiceIsBound;
    private ServiceConnection mServiceConnection;
    private boolean mFinished;
    private JobParameters mJobParameters;

    private boolean startBitcoinService()
    {
        if(mBitcoin.isRunning())
            return false;

        Intent intent = new Intent(this, BitcoinService.class);
        intent.putExtra("FinishMode", Bitcoin.FINISH_ON_SYNC);
        startService(intent);

        if(!mServiceIsBound)
        {
            Log.d(logTag, "Binding Bitcoin service");
            bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }

        return true;
    }

    @Override
    public void onCreate()
    {
        mBitcoin = ((MainApp)getApplication()).bitcoin;

        mServiceCallBacks = new BitcoinService.CallBacks()
        {
            @Override
            public void onLoad()
            {
            }

            @Override
            public boolean onTransactionUpdate(int pWalletOffset, Transaction pTransaction)
            {
                return true;
            }

            @Override
            public boolean onUpdate()
            {
                return true;
            }

            @Override
            public void onFinish()
            {
                if(!mFinished)
                {
                    jobFinished(mJobParameters, false);
                    mFinished = true;
                }
            }
        };

        mServiceIsBound = false;
        mService = null;
        mServiceConnection = new ServiceConnection()
        {
            @Override
            public void onServiceConnected(ComponentName pComponentName, IBinder pBinder)
            {
                Log.d(logTag, "Bitcoin service connected");
                mServiceIsBound = true;
                mService = ((BitcoinService.LocalBinder)pBinder).getService();
                mService.setCallBacks(mServiceCallBacks);
            }

            @Override
            public void onServiceDisconnected(ComponentName pComponentName)
            {
                Log.d(logTag, "Bitcoin service disconnected");
                mServiceIsBound = true;
                mService.removeCallBacks(mServiceCallBacks);
                mService = null;
                if(!mFinished)
                {
                    jobFinished(mJobParameters, false);
                    mFinished = true;
                }
            }
        };

        super.onCreate();
    }

    @Override
    public void onDestroy()
    {
        if(mServiceIsBound)
        {
            Log.d(logTag, "Unbinding Bitcoin service");
            unbindService(mServiceConnection);
        }
        super.onDestroy();
    }

    @Override
    public boolean onStartJob(JobParameters pParams)
    {
        Log.i(logTag, "Starting job");
        mJobParameters = pParams;
        mFinished = false;

        if(!startBitcoinService())
            return false;

        new FiatRateRequestTask(getFilesDir()).execute();

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters pParams)
    {
        Log.i(logTag, "Stopping job from stop job request");
        if(mBitcoin.finishMode() == Bitcoin.FINISH_ON_SYNC)
            mBitcoin.stop();
        else
            jobFinished(mJobParameters, false);
        return false;
    }
}
