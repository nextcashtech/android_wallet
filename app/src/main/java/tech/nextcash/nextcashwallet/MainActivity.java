package tech.nextcash.nextcashwallet;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
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
    public static final int SETTINGS_REQUEST_CODE = 20;

    private Handler mStatusUpdateHandler;
    private Runnable mStatusUpdateRunnable;

    private enum Mode { LOADING, WALLETS, ADD_WALLET, SETUP }
    private Mode mMode;
    private long mTotalBalance;
    private Bitcoin mBitcoin;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mBitcoin = ((MainApp)getApplication()).bitcoin;
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
                    if(current == null)
                        scheduleNeeded = true;
                    else if(!current.isPeriodic() || current.getIntervalMillis() != syncFrequency * 60 * 1000 ||
                      current.getNetworkType() != JobInfo.NETWORK_TYPE_ANY)
                    {
                        jobScheduler.cancel(BitcoinJob.SYNC_JOB_ID);
                        scheduleNeeded = true;
                    }
                }
                else
                {
                    jobScheduler.cancel(BitcoinJob.SYNC_JOB_ID);
                    scheduleNeeded = true;
                }

//                if(scheduleNeeded)
//                {
//                    JobInfo.Builder updateJobInfoBuilder = new JobInfo.Builder(BitcoinJob.SYNC_JOB_ID,
//                      new ComponentName(this, BitcoinJob.class));
//                    updateJobInfoBuilder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
//                    updateJobInfoBuilder.setPeriodic(syncFrequency * 60 * 1000);
//                    jobScheduler.schedule(updateJobInfoBuilder.build());
//                }
            }

            return true;
        }
    }

    @Override
    public void onStart()
    {
        super.onStart();
        if(!mBitcoin.start(Bitcoin.FINISH_ON_REQUEST))
            mBitcoin.setFinishMode(Bitcoin.FINISH_ON_REQUEST);
    }

    @Override
    public void onResume()
    {
        updateStatus();
        super.onResume();
    }

    @Override
    public void onPause()
    {
        mStatusUpdateHandler.removeCallbacks(mStatusUpdateRunnable);
        super.onPause();
    }

    @Override
    public void onStop()
    {
        mBitcoin.setFinishMode(Bitcoin.FINISH_ON_SYNC);
        super.onStop();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    private void updateStatus()
    {
        TextView status = findViewById(R.id.status);

        switch(mBitcoin.status())
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
        if(mBitcoin.isLoaded())
            blocks.setText(String.format(Locale.getDefault(), "%,d / %,d", mBitcoin.merkleHeight(),
              mBitcoin.blockHeight()));
        else
            blocks.setText("- / -");

        switch(mMode)
        {
        case LOADING:
            if(mBitcoin.isLoaded())
                updateWallets();
            break;
        case WALLETS:
            if(mTotalBalance != mBitcoin.balance())
            {
                // TODO Notify of new transactions. Possibly disable wallets in recovery mode that haven't synced yet.
                updateWallets();
            }
            break;
        case ADD_WALLET:
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
        View view;
        boolean addButtonNeeded = false, addView;

        if(mMode != Mode.WALLETS)
        {
            // Rebuild all wallets
            addButtonNeeded = true;
            content.removeAllViews();

            // Setup action bar
            ActionBar actionBar = getSupportActionBar();
            if(actionBar != null)
            {
                actionBar.setIcon(null);
                actionBar.setTitle(getResources().getString(R.string.app_name));
                actionBar.setDisplayHomeAsUpEnabled(false); // Show the Up button in the action bar.
            }
        }

        for(int i=0;i<mBitcoin.keyCount();i++)
        {
            addView = false;
            view = null;

            if(mMode == Mode.WALLETS)
                view = content.findViewWithTag(i);

            if(view == null)
            {
                // Remove add wallet button
                view = content.findViewWithTag(R.id.addWallet);
                if(view != null)
                    content.removeView(view);
                addButtonNeeded = true;

                // Add wallet
                view = inflater.inflate(R.layout.wallet_item, content, false);
                view.setTag(i);
                addView = true;
            }

            ((TextView)view.findViewById(R.id.walletBalance)).setText(String.format(Locale.getDefault(), "%,.5f",
              Bitcoin.bitcoins(mBitcoin.keyBalance(i, true))));

            ((TextView)view.findViewById(R.id.walletName)).setText(String.format(Locale.getDefault(),
              "Wallet %d", i + 1));

            //TODO Add/Update recent transactions

            if(addView)
                content.addView(view);
        }

        if(addButtonNeeded)
        {
            view = inflater.inflate(R.layout.button, content, false);
            view.setTag(R.id.addWallet);
            ((TextView)view.findViewById(R.id.title)).setText(R.string.add_wallet);
            content.addView(view);
        }

        mTotalBalance = mBitcoin.balance();
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
            case android.R.id.home:
                updateWallets();
                return true;
            case R.id.action_settings:
            {
                Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
                startActivityForResult(intent, SETTINGS_REQUEST_CODE);
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
            case SETTINGS_REQUEST_CODE:
                if(pResultCode > 0) // Sync frequency updated
                    scheduleJobs();
                break;
            default:
                super.onActivityResult(pRequestCode, pResultCode, pData);
                break;
        }
    }

    public void onClick(View pView)
    {
        switch(pView.getId())
        {
        default:
            break;
        case R.id.button:
            int tag = 0;
            if(pView.getTag() != null)
                tag = (Integer)pView.getTag();

            switch(tag)
            {
                default:
                    break;
                case R.id.addWallet:
                {
                    // Display options for adding wallets
                    View button;
                    LayoutInflater inflater = getLayoutInflater();
                    ViewGroup contentView = findViewById(R.id.content);

                    contentView.removeAllViews();

                    button = inflater.inflate(R.layout.button, contentView, false);
                    button.setTag(R.id.createWallet);
                    ((TextView)button.findViewById(R.id.title)).setText(R.string.create_new_key);
                    contentView.addView(button);

                    button = inflater.inflate(R.layout.button, contentView, false);
                    button.setTag(R.id.recoverWallet);
                    ((TextView)button.findViewById(R.id.title)).setText(R.string.recover_wallet);
                    contentView.addView(button);

                    button = inflater.inflate(R.layout.button, contentView, false);
                    button.setTag(R.id.importWallet);
                    ((TextView)button.findViewById(R.id.title)).setText(R.string.import_bip0032_key);
                    contentView.addView(button);

                    ActionBar actionBar = getSupportActionBar();
                    if(actionBar != null)
                    {
                        actionBar.setIcon(R.drawable.ic_add_black_24dp);
                        actionBar.setTitle(" " + getResources().getString(R.string.title_add_wallet));
                        actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
                    }

                    mMode = Mode.ADD_WALLET;
                    break;
                }
                case R.id.createWallet:
                    break;
                case R.id.recoverWallet:
                    break;
                case R.id.importWallet:
                {
                    LayoutInflater inflater = getLayoutInflater();
                    ViewGroup contentView = findViewById(R.id.content);

                    contentView.removeAllViews();
                    inflater.inflate(R.layout.import_bip32_key, contentView, true);
                    focusOnText(R.id.importText);

                    Spinner derivationMethod = findViewById(R.id.derivationMethodSpinner);
                    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                      R.array.derivation_methods, android.R.layout.simple_spinner_item);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    derivationMethod.setAdapter(adapter);
                    break;
                }
            }
            break;
        case R.id.importButton: // Import BIP-0032 encoded key
        {
            String encodedKey = ((EditText)findViewById(R.id.importText)).getText().toString();
            switch(mBitcoin.addKey(encodedKey,
              ((Spinner)findViewById(R.id.derivationMethodSpinner)).getSelectedItemPosition()))
            {
                case 0: // Success
                    updateWallets(); // Rebuild view showing wallets
                    break;
                case 1: // Unknown error
                    Toast.makeText(this, R.string.failed_key_import, Toast.LENGTH_LONG).show();
                    break;
                case 2: // Invalid format
                    Toast.makeText(this, R.string.failed_key_import_format, Toast.LENGTH_LONG).show();
                    break;
                case 3: // Already exists
                    Toast.makeText(this, R.string.failed_key_import_exists, Toast.LENGTH_LONG).show();
                    break;
                case 4: // Invalid derivation method
                    Toast.makeText(this, R.string.failed_key_import_method, Toast.LENGTH_LONG).show();
                    break;
            }
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
        }
    }

    @Override
    public void onBackPressed()
    {
        if(mMode == Mode.ADD_WALLET)
            updateWallets(); // Go back to main wallets view
        else
        {
            mBitcoin.stop();
            super.onBackPressed();
        }
    }
}
