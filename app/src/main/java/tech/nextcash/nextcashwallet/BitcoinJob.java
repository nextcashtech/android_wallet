package tech.nextcash.nextcashwallet;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
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
    private boolean mServiceIsBound, mServiceIsBinding;
    private ServiceConnection mServiceConnection;
    private boolean mFinished;
    private JobParameters mJobParameters;

    @Override
    public void onCreate()
    {
        mBitcoin = ((MainApp)getApplication()).bitcoin;

        mServiceCallBacks = new BitcoinService.CallBacks()
        {
            @Override
            public void onWalletsLoad()
            {
            }

            @Override
            public void onChainLoad()
            {
            }

            @Override
            public boolean onTransactionUpdate(int pWalletOffset, Transaction pTransaction)
            {
                return false;
            }

            @Override
            public boolean onUpdate()
            {
                return false;
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
        mServiceIsBinding = false;
        mService = null;
        mServiceConnection = new ServiceConnection()
        {
            @Override
            public void onServiceConnected(ComponentName pComponentName, IBinder pBinder)
            {
                Log.d(logTag, "Bitcoin service connected");
                mService = ((BitcoinService.LocalBinder)pBinder).getService();
                mService.setCallBacks(mServiceCallBacks);
                mServiceIsBound = true;
                mServiceIsBinding = false;
            }

            @Override
            public void onServiceDisconnected(ComponentName pComponentName)
            {
                Log.d(logTag, "Bitcoin service disconnected");
                mServiceIsBound = false;
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
        if(mServiceIsBound || mServiceIsBinding)
        {
            Log.d(logTag, "Unbinding Bitcoin service");
            mService.removeCallBacks(mServiceCallBacks);
            mServiceIsBound = false;
            unbindService(mServiceConnection);
            mService = null;
        }
        super.onDestroy();
    }

    private boolean startBitcoinService()
    {
        if(mBitcoin.appIsOpen)
            return false;

        if(!mServiceIsBound && !mServiceIsBinding)
        {
            Log.d(logTag, "Binding Bitcoin service");
            mServiceIsBinding = true;
            Intent intent = new Intent(this, BitcoinService.class);
            intent.putExtra("FinishMode", Bitcoin.FINISH_ON_SYNC);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(intent);
            else
                startService(intent);
            bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }

        return true;
    }

    @Override
    public boolean onStartJob(JobParameters pParams)
    {
        Log.i(logTag, "Starting job");
        mJobParameters = pParams;
        mFinished = false;

        if(!startBitcoinService())
        {
            Log.i(logTag, "Aborting job start");
            return false;
        }

        new FiatRateRequestTask(getApplicationContext()).execute();

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters pParams)
    {
        Log.i(logTag, "Stopping job from stop job request");
        if(!mBitcoin.appIsOpen)
        {
            mBitcoin.stop();
            Log.i(logTag, "Stopping bitcoin because app is not open");
        }
        else
            jobFinished(mJobParameters, false);
        return false;
    }
}
