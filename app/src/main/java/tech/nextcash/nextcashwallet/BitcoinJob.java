package tech.nextcash.nextcashwallet;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;


/***********************************************************************************************************************
 * This is the main service for running the Bitcoin SPV node. It can be used directly as just a background thread by
 * calling start from the main activity, or as a job service by scheduling it with the job scheduler.
 **********************************************************************************************************************/
public class BitcoinJob extends JobService
{
    private static final String logTag = "BitcoinJob";

    public static final int SYNC_JOB_ID = 92; // Sync to latest block and exit (background only)

    JobParameters mJobParameters;
    Thread mWaitForStopThread;

    private Runnable mWaitForStopRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            while(Bitcoin.isRunning())
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
        return Bitcoin.start(getFilesDir().getPath(), pParams.getJobId());
    }

    @Override
    public boolean onStopJob(JobParameters pParams)
    {
        Log.i(logTag, "Stopping job");
        mJobParameters = pParams;
        if(Bitcoin.requestStop())
        {
            mWaitForStopThread = new Thread(mWaitForStopRunnable, "BitcoinJobWaitForStopThread");
            mWaitForStopThread.start();
            return true;
        }
        else
            return false;
    }
}
