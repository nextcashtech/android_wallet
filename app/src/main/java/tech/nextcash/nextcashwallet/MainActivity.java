package tech.nextcash.nextcashwallet;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
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
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Locale;


public class MainActivity extends AppCompatActivity
{
    public static final String logTag = "MainActivity";
    public static final int SETTINGS_REQUEST_CODE = 20;

    private Handler mDelayHandler;
    private Runnable mStatusUpdateRunnable, mClearFinishOnBack, mClearNotification;

    private enum Mode { LOADING, WALLETS, ADD_WALLET, EDIT_WALLET, HISTORY, SETUP }
    private Mode mMode;
    private Bitcoin mBitcoin;
    private boolean mFinishOnBack;
    private Bitcoin.CallBacks mBitcoinCallBacks;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mBitcoin = ((MainApp)getApplication()).bitcoin;
        mDelayHandler = new Handler();
        mStatusUpdateRunnable = new Runnable() { @Override public void run() { updateStatus(); } };
        mClearFinishOnBack = new Runnable() { @Override public void run() { mFinishOnBack = false; } };
        mClearNotification = new Runnable()
        {
            @Override
            public void run()
            {
                findViewById(R.id.notification).setVisibility(View.GONE);
            }
        };
        mMode = Mode.LOADING;
        mFinishOnBack = false;

