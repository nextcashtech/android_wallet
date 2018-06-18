package tech.nextcash.nextcashwallet;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.google.zxing.integration.android.IntentIntegrator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import java.util.TimeZone;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener,
  CompoundButton.OnCheckedChangeListener
{
    public static final String logTag = "MainActivity";
    public static final int SCAN_REQUEST_CODE = 30;

    private Handler mDelayHandler;
    private Runnable mStatusUpdateRunnable, mRateUpdateRunnable, mClearFinishOnBack, mClearNotification,
      mRequestExpiresUpdater;
    private enum Mode { LOADING, IN_PROGRESS, WALLETS, ADD_WALLET, CREATE_WALLET, VERIFY_SEED, BACKUP_WALLET,
      RECOVER_WALLET, EDIT_WALLET, HISTORY, TRANSACTION, RECEIVE, SEND, SETUP, AUTHORIZE, INFO, SETTINGS }
    private Mode mMode;
    private boolean mWalletsLoaded;
    private enum AuthorizedTask { NONE, ADD_KEY, BACKUP_KEY, REMOVE_KEY, SIGN_TRANSACTION }
    private AuthorizedTask mAuthorizedTask;
    private String mKeyToLoad, mSeed;
    private boolean mSeedIsRecovered, mSeedIsBackedUp;
    private int mCurrentWalletIndex;
    private boolean mSeedBackupOnly;
    private int mDerivationPathMethodToLoad;
    private double mFiatRate;
    private Bitcoin mBitcoin;
    private boolean mFinishOnBack;
    private BitcoinService.CallBacks mServiceCallBacks;
    private BitcoinService mService;
    private boolean mServiceIsBound, mServiceIsBinding;
    private ServiceConnection mServiceConnection;
    private IntentIntegrator mQRScanner;
    private PaymentRequest mPaymentRequest;
    private boolean mDontUpdatePaymentAmount;
    private TextWatcher mSeedWordWatcher, mAmountWatcher, mRequestAmountWatcher;
    private int mTransactionToShowWalletIndex;
    private String mTransactionToShow;


    public class TransactionRunnable implements Runnable
    {
        Transaction mTransaction;

        TransactionRunnable(Transaction pTransaction)
        {
            mTransaction = pTransaction;
        }

        @Override
        public void run()
        {
            showMessage(mTransaction.description(MainActivity.this), 2000);
            if(mMode == Mode.WALLETS)
                updateWallets();
        }
    }

    @Override
    protected void onCreate(Bundle pSavedInstanceState)
    {
        super.onCreate(pSavedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mBitcoin = ((MainApp)getApplication()).bitcoin;
        mDelayHandler = new Handler();
        mStatusUpdateRunnable = new Runnable() { @Override public void run() { updateStatus(); } };
        mRateUpdateRunnable = new Runnable() { @Override public void run() { startUpdateRates(); } };
        mClearFinishOnBack = new Runnable() { @Override public void run() { mFinishOnBack = false; } };
        mClearNotification = new Runnable()
        {
            @Override
            public void run()
            {
                findViewById(R.id.notification).setVisibility(View.GONE);
            }
        };
        mRequestExpiresUpdater = new Runnable()
        {
            @Override
            public void run()
            {
                updateRequestExpires();
            }
        };
        mMode = Mode.LOADING;
        mAuthorizedTask = AuthorizedTask.NONE;
        mFinishOnBack = false;
        mFiatRate = 0.0;
        mCurrentWalletIndex = -1;
        mWalletsLoaded = false;
        mDontUpdatePaymentAmount = false;

        mServiceCallBacks = new BitcoinService.CallBacks()
        {
            @Override
            public void onLoad()
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if(mMode == Mode.LOADING)
                            updateWallets();
                        if(mTransactionToShow != null)
                            openTransaction(mTransactionToShowWalletIndex, mTransactionToShow);
                    }
                });
            }

            @Override
            public boolean onTransactionUpdate(int pWalletOffset, Transaction pTransaction)
            {
                runOnUiThread(new TransactionRunnable(pTransaction));
                return true;
            }

            @Override
            public boolean onUpdate()
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if(mMode == Mode.WALLETS)
                            updateWallets();
                        updateStatus();
                    }
                });
                return true;
            }

            @Override
            public void onFinish()
            {

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
                mServiceIsBound = true;
                mServiceIsBinding = false;
                mService = ((BitcoinService.LocalBinder)pBinder).getService();
                mService.setCallBacks(mServiceCallBacks);
                mBitcoin.setFinishMode(Bitcoin.FINISH_ON_REQUEST);
            }

            @Override
            public void onServiceDisconnected(ComponentName pComponentName)
            {
                Log.d(logTag, "Bitcoin service disconnected");
                mServiceIsBound = false;
                mService.removeCallBacks(mServiceCallBacks);
                mService = null;
            }
        };

        mQRScanner = new IntentIntegrator(this);
        //mQRScanner.addExtra(); // TODO Find extra value that can switch to portrait mode

        scheduleJobs();

        startBitcoinService();
    }

    private void startBitcoinService()
    {
        if(!mServiceIsBound && !mServiceIsBinding)
        {
            Log.d(logTag, "Binding Bitcoin service");
            mServiceIsBinding = true;
            Intent intent = new Intent(this, BitcoinService.class);
            intent.putExtra("FinishMode", Bitcoin.FINISH_ON_REQUEST);
            bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }
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
            if(syncFrequency == -1)
                jobScheduler.cancel(BitcoinJob.SYNC_JOB_ID);
            else
            {
                if(syncFrequency == 0)
                    syncFrequency = 360; // Default of 6 hours

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
                    if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                        updateJobInfoBuilder.setRequiresBatteryNotLow(true);
                    jobScheduler.schedule(updateJobInfoBuilder.build());
                }
            }

            return true;
        }
    }

    @Override
    public void onNewIntent(Intent pIntent)
    {
        String action = pIntent.getAction();
        Bundle extras = pIntent.getExtras();

        if(action != null && action.equals("Transaction") &&
          extras != null && extras.containsKey("Wallet") && extras.containsKey("Transaction"))
        {
            if(mBitcoin.isLoaded())
                openTransaction(extras.getInt("Wallet"), extras.getString("Transaction"));
            else
            {
                mTransactionToShowWalletIndex = extras.getInt("Wallet");
                mTransactionToShow = extras.getString("Transaction");
            }
        }

        if(action != null && action.equals("Message") &&
          extras != null && extras.containsKey("Message"))
            showMessage(getString(extras.getInt("Message")), 2000);

        super.onNewIntent(pIntent);
    }

    @Override
    public void onStart()
    {
        startBitcoinService();
        startUpdateRates();
        super.onStart();
    }

    @Override
    public void onResume()
    {
        mBitcoin.clearFinishTime();
        if(!mBitcoin.isRunning())
            startBitcoinService();
        updateStatus();
        super.onResume();
    }

    @Override
    public void onPause()
    {
        mBitcoin.setFinishTime(180); // 180 seconds in the future
        mDelayHandler.removeCallbacks(mStatusUpdateRunnable);
        mDelayHandler.removeCallbacks(mRateUpdateRunnable);
        mDelayHandler.removeCallbacks(mRequestExpiresUpdater);
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
        if(mServiceIsBound)
        {
            Log.d(logTag, "Unbinding Bitcoin service");
            unbindService(mServiceConnection);
        }
        super.onDestroy();
    }

    private void startUpdateRates()
    {
        new FiatRateRequestTask(getFilesDir()).execute();

        // Run again in 60 seconds
        mDelayHandler.postDelayed(mRateUpdateRunnable, 60000);
    }

    private synchronized void updateRequestExpires()
    {
        TextView expires = findViewById(R.id.expires);
        if(expires == null)
            return;

        if(mPaymentRequest.protocolDetails != null && mPaymentRequest.protocolDetails.hasExpires())
        {
            expires.setVisibility(View.VISIBLE);

            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            if(mPaymentRequest.protocolDetails.getExpires() <=
              (calendar.getTimeInMillis() / 1000L) + 5)
            {
                expires.setText(getString(R.string.request_expired));
                expires.setTextColor(getResources().getColor(R.color.textNegative));
            }
            else
            {
                expires.setText(String.format(Locale.getDefault(), "%s %d %s", getString(R.string.expires_in),
                  mPaymentRequest.protocolDetails.getExpires() - (calendar.getTimeInMillis() / 1000L),
                  getString(R.string.seconds_abbreviation)));
                expires.setTextColor(getResources().getColor(R.color.textWarning));

                // Run again in 1 second
                mDelayHandler.postDelayed(mRequestExpiresUpdater, 1000);
            }
        }
        else
            expires.setVisibility(View.GONE);
    }

    private synchronized void updateStatus()
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
                if(mMode != Mode.LOADING && mMode != Mode.SETTINGS && mMode != Mode.INFO)
                {
                    findViewById(R.id.wallets).setVisibility(View.GONE);
                    findViewById(R.id.dialog).setVisibility(View.GONE);
                    findViewById(R.id.progress).setVisibility(View.VISIBLE);
                    mMode = Mode.LOADING;
                }
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

        double fiatRate = Settings.getInstance(getFilesDir()).doubleValue("usd_rate");
        boolean exchangeRateUpdated = fiatRate != mFiatRate;
        TextView exchangeRate = findViewById(R.id.exchangeRate);

        if(fiatRate == 0.0)
            exchangeRate.setText("");
        else
            exchangeRate.setText(String.format(Locale.getDefault(), "1 BCH = $%,d USD", (int)fiatRate));

        boolean isLoaded = mBitcoin.isLoaded();
        int merkleHeight = mBitcoin.merkleHeight();
        int blockHeight = mBitcoin.blockHeight();
        TextView merkleBlocks = findViewById(R.id.merkleBlockHeight);
        TextView blocks = findViewById(R.id.blockHeight);
        TextView peerCountField = findViewById(R.id.peerCount);
        TextView blocksLabel = findViewById(R.id.blocksLabel);
        if(isLoaded)
        {
            merkleBlocks.setText(String.format(Locale.getDefault(), "%,d", merkleHeight));

            blocks.setText(String.format(Locale.getDefault(), "%,d", blockHeight));
            if(mBitcoin.isInSync())
            {
                if(merkleHeight == blockHeight)
                    merkleBlocks.setTextColor(getResources().getColor(R.color.textPositive));
                else
                    merkleBlocks.setTextColor(getResources().getColor(R.color.textWarning));
                blocks.setTextColor(getResources().getColor(R.color.textPositive));
                blocksLabel.setTextColor(getResources().getColor(R.color.textPositive));
            }
            else
            {
                merkleBlocks.setTextColor(getResources().getColor(R.color.textWarning));
                blocks.setTextColor(getResources().getColor(R.color.textWarning));
                blocksLabel.setTextColor(getResources().getColor(R.color.textWarning));
            }

            int count = mBitcoin.peerCount();
            peerCountField.setText(String.format(Locale.getDefault(), "%,d %s", count,
              getString(R.string.peers)));

            if(count == 0)
                peerCountField.setTextColor(getResources().getColor(R.color.textNegative));
            else if(count < 3)
                peerCountField.setTextColor(getResources().getColor(R.color.textWarning));
            else
                peerCountField.setTextColor(getResources().getColor(R.color.textPositive));

            if(mMode == Mode.LOADING)
                updateWallets();
        }
        else
        {
            merkleBlocks.setText("-");
            merkleBlocks.setTextColor(Color.BLACK);
            blocks.setText("-");
            blocks.setTextColor(Color.BLACK);
            blocksLabel.setTextColor(Color.BLACK);
            peerCountField.setText(String.format("- %s", getString(R.string.peers)));
            peerCountField.setTextColor(Color.BLACK);
            if(mMode != Mode.LOADING)
            {
                findViewById(R.id.wallets).setVisibility(View.GONE);
                findViewById(R.id.dialog).setVisibility(View.GONE);
                findViewById(R.id.progress).setVisibility(View.VISIBLE);
                mMode = Mode.LOADING;
            }
        }

        if(mBitcoin.initialBlockDownloadIsComplete())
        {
            View ibdMessage = findViewById(R.id.initialBlockDownloadMessage);
            if(ibdMessage != null)
                ibdMessage.setVisibility(View.GONE);
        }

        if(exchangeRateUpdated && mMode == Mode.WALLETS)
            updateWallets();

        // Run again in 2 seconds
        mDelayHandler.postDelayed(mStatusUpdateRunnable, 2000);
    }

    public synchronized void displayWallets()
    {
        findViewById(R.id.dialog).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.wallets).setVisibility(View.VISIBLE);
        findViewById(R.id.statusBar).setVisibility(View.VISIBLE);
        mMode = Mode.IN_PROGRESS;
    }

    public synchronized void updateWallets()
    {
        if(!mBitcoin.isLoaded())
            return;

        LayoutInflater inflater = getLayoutInflater();
        ViewGroup wallets = findViewById(R.id.wallets), transactions;
        View walletView;
        int index;

        mFiatRate = Settings.getInstance(getFilesDir()).doubleValue("usd_rate");

        if(mMode != Mode.WALLETS)
        {
            // Setup action bar
            ActionBar actionBar = getSupportActionBar();
            if(actionBar != null)
            {
                actionBar.setIcon(null);
                actionBar.setTitle(getResources().getString(R.string.app_name));
                actionBar.setDisplayHomeAsUpEnabled(false); // Show the Up button in the action bar.
            }
        }

        synchronized(mBitcoin)
        {
            if(mBitcoin.walletsModified || !mWalletsLoaded)
            {
                Log.i(logTag, String.format("Rebuilding %d wallets", mBitcoin.wallets.length));

                // Rebuild all wallets
                wallets.removeAllViews();

                // Add a view for each wallet
                for(index = 0; index < mBitcoin.wallets.length; index++)
                {
                    walletView = inflater.inflate(R.layout.wallet_item, wallets, false);
                    walletView.setTag(index);
                    wallets.addView(walletView);
                }

                if(!mBitcoin.initialBlockDownloadIsComplete())
                    inflater.inflate(R.layout.initial_block_download_message, wallets, true);
            }

            if(mBitcoin.wallets != null)
            {
                Log.i(logTag, String.format("Updating %d wallets", mBitcoin.wallets.length));

                index = 0;
                for(Wallet wallet : mBitcoin.wallets)
                {
                    walletView = wallets.getChildAt(index);
                    if(walletView == null)
                        break;

                    ((TextView)walletView.findViewById(R.id.walletBalance)).setText(Bitcoin.amountText(wallet.balance,
                      mFiatRate));

                    if(mFiatRate != 0.0)
                    {
                        ((TextView)walletView.findViewById(R.id.walletBitcoinBalance)).setText(String.format(Locale.getDefault(),
                          "%,.5f BCH", Bitcoin.bitcoinsFromSatoshis(wallet.balance)));
                    }

                    ((TextView)walletView.findViewById(R.id.walletName)).setText(wallet.name);

                    if(!wallet.isPrivate)
                    {
                        walletView.findViewById(R.id.walletLocked).setVisibility(View.VISIBLE);
                        walletView.findViewById(R.id.walletSend).setVisibility(View.GONE);
                        walletView.findViewById(R.id.walletLockedMessage).setVisibility(View.VISIBLE);
                    }

                    if(!mBitcoin.initialBlockDownloadIsComplete())
                        walletView.findViewById(R.id.walletSend).setVisibility(View.GONE);

                    if(!wallet.isSynchronized)
                    {
                        TextView walletWarning = walletView.findViewById(R.id.walletWarning);
                        if(walletWarning != null)
                        {
                            walletWarning.setVisibility(View.VISIBLE);
                            walletWarning.setText(R.string.synchronizing);
                        }
                    }
                    else if(!wallet.isBackedUp)
                    {
                        TextView walletWarning = walletView.findViewById(R.id.walletWarning);
                        if(walletWarning != null)
                        {
                            walletWarning.setVisibility(View.VISIBLE);
                            walletWarning.setText(R.string.needs_backed_up);
                        }
                    }
                    else
                    {
                        TextView walletWarning = walletView.findViewById(R.id.walletWarning);
                        if(walletWarning != null)
                            walletWarning.setVisibility(View.GONE);
                    }

                    transactions = walletView.findViewById(R.id.walletTransactions);
                    populateTransactions(transactions, wallet.transactions, 3);

                    alignTransactions(transactions);

                    index++;
                }
            }

            if(mBitcoin.walletsModified || !mWalletsLoaded)
            {
                walletView = inflater.inflate(R.layout.button, wallets, false);
                walletView.setTag(R.id.addWallet);
                ((TextView)walletView.findViewById(R.id.text)).setText(R.string.add_wallet);
                wallets.addView(walletView);

                walletView = inflater.inflate(R.layout.button, wallets, false);
                walletView.setTag(R.id.buyFromCoinbase);
                ((TextView)walletView.findViewById(R.id.text)).setText(R.string.buy_bitcoin_cash);
                wallets.addView(walletView);
            }

            mBitcoin.walletsModified = false;
            mWalletsLoaded = true;
            wallets.setVisibility(View.VISIBLE);
        }

        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.dialog).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.VISIBLE);

        mMode = Mode.WALLETS;
    }

    public void focusOnText(EditText pEditView)
    {
        if(pEditView.requestFocus())
        {
            InputMethodManager inputManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(inputManager != null)
            {
                inputManager.showSoftInput(pEditView, InputMethodManager.SHOW_IMPLICIT);
                inputManager.updateSelection(pEditView, 0, pEditView.getText().length(), 0, 0);
            }
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
    public void onCheckedChanged(CompoundButton pButtonView, boolean pIsChecked)
    {
        switch(pButtonView.getId())
        {
        case R.id.notifyTransactionsToggle:
            Settings.getInstance(getFilesDir()).setBoolValue("notify_transactions", pIsChecked);
            if(pIsChecked)
                Log.i(logTag, "Transaction notifications turned on");
            else
                Log.i(logTag, "Transaction notifications turned off");
            break;
        case R.id.usePendingToggle:
            mPaymentRequest.usePending = ((Switch)findViewById(R.id.usePendingToggle)).isChecked();
            updateFee(); // Update "insufficient funds" message
            break;
        }
    }

    public synchronized void displaySettings()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.wallets).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_settings_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.title_settings));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        View settingsView = inflater.inflate(R.layout.settings, dialogView, false);

        Settings settings = Settings.getInstance(getFilesDir());

        // Configure sync frequency options
        int currentFrequency = settings.intValue("sync_frequency");
        if(currentFrequency == 0)
            currentFrequency = 360; // Default to 6 hours

        Spinner syncFrequency = settingsView.findViewById(R.id.syncFrequencySpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
          R.array.sync_frequency_titles, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        syncFrequency.setAdapter(adapter);
        syncFrequency.setOnItemSelectedListener(this);

        boolean found = false;
        int[] frequencyValues = getResources().getIntArray(R.array.sync_frequency_values);
        for(int i=0;i<frequencyValues.length;i++)
            if(frequencyValues[i] == currentFrequency)
            {
                found = true;
                syncFrequency.setSelection(i);
                break;
            }

        if(!found)
            syncFrequency.setSelection(2); // Default to 6 hours

        // Configure transaction notifications toggle
        Switch notifyTransactions = settingsView.findViewById(R.id.notifyTransactionsToggle);
        notifyTransactions.setOnCheckedChangeListener(this);
        if(settings.containsValue("notify_transactions"))
            notifyTransactions.setChecked(settings.boolValue("notify_transactions"));
        else
            notifyTransactions.setChecked(true);

        // Hide system notification settings for versions before 26
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            settingsView.findViewById(R.id.systemNotificationSettings).setVisibility(View.GONE);

        synchronized(mBitcoin)
        {
            // Add Wallet buttons
            ViewGroup walletsView = settingsView.findViewById(R.id.walletsSettings);
            View button;
            int offset = 0;

            if(mBitcoin.wallets != null)
                for(Wallet wallet : mBitcoin.wallets)
                {
                    // Edit button
                    button = inflater.inflate(R.layout.button, walletsView, false);
                    button.findViewById(R.id.button).setId(R.id.editWallet);
                    button.setTag(offset);
                    ((TextView)button.findViewById(R.id.text)).setText(wallet.name);
                    walletsView.addView(button);
                    offset++;
                }
        }

        dialogView.addView(settingsView);
        dialogView.setVisibility(View.VISIBLE);
        ((ScrollView)findViewById(R.id.mainScroll)).setScrollY(0);
        mMode = Mode.SETTINGS;
    }

    public synchronized void displayInfo()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.wallets).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_info_outline_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.information));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        inflater.inflate(R.layout.info, dialogView, true);

        ((TextView)findViewById(R.id.nodeUserAgentValue)).setText(Bitcoin.userAgent());
        ((TextView)findViewById(R.id.networkValue)).setText(Bitcoin.networkName());

        dialogView.setVisibility(View.VISIBLE);
        ((ScrollView)findViewById(R.id.mainScroll)).setScrollY(0);
        mMode = Mode.INFO;
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
                displaySettings();
                return true;
            case R.id.action_info:
                displayInfo();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int pRequestCode, int pResultCode, Intent pData)
    {
        switch(pRequestCode)
        {
            case SCAN_REQUEST_CODE:
            case IntentIntegrator.REQUEST_CODE:
                if(pResultCode == RESULT_OK && pData != null)
                {
                    mPaymentRequest = mBitcoin.decodePaymentCode(pData.getStringExtra("SCAN_RESULT"));
                    if(mPaymentRequest != null && mPaymentRequest.format != PaymentRequest.FORMAT_INVALID)
                        displaySendPayment();
                    else
                    {
                        showMessage(getString(R.string.failed_payment_code), 2000);
                        updateWallets();
                    }
                }
                break;
            default:
                super.onActivityResult(pRequestCode, pResultCode, pData);
                break;
        }
    }

    public void updateFee()
    {
        TextView sendFee = findViewById(R.id.sendFee);
        TextView satoshiFee = findViewById(R.id.satoshiFee);
        Spinner units = findViewById(R.id.units);

        long feeSatoshis = mPaymentRequest.estimatedFee();
        long available = mPaymentRequest.amountAvailable();

        if(mPaymentRequest.sendMax)
        {
            mPaymentRequest.amount = available - feeSatoshis;
            updateSendAmount();
        }

        if(mPaymentRequest.amount + feeSatoshis > available)
        {
            findViewById(R.id.insufficientFunds).setVisibility(View.VISIBLE);
            findViewById(R.id.usePendingToggle).setVisibility(View.VISIBLE);
        }
        else
            findViewById(R.id.insufficientFunds).setVisibility(View.GONE);

        switch(units.getSelectedItemPosition())
        {
            case 0: // USD
                sendFee.setText(String.format(Locale.getDefault(), "%.2f",
                  Bitcoin.bitcoinsFromSatoshis(feeSatoshis) * mFiatRate));
                break;
            case 1: // bits
                sendFee.setText(String.format(Locale.getDefault(), "%.6f",
                  Bitcoin.bitsFromBitcoins(Bitcoin.bitcoinsFromSatoshis(feeSatoshis))));
                break;
            case 2: // bitcoins
                sendFee.setText(String.format(Locale.getDefault(), "%.8f",
                  Bitcoin.bitcoinsFromSatoshis(feeSatoshis)));
                break;
            default:
                sendFee.setText(String.format(Locale.getDefault(), "%.2f",
                  0.0));
                break;
        }

        satoshiFee.setText(Bitcoin.satoshiText(feeSatoshis));
    }

    // Update amount fields based on mPaymentAmount value
    public void updateSendAmount()
    {
        Spinner units = findViewById(R.id.units);
        EditText amountField = findViewById(R.id.sendAmount);
        TextView satoshiAmount = findViewById(R.id.satoshiAmount);
        double amount;

        if(units == null || amountField == null || satoshiAmount == null)
            return;

        mDontUpdatePaymentAmount = true;
        switch(units.getSelectedItemPosition())
        {
            case 0: // USD
                amount = Bitcoin.bitcoinsFromSatoshis(mPaymentRequest.amount) * mFiatRate;
                amountField.setText(String.format(Locale.getDefault(), "%.2f", amount));
                break;
            case 1: // bits
                amount = Bitcoin.bitsFromSatoshis(mPaymentRequest.amount);
                amountField.setText(String.format(Locale.getDefault(), "%.6f", amount));
                break;
            case 2: // bitcoins
                amount = Bitcoin.bitcoinsFromSatoshis(mPaymentRequest.amount);
                amountField.setText(String.format(Locale.getDefault(), "%.8f", amount));
                break;
            default:
                amount = 0.0;
                amountField.setText(String.format(Locale.getDefault(), "%.2f", amount));
                break;
        }

        satoshiAmount.setText(Bitcoin.satoshiText(mPaymentRequest.amount));
    }

    public void displaySendPayment()
    {
        if(mPaymentRequest == null)
        {
            showMessage(getString(R.string.failed_payment_code), 2000);
            updateWallets();
            return;
        }

        if(mPaymentRequest.secureURL != null && mPaymentRequest.paymentScript == null)
        {
            ProcessPaymentRequestTask processPaymentRequestTask = new ProcessPaymentRequestTask(this, mCurrentWalletIndex,
              mPaymentRequest);
            processPaymentRequestTask.execute();

            findViewById(R.id.wallets).setVisibility(View.GONE);
            findViewById(R.id.statusBar).setVisibility(View.GONE);
            findViewById(R.id.dialog).setVisibility(View.GONE);
            findViewById(R.id.progress).setVisibility(View.VISIBLE);
            mMode = Mode.SEND;
            return;
        }

        mFiatRate = Settings.getInstance(getFilesDir()).doubleValue("usd_rate");

        mPaymentRequest.outpoints = mBitcoin.getUnspentOutputs(mCurrentWalletIndex);

        if(mPaymentRequest.format == PaymentRequest.FORMAT_INVALID ||
          (mPaymentRequest.type != PaymentRequest.TYPE_PUB_KEY_HASH &&
          mPaymentRequest.type != PaymentRequest.TYPE_BIP0700))
        {
            showMessage(getString(R.string.invalid_payment_code), 2000);
            updateWallets();
            return;
        }

        synchronized(this)
        {
            LayoutInflater inflater = getLayoutInflater();
            ViewGroup dialogView = findViewById(R.id.dialog);

            findViewById(R.id.wallets).setVisibility(View.GONE);

            dialogView.removeAllViews();
            findViewById(R.id.statusBar).setVisibility(View.GONE);
            findViewById(R.id.progress).setVisibility(View.GONE);

            ActionBar actionBar = getSupportActionBar();
            if(actionBar != null)
            {
                actionBar.setIcon(R.drawable.ic_send_black_36dp);
                actionBar.setTitle(" " + getResources().getString(R.string.send_payment));
                actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
            }

            ViewGroup sendView = (ViewGroup)inflater.inflate(R.layout.send_payment, dialogView, false);

            // Display payment details
            String formatText;
            switch(mPaymentRequest.format)
            {
            default:
            case PaymentRequest.FORMAT_LEGACY:
                formatText = getString(R.string.legacy);
                sendView.findViewById(R.id.legacyWarning).setVisibility(View.VISIBLE);
                break;
            case PaymentRequest.FORMAT_CASH:
                formatText = getString(R.string.cash);
                break;
            }

            String title;
            if(mPaymentRequest.secure)
                title = String.format("%s %s %s", getString(R.string.secure), formatText, getString(R.string.address));
            else
                title = String.format("%s %s", formatText, getString(R.string.address));

            ((TextView)sendView.findViewById(R.id.title)).setText(title);

            // Configure "use pending" toggle
            Switch usePending = sendView.findViewById(R.id.usePendingToggle);
            usePending.setChecked(mPaymentRequest.usePending);
            usePending.setOnCheckedChangeListener(this);

            EditText amount = sendView.findViewById(R.id.sendAmount);
            TextView satoshiAmount = sendView.findViewById(R.id.satoshiAmount);
            if(mPaymentRequest.amountSpecified)
            {
                amount.setText(Bitcoin.amountText(mPaymentRequest.amount, mFiatRate));
                satoshiAmount.setText(Bitcoin.satoshiText(mPaymentRequest.amount));
                sendView.findViewById(R.id.sendMax).setVisibility(View.GONE);
            }
            else
                satoshiAmount.setText(Bitcoin.satoshiText(0));

            if(mAmountWatcher == null)
            {
                mAmountWatcher = new TextWatcher()
                {
                    @Override
                    public void beforeTextChanged(CharSequence pString, int pStart, int pCount, int pAfter)
                    {

                    }

                    @Override
                    public void onTextChanged(CharSequence pString, int pStart, int pBefore, int pCount)
                    {
                        if(mDontUpdatePaymentAmount)
                        {
                            mDontUpdatePaymentAmount = false;
                            return;
                        }

                        Spinner units = findViewById(R.id.units);
                        EditText amountField = findViewById(R.id.sendAmount);
                        TextView satoshiAmount = findViewById(R.id.satoshiAmount);
                        double amount;

                        if(units == null || amountField == null || satoshiAmount == null)
                            return;

                        try
                        {
                            amount = Double.parseDouble(amountField.getText().toString());
                        }
                        catch(Exception pException)
                        {
                            amount = 0.0;
                        }

                        switch(units.getSelectedItemPosition())
                        {
                            case 0: // USD
                                mPaymentRequest.amount = Bitcoin.satoshisFromBitcoins(amount / mFiatRate);
                                break;
                            case 1: // bits
                                mPaymentRequest.amount = Bitcoin.satoshisFromBits(amount);
                                break;
                            case 2: // bitcoins
                                mPaymentRequest.amount = Bitcoin.satoshisFromBitcoins(amount);
                                break;
                            default:
                                mPaymentRequest.amount = 0;
                                break;
                        }

                        satoshiAmount.setText(Bitcoin.satoshiText(mPaymentRequest.amount));
                        updateFee();
                    }

                    @Override
                    public void afterTextChanged(Editable pString)
                    {

                    }
                };
            }
            if(!mPaymentRequest.amountSpecified)
                amount.addTextChangedListener(mAmountWatcher);

            Spinner units = sendView.findViewById(R.id.units);
            ArrayAdapter<CharSequence> unitAdapter = ArrayAdapter.createFromResource(this, R.array.amount_units,
              android.R.layout.simple_spinner_item);
            unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            units.setAdapter(unitAdapter);
            units.setOnItemSelectedListener(this);

            Spinner feeRates = sendView.findViewById(R.id.feeRates);
            ArrayAdapter<CharSequence> feeRateAdapter = ArrayAdapter.createFromResource(this, R.array.fee_rates,
              android.R.layout.simple_spinner_item);
            feeRateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            feeRates.setAdapter(feeRateAdapter);
            feeRates.setOnItemSelectedListener(this);
            feeRates.setSelection(2); // Default to Low

            if(mPaymentRequest.amount != 0)
            {
                // Make amount not modifiable
                amount.setInputType(InputType.TYPE_NULL);
                amount.setEnabled(false);
                amount.setFocusable(false);
            }

            if(mPaymentRequest.address != null)
                ((TextView)sendView.findViewById(R.id.address)).setText(mPaymentRequest.address.toLowerCase());
            else if(mPaymentRequest.site != null && mPaymentRequest.label != null)
                ((TextView)sendView.findViewById(R.id.address)).setText(String.format("%s (%s)", mPaymentRequest.site,
                  mPaymentRequest.label));
            else if(mPaymentRequest.site != null)
                ((TextView)sendView.findViewById(R.id.address)).setText(mPaymentRequest.site);
            else if(mPaymentRequest.label != null)
                ((TextView)sendView.findViewById(R.id.address)).setText(mPaymentRequest.label);
            else
                ((TextView)sendView.findViewById(R.id.address)).setText("");

            // Description
            String description = mPaymentRequest.description();
            if(description != null)
                ((TextView)sendView.findViewById(R.id.description)).setText(description);
            else
            {
                sendView.findViewById(R.id.descriptionTitle).setVisibility(View.GONE);
                sendView.findViewById(R.id.description).setVisibility(View.GONE);
            }

            dialogView.addView(sendView);

            // Verify button
            View button = inflater.inflate(R.layout.button, dialogView, false);
            button.setTag(R.id.sendPayment);
            ((TextView)button.findViewById(R.id.text)).setText(R.string.continue_string);
            dialogView.addView(button);

            dialogView.setVisibility(View.VISIBLE);
            ((ScrollView)findViewById(R.id.mainScroll)).setScrollY(0);

            if(mPaymentRequest.protocolDetails != null && mPaymentRequest.protocolDetails.hasExpires())
                updateRequestExpires();

            if(mPaymentRequest.amount == 0)
            {
                focusOnText(amount);
                amount.selectAll();
            }

            mMode = Mode.SEND;
        }
    }

    public void acknowledgePaymentSent()
    {
        if(mPaymentRequest.protocolDetails != null && mPaymentRequest.protocolDetails.hasPaymentUrl())
        {
            FinishPaymentRequestTask finishPaymentRequestTask = new FinishPaymentRequestTask(this,
              mCurrentWalletIndex, mPaymentRequest);
            finishPaymentRequestTask.execute();
        }
    }

    public void clearPaymentProcess()
    {
        mPaymentRequest = null;
    }

    public void openTransaction(int pWalletOffset, String pTransactionHash)
    {
        mTransactionToShowWalletIndex = 0;
        mTransactionToShow = null;

        findViewById(R.id.wallets).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.VISIBLE);

        GetTransactionTask task = new GetTransactionTask(this, mBitcoin, pWalletOffset, pTransactionHash);
        task.execute();
    }

    public void displayTransaction(FullTransaction pTransaction)
    {
        synchronized(this)
        {
            LayoutInflater inflater = getLayoutInflater();
            ViewGroup dialogView = findViewById(R.id.dialog);

            dialogView.removeAllViews();

            findViewById(R.id.wallets).setVisibility(View.GONE);
            findViewById(R.id.statusBar).setVisibility(View.GONE);
            findViewById(R.id.progress).setVisibility(View.GONE);

            ActionBar actionBar = getSupportActionBar();
            if(actionBar != null)
            {
                actionBar.setIcon(null);
                actionBar.setTitle(" " + getResources().getString(R.string.transaction));
                actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
            }

            ViewGroup transactionView = (ViewGroup)inflater.inflate(R.layout.transaction, dialogView, false);

            if(pTransaction.block != null)
            {
                ((TextView)transactionView.findViewById(R.id.statusHeading)).setText(R.string.confirmed_in_block);
                ((TextView)transactionView.findViewById(R.id.blockID)).setText(pTransaction.block);
                ((TextView)transactionView.findViewById(R.id.transactionStatus))
                  .setText(String.format(Locale.getDefault(), "%,d %s (%3$tY-%3$tm-%3$td %3$tH:%3$tM)",
                    pTransaction.count, getString(R.string.confirmations), pTransaction.date * 1000));
            }
            else
            {
                ((TextView)transactionView.findViewById(R.id.statusHeading)).setText(R.string.unconfirmed);
                transactionView.findViewById(R.id.blockIDScroll).setVisibility(View.GONE);
                ((TextView)transactionView.findViewById(R.id.transactionStatus))
                  .setText(String.format(Locale.getDefault(), "%s %,d %s", getString(R.string.validated_by),
                    pTransaction.count, getString(R.string.peers_period)));
            }

            ((TextView)transactionView.findViewById(R.id.id)).setText(pTransaction.hash);
            ((TextView)transactionView.findViewById(R.id.lockTime)).setText(pTransaction.lockTimeString(this));
            ((TextView)transactionView.findViewById(R.id.version)).setText(String.format(Locale.getDefault(), "%d",
              pTransaction.version));

            long amount = pTransaction.amount();
            TextView amountText = transactionView.findViewById(R.id.transactionAmount);
            TextView bitcoinsAmountText = transactionView.findViewById(R.id.bitcoinsAmount);
            amountText.setText(Bitcoin.amountText(amount, mFiatRate));
            bitcoinsAmountText.setText(Bitcoin.satoshiText(amount));
            if(amount > 0)
            {
                ((TextView)transactionView.findViewById(R.id.title)).setText(R.string.receive);
                amountText.setTextColor(getResources().getColor(R.color.colorPositive));
                bitcoinsAmountText.setTextColor(getResources().getColor(R.color.colorPositive));
            }
            else
            {
                ((TextView)transactionView.findViewById(R.id.title)).setText(R.string.send);
                amountText.setTextColor(getResources().getColor(R.color.colorNegative));
                bitcoinsAmountText.setTextColor(getResources().getColor(R.color.colorNegative));
            }

            long fee = pTransaction.fee();

            if(fee == -1)
            {
                transactionView.findViewById(R.id.feeGroup).setVisibility(View.GONE);
                transactionView.findViewById(R.id.feeBitcoinsGroup).setVisibility(View.GONE);
            }
            else
            {
                TextView feeText = transactionView.findViewById(R.id.transactionFee);
                TextView bitcoinsFeeText = transactionView.findViewById(R.id.bitcoinsFee);
                feeText.setText(Bitcoin.amountText(fee, mFiatRate));
                bitcoinsFeeText.setText(Bitcoin.satoshiText(fee));
            }

            // Inputs
            if(pTransaction.inputs.length > 1)
                ((TextView)transactionView.findViewById(R.id.inputsTitle)).setText(String.format(Locale.getDefault(),
                  "%d %s", pTransaction.inputs.length, getString(R.string.inputs)));
            else
                ((TextView)transactionView.findViewById(R.id.inputsTitle)).setText(String.format(Locale.getDefault(),
                  "%d %s", pTransaction.inputs.length, getString(R.string.input)));
            ViewGroup inputsView = transactionView.findViewById(R.id.inputs);
            for(Input input : pTransaction.inputs)
            {
                ViewGroup inputView = (ViewGroup)inflater.inflate(R.layout.input, inputsView, false);

                ((TextView)inputView.findViewById(R.id.outpointHash)).setText(input.outpointID);
                ((TextView)inputView.findViewById(R.id.outpointIndex)).setText(String.format(Locale.getDefault(),
                  "%s %d", getString(R.string.index), input.outpointIndex));

                ((TextView)inputView.findViewById(R.id.sequence)).setText(String.format(Locale.getDefault(), "0x%08x",
                  input.sequence));

                if(input.address != null && input.address.length() > 0)
                    ((TextView)inputView.findViewById(R.id.address)).setText(input.address);
                else
                {
                    inputView.findViewById(R.id.addressTitle).setVisibility(View.GONE);
                    inputView.findViewById(R.id.address).setVisibility(View.GONE);
                }

                if(input.amount != -1)
                {
                    inputView.setBackgroundColor(getResources().getColor(R.color.highlight));
                    TextView inputAmountText = inputView.findViewById(R.id.inputAmount);
                    inputAmountText.setText(Bitcoin.amountText(input.amount, mFiatRate));
                    inputAmountText.setTextColor(getResources().getColor(R.color.colorNegative));

                    TextView inputBitcoinAmountText = inputView.findViewById(R.id.bitcoinsAmount);
                    inputBitcoinAmountText.setText(Bitcoin.satoshiText(input.amount));
                    inputBitcoinAmountText.setTextColor(getResources().getColor(R.color.colorNegative));
                }
                else
                {
                    inputView.findViewById(R.id.amountGroup).setVisibility(View.GONE);
                    inputView.findViewById(R.id.bitcoinAmountGroup).setVisibility(View.GONE);
                }

                inputsView.addView(inputView);
            }

            // Outputs
            if(pTransaction.outputs.length > 1)
                ((TextView)transactionView.findViewById(R.id.outputsTitle)).setText(String.format(Locale.getDefault(),
                  "%d %s", pTransaction.outputs.length, getString(R.string.outputs)));
            else
                ((TextView)transactionView.findViewById(R.id.outputsTitle)).setText(String.format(Locale.getDefault(),
                  "%d %s", pTransaction.outputs.length, getString(R.string.output)));
            ViewGroup outputsView = transactionView.findViewById(R.id.outputs);
            for(Output output : pTransaction.outputs)
            {
                ViewGroup outputView = (ViewGroup)inflater.inflate(R.layout.output, outputsView, false);

                if(output.related)
                    outputView.setBackgroundColor(getResources().getColor(R.color.highlight));

                if(output.address != null && output.address.length() > 0)
                    ((TextView)outputView.findViewById(R.id.address)).setText(output.address);
                else
                {
                    outputView.findViewById(R.id.addressTitle).setVisibility(View.GONE);
                    outputView.findViewById(R.id.address).setVisibility(View.GONE);
                }

                TextView outputAmountText = outputView.findViewById(R.id.outputAmount);
                TextView outputBitcoinsAmountText = outputView.findViewById(R.id.bitcoinsAmount);
                outputAmountText.setText(Bitcoin.amountText(output.amount, mFiatRate));
                outputBitcoinsAmountText.setText(Bitcoin.satoshiText(output.amount));
                if(output.related)
                {
                    outputAmountText.setTextColor(getResources().getColor(R.color.colorPositive));
                    outputBitcoinsAmountText.setTextColor(getResources().getColor(R.color.colorPositive));
                }

                outputsView.addView(outputView);
            }

            dialogView.addView(transactionView);
            dialogView.setVisibility(View.VISIBLE);
            ((ScrollView)findViewById(R.id.mainScroll)).setScrollY(0);
            mMode = Mode.TRANSACTION;
        }
    }

    public void displayEnterPaymentCode()
    {
        synchronized(this)
        {
            LayoutInflater inflater = getLayoutInflater();
            ViewGroup dialogView = findViewById(R.id.dialog);

            dialogView.removeAllViews();
            findViewById(R.id.wallets).setVisibility(View.GONE);
            findViewById(R.id.statusBar).setVisibility(View.GONE);
            findViewById(R.id.progress).setVisibility(View.GONE);

            ActionBar actionBar = getSupportActionBar();
            if(actionBar != null)
            {
                actionBar.setIcon(R.drawable.ic_send_black_36dp);
                actionBar.setTitle(" " + getResources().getString(R.string.send_payment));
                actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
            }

            ViewGroup paymentCodeView = (ViewGroup)inflater.inflate(R.layout.enter_payment_code, dialogView,
              false);
            dialogView.addView(paymentCodeView);

            // Continue button
            View continueButton = inflater.inflate(R.layout.button, dialogView, false);
            continueButton.setTag(R.id.enterPaymentDetails);
            ((TextView)continueButton.findViewById(R.id.text)).setText(R.string.continue_string);
            dialogView.addView(continueButton);

            // Scan button
            View scanButton = inflater.inflate(R.layout.button, dialogView, false);
            scanButton.setTag(R.id.openScanner);
            ImageView scanIcon = scanButton.findViewById(R.id.image);
            scanIcon.setImageResource(R.drawable.ic_scan_white_36dp);
            scanIcon.setVisibility(View.VISIBLE);
            ((TextView)scanButton.findViewById(R.id.text)).setText(R.string.scan);
            dialogView.addView(scanButton);

            dialogView.setVisibility(View.VISIBLE);
            ((ScrollView)findViewById(R.id.mainScroll)).setScrollY(0);
            focusOnText((EditText)paymentCodeView.findViewById(R.id.paymentCode));
            mMode = Mode.SEND;
        }
    }

    public void displayRequestPaymentCode(PaymentRequest pPaymentCode, Bitmap pQRCode)
    {
        if(pPaymentCode == null || pPaymentCode.uri == null || pQRCode == null)
            updateWallets();
        else
        {
            synchronized(this)
            {
                LayoutInflater inflater = getLayoutInflater();
                ViewGroup dialogView = findViewById(R.id.dialog);

                dialogView.removeAllViews();
                findViewById(R.id.wallets).setVisibility(View.GONE);
                findViewById(R.id.progress).setVisibility(View.GONE);
                findViewById(R.id.statusBar).setVisibility(View.GONE);

                ActionBar actionBar = getSupportActionBar();
                if(actionBar != null)
                {
                    actionBar.setIcon(R.drawable.ic_add_circle_black_36dp);
                    actionBar.setTitle(" " + getResources().getString(R.string.receive));
                    actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
                }

                mFiatRate = Settings.getInstance(getFilesDir()).doubleValue("usd_rate");
                mPaymentRequest = pPaymentCode;

                ViewGroup receiveView = (ViewGroup)inflater.inflate(R.layout.receive, dialogView, false);

                // Set image and text
                ((ImageView)receiveView.findViewById(R.id.image)).setImageBitmap(pQRCode);
                ((TextView)receiveView.findViewById(R.id.paymentCode)).setText(mPaymentRequest.uri);
                dialogView.addView(receiveView);

                // Set amount
                EditText amount = receiveView.findViewById(R.id.requestAmount);
                TextView satoshiAmount = receiveView.findViewById(R.id.satoshiAmount);
                if(mPaymentRequest.amount != 0)
                {
                    amount.setText(Bitcoin.amountText(mPaymentRequest.amount, mFiatRate));
                    satoshiAmount.setText(Bitcoin.satoshiText(mPaymentRequest.amount));
                }
                else
                {
                    amount.setText("");
                    satoshiAmount.setText(Bitcoin.satoshiText(0));
                }

                if(mRequestAmountWatcher == null)
                {
                    mRequestAmountWatcher = new TextWatcher()
                    {
                        @Override
                        public void beforeTextChanged(CharSequence pString, int pStart, int pCount, int pAfter)
                        {

                        }

                        @Override
                        public void onTextChanged(CharSequence pString, int pStart, int pBefore, int pCount)
                        {
                            Spinner units = findViewById(R.id.units);
                            EditText amountField = findViewById(R.id.requestAmount);
                            TextView satoshiAmount = findViewById(R.id.satoshiAmount);
                            double amount;

                            if(units == null || amountField == null || satoshiAmount == null)
                                return;

                            try
                            {
                                amount = Double.parseDouble(amountField.getText().toString());
                            }
                            catch(Exception pException)
                            {
                                amount = 0.0;
                            }

                            switch(units.getSelectedItemPosition())
                            {
                                case 0: // USD
                                    mPaymentRequest.amount = Bitcoin.satoshisFromBitcoins(amount / mFiatRate);
                                    break;
                                case 1: // bits
                                    mPaymentRequest.amount = Bitcoin.satoshisFromBits(amount);
                                    break;
                                case 2: // bitcoins
                                    mPaymentRequest.amount = Bitcoin.satoshisFromBitcoins(amount);
                                    break;
                                default:
                                    mPaymentRequest.amount = 0;
                                    break;
                            }

                            satoshiAmount.setText(Bitcoin.satoshiText(mPaymentRequest.amount));
                        }

                        @Override
                        public void afterTextChanged(Editable pString)
                        {

                        }
                    };
                }
                amount.addTextChangedListener(mRequestAmountWatcher);

                Spinner units = receiveView.findViewById(R.id.units);
                ArrayAdapter<CharSequence> unitAdapter = ArrayAdapter.createFromResource(this, R.array.amount_units,
                  android.R.layout.simple_spinner_item);
                unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                units.setAdapter(unitAdapter);
                units.setOnItemSelectedListener(this);

                // Label
                if(mPaymentRequest.label != null && mPaymentRequest.label.length() > 0)
                    ((EditText)receiveView.findViewById(R.id.label)).setText(mPaymentRequest.label);

                // Message
                if(mPaymentRequest.message != null && mPaymentRequest.message.length() > 0)
                    ((EditText)receiveView.findViewById(R.id.message)).setText(mPaymentRequest.message);

                // Add amount button
                View button = inflater.inflate(R.layout.button, dialogView, false);
                button.setTag(R.id.updateRequestPaymentCode);
                ((TextView)button.findViewById(R.id.text)).setText(R.string.update);
                dialogView.addView(button);

                dialogView.setVisibility(View.VISIBLE);
                ((ScrollView)findViewById(R.id.mainScroll)).setScrollY(0);

                amount.selectAll();

                mMode = Mode.RECEIVE;
            }
        }
    }

    public synchronized void displayCreateWallet()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.wallets).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_add_circle_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.create_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup createWallet = (ViewGroup)inflater.inflate(R.layout.view_seed, dialogView, false);
        mSeed = mBitcoin.generateMnemonicSeed(28);
        mSeedIsBackedUp = false;
        mSeedIsRecovered = false;
        mDerivationPathMethodToLoad = Bitcoin.BIP0044_DERIVATION;
        mSeedBackupOnly = false;
        ((TextView)createWallet.findViewById(R.id.seed)).setText(mSeed);

        Spinner entropy = createWallet.findViewById(R.id.seedEntropy);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
          R.array.mnemonic_seed_length_titles, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        entropy.setAdapter(adapter);
        entropy.setOnItemSelectedListener(this);
        entropy.setSelection(1);

        dialogView.addView(createWallet);

        // Verify button
        View button = inflater.inflate(R.layout.button, dialogView, false);
        button.setTag(R.id.verifySeedSaved);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.continue_string);
        dialogView.addView(button);

        dialogView.setVisibility(View.VISIBLE);
        ((ScrollView)findViewById(R.id.mainScroll)).setScrollY(0);
        mMode = Mode.CREATE_WALLET;
    }

    @Override
    public void onItemSelected(AdapterView<?> pParent, View pView, int pPosition, long pID)
    {
        if(pParent.getId() == R.id.seedEntropy)
        {
            int[] values = getResources().getIntArray(R.array.mnemonic_seed_length_values);

            if(pPosition >= values.length)
                Log.e(logTag, String.format("Invalid seed entropy position selected : %d", pPosition));
            else
            {
                mSeed = mBitcoin.generateMnemonicSeed(values[pPosition]);
                mSeedIsRecovered = false;
                mSeedIsBackedUp = false;
                ((TextView)findViewById(R.id.seed)).setText(mSeed);
            }
        }
        else if(pParent.getId() == R.id.units)
        {
            EditText amountField = findViewById(R.id.sendAmount);
            boolean isRequest = false;
            if(amountField == null)
            {
                isRequest = true;
                amountField = findViewById(R.id.requestAmount);
            }

            if(mPaymentRequest.amount == 0)
                amountField.setText("");
            else
                switch(pPosition)
                {
                    case 0: // USD
                        amountField.setText(String.format(Locale.getDefault(), "%.2f",
                          Bitcoin.bitcoinsFromSatoshis(mPaymentRequest.amount) * mFiatRate));
                        break;
                    case 1: // bits
                        amountField.setText(String.format(Locale.getDefault(), "%.6f",
                          Bitcoin.bitsFromBitcoins(Bitcoin.bitcoinsFromSatoshis(mPaymentRequest.amount))));
                        break;
                    case 2: // bitcoins
                        amountField.setText(String.format(Locale.getDefault(), "%.8f",
                          Bitcoin.bitcoinsFromSatoshis(mPaymentRequest.amount)));
                        break;
                    default:
                        amountField.setText("");
                        break;
                }

            if(!isRequest)
                updateFee();
        }
        else if(pParent.getId() == R.id.feeRates)
        {
            switch(pPosition)
            {
                case 0: // Priority
                    mPaymentRequest.feeRate = 5.0;
                    break;
                default:
                case 1: // Normal
                    mPaymentRequest.feeRate = 2.0;
                    break;
                case 2: // Low
                    mPaymentRequest.feeRate = 1.0;
                    break;
            }

            updateFee();
        }
        else if(pParent.getId() == R.id.syncFrequencySpinner)
        {
            int[] frequencyValues = getResources().getIntArray(R.array.sync_frequency_values);

            if(pPosition >= frequencyValues.length)
                Log.e(logTag, String.format("Invalid sync frequency position selected : %d", pPosition));
            else
            {
                Settings.getInstance(getFilesDir()).setIntValue("sync_frequency", frequencyValues[pPosition]);
                setResult(1);
                if(frequencyValues[pPosition] == -1)
                    Log.i(logTag, "Sync frequency set to never.");
                else if(frequencyValues[pPosition] >= 60)
                    Log.i(logTag, String.format("Sync frequency set to %d hours.", frequencyValues[pPosition] / 60));
                else
                    Log.i(logTag, String.format("Sync frequency set to %d minutes.", frequencyValues[pPosition]));
                scheduleJobs();
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent)
    {

    }

    public synchronized void displayVerifySeed()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.wallets).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_add_circle_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.verify_seed));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup verifySeed = (ViewGroup)inflater.inflate(R.layout.verify_seed, dialogView, false);
        LinearLayout wordButtons = verifySeed.findViewById(R.id.seedButtons);
        ArrayList<String> words = new ArrayList<>();
        TextView wordButton;

        dialogView.addView(verifySeed);

        Collections.addAll(words, mSeed.split(" "));
        Collections.shuffle(words);

        for(String word : words)
        {
            wordButton = (TextView)inflater.inflate(R.layout.seed_word_button, wordButtons, false);
            wordButton.setText(word);
            wordButtons.addView(wordButton);
        }

        // Skip button
        View button = inflater.inflate(R.layout.negative_button, dialogView, false);
        button.setTag(R.id.skipCheckSeed);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.skip_check_seed);
        dialogView.addView(button);

        dialogView.setVisibility(View.VISIBLE);
        ((ScrollView)findViewById(R.id.mainScroll)).setScrollY(0);
        mMode = Mode.VERIFY_SEED;
    }

    public synchronized void displayRecoverWallet()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.wallets).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(null);
            actionBar.setTitle(" " + getResources().getString(R.string.recover_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup enterSeed = (ViewGroup)inflater.inflate(R.layout.enter_seed, dialogView, false);

        if(mSeedWordWatcher == null)
        {
            mSeedWordWatcher = new TextWatcher()
            {
                @Override
                public void beforeTextChanged(CharSequence pString, int pStart, int pCount, int pAfter)
                {

                }

                @Override
                public void onTextChanged(CharSequence pString, int pStart, int pBefore, int pCount)
                {
                    LinearLayout wordButtons = findViewById(R.id.seedButtons);
                    EditText textField = findViewById(R.id.seedWordEntry);

                    if(wordButtons == null || textField == null)
                        return;

                    wordButtons.removeAllViews();

                    if(pString.length() == 0)
                        return;

                    // Find all seed words that start with the text field
                    String[] matchingWords = mBitcoin.getMnemonicWords(pString.toString());

                    if(matchingWords.length > 20)
                        return;

                    // Put those words into the word buttons group
                    LayoutInflater inflater = getLayoutInflater();
                    TextView wordButton;

                    for(String word : matchingWords)
                    {
                        wordButton = (TextView)inflater.inflate(R.layout.seed_word_button, wordButtons, false);
                        wordButton.setText(word);
                        wordButtons.addView(wordButton);
                    }
                }

                @Override
                public void afterTextChanged(Editable pString)
                {

                }
            };
        }

        EditText seedWordEntry = enterSeed.findViewById(R.id.seedWordEntry);
        seedWordEntry.addTextChangedListener(mSeedWordWatcher);

        dialogView.addView(enterSeed);

        // Done button
        View button = inflater.inflate(R.layout.button, dialogView, false);
        button.setTag(R.id.importSeed);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.okay);
        dialogView.addView(button);

        focusOnText(seedWordEntry);
        mSeedIsRecovered = true;
        dialogView.setVisibility(View.VISIBLE);
        ((ScrollView)findViewById(R.id.mainScroll)).setScrollY(0);
        mMode = Mode.RECOVER_WALLET;
    }

    public synchronized void displayBackupWallet(String pPassCode)
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.wallets).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_add_circle_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.backup_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup createWallet = (ViewGroup)inflater.inflate(R.layout.view_seed, dialogView, false);
        mSeed = mBitcoin.seed(pPassCode, mCurrentWalletIndex);
        if(mSeed == null)
        {
            showMessage(getString(R.string.failed_retrieve_seed), 2000);
            updateWallets();
            return;
        }
        mSeedIsRecovered = false;
        mSeedIsBackedUp = false;
        mSeedBackupOnly = true;
        ((TextView)createWallet.findViewById(R.id.seed)).setText(mSeed);

        createWallet.findViewById(R.id.seedEntropyContainer).setVisibility(View.GONE);

        dialogView.addView(createWallet);

        // Verify button
        View button = inflater.inflate(R.layout.button, dialogView, false);
        button.setTag(R.id.verifySeedSaved);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.continue_string);
        dialogView.addView(button);

        dialogView.setVisibility(View.VISIBLE);
        ((ScrollView)findViewById(R.id.mainScroll)).setScrollY(0);
        mMode = Mode.BACKUP_WALLET;
    }

    public synchronized void displayAuthorize()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.wallets).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_lock_black_36dp);
            switch(mAuthorizedTask)
            {
                case NONE:
                    break;
                case ADD_KEY:
                    actionBar.setTitle(" " + getString(R.string.authorize) + " " +
                      getString(R.string.add_wallet));
                    break;
                case BACKUP_KEY:
                    actionBar.setTitle(" " + getString(R.string.authorize) + " " +
                      getString(R.string.backup_wallet));
                    break;
                case REMOVE_KEY:
                    actionBar.setTitle(" " + getString(R.string.authorize) + " " +
                      getString(R.string.remove_wallet));
                    break;
                case SIGN_TRANSACTION:
                    actionBar.setTitle(" " + getString(R.string.authorize) + " " +
                      getString(R.string.send_payment));
                    break;
            }
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        // Pass code input
        ViewGroup passcode = (ViewGroup)inflater.inflate(R.layout.passcode, dialogView, false);
        if(!mBitcoin.hasPassCode())
            passcode.findViewById(R.id.createDescription).setVisibility(View.VISIBLE);
        dialogView.addView(passcode);

        // Authorize button
        View button = inflater.inflate(R.layout.button, dialogView, false);
        button.setTag(R.id.authorize);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.authorize);
        dialogView.addView(button);

        focusOnText((EditText)passcode.findViewById(R.id.passcode));
        dialogView.setVisibility(View.VISIBLE);
        ((ScrollView)findViewById(R.id.mainScroll)).setScrollY(0);
        mMode = Mode.AUTHORIZE;
    }

    public synchronized void displayEditWallet()
    {
        Wallet wallet = mBitcoin.wallets[mCurrentWalletIndex];

        if(wallet == null)
            return;

        View button;
        ViewGroup editName;
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.wallets).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_edit_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.title_edit_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        // Edit Name
        editName = (ViewGroup)inflater.inflate(R.layout.edit_name, dialogView, false);
        ((EditText)editName.findViewById(R.id.name)).setText(wallet.name);

        dialogView.addView(editName);

        // Update button
        button = inflater.inflate(R.layout.button, dialogView, false);
        button.setTag(R.id.updateWalletName);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.update_name);
        dialogView.addView(button);

        // Backup button
        button = inflater.inflate(R.layout.button, dialogView, false);
        button.setTag(R.id.backupWallet);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.backup_wallet);
        dialogView.addView(button);

        // Remove button
        button = inflater.inflate(R.layout.button, dialogView, false);
        button.setTag(R.id.removeWallet);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.remove_wallet);
        dialogView.addView(button);

        dialogView.setVisibility(View.VISIBLE);
        ((ScrollView)findViewById(R.id.mainScroll)).setScrollY(0);
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

            transaction.updateView(this, transactionView, mFiatRate);

            // Set tag with transaction offset
            transactionView.setTag(transaction.hash);

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

    public synchronized void displayWalletHistory()
    {
        Wallet wallet = mBitcoin.wallets[mCurrentWalletIndex];
        if(wallet == null)
            return;

        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        mFiatRate = Settings.getInstance(getFilesDir()).doubleValue("usd_rate");

        dialogView.removeAllViews();
        findViewById(R.id.wallets).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_history_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.title_wallet_history));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup historyView = (ViewGroup)inflater.inflate(R.layout.wallet_history, dialogView, false);
        ((TextView)historyView.findViewById(R.id.title)).setText(wallet.name);

        ViewGroup transactions = historyView.findViewById(R.id.walletTransactions);
        populateTransactions(transactions, wallet.transactions,
          100);

        dialogView.addView(historyView);

        alignTransactions(transactions);

        dialogView.setVisibility(View.VISIBLE);
        ((ScrollView)findViewById(R.id.mainScroll)).setScrollY(0);
        mMode = Mode.HISTORY;
    }

    public void copyToClipBoard(View pView)
    {
        ClipboardManager manager = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(manager != null)
        {
            ClipData clip = ClipData.newPlainText("Bitcoin Cash Address",
              ((TextView)pView).getText().toString());
            manager.setPrimaryClip(clip);
            showMessage(getString(R.string.copied_to_clipboard), 2000);
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
            if(((View)pView.getParent()).getTag() != null)
                tag = (Integer)((View)pView.getParent()).getTag();

            switch(tag)
            {
                default:
                    break;
                case R.id.addWallet:
                {
                    synchronized(this)
                    {
                        // Display options for adding wallets
                        View button;
                        LayoutInflater inflater = getLayoutInflater();
                        ViewGroup dialogView = findViewById(R.id.dialog);

                        dialogView.removeAllViews();
                        findViewById(R.id.wallets).setVisibility(View.GONE);
                        findViewById(R.id.progress).setVisibility(View.GONE);
                        findViewById(R.id.statusBar).setVisibility(View.GONE);

                        button = inflater.inflate(R.layout.button, dialogView, false);
                        button.setTag(R.id.createWallet);
                        ((TextView)button.findViewById(R.id.text)).setText(R.string.create_new_key);
                        dialogView.addView(button);

                        button = inflater.inflate(R.layout.button, dialogView, false);
                        button.setTag(R.id.recoverWallet);
                        ((TextView)button.findViewById(R.id.text)).setText(R.string.recover_wallet);
                        dialogView.addView(button);

                        button = inflater.inflate(R.layout.button, dialogView, false);
                        button.setTag(R.id.importWallet);
                        ((TextView)button.findViewById(R.id.text)).setText(R.string.import_bip0032_key);
                        dialogView.addView(button);

                        ActionBar actionBar = getSupportActionBar();
                        if(actionBar != null)
                        {
                            actionBar.setIcon(R.drawable.ic_add_black_36dp);
                            actionBar.setTitle(" " + getResources().getString(R.string.title_add_wallet));
                            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
                        }

                        dialogView.setVisibility(View.VISIBLE);
                        ((ScrollView)findViewById(R.id.mainScroll)).setScrollY(0);
                        mMode = Mode.ADD_WALLET;
                    }
                    break;
                }
                case R.id.buyFromCoinbase:
                {
                    String coinbaseReferallUrl = "https://www.coinbase.com/join/597a904283ff1a00a71aae6c";
                    Intent coinbaseIntent = new Intent(Intent.ACTION_VIEW);
                    coinbaseIntent.setData(Uri.parse(coinbaseReferallUrl));
                    startActivity(coinbaseIntent);
                    break;
                }
                case R.id.createWallet:
                    displayCreateWallet();
                    break;
                case R.id.verifySeedSaved:
                    displayVerifySeed();
                    break;
                case R.id.skipCheckSeed:
                    if(mSeedBackupOnly)
                    {
                        mSeed = null;
                        updateWallets();
                    }
                    else
                    {
                        mAuthorizedTask = AuthorizedTask.ADD_KEY;
                        mKeyToLoad = null;
                        displayAuthorize();
                    }
                    break;
                case R.id.recoverWallet:
                    displayRecoverWallet();
                    break;
                case R.id.importSeed:
                {
                    mSeed = ((TextView)findViewById(R.id.seed)).getText().toString();
                    mDerivationPathMethodToLoad = Bitcoin.BIP0044_DERIVATION; // TODO Add options to specify this
                    mKeyToLoad = null;
                    mSeedIsBackedUp = true;

                    View invalidDescription = findViewById(R.id.invalidDescription);
                    if(mBitcoin.seedIsValid(mSeed) || invalidDescription.getVisibility() == View.VISIBLE)
                    {
                        mAuthorizedTask = AuthorizedTask.ADD_KEY;
                        displayAuthorize();
                    }
                    else
                        invalidDescription.setVisibility(View.VISIBLE);
                    break;
                }
                case R.id.importWallet:
                {
                    synchronized(this)
                    {
                        LayoutInflater inflater = getLayoutInflater();
                        ViewGroup dialogView = findViewById(R.id.dialog);

                        dialogView.removeAllViews();
                        findViewById(R.id.wallets).setVisibility(View.GONE);
                        findViewById(R.id.progress).setVisibility(View.GONE);
                        findViewById(R.id.statusBar).setVisibility(View.GONE);

                        inflater.inflate(R.layout.import_bip32_key, dialogView, true);

                        Spinner derivationMethod = findViewById(R.id.derivationMethodSpinner);
                        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.derivation_methods, android.R.layout.simple_spinner_item);
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        derivationMethod.setAdapter(adapter);

                        // Load button
                        View button = inflater.inflate(R.layout.button, dialogView, false);
                        button.setTag(R.id.loadKey);
                        ((TextView)button.findViewById(R.id.text)).setText(R.string.import_text);
                        dialogView.addView(button);

                        dialogView.setVisibility(View.VISIBLE);
                        ((ScrollView)findViewById(R.id.mainScroll)).setScrollY(0);
                        focusOnText((EditText)dialogView.findViewById(R.id.importText));
                    }
                    break;
                }
                case R.id.loadKey: // Import BIP-0032 encoded key
                {
                    mAuthorizedTask = AuthorizedTask.ADD_KEY;
                    mKeyToLoad = ((EditText)findViewById(R.id.importText)).getText().toString();
                    mDerivationPathMethodToLoad = ((Spinner)findViewById(R.id.derivationMethodSpinner)).getSelectedItemPosition();
                    mSeed = null;
                    displayAuthorize();
                    break;
                }
                case R.id.updateWalletName:
                {
                    ViewGroup dialogView = findViewById(R.id.dialog);
                    EditText nameView = dialogView.findViewById(R.id.name);
                    String name = nameView.getText().toString();
                    if(mCurrentWalletIndex != -1 && mBitcoin.setName(mCurrentWalletIndex, name))
                        updateWallets();
                    else
                        showMessage(getString(R.string.failed_update_name), 2000);
                    break;
                }
                case R.id.backupWallet:
                    mSeedBackupOnly = true;
                    mAuthorizedTask = AuthorizedTask.BACKUP_KEY;
                    displayAuthorize();
                    break;
                case R.id.removeWallet:
                    mAuthorizedTask = AuthorizedTask.REMOVE_KEY;
                    displayAuthorize();
                    break;
                case R.id.enterPaymentDetails:
                {
                    TextView paymentCode = findViewById(R.id.paymentCode);
                    if(paymentCode != null)
                    {
                        mPaymentRequest = mBitcoin.decodePaymentCode(paymentCode.getText().toString());
                        if(mPaymentRequest != null && mPaymentRequest.format != PaymentRequest.FORMAT_INVALID)
                            displaySendPayment();
                        else
                        {
                            showMessage(getString(R.string.failed_payment_code), 2000);
                            updateWallets();
                        }
                    }
                    else
                    {
                        showMessage(getString(R.string.failed_payment_code), 2000);
                        updateWallets();
                    }
                    break;
                }
                case R.id.updateRequestPaymentCode:
                {
                    String label = ((EditText)findViewById(R.id.label)).getText().toString();
                    String message = ((EditText)findViewById(R.id.message)).getText().toString();
                    mPaymentRequest.setLabel(label);
                    mPaymentRequest.setMessage(message);
                    CreatePaymentRequestTask task = new CreatePaymentRequestTask(this, mBitcoin,
                      mPaymentRequest);
                    task.execute();

                    synchronized(this)
                    {
                        ViewGroup walletsView = findViewById(R.id.wallets);
                        walletsView.setVisibility(View.GONE);

                        findViewById(R.id.dialog).setVisibility(View.GONE);
                        findViewById(R.id.progress).setVisibility(View.VISIBLE);
                        mMode = Mode.IN_PROGRESS;
                    }
                    break;
                }
                case R.id.openScanner:
                    mQRScanner.initiateScan(IntentIntegrator.QR_CODE_TYPES);
                    break;
                case R.id.sendPayment:
                {
                    mDelayHandler.removeCallbacks(mRequestExpiresUpdater);

                    if(mPaymentRequest.protocolDetails != null && mPaymentRequest.protocolDetails.hasExpires())
                    {
                        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                        if(mPaymentRequest.protocolDetails.getExpires() <=
                          (calendar.getTimeInMillis() / 1000L) + 6)
                        {
                            showMessage(getString(R.string.request_expired), 2000);
                            mPaymentRequest = null;
                            displayWallets();
                            return;
                        }
                    }

                    mAuthorizedTask = AuthorizedTask.SIGN_TRANSACTION;
                    displayAuthorize();
                    break;
                }
                case R.id.authorize:
                    String passcode = ((EditText)findViewById(R.id.passcode)).getText().toString();
                    switch(mAuthorizedTask)
                    {
                        case ADD_KEY:
                        {
                            if(mKeyToLoad != null)
                            {
                                ImportKeyTask task = new ImportKeyTask(this, mBitcoin, passcode, mKeyToLoad,
                                  mDerivationPathMethodToLoad);
                                task.execute();
                                mKeyToLoad = null;
                            }
                            else
                            {
                                CreateKeyTask task = new CreateKeyTask(this, mBitcoin, passcode, mSeed,
                                  mDerivationPathMethodToLoad, mSeedIsRecovered, mSeedIsBackedUp);
                                task.execute();
                                mSeed = null;
                            }

                            synchronized(this)
                            {
                                ViewGroup dialogView = findViewById(R.id.dialog);
                                dialogView.removeAllViews();

                                findViewById(R.id.wallets).setVisibility(View.GONE);
                                findViewById(R.id.dialog).setVisibility(View.GONE);
                                findViewById(R.id.progress).setVisibility(View.VISIBLE);
                                mMode = Mode.IN_PROGRESS;
                            }
                            break;
                        }
                        case BACKUP_KEY:
                            displayBackupWallet(passcode);
                            break;
                        case REMOVE_KEY:
                        {
                            RemoveKeyTask task = new RemoveKeyTask(this, mBitcoin, passcode,
                              mCurrentWalletIndex);
                            task.execute();

                            synchronized(this)
                            {
                                ViewGroup dialogView = findViewById(R.id.dialog);
                                dialogView.removeAllViews();

                                findViewById(R.id.wallets).setVisibility(View.GONE);
                                findViewById(R.id.dialog).setVisibility(View.GONE);
                                findViewById(R.id.progress).setVisibility(View.VISIBLE);
                                mMode = Mode.IN_PROGRESS;
                            }
                            break;
                        }
                        case SIGN_TRANSACTION:
                        {
                            if(mPaymentRequest.protocolDetails != null && mPaymentRequest.protocolDetails.hasExpires())
                            {
                                Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                                if(mPaymentRequest.protocolDetails.getExpires() <=
                                  (calendar.getTimeInMillis() / 1000L) + 5)
                                {
                                    showMessage(getString(R.string.request_expired), 2000);
                                    mPaymentRequest = null;
                                    displayWallets();
                                    return;
                                }
                            }

                            CreateTransactionTask task = new CreateTransactionTask(this, mBitcoin, passcode,
                              mCurrentWalletIndex, mPaymentRequest);
                            task.execute();

                            synchronized(this)
                            {
                                ViewGroup dialogView = findViewById(R.id.dialog);
                                dialogView.removeAllViews();

                                findViewById(R.id.wallets).setVisibility(View.GONE);
                                findViewById(R.id.dialog).setVisibility(View.GONE);
                                findViewById(R.id.progress).setVisibility(View.VISIBLE);
                                mMode = Mode.IN_PROGRESS;
                            }
                            break;
                        }
                    }
                    break;
            }
            break;
        case R.id.seedWordButton:
        {
            ViewGroup wordButtons = (ViewGroup)pView.getParent();

            // Add word to seed
            TextView seed = findViewById(R.id.seed);
            if(seed.getText().length() > 0)
                seed.setText(String.format("%s %s", seed.getText(), ((TextView)pView).getText()));
            else
                seed.setText(((TextView)pView).getText());

            if(mMode == Mode.RECOVER_WALLET)
            {
                wordButtons.removeAllViews();
                ((EditText)findViewById(R.id.seedWordEntry)).setText("");
                if(mBitcoin.seedIsValid(seed.getText().toString()))
                {
                    findViewById(R.id.invalidDescription).setVisibility(View.GONE);
                    findViewById(R.id.isValid).setVisibility(View.VISIBLE);
                }
                else
                    findViewById(R.id.isValid).setVisibility(View.GONE);
            }
            else
            {
                wordButtons.removeView(pView);

                if(wordButtons.getChildCount() == 0)
                {
                    // Verify seed matches
                    String seedText = (String)seed.getText();
                    if(seedText.equals(mSeed))
                    {
                        if(mSeedBackupOnly)
                        {
                            mSeed = null;
                            mBitcoin.setIsBackedUp(mCurrentWalletIndex);
                            showMessage(getString(R.string.seed_matches), 2000);
                            updateWallets();
                        }
                        else
                        {
                            mAuthorizedTask = AuthorizedTask.ADD_KEY;
                            mKeyToLoad = null;
                            mSeedIsBackedUp = true;
                            displayAuthorize();
                        }
                    }
                    else
                    {
                        showMessage(getString(R.string.seed_doesnt_match), 2000);
                        displayVerifySeed();
                    }
                }
            }
            break;
        }
        case R.id.removeSeedWord:
        {
            TextView seed = findViewById(R.id.seed);
            if(seed != null && seed.getText().length() > 0)
            {
                String[] words = seed.getText().toString().split(" ");
                StringBuffer newSeed = new StringBuffer();
                for(int index = 0; index < words.length - 1; index++)
                {
                    if(index > 0)
                        newSeed.append(" ");
                    newSeed.append(words[index]);
                }
                seed.setText(newSeed);

                if(mMode == Mode.VERIFY_SEED)
                {
                    // Add button
                    LayoutInflater inflater = getLayoutInflater();
                    LinearLayout wordButtons = findViewById(R.id.seedButtons);
                    TextView wordButton;

                    wordButton = (TextView)inflater.inflate(R.layout.seed_word_button, wordButtons, false);
                    wordButton.setText(words[words.length - 1]);
                    wordButtons.addView(wordButton);
                }
            }
            break;
        }
        case R.id.walletHeader: // Expand/Compress wallet
        {
            ViewGroup walletView = (ViewGroup)pView.getParent();
            if(walletView != null)
            {
                View walletDetails = walletView.findViewById(R.id.walletDetails);
                if(walletDetails != null)
                {
                    int visibility = walletDetails.getVisibility();
                    if(visibility == View.GONE)
                    {
                        mCurrentWalletIndex = (int)walletView.getTag();

                        ((ImageView)pView.findViewById(R.id.walletExpand))
                          .setImageResource(R.drawable.ic_expand_less_white_36dp);
                        walletDetails.setVisibility(View.VISIBLE);

                        // Make all others gone
                        ViewGroup wallets = findViewById(R.id.wallets);

                        synchronized(this)
                        {
                            synchronized(mBitcoin)
                            {
                                int index = 0;
                                for(Wallet wallet : mBitcoin.wallets)
                                {
                                    if(mCurrentWalletIndex != index)
                                    {
                                        View otherWalletView = wallets.getChildAt(index);
                                        otherWalletView.findViewById(R.id.walletDetails).setVisibility(View.GONE);
                                        ((ImageView)otherWalletView.findViewById(R.id.walletExpand))
                                          .setImageResource(R.drawable.ic_expand_more_white_36dp);
                                    }
                                    index++;
                                }
                            }
                        }
                    }
                    else
                    {
                        ((ImageView)pView.findViewById(R.id.walletExpand))
                          .setImageResource(R.drawable.ic_expand_more_white_36dp);
                        walletDetails.setVisibility(View.GONE);
                    }
                }
            }
            break;
        }
        case R.id.walletSend:
        {
            ViewGroup walletView = (ViewGroup)pView.getParent().getParent().getParent();
            mCurrentWalletIndex = (int)walletView.getTag();
            displayEnterPaymentCode();
            break;
        }
        case R.id.walletReceive:
        {
            ViewGroup walletView = (ViewGroup)pView.getParent().getParent().getParent();
            mCurrentWalletIndex = (int)walletView.getTag();
            CreateAddressTask task = new CreateAddressTask(this, mBitcoin, mCurrentWalletIndex, 0);
            task.execute();

            synchronized(this)
            {
                ViewGroup walletsView = findViewById(R.id.wallets);
                walletsView.setVisibility(View.GONE);

                findViewById(R.id.progress).setVisibility(View.VISIBLE);
                mMode = Mode.IN_PROGRESS;
            }
            break;
        }
        case R.id.walletHistory:
        {
            ViewGroup walletView = (ViewGroup)pView.getParent().getParent().getParent();
            mCurrentWalletIndex = (int)walletView.getTag();
            displayWalletHistory();
            break;
        }
        case R.id.editWallet:
        {
            mCurrentWalletIndex = (int)((View)pView.getParent()).getTag();
            displayEditWallet();
            break;
        }
        case R.id.walletTransaction:
        {
            openTransaction(mCurrentWalletIndex, (String)pView.getTag());
            break;
        }
        case R.id.paymentCode:
        {
            ClipboardManager manager = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(manager != null)
            {
                ClipData clip = ClipData.newPlainText("Bitcoin Cash Payment Code",
                  ((TextView)pView).getText().toString());
                manager.setPrimaryClip(clip);
                showMessage(getString(R.string.payment_code_clipboard), 2000);
            }
            break;
        }
        case R.id.sendMax:
        {
            if(!mPaymentRequest.amountSpecified)
            {
                mPaymentRequest.sendMax = true;
                findViewById(R.id.sendAmount).setEnabled(false);
                findViewById(R.id.sendAmount).setFocusable(false);

                mPaymentRequest.amount = mPaymentRequest.amountAvailable() - mPaymentRequest.estimatedFee();
                updateFee();
            }
        }
        case R.id.systemNotificationSettings:
        {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                Intent settingsIntent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                //.putExtra(Settings.EXTRA_CHANNEL_ID, MY_CHANNEL_ID);
                startActivity(settingsIntent);
            }
        }
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
        {
            mDelayHandler.removeCallbacks(mRequestExpiresUpdater);
            if(mMode == Mode.AUTHORIZE)
            {
                mSeed = null;
                mKeyToLoad = null;
            }
            updateWallets(); // Go back to main wallets view
        }
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
