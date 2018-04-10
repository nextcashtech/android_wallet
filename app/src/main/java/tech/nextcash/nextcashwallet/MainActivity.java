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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;


public class MainActivity extends AppCompatActivity
{
    public static final String logTag = "MainActivity";
    public static final int ADD_WALLET_REQUEST_CODE = 10;
    public static final int SETTINGS_REQUEST_CODE = 20;

    private Handler mStatusUpdateHandler;
    private Runnable mStatusUpdateRunnable;

    private enum Mode { LOADING, WALLETS, ADD_WALLETS, SETUP };
    private Mode mMode;
    private long mTotalBalance;

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
        mMode = Mode.LOADING;
        mTotalBalance = 0;

        scheduleJobs();
    }

    private boolean scheduleJobs()
    {
        JobScheduler jobScheduler = (JobScheduler)getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobScheduler == null)
        {
            Log.e(logTag, "Failed to get Job Scheduler Service");
            return false;
        }
        else
        {
            int syncFrequency = Settings.getInstance(getFilesDir()).intValue("sync_frequency");
            if(syncFrequency != -1)
            {
                if(syncFrequency == 0)
                    syncFrequency = 60; // Default of 60 minutes

                boolean scheduleNeeded = false;
                if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                {
                    JobInfo current = jobScheduler.getPendingJob(BitcoinJob.SYNC_JOB_ID);
                    if(current == null || !current.isPeriodic() ||
                      current.getIntervalMillis() != syncFrequency * 60 * 1000 ||
                      current.getNetworkType() != JobInfo.NETWORK_TYPE_ANY)
                        scheduleNeeded = true;
                }
                else
                {
                    jobScheduler.cancel(BitcoinJob.SYNC_JOB_ID);
                    scheduleNeeded = true;
                }

                if(scheduleNeeded)
                {
                    JobInfo.Builder updateJobInfoBuilder = new JobInfo.Builder(BitcoinJob.SYNC_JOB_ID,
                      new ComponentName(this, BitcoinJob.class));
                    updateJobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
                    updateJobInfoBuilder.setPeriodic(syncFrequency * 60 * 1000);
                    jobScheduler.schedule(updateJobInfoBuilder.build());
                }
            }

            return true;
        }
    }

    @Override
    public void onStart()
    {
        super.onStart();

        Bitcoin.start(getFilesDir().getPath(), Bitcoin.FINISH_ON_REQUEST);

        updateStatus();
    }

    @Override
    public void onResume()
    {
        Bitcoin.start(getFilesDir().getPath(), Bitcoin.FINISH_ON_REQUEST);
        super.onResume();
    }

    @Override
    public void onStop()
    {
        mStatusUpdateHandler.removeCallbacks(mStatusUpdateRunnable);
        super.onStop();
    }

    @Override
    protected void onDestroy()
    {
        Bitcoin.waitForStop();
        Bitcoin.destroy();
        NextCash.destroy();
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
        switch(item.getItemId())
        {
        case R.id.action_settings:
        {
            Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
            startActivityForResult(intent, SETTINGS_REQUEST_CODE);
            return true;
        }
        case R.id.action_add_wallet:
        {
            Intent intent = new Intent(getApplicationContext(), AddWalletActivity.class);
            startActivityForResult(intent, ADD_WALLET_REQUEST_CODE);
            return true;
        }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int pRequestCode, int pResultCode, Intent pData)
    {
        switch(pRequestCode)
        {
        case ADD_WALLET_REQUEST_CODE:
            if(pResultCode > 0) // Wallet added
                updateWallets();
            break;
        case SETTINGS_REQUEST_CODE:
            if(pResultCode > 0) // Sync frequency updated
                scheduleJobs();
            break;
        default:
            super.onActivityResult(pRequestCode, pResultCode, pData);
            break;
        }
    }

    private void updateStatus()
    {
        TextView status = findViewById(R.id.status);

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
                status.setText(R.string.synchronized_text);
                break;
            case 6: // Finding Transactions
                status.setText(R.string.looking_for_transactions);
                break;
        }

        TextView blocks = findViewById(R.id.blockHeight);
        blocks.setText(String.format(Locale.getDefault(), "%,d / %,d", Bitcoin.merkleHeight(),
          Bitcoin.blockHeight()));

        switch(mMode)
        {
        case LOADING:
            if(Bitcoin.isLoaded())
                updateWallets();
            break;
        case WALLETS:
            if(mTotalBalance != Bitcoin.balance())
                updateWallets();
            break;
        case ADD_WALLETS:
            break;
        case SETUP:
            break;
        }

        // Run again in 2 seconds
        mStatusUpdateHandler.postDelayed(mStatusUpdateRunnable, 2000);
    }

    public void updateWallets()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup content = findViewById(R.id.content);
        View wallet;

        if(mMode != Mode.WALLETS)
            content.removeAllViews();

        for(int i=0;i<Bitcoin.keyCount();i++)
        {
            wallet = null;

            if(mMode == Mode.WALLETS)
                wallet = content.findViewWithTag(i);

            if(wallet == null)
            {
                // Add wallet
                wallet = inflater.inflate(R.layout.wallet_item, content, true);
                wallet.setTag(i);
            }

            ((TextView)wallet.findViewById(R.id.walletBalance)).setText(String.format(Locale.getDefault(), "%,.2f",
              Bitcoin.bitcoins(Bitcoin.keyBalance(i, true))));

            ((TextView)wallet.findViewById(R.id.walletName)).setText(String.format(Locale.getDefault(),
              "Wallet %d", i));

            //TODO Add/Update recent transactions
        }

        mTotalBalance = Bitcoin.balance();
        mMode = Mode.WALLETS;
    }

    public void focusOnText(int pTextID)
    {
        EditText text = findViewById(pTextID);
        if(text.requestFocus())
        {
            InputMethodManager inputManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(inputManager != null)
                inputManager.showSoftInput(text, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void onClick(View pView)
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup contentView = findViewById(R.id.content);

        switch(pView.getId())
        {
        case R.id.createNewWallet:
            break;
        case R.id.recoverWallet:
            break;
        case R.id.importBIP32Key: // Show dialog for entering BIP-0032 encoded key
        {
            contentView.removeAllViews();
            inflater.inflate(R.layout.import_bip32_key, contentView);
            focusOnText(R.id.importText);

            Spinner derivationMethod = findViewById(R.id.derivationMethodSpinner);
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
              R.array.derivation_methods, android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            derivationMethod.setAdapter(adapter);
            break;
        }
        case R.id.importButton: // Import BIP-0032 encoded key
        {
            String encodedKey = ((EditText)findViewById(R.id.importText)).getText().toString();
            if(Bitcoin.addKey(encodedKey, ((Spinner)findViewById(R.id.derivationMethodSpinner)).getSelectedItemPosition()))
                updateWallets(); // Rebuild view showing wallets
            else
                Toast.makeText(this, R.string.failed_key_import, Toast.LENGTH_LONG).show();
            break;
        }
        case R.id.walletHeader: // Expand/Compress wallet
        {
            ViewGroup wallet = (ViewGroup)pView.getParent();
            if(wallet != null)
            {
                View walletDetails = wallet.findViewById(R.id.walletDetails);
                if(walletDetails != null)
                {
                    int visibility = walletDetails.getVisibility();
                    if(visibility == View.GONE)
                        walletDetails.setVisibility(View.VISIBLE);
                    else
                        walletDetails.setVisibility(View.GONE);
                }
            }
            break;
        }
        case R.id.walletSend:
        {
            break;
        }
        case R.id.walletReceive:
        {
            break;
        }
        case R.id.walletHistory:
        {
            break;
        }
        default:
            break;
        }
    }
}
