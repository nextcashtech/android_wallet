package tech.nextcash.nextcashwallet;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;


public class MainActivity extends AppCompatActivity
{
    public static final String logTag = "MainActivity";

    // Used to load the native libraries on application startup.
    static
    {
        System.loadLibrary("nextcash");
        System.loadLibrary("bitcoin");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        JobScheduler jobScheduler = (JobScheduler)getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobScheduler == null)
        {
            Log.e(logTag, "Failed to get Job Scheduler Service");
        }
        else
        {
            int syncFrequency = Settings.getInstance(getFilesDir()).intValue("sync_frequency");
            if(syncFrequency != -1)
            {
                JobInfo.Builder updateJobInfoBuilder = new JobInfo.Builder(MainService.UPDATE_JOB_ID, new ComponentName(this, MainService.class));
                updateJobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
                updateJobInfoBuilder.setPeriodic(syncFrequency * 60 * 1000);
                jobScheduler.schedule(updateJobInfoBuilder.build());
            }
        }
    }

    @Override
    public void onStart()
    {
        super.onStart();
        JobScheduler jobScheduler = (JobScheduler)getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobScheduler == null)
        {
            Log.e(logTag, "Failed to get Job Scheduler Service");
        }
        else
        {
            JobInfo.Builder monitorJobInfoBuilder = new JobInfo.Builder(MainService.MONITOR_JOB_ID, new ComponentName(this, MainService.class));
            monitorJobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            monitorJobInfoBuilder.setRequiresDeviceIdle(true);
            jobScheduler.schedule(monitorJobInfoBuilder.build());
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch(item.getItemId())
        {
        case R.id.action_settings:
        {
            Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
            startActivity(intent);
            return true;
        }
        case R.id.action_add_wallet:
        {
            Intent intent = new Intent(getApplicationContext(), AddWalletActivity.class);
            startActivity(intent);
            return true;
        }
        }

        return super.onOptionsItemSelected(item);
    }
}
