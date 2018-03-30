package tech.nextcash.nextcashwallet;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;


public class MainService extends JobService implements Runnable
{
    private static final String logTag = "MainService";

    public static final int MONITOR_JOB_ID = 91; // Watch for incoming pending transactions (while app is open)
    public static final int UPDATE_JOB_ID = 92; // Sync to latest block and exit (background only)

    private JobParameters mJobParameters;
    private static Thread mMainThread;
    private int mCurrentJobID;
    private String mPath;
    private boolean mIsJob;

    // Used to load the native libraries on application startup.
    static
    {
        System.loadLibrary("nextcash");
        System.loadLibrary("bitcoin");
        System.loadLibrary("nextcash_jni");
    }

    public MainService()
    {
        mIsJob = false;
    }

    @Override
    public boolean onStartJob(JobParameters pParams)
    {
        mIsJob = true;
        mJobParameters = pParams;
        return start(getFilesDir().getPath(), pParams.getJobId());
    }

    @Override
    public boolean onStopJob(JobParameters pParams)
    {
        if(mMainThread == null || !mMainThread.isAlive())
        {
            Log.w(logTag, "Main thread stop requested while not running");
            return false;
        }

        // Tell thread to stop
        Log.i(logTag, "Main thread stop requested");
        Bitcoin.stop();
        return true;
    }

    public boolean start(String pPath, int pJobID)
    {
        if(mMainThread != null && mMainThread.isAlive())
        {
            if(pJobID == MONITOR_JOB_ID && mCurrentJobID == UPDATE_JOB_ID)
            {
                // Upgrade to monitor
                mCurrentJobID = pJobID;
                Bitcoin.setFinishMode(Bitcoin.FINISH_ON_REQUEST);

                Log.i(logTag, "Upgraded to monitoring");
            }
            Log.i(logTag, "Main thread already running");
            return false;
        }
        else
        {
            Log.i(logTag, "Starting main thread");
            mCurrentJobID = pJobID;
            mPath = pPath;
            mMainThread = new Thread(this, "MainServiceThread");
            mMainThread.start();
            return true;
        }
    }

    public void stopMonitoring()
    {
        mCurrentJobID = UPDATE_JOB_ID;
        Bitcoin.setFinishMode(Bitcoin.FINISH_ON_SYNC);
    }

    @Override
    public void run()
    {
        Log.i(logTag, "Bitcoin thread starting");

        // Set path
        Bitcoin.setPath(mPath);

        // Set IP to "unknown" value
        byte[] ip = new byte[16];
        for(int i=0;i<16;i++)
            ip[i] = 0;
        ip[10] = -1;
        ip[11] = -1;
        ip[12] = 127;
        ip[13] = 0;
        ip[14] = 0;
        ip[15] = 1;
        Bitcoin.setIP(ip);

        int finishMode = Bitcoin.FINISH_ON_REQUEST;
        if(mCurrentJobID == UPDATE_JOB_ID)
            finishMode = Bitcoin.FINISH_ON_SYNC;
        Bitcoin.run(finishMode);
        Log.i(logTag, "Bitcoin thread finished");
        if(mIsJob)
            jobFinished(mJobParameters, false);
    }
}