        mBitcoinCallBacks = new Bitcoin.CallBacks()
        {
            @Override
            public void onLoad()
            {
            }

            @Override
            public boolean onTransactionUpdate(int pWalletOffset, Transaction pTransaction)
            {
                showMessage(pTransaction.description(getApplicationContext()), 2000);
                return true;
            }

            @Override
            public void onFinish()
            {
                mBitcoin.clearProgress(getApplicationContext());
                mBitcoin.clearCallBacks(mBitcoinCallBacks);
            }
        };

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
        stopService(new Intent(this, BitcoinJob.class));
        if(!mBitcoin.start(Bitcoin.FINISH_ON_REQUEST))
            mBitcoin.setFinishMode(Bitcoin.FINISH_ON_REQUEST);
        mBitcoin.setCallBacks(mBitcoinCallBacks);
    }

    @Override
    public void onResume()
    {
        updateStatus();
        super.onResume();
    }

    @Override
    public void onNewIntent(Intent pIntent)
    {
        super.onNewIntent(pIntent);
    }

    @Override
    public void onPause()
    {
        mDelayHandler.removeCallbacks(mStatusUpdateRunnable);
        super.onPause();
    }

    @Override
    public void onStop()
    {
        mBitcoin.setFinishModeNoCreate(Bitcoin.FINISH_ON_SYNC);
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
                mBitcoin.start(Bitcoin.FINISH_ON_REQUEST);
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
                status.setText(R.string.looking_for_blocks);
                break;
            case 5: // Synchronized
                status.setText(R.string.synchronized_text);
                break;
            case 6: // Finding Transactions
                status.setText(R.string.looking_for_transactions);
                break;
        }

        boolean isLoaded = mBitcoin.isLoaded();
        TextView blocks = findViewById(R.id.blockHeight);
        TextView peerCount = findViewById(R.id.peerCount);
        if(isLoaded)
        {
            blocks.setText(String.format(Locale.getDefault(), "%,d / %,d", mBitcoin.merkleHeight(),
              mBitcoin.blockHeight()));
            peerCount.setText(String.format(Locale.getDefault(), "%,d", mBitcoin.peerCount()));
        }
        else
        {
            blocks.setText("- / -");
            peerCount.setText("-");
        }

        switch(mMode)
        {
        case LOADING:
        case WALLETS:
            if(isLoaded && mBitcoin.update(getApplicationContext()))
                updateWallets();
            break;
        default:
            break;
        }

        // Run again in 2 seconds
        mDelayHandler.postDelayed(mStatusUpdateRunnable, 2000);
    }

    public void updateWallets()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup content = findViewById(R.id.content), transactions;
        View view;
        boolean addButtonNeeded = false, addView;
        String name;

        findViewById(R.id.progress).setVisibility(View.GONE);

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

        if(mBitcoin.wallets != null)
        {
            int offset = 0;
            for(Wallet wallet : mBitcoin.wallets)
            {
                addView = false;
                view = null;

                if(mMode == Mode.WALLETS)
                    view = content.findViewWithTag(offset);

                if(view == null)
                {
                    // Remove add wallet button
                    view = content.findViewWithTag(R.id.addWallet);
                    if(view != null)
                        content.removeView(view);
                    addButtonNeeded = true;

                    // Add wallet
                    view = inflater.inflate(R.layout.wallet_item, content, false);
                    view.setTag(offset);
                    addView = true;

                    view.findViewById(R.id.walletDetails).setVisibility(View.GONE);
                }

                ((TextView)view.findViewById(R.id.walletBalance)).setText(String.format(Locale.getDefault(),
                  "%,.5f", Bitcoin.bitcoins(wallet.balance)));

                name = wallet.name;
                if(name == null || name.length() == 0)
                    ((TextView)view.findViewById(R.id.walletName)).setText(String.format(Locale.getDefault(),
                      "Wallet %d", offset + 1));
                else
                    ((TextView)view.findViewById(R.id.walletName)).setText(name);

                if(!wallet.isPrivate)
                {
                    view.findViewById(R.id.walletLocked).setVisibility(View.VISIBLE);
                    view.findViewById(R.id.walletSend).setVisibility(View.GONE);
                    view.findViewById(R.id.walletLockedMessage).setVisibility(View.VISIBLE);
                }

                transactions = view.findViewById(R.id.walletTransactions);
                populateTransactions(transactions, wallet.transactions,
                  3);

                if(addView)
                    content.addView(view);

                alignTransactions(transactions);

                offset++;
            }
        }

        if(addButtonNeeded)
        {
            view = inflater.inflate(R.layout.button, content, false);
            view.setTag(R.id.addWallet);
            ((TextView)view.findViewById(R.id.title)).setText(R.string.add_wallet);
            content.addView(view);
        }

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

    public void displayEditWallet(int pKeyOffset)
    {
        View button;
        ViewGroup editName;
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup contentView = findViewById(R.id.content);

        contentView.removeAllViews();

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_edit_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.title_edit_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        // Edit Name
        editName = (ViewGroup)inflater.inflate(R.layout.edit_name, contentView, false);
        ((EditText)editName.findViewById(R.id.name)).setText(mBitcoin.wallets[pKeyOffset].name);
        editName.findViewById(R.id.name).setTag(pKeyOffset);

        contentView.addView(editName);

        // Update button
        button = inflater.inflate(R.layout.button, contentView, false);
        button.setTag(R.id.updateWallet);
        ((TextView)button.findViewById(R.id.title)).setText(R.string.update_wallet);
        contentView.addView(button);

        // Backup button
        button = inflater.inflate(R.layout.button, contentView, false);
        button.setTag(R.id.backupWallet);
        ((TextView)button.findViewById(R.id.title)).setText(R.string.backup_wallet);
        contentView.addView(button);

        // Remove button
        button = inflater.inflate(R.layout.button, contentView, false);
        button.setTag(R.id.removeWallet);
        ((TextView)button.findViewById(R.id.title)).setText(R.string.remove_wallet);
        contentView.addView(button);

        mMode = Mode.EDIT_WALLET;
    }

    public void alignColumns(ViewGroup pView, int pColumnCount)
    {
        ViewGroup row;
        int rowCount = pView.getChildCount();
        TextView entry;
        int widest[] = new int[pColumnCount];
        int width;
        boolean shade = false;

        // Find widest columns
        for(int rowOffset = 0; rowOffset < rowCount; rowOffset++)
        {
            row = (ViewGroup)pView.getChildAt(rowOffset);

            for(int columnOffset = 0; columnOffset < pColumnCount; columnOffset++)
            {
                entry = (TextView)row.getChildAt(columnOffset);
                entry.measure(0, 0);
                width = entry.getMeasuredWidth();
                if(width > widest[columnOffset])
                    widest[columnOffset] = width;
            }
        }

        // Set column widths to align
        for(int rowOffset = 0; rowOffset < rowCount; rowOffset++)
        {
            row = (ViewGroup)pView.getChildAt(rowOffset);

            for(int columnOffset = 0; columnOffset < pColumnCount; columnOffset++)
            {
                entry = (TextView)row.getChildAt(columnOffset);
                entry.setWidth(widest[columnOffset]);
            }

            if(shade)
                row.setBackgroundColor(getResources().getColor(R.color.rowShade));

            shade = !shade;
        }
    }

    public void alignTransactions(ViewGroup pView)
    {
        ViewGroup pending = pView.findViewById(R.id.walletPending);
        if(pending.getVisibility() != View.GONE)
            alignColumns(pending, 3);

        ViewGroup recent = pView.findViewById(R.id.walletRecent);
        if(recent.getVisibility() != View.GONE)
            alignColumns(recent, 3);
    }

    public void populateTransactions(ViewGroup pView, Transaction[] pTransactions, int pLimit)
    {
        LayoutInflater inflater = getLayoutInflater();
        int pendingCount, recentCount;
        ViewGroup recentView, pendingView, transactionViewGroup;
        View transactionView;

        // Add/Update transactions
        recentView = pView.findViewById(R.id.walletRecent);
        recentView.removeAllViews();
        pendingView = pView.findViewById(R.id.walletPending);
        pendingView.removeAllViews();

        pendingCount = 0;
        recentCount = 0;

        for(Transaction transaction : pTransactions)
        {
            if(transaction.block == null)
            {
                transactionViewGroup = pendingView;
                pendingCount++;
                if(pendingCount == 1)
                {
                    // Add header line
                    transactionView = inflater.inflate(R.layout.wallet_transaction_header, transactionViewGroup,
                      false);
                    ((TextView)transactionView.findViewById(R.id.count)).setText(R.string.peers_title);
                    transactionViewGroup.addView(transactionView);
                }
            }
            else
            {
                transactionViewGroup = recentView;
                recentCount++;
                if(recentCount > pLimit)
                    continue;
                if(recentCount == 1)
                {
                    // Add header line
                    transactionView = inflater.inflate(R.layout.wallet_transaction_header, transactionViewGroup,
                      false);
                    ((TextView)transactionView.findViewById(R.id.count)).setText(R.string.confirms_title);
                    transactionViewGroup.addView(transactionView);
                }
            }

            transactionView = inflater.inflate(R.layout.wallet_transaction, transactionViewGroup,
              false);

            transaction.updateView(this, transactionView);

            // Set tag with transaction offset
            if(transaction.block == null)
                transactionView.setTag(pendingCount - 1);
            else
                transactionView.setTag(recentCount - 1);

            transactionViewGroup.addView(transactionView);
        }

        if(pendingCount > 0)
        {
            pView.findViewById(R.id.walletPendingTitle).setVisibility(View.VISIBLE);
            pendingView.setVisibility(View.VISIBLE);
        }
        else
        {
            pView.findViewById(R.id.walletPendingTitle).setVisibility(View.GONE);
            pendingView.setVisibility(View.GONE);
        }

        if(recentCount > 0)
        {
            pView.findViewById(R.id.walletRecentTitle).setVisibility(View.VISIBLE);
            recentView.setVisibility(View.VISIBLE);
        }
        else
        {
            pView.findViewById(R.id.walletRecentTitle).setVisibility(View.GONE);
            recentView.setVisibility(View.GONE);
        }

        if(pendingCount > 0 || recentCount > 0)
            pView.findViewById(R.id.walletNoTransTitle).setVisibility(View.GONE);
        else
            pView.findViewById(R.id.walletNoTransTitle).setVisibility(View.VISIBLE);
    }

    public void displayWalletHistory(int pOffset)
    {
        if(pOffset >= mBitcoin.wallets.length)
            return;

        ViewGroup historyView;
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup contentView = findViewById(R.id.content);

        contentView.removeAllViews();

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_history_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.title_wallet_history));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        Wallet wallet = mBitcoin.wallets[pOffset];

        historyView = (ViewGroup)inflater.inflate(R.layout.wallet_history, contentView, false);
        ((TextView)historyView.findViewById(R.id.title)).setText(wallet.name);

        ViewGroup transactions = historyView.findViewById(R.id.walletTransactions);
        populateTransactions(transactions, wallet.transactions,
          100);

        contentView.addView(historyView);

        alignTransactions(transactions);

        mMode = Mode.HISTORY;
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
                        actionBar.setIcon(R.drawable.ic_add_black_36dp);
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
                case R.id.updateWallet:
                {
                    ViewGroup contentView = findViewById(R.id.content);
                    EditText nameView = contentView.findViewById(R.id.name);
                    String name = nameView.getText().toString();
                    if((int)nameView.getTag() < mBitcoin.wallets.length &&
                      mBitcoin.setName((int)nameView.getTag(), name))
                        updateWallets();
                    else
                        showMessage(getString(R.string.failed_update_name), 2000);
                    break;
                }
                case R.id.backupWallet:
                    //TODO Provide seed to user
                    break;
                case R.id.removeWallet:
                    //TODO Delete wallet
                    break;
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
                    showMessage(getString(R.string.failed_key_import), 2000);
                    break;
                case 2: // Invalid format
                    showMessage(getString(R.string.failed_key_import_format), 2000);
                    break;
                case 3: // Already exists
                    showMessage(getString(R.string.failed_key_import_exists), 2000);
                    break;
                case 4: // Invalid derivation method
                    showMessage(getString(R.string.failed_key_import_method), 2000);
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
                    {
                        ((ImageView)pView.findViewById(R.id.walletExpand)).setImageResource(R.drawable.ic_expand_less_white_36dp);
                        walletDetails.setVisibility(View.VISIBLE);
                    }
                    else
                    {
                        ((ImageView)pView.findViewById(R.id.walletExpand)).setImageResource(R.drawable.ic_expand_more_white_36dp);
                        walletDetails.setVisibility(View.GONE);
                    }
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
            ViewGroup wallet = (ViewGroup)pView.getParent().getParent().getParent();
            displayWalletHistory((int)wallet.getTag());
            break;
        }
        case R.id.walletEdit:
        {
            ViewGroup wallet = (ViewGroup)pView.getParent().getParent().getParent();
            displayEditWallet((int)wallet.getTag());
            break;
        }
        case R.id.walletLocked:
            showMessage(getString(R.string.locked_message), 2000);
            break;
        case R.id.walletTransaction:
            // TODO Show transaction details
            break;
        }
    }

    public void showMessage(String pText, int pDelay)
    {
        ((TextView)findViewById(R.id.notificationText)).setText(pText);
        findViewById(R.id.notification).setVisibility(View.VISIBLE);
        mDelayHandler.postDelayed(mClearNotification, pDelay);
    }

    @Override
    public void onBackPressed()
    {
        if(mMode != Mode.WALLETS && mMode != Mode.LOADING)
            updateWallets(); // Go back to main wallets view
        else if(mFinishOnBack)
        {
            mBitcoin.stop();
            super.onBackPressed();
        }
        else
        {
            mFinishOnBack = true;
            showMessage(getString(R.string.double_tap_back), 1000);
            mDelayHandler.postDelayed(mClearFinishOnBack, 1000);
        }
    }
}
