package tech.nextcash.nextcashwallet;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import java.util.Locale;


public class MainActivity extends AppCompatActivity
{
    public static final String logTag = "MainActivity";

    private Handler mStatusUpdateHandler;
    private Runnable mStatusUpdateRunnable;
    private MainService mMainService;

    // Used to load the native libraries on application startup.
    static
    {
        System.loadLibrary("nextcash");
        System.loadLibrary("bitcoin");
        System.loadLibrary("nextcash_jni");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mStatusUpdateHandler = new Handler();
        mStatusUpdateRunnable = new Runnable() { public void run() { updateStatus(); } };
        mMainService = new MainService();

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

        if(!Bitcoin.isLoaded())
            new BitcoinLoader().execute(getFilesDir().getPath());

        mMainService.start(getFilesDir().getPath(), MainService.MONITOR_JOB_ID);

        updateStatus();
    }

    @Override
    public void onStop()
    {
        mMainService.stopMonitoring();
        super.onStop();
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

    private void updateStatus()
    {
        TextView status = findViewById(R.id.status);

        int blockHeight = Bitcoin.blockHeight();
        int merkleHeight = Bitcoin.merkleHeight();

        switch(Bitcoin.status())
        {
            default:
            case 0: // Inactive
                status.setText(R.string.inactive);
                break;
            case 1: // Loading
                status.setText(R.string.loading);
                break;
            case 2: // Finding peers
                status.setText(R.string.finding_peers);
                break;
            case 3: // Connecting to peers
                status.setText(R.string.connecting_to_peers);
                break;
            case 4: // Synchronizing
                status.setText(R.string.synchronizing);
                break;
            case 5: // Synchronized
                if(merkleHeight != 0 && merkleHeight < blockHeight)
                    status.setText(R.string.looking_for_transactions);
                else
                    status.setText(R.string.synchronized_text);
                break;
        }

        TextView blocks = findViewById(R.id.blockHeight);
        blocks.setText(String.format(Locale.getDefault(), "%,d / %,d", merkleHeight, blockHeight));

        // Run again in 2 seconds
        mStatusUpdateHandler.postDelayed(mStatusUpdateRunnable, 2000);
    }
}
