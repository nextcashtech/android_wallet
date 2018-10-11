/**************************************************************************
 * Copyright 2017-2018 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
package tech.nextcash.nextcashwallet;

import android.Manifest;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Locale;
import java.util.TimeZone;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener,
  CompoundButton.OnCheckedChangeListener, Scanner.CallBack
{
    public static final String logTag = "MainActivity";
    public static final int CAMERA_PERMISSION_REQUEST_CODE = 41;

    public static final String ACTIVITY_ACTION = "tech.nextcash.nextcashwallet.ACTIVITY_ACTION";
    public static final String ACTION_MESSAGE_ID_FIELD = "MESSAGE_ID";
    public static final String ACTION_MESSAGE_STRING_FIELD = "MESSAGE_STRING";
    public static final String ACTION_MESSAGE_PERSISTENT_FIELD = "MESSAGE_PERSISTENT";
    public static final String ACTION_SHOW_MESSAGE = "SHOW_MESSAGE";
    public static final String ACTION_DISPLAY_WALLETS = "DISPLAY_WALLETS";
    public static final String ACTION_DISPLAY_TRANSACTION = "DISPLAY_TRANSACTION";
    public static final String ACTION_TRANSACTION_NOT_FOUND = "TRANSACTION_NOT_FOUND";
    public static final String ACTION_DISPLAY_DIALOG = "DISPLAY_DIALOG";
    public static final String ACTION_DISPLAY_SETTINGS = "DISPLAY_SETTINGS";
    public static final String ACTION_DISPLAY_INFO = "DISPLAY_INFO";
    public static final String ACTION_DISPLAY_REQUEST_PAYMENT = "DISPLAY_REQUEST_PAYMENT";
    public static final String ACTION_DISPLAY_ENTER_PAYMENT = "DISPLAY_ENTER_PAYMENT";
    public static final String ACTION_ACKNOWLEDGE_PAYMENT = "ACKNOWLEDGE_PAYMENT";
    public static final String ACTION_CLEAR_PAYMENT = "CLEAR_PAYMENT";
    public static final String ACTION_EXCHANGE_RATE_UPDATED = "EXCHANGE_RATE_UPDATED";
    public static final String ACTION_EXCHANGE_RATE_FIELD = "EXCHANGE_RATE";

    private Handler mDelayHandler;
    private Runnable mStatusUpdateRunnable, mRateUpdateRunnable, mClearFinishOnBack, mClearNotification,
      mRequestExpiresUpdater, mRequestTransactionRunnable;

    private enum Mode { LOADING_WALLETS, LOADING_CHAIN, IN_PROGRESS, WALLETS, ADD_WALLET, CREATE_WALLET, RECOVER_WALLET,
      IMPORT_WALLET, VERIFY_SEED, BACKUP_WALLET, EDIT_WALLET, HISTORY, TRANSACTION, SCAN, RECEIVE, ENTER_PAYMENT_CODE,
      CLIPBOARD_PAYMENT_CODE, ENTER_PAYMENT_DETAILS, AUTHORIZE, INFO, HELP, SETTINGS }
    private Mode mMode, mPreviousMode;
    private boolean mWalletsNeedUpdated;
    private enum AuthorizedTask { NONE, INITIALIZE, ADD_KEY, BACKUP_KEY, REMOVE_KEY, SIGN_TRANSACTION }
    private AuthorizedTask mAuthorizedTask;
    private String mKeyToLoad, mSeed;
    private int mSeedEntropyBytes;
    private long mRecoverDate;
    private boolean mSeedIsBackedUp;
    private int mCurrentWalletIndex;
    private boolean mSeedBackupOnly;
    private int mDerivationPathMethodToLoad;
    private String mExchangeType;
    private double mExchangeRate;
    private Bitcoin mBitcoin;
    private boolean mFinishOnBack;
    private BitcoinService.CallBacks mServiceCallBacks;
    private BitcoinService mService;
    private boolean mServiceIsBound, mServiceIsBinding;
    private ServiceConnection mServiceConnection;
    private BroadcastReceiver mReceiver;
    private PaymentRequest mPaymentRequest;
    private boolean mIsSupportURI;
    private Bitmap mQRCode;
    private boolean mDontUpdatePaymentAmount;
    private TextWatcher mSeedWordWatcher, mAmountWatcher, mRequestAmountWatcher;
    private int mRequestedTransactionWalletIndex, mHistoryToShowWalletIndex;
    private String mRequestedTransactionID;
    private int mRequestedTransactionAttempts;
    private FullTransaction mTransaction;
    private int mTransactionWalletIndex;
    private ArrayList<String> mPersistentMessages;
    private Messages mMessages;
    private Scanner mScanner;
    private String mPIN;


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
            showMessage(mTransaction.description(getApplicationContext(), mExchangeType, mExchangeRate), 2000);
        }
    }

    @Override
    protected void onCreate(Bundle pSavedInstanceState)
    {
        super.onCreate(pSavedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Settings settings = Settings.getInstance(getApplicationContext().getFilesDir());

        mBitcoin = ((MainApp)getApplication()).bitcoin;
        mBitcoin.appIsOpen = true;
        mDelayHandler = new Handler();
        mStatusUpdateRunnable = new Runnable() { @Override public void run() { updateStatus(); } };
        mRateUpdateRunnable = new Runnable() { @Override public void run() { scheduleExchangeRateUpdate(); } };
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
        mRequestTransactionRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                openTransaction(mRequestedTransactionWalletIndex, mRequestedTransactionID);
            }
        };
        mMode = Mode.LOADING_WALLETS;
        mPreviousMode = Mode.LOADING_WALLETS;
        mAuthorizedTask = AuthorizedTask.NONE;
        mFinishOnBack = false;
        mExchangeType = settings.value(FiatRateRequestTask.EXCHANGE_TYPE_NAME);
        if(mExchangeType == null || mExchangeType.length() == 0)
        {
            mExchangeType = "USD";
            settings.setValue(FiatRateRequestTask.EXCHANGE_TYPE_NAME, mExchangeType);
            mExchangeRate = 0.0;
        }
        else
            mExchangeRate = settings.doubleValue(FiatRateRequestTask.EXCHANGE_RATE_NAME);
        mCurrentWalletIndex = -1;
        mWalletsNeedUpdated = false;
        mDontUpdatePaymentAmount = false;
        mSeedEntropyBytes = 0;
        mRecoverDate = 0L;
        mRequestedTransactionWalletIndex = -1;
        mHistoryToShowWalletIndex = -1;
        mPersistentMessages = new ArrayList<>();
        mRequestedTransactionAttempts = 0;
        mIsSupportURI = false;
        mMessages = new Messages();
        mMessages.load(getApplicationContext());
        refreshPersistentMessages();
        mScanner = new Scanner(this, new Handler(getMainLooper()));
        mPIN = null;

        if(!settings.containsValue("beta_message"))
        {
            addPersistentMessage(getString(R.string.beta_message));
            settings.setLongValue("beta_message", System.currentTimeMillis() / 1000);
        }

        mServiceCallBacks = new BitcoinService.CallBacks()
        {
            @Override
            public void onWalletsLoad()
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        MainActivity.this.onWalletsLoad();
                    }
                });
            }

            @Override
            public void onChainLoad()
            {
                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        MainActivity.this.onChainLoad();
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
                        MainActivity.this.onUpdate();
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
            }
        };

        mReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context pContext, Intent pIntent)
            {
                if(pIntent.getAction() == null)
                    return;

                String message = null;
                if(pIntent.getExtras() != null)
                {
                    if(pIntent.getExtras().containsKey(MainActivity.ACTION_MESSAGE_ID_FIELD))
                        message = getString(pIntent.getExtras().getInt(MainActivity.ACTION_MESSAGE_ID_FIELD));
                    else if(pIntent.getExtras().containsKey(MainActivity.ACTION_MESSAGE_STRING_FIELD))
                        message = pIntent.getExtras().getString(MainActivity.ACTION_MESSAGE_STRING_FIELD);

                    if(message != null)
                    {
                        if(pIntent.getExtras().containsKey(MainActivity.ACTION_MESSAGE_PERSISTENT_FIELD))
                        {
                            Log.i(logTag, String.format("Persistent message contained in action : %s", message));
                            addPersistentMessage(message);
                        }
                        else
                        {
                            Log.i(logTag, String.format("Message contained in action : %s", message));
                            showMessage(message, 2000);
                        }
                    }
                }

                switch(pIntent.getAction())
                {
                case ACTION_SHOW_MESSAGE:
                    Log.i(logTag, "Show message action received");
                    if(message == null)
                        Log.w(logTag, "Show message action received with no message value");
                    break;
                case ACTION_DISPLAY_WALLETS:
                    Log.i(logTag, "Display wallets action received");
                    if(mBitcoin.walletsAreLoaded())
                        displayWallets();
                    break;
                case ACTION_DISPLAY_TRANSACTION:
                    Log.i(logTag, "Display transaction action received");
                    displayTransaction();
                    break;
                case ACTION_TRANSACTION_NOT_FOUND:
                    Log.i(logTag, "Transaction not found action received");

                    if(mRequestedTransactionAttempts > 10)
                    {
                        showMessage(getString(R.string.unable_to_find_transaction), 2000);
                        if(mBitcoin.walletsAreLoaded())
                            displayWallets();
                    }
                    else
                    {
                        mDelayHandler.removeCallbacks(mRequestTransactionRunnable);
                        mDelayHandler.postDelayed(mRequestTransactionRunnable, 2000);
                        mRequestedTransactionAttempts++;
                    }

                    break;
                case ACTION_DISPLAY_DIALOG:
                    Log.i(logTag, "Display dialog action received");
                    displayDialog();
                    break;
                case ACTION_DISPLAY_INFO:
                    Log.i(logTag, "Display info action received");
                    displayInfo();
                    break;
                case ACTION_DISPLAY_SETTINGS:
                    Log.i(logTag, "Display settings action received");
                    displaySettings();
                    break;
                case ACTION_DISPLAY_REQUEST_PAYMENT:
                    Log.i(logTag, "Display request payment action received");
                    displayRequestPaymentCode();
                    break;
                case ACTION_DISPLAY_ENTER_PAYMENT:
                    Log.i(logTag, "Display enter payment details action received");
                    displayPaymentDetails();
                    break;
                case ACTION_ACKNOWLEDGE_PAYMENT:
                    Log.i(logTag, "Acknowledge payment action received");
                    if(mPaymentRequest != null && mPaymentRequest.protocolDetails != null &&
                      mPaymentRequest.protocolDetails.hasPaymentUrl())
                    {
                        FinishPaymentRequestTask finishPaymentRequestTask =
                          new FinishPaymentRequestTask(getApplicationContext(), mBitcoin, mCurrentWalletIndex,
                            mPaymentRequest);
                        finishPaymentRequestTask.execute();
                    }
                    else
                        displayWallets();
                    break;
                case ACTION_CLEAR_PAYMENT:
                    Log.i(logTag, "Clear payment action received");
                    mPaymentRequest = null;
                    if(mBitcoin.walletsAreLoaded())
                        displayWallets();
                    break;
                case ACTION_EXCHANGE_RATE_UPDATED:
                    if(pIntent.getExtras().containsKey(MainActivity.ACTION_EXCHANGE_RATE_FIELD))
                    {
                        double exchangeRate = pIntent.getExtras().getDouble(MainActivity.ACTION_EXCHANGE_RATE_FIELD);
                        Log.i(logTag, String.format("New exchange rate : %.4f", exchangeRate));
                        mExchangeRate = exchangeRate;
                        updateWallets(); // Update transaction amounts with new exchange rate
                    }
                    break;
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTIVITY_ACTION);
        filter.addAction(ACTION_SHOW_MESSAGE);
        filter.addAction(ACTION_DISPLAY_WALLETS);
        filter.addAction(ACTION_DISPLAY_TRANSACTION);
        filter.addAction(ACTION_TRANSACTION_NOT_FOUND);
        filter.addAction(ACTION_DISPLAY_DIALOG);
        filter.addAction(ACTION_DISPLAY_SETTINGS);
        filter.addAction(ACTION_DISPLAY_INFO);
        filter.addAction(ACTION_ACKNOWLEDGE_PAYMENT);
        filter.addAction(ACTION_CLEAR_PAYMENT);
        filter.addAction(ACTION_DISPLAY_REQUEST_PAYMENT);
        filter.addAction(ACTION_DISPLAY_ENTER_PAYMENT);
        filter.addAction(ACTION_EXCHANGE_RATE_UPDATED);
        registerReceiver(mReceiver, filter);

        LinearLayout pinLayout = findViewById(R.id.pin);
        LayoutInflater inflater = getLayoutInflater();
        inflater.inflate(R.layout.pin_entry, pinLayout, true);
        pinLayout.setVisibility(View.GONE);

        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.dialog).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.VISIBLE);
        findViewById(R.id.statusBar).setVisibility(View.VISIBLE);
        findViewById(R.id.controls).setVisibility(View.VISIBLE);
        mMode = Mode.LOADING_WALLETS;

        scheduleJobs();

        if(pSavedInstanceState != null && pSavedInstanceState.containsKey("State"))
        {
            String state = pSavedInstanceState.getString("State");
            if(state != null)
                switch(state)
                {
                case "Transaction":
                    if(pSavedInstanceState.containsKey("Transaction") && pSavedInstanceState.containsKey("Wallet"))
                    {
                        mRequestedTransactionWalletIndex = pSavedInstanceState.getInt("Wallet");
                        mRequestedTransactionID = pSavedInstanceState.getString("Transaction");
                    }
                    break;
                case "History":
                    if(pSavedInstanceState.containsKey("Wallet"))
                        mHistoryToShowWalletIndex = pSavedInstanceState.getInt("Wallet");
                    break;
                case "Info":
                    displayInfo();
                    break;
                case "Settings":
                    displaySettings();
                    break;
                }
        }

        if(!settings.containsValue("add_wallet_message"))
        {
            addPersistentMessage(getString(R.string.add_wallet_message));
            settings.setLongValue("add_wallet_message", System.currentTimeMillis() / 1000);
            mAuthorizedTask = AuthorizedTask.INITIALIZE;
            displayAuthorize();
        }
        else if(!settings.containsValue(Bitcoin.PIN_CREATED_NAME))
        {
            // Set to "unknown" if pin created before this value existed.
            settings.setLongValue(Bitcoin.PIN_CREATED_NAME, 0);
        }

        Intent intent = getIntent();
        if(intent != null)
            handleIntent(intent);
    }

    private synchronized void startBitcoinService()
    {
        if(!mServiceIsBound && !mServiceIsBinding)
        {
            Log.d(logTag, "Binding Bitcoin service");
            mServiceIsBinding = true;

            Intent intent = new Intent(this, BitcoinService.class);
            intent.putExtra("FinishMode", Bitcoin.FINISH_ON_REQUEST);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                startForegroundService(intent);
            else
                startService(intent);
            bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        }
        else
            mBitcoin.setFinishMode(Bitcoin.FINISH_ON_REQUEST);
    }

    private void onWalletsLoad()
    {
        Log.i(logTag, "Wallets Loaded");

        // Update header
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup headerView = findViewById(R.id.header);

        View chainLoadingView = headerView.findViewById(R.id.blockChainLoading);
        if(mBitcoin.chainIsLoaded())
        {
            if(chainLoadingView != null)
                headerView.removeView(chainLoadingView);
        }
        else if(chainLoadingView == null)
            inflater.inflate(R.layout.block_chain_loading, headerView, true);

        View initialBlockDownloadView = headerView.findViewById(R.id.initialBlockDownloadMessage);
        if(mBitcoin.initialBlockDownloadIsComplete())
        {
            if(initialBlockDownloadView != null)
                headerView.removeView(initialBlockDownloadView);
        }
        else if(initialBlockDownloadView == null)
            inflater.inflate(R.layout.initial_block_download_message, headerView, true);

        // Update wallets
        mWalletsNeedUpdated = false; // Force rebuild of wallets
        updateWallets();
        updateStatus();
        if(mRequestedTransactionID != null)
            openTransaction(mRequestedTransactionWalletIndex, mRequestedTransactionID);
        else if(mHistoryToShowWalletIndex != -1)
        {
            mCurrentWalletIndex = mHistoryToShowWalletIndex;
            mHistoryToShowWalletIndex = -1;
            displayWalletHistory();
        }
        else if(mMode == Mode.LOADING_WALLETS)
            displayWallets();
    }

    private void onChainLoad()
    {
        Log.i(logTag, "Chain Loaded");

        // Update header
        ViewGroup headerView = findViewById(R.id.header);
        View chainLoadingView = headerView.findViewById(R.id.blockChainLoading);
        if(chainLoadingView != null)
            headerView.removeView(chainLoadingView);

        // Update wallets
        mWalletsNeedUpdated = false; // Force rebuild of wallets

        onUpdate();
    }

    private void onUpdate()
    {
        updateWallets();
        updateStatus();

        switch(mMode)
        {
            case LOADING_WALLETS:
                break;
            case LOADING_CHAIN:
                break;
            case IN_PROGRESS:
                break;
            case WALLETS:
                break;
            case ADD_WALLET:
                break;
            case CREATE_WALLET:
                break;
            case RECOVER_WALLET:
                break;
            case IMPORT_WALLET:
                break;
            case VERIFY_SEED:
                break;
            case BACKUP_WALLET:
                break;
            case EDIT_WALLET:
                break;
            case HISTORY:
                displayWalletHistory(); // Reload
                break;
            case TRANSACTION:
                if(mTransaction != null)
                    openTransaction(mTransactionWalletIndex, mTransaction.hash); // Reload
                break;
            case SCAN:
                break;
            case RECEIVE:
                break;
            case ENTER_PAYMENT_CODE:
                break;
            case CLIPBOARD_PAYMENT_CODE:
                break;
            case ENTER_PAYMENT_DETAILS:
                break;
            case AUTHORIZE:
                break;
            case INFO:
                break;
            case HELP:
                break;
            case SETTINGS:
                displaySettings(); // Reload
                break;
        }
    }

    private void scheduleJobs()
    {
        JobScheduler jobScheduler = (JobScheduler)getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobScheduler == null)
            Log.e(logTag, "Failed to get Job Scheduler Service");
        else
        {
            int syncFrequency = Settings.getInstance(getFilesDir()).intValue(Bitcoin.SYNC_FREQUENCY_NAME);
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
        }
    }

    public synchronized void handleIntent(Intent pIntent)
    {
        String action = pIntent.getAction();
        Bundle extras = pIntent.getExtras();

        if(action != null && action.equals("Transaction") &&
          extras != null && extras.containsKey("Wallet") && extras.containsKey("Transaction"))
        {
            if(mBitcoin.walletsAreLoaded())
                openTransaction(extras.getInt("Wallet"), extras.getString("Transaction"));
            else
            {
                mRequestedTransactionWalletIndex = extras.getInt("Wallet");
                mRequestedTransactionID = extras.getString("Transaction");
            }
        }

        if(action != null && action.equals("Message") &&
          extras != null && extras.containsKey("Message"))
            showMessage(getString(extras.getInt("Message")), 2000);
    }

    @Override
    public void onNewIntent(Intent pIntent)
    {
        handleIntent(pIntent);
        super.onNewIntent(pIntent);
    }

    @Override
    protected void onSaveInstanceState(Bundle pState)
    {
        switch(mMode)
        {
            case LOADING_WALLETS:
                break;
            case LOADING_CHAIN:
                break;
            case IN_PROGRESS:
                break;
            case WALLETS:
                break;
            case ADD_WALLET:
                break;
            case CREATE_WALLET:
                break;
            case RECOVER_WALLET:
                break;
            case IMPORT_WALLET:
                break;
            case VERIFY_SEED:
                break;
            case BACKUP_WALLET:
                break;
            case EDIT_WALLET:
                break;
            case HISTORY:
                pState.putString("State", "History");
                pState.putInt("Wallet", mCurrentWalletIndex);
                break;
            case TRANSACTION:
                pState.putString("State", "Transaction");
                pState.putString("Transaction", ((TextView)findViewById(R.id.id)).getText().toString());
                pState.putInt("Wallet", mCurrentWalletIndex);
                break;
            case SCAN:
                break;
            case RECEIVE:
                break;
            case ENTER_PAYMENT_CODE:
                break;
            case CLIPBOARD_PAYMENT_CODE:
                break;
            case ENTER_PAYMENT_DETAILS:
                break;
            case AUTHORIZE:
                break;
            case INFO:
                pState.putString("State", "Info");
                break;
            case HELP:
                pState.putString("State", "Help");
                break;
            case SETTINGS:
                pState.putString("State", "Settings");
                break;
        }
        super.onSaveInstanceState(pState);
    }

    @Override
    public void onStart()
    {
        Log.d(logTag, "Starting");
        mBitcoin.appIsOpen = true;
        startBitcoinService();
        mBitcoin.clearFinishTime();
        scheduleExchangeRateUpdate();
        updateStatus();

        super.onStart();
    }

    @Override
    public synchronized void onPause()
    {
        if(mMode == Mode.SCAN)
            displayWallets();
        super.onPause();
    }

    @Override
    public synchronized void onStop()
    {
        Log.d(logTag, "Stopping");
        if(mMode == Mode.SCAN)
            mScanner.close();
        mDelayHandler.removeCallbacks(mStatusUpdateRunnable);
        mDelayHandler.removeCallbacks(mRateUpdateRunnable);
        mDelayHandler.removeCallbacks(mRequestExpiresUpdater);
        mBitcoin.appIsOpen = false;
        mBitcoin.setFinishTime(180); // 180 seconds in the future
        mBitcoin.setFinishMode(Bitcoin.FINISH_ON_SYNC);
        if(mServiceIsBound)
        {
            Log.d(logTag, "Unbinding Bitcoin service");
            mService.removeCallBacks(mServiceCallBacks);
            unbindService(mServiceConnection);
            mServiceIsBound = false;
            mService = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy()
    {
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    private void scheduleExchangeRateUpdate()
    {
        mDelayHandler.removeCallbacks(mRateUpdateRunnable); // Ensure we don't get multiple scheduled

        new FiatRateRequestTask(getApplicationContext()).execute();
        mDelayHandler.postDelayed(mRateUpdateRunnable, 60000); // Run again in 60 seconds
    }

    private synchronized void updateRequestExpires()
    {
        TextView expires = findViewById(R.id.expires);
        if(expires == null)
            return;

        if(mPaymentRequest != null && mPaymentRequest.protocolDetails != null &&
          mPaymentRequest.protocolDetails.hasExpires())
        {
            expires.setVisibility(View.VISIBLE);

            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            if(mPaymentRequest.protocolDetails.getExpires() <= (calendar.getTimeInMillis() / 1000L) + 5)
            {
                expires.setText(getString(R.string.request_expired));
                expires.setTextColor(getResources().getColor(R.color.textNegative));
            }
            else
            {
                int seconds = (int)(mPaymentRequest.protocolDetails.getExpires() -
                  (calendar.getTimeInMillis() / 1000L) - 2L);
                int minutes = seconds / 60;
                seconds -= minutes * 60;
                expires.setText(String.format(Locale.getDefault(), "%s %02d:%02d",
                  getString(R.string.expires_in), minutes, seconds));
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
            case 1: // Loading Wallets
                status.setText(R.string.loading_wallets);
                break;
            case 2: // Loading Chain
                status.setText(R.string.loading_chain);
                break;
            case 3: // Finding peers
                status.setText(R.string.finding_peers);
                break;
            case 4: // Connecting to peers
                status.setText(R.string.connecting_to_peers);
                break;
            case 5: // Synchronizing
                status.setText(R.string.requesting_blocks);
                break;
            case 6: // Synchronized
                status.setText(R.string.monitoring);
                break;
            case 7: // Finding Transactions
                status.setText(R.string.requesting_transactions);
                break;
        }

        TextView exchangeRate = findViewById(R.id.exchangeRate);
        if(mExchangeRate == 0.0)
            exchangeRate.setText("");
        else
            exchangeRate.setText(String.format(Locale.getDefault(), "%d BCH = %s %s", 1,
              Bitcoin.amountText(100000000, mExchangeType, mExchangeRate), mExchangeType));

        boolean areWalletsLoaded = mBitcoin.walletsAreLoaded();
        boolean isChainLoaded = mBitcoin.chainIsLoaded();
        int merkleHeight = mBitcoin.merkleHeight();
        int blockHeight = mBitcoin.headerHeight();
        TextView merkleBlocks = findViewById(R.id.merkleBlockHeight);
        TextView blocks = findViewById(R.id.blockHeight);
        TextView peerCountField = findViewById(R.id.peerCount);
        TextView blocksLabel = findViewById(R.id.blocksLabel);
        if(!areWalletsLoaded)
        {
            merkleBlocks.setText(String.format(Locale.getDefault(), "%,d", merkleHeight));
            merkleBlocks.setTextColor(getResources().getColor(R.color.textWarning));

            blocks.setText("-");
            blocks.setTextColor(Color.BLACK);
            blocksLabel.setTextColor(Color.BLACK);

            peerCountField.setText(String.format("- %s", getString(R.string.peers)));
            peerCountField.setTextColor(Color.BLACK);
        }
        else if(isChainLoaded)
        {
            merkleBlocks.setText(String.format(Locale.getDefault(), "%,d", merkleHeight));

            blocks.setText(String.format(Locale.getDefault(), "%,d", blockHeight));
            if(mBitcoin.isInSync())
            {
                if(merkleHeight == blockHeight || mBitcoin.walletCount() == 0)
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
            else if(count < 4)
                peerCountField.setTextColor(getResources().getColor(R.color.textWarning));
            else
                peerCountField.setTextColor(getResources().getColor(R.color.textPositive));
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
        }

        if(mBitcoin.initialBlockDownloadIsComplete())
        {
            View ibdMessage = findViewById(R.id.initialBlockDownloadMessage);
            if(ibdMessage != null)
                ibdMessage.setVisibility(View.GONE);
        }

        // Run again in 2 seconds
        mDelayHandler.removeCallbacks(mStatusUpdateRunnable);
        mDelayHandler.postDelayed(mStatusUpdateRunnable, 2000);
    }

    public synchronized void displayDialog()
    {
        if(mMode != Mode.IN_PROGRESS)
            return;

        // Setup action bar
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(null);
            actionBar.setTitle(getResources().getString(R.string.app_name));
            actionBar.setDisplayHomeAsUpEnabled(false); // Show the Up button in the action bar.
        }

        findViewById(R.id.dialog).setVisibility(View.VISIBLE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);

        mMode = mPreviousMode;
    }

    public synchronized void displayWallets()
    {
        if(mMode == Mode.WALLETS)
            return;

        if(mMode == Mode.SCAN)
            mScanner.close();

        // Setup action bar
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(null);
            actionBar.setTitle(getResources().getString(R.string.app_name));
            actionBar.setDisplayHomeAsUpEnabled(false); // Show the Up button in the action bar.
        }

        findViewById(R.id.dialog).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.main).setVisibility(View.VISIBLE);
        findViewById(R.id.statusBar).setVisibility(View.VISIBLE);
        findViewById(R.id.controls).setVisibility(View.VISIBLE);

        mMode = Mode.WALLETS;
    }

    public synchronized void updateWallets()
    {
        if(!mBitcoin.walletsAreLoaded())
            return;

        LayoutInflater inflater = getLayoutInflater();
        ViewGroup walletsView = findViewById(R.id.wallets), transactions;
        Wallet[] wallets;
        boolean rebuildNeeded;
        View walletView;
        int walletIndex;

        if(mBitcoin.initialBlockDownloadIsComplete())
        {
            ViewGroup headerView = findViewById(R.id.header);
            View initialBlockDownloadView = headerView.findViewById(R.id.initialBlockDownloadMessage);
            if(initialBlockDownloadView != null)
                headerView.removeView(initialBlockDownloadView);
        }

        synchronized(mBitcoin)
        {
            wallets = mBitcoin.wallets();
            rebuildNeeded = mBitcoin.walletsModified || !mWalletsNeedUpdated;
            mBitcoin.walletsModified = false;
        }

        if(rebuildNeeded)
        {
            Log.i(logTag, String.format("Rebuilding %d wallets", wallets.length));

            // Rebuild all wallets
            walletsView.removeAllViews();

            // Add a view for each wallet
            for(walletIndex = 0; walletIndex < wallets.length; walletIndex++)
            {
                walletView = inflater.inflate(R.layout.wallet_item, walletsView, false);
                walletView.setTag(walletIndex);
                walletsView.addView(walletView);
            }
        }

        Log.i(logTag, String.format("Updating %d wallets", wallets.length));

        walletIndex = 0;
        for(Wallet wallet : wallets)
        {
            if(wallet == null)
                break;

            walletView = walletsView.getChildAt(walletIndex);
            if(walletView == null)
                break;

            ((TextView)walletView.findViewById(R.id.walletBalance)).setText(Bitcoin.amountText(wallet.balance,
              mExchangeType, mExchangeRate));

            if(mExchangeRate != 0.0)
            {
                ((TextView)walletView.findViewById(R.id.walletBitcoinBalance)).setText(
                  Bitcoin.satoshiText(wallet.balance));
            }

            ((TextView)walletView.findViewById(R.id.walletName)).setText(wallet.name);

            if(wallet.hasPending())
            {
                walletView.findViewById(R.id.walletPendingIcon).setVisibility(View.VISIBLE);
                walletView.findViewById(R.id.walletPendingGroup).setVisibility(View.VISIBLE);

                ((TextView)walletView.findViewById(R.id.walletPendingBalance)).setText(Bitcoin.amountText(
                  wallet.pendingBalance, mExchangeType, mExchangeRate));

                if(mExchangeRate != 0.0)
                    ((TextView)walletView.findViewById(R.id.walletBitcoinPendingBalance)).setText(
                      Bitcoin.satoshiText(wallet.pendingBalance));
            }
            else
            {
                walletView.findViewById(R.id.walletPendingIcon).setVisibility(View.GONE);
                walletView.findViewById(R.id.walletPendingGroup).setVisibility(View.GONE);
            }

            if(!wallet.isPrivate)
            {
                walletView.findViewById(R.id.walletLocked).setVisibility(View.VISIBLE);
                walletView.findViewById(R.id.walletLockedMessage).setVisibility(View.VISIBLE);
            }

            if(wallet.isPrivate && wallet.isSynchronized && mBitcoin.chainIsLoaded() &&
              mBitcoin.initialBlockDownloadIsComplete() && mBitcoin.isInRoughSync())
                walletView.findViewById(R.id.walletSend).setVisibility(View.VISIBLE);
            else
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

            if(mCurrentWalletIndex == walletIndex)
            {
                walletView.findViewById(R.id.walletDetails).setVisibility(View.VISIBLE);
                ((ImageView)walletView.findViewById(R.id.walletExpand))
                  .setImageResource(R.drawable.ic_expand_less_white_36dp);
            }
            else
            {
                walletView.findViewById(R.id.walletDetails).setVisibility(View.GONE);
                ((ImageView)walletView.findViewById(R.id.walletExpand))
                  .setImageResource(R.drawable.ic_expand_more_white_36dp);
            }

            walletIndex++;
        }

        mWalletsNeedUpdated = true;
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
        }
    }

    public synchronized void displaySettings()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_settings_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.title_settings));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        Settings settings = Settings.getInstance(getFilesDir());
        View settingsView = inflater.inflate(R.layout.settings, dialogView, false);

        Spinner exchangeCurrency = settingsView.findViewById(R.id.exchangeCurrency);
        String[] exchangeTypes = getResources().getStringArray(R.array.exchange_types);
        exchangeCurrency.setOnItemSelectedListener(this);
        for(int i = 0; i < exchangeTypes.length; i++)
            if(mExchangeType.equals(exchangeTypes[i]))
            {
                exchangeCurrency.setSelection(i);
                break;
            }

        // Configure sync frequency options
        int currentFrequency = settings.intValue(Bitcoin.SYNC_FREQUENCY_NAME);
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

        // Add Wallet buttons
        ViewGroup walletsView = settingsView.findViewById(R.id.walletsSettings);
        TextButton button;
        int offset = 0;

        for(Wallet wallet : mBitcoin.wallets())
        {
            // Edit button
            button = (TextButton)inflater.inflate(R.layout.button, walletsView, false);
            button.setId(R.id.editWallet);
            button.setTag(offset);
            button.setText(wallet.name);
            walletsView.addView(button);
            offset++;
        }

        dialogView.addView(settingsView);
        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.SETTINGS;
    }

    public synchronized void displayHelp()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_info_outline_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.instructions));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        inflater.inflate(R.layout.instructions, dialogView, true);

        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.HELP;
    }

    public synchronized void displayInfo()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);

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
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.INFO;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId())
        {
            case android.R.id.home:
                if(mMode == Mode.AUTHORIZE && mAuthorizedTask == AuthorizedTask.INITIALIZE)
                    showMessage(getString(R.string.must_create_pin), 2000);
                else
                    displayWallets();
                return true;
            case R.id.action_settings:
                displaySettings();
                return true;
            case R.id.action_help:
                displayHelp();
                return true;
            case R.id.action_info:
                displayInfo();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int pRequestCode, @NonNull String[] pPermissions, @NonNull int[] pGrantResults)
    {
        switch(pRequestCode)
        {
        case CAMERA_PERMISSION_REQUEST_CODE:
            if(pGrantResults[0] == PackageManager.PERMISSION_GRANTED)
                displayScanPaymentCode();
            else
                displayEnterPaymentCode();
            break;
        }
        super.onRequestPermissionsResult(pRequestCode, pPermissions, pGrantResults);
    }

    public void updateFee()
    {
        if(mPaymentRequest == null)
            return;

        TextView sendFee = findViewById(R.id.sendFee);
        TextView satoshiFee = findViewById(R.id.satoshiFee);
        Spinner units = findViewById(R.id.units);

        long feeSatoshis = mPaymentRequest.estimatedFee();
        long available = mPaymentRequest.amountAvailable(true);

        if(mPaymentRequest.sendMax)
        {
            mPaymentRequest.amount = available - feeSatoshis;
            updateSendAmount();
        }

        if(mPaymentRequest.amount + feeSatoshis > available)
        {
            findViewById(R.id.insufficientFunds).setVisibility(View.VISIBLE);
            findViewById(R.id.usingPending).setVisibility(View.GONE);
        }
        else if(mPaymentRequest.requiresPending())
        {
            findViewById(R.id.insufficientFunds).setVisibility(View.GONE);
            findViewById(R.id.usingPending).setVisibility(View.VISIBLE);
        }
        else
        {
            findViewById(R.id.insufficientFunds).setVisibility(View.GONE);
            findViewById(R.id.usingPending).setVisibility(View.GONE);
        }

        sendFee.setText(amountText(units.getSelectedItemPosition(), feeSatoshis));
        satoshiFee.setText(Bitcoin.satoshiText(feeSatoshis));
    }

    // Update amount fields based on Payment Request value
    public void updateSendAmount()
    {
        Spinner units = findViewById(R.id.units);
        EditText amountField = findViewById(R.id.sendAmount);
        TextView satoshiAmount = findViewById(R.id.satoshiAmount);

        if(units == null || amountField == null || satoshiAmount == null)
            return;

        mDontUpdatePaymentAmount = true;
        amountField.setText(amountText(units.getSelectedItemPosition(), mPaymentRequest.amount));
        satoshiAmount.setText(Bitcoin.satoshiText(mPaymentRequest.amount));
    }

    public synchronized void displayPaymentDetails()
    {
        if(mPaymentRequest == null)
        {
            showMessage(getString(R.string.failed_payment_code), 2000);
            displayWallets();
            return;
        }

        if(mPaymentRequest.secureURL != null && mPaymentRequest.paymentScript == null)
        {
            displayProgress();

            ProcessPaymentRequestTask processPaymentRequestTask = new ProcessPaymentRequestTask(this,
              mPaymentRequest);
            processPaymentRequestTask.execute();
            return;
        }

        mPaymentRequest.outpoints = mBitcoin.getUnspentOutputs(mCurrentWalletIndex);

        if(mPaymentRequest.format == PaymentRequest.FORMAT_INVALID ||
          (mPaymentRequest.type != PaymentRequest.TYPE_PUB_KEY_HASH &&
          mPaymentRequest.type != PaymentRequest.TYPE_SCRIPT_HASH &&
          mPaymentRequest.type != PaymentRequest.TYPE_BIP0700))
        {
            showMessage(getString(R.string.invalid_payment_code), 2000);
            displayWallets();
            return;
        }

        Wallet wallet = mBitcoin.wallet(mCurrentWalletIndex);
        if(wallet == null)
        {
            displayWallets();
            return;
        }

        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        findViewById(R.id.main).setVisibility(View.GONE);

        dialogView.removeAllViews();
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);
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

        String subtitle;
        switch(mPaymentRequest.type)
        {
            default:
            case PaymentRequest.TYPE_PUB_KEY_HASH:
                subtitle = getString(R.string.pub_key_hash);
                break;
            case PaymentRequest.TYPE_SCRIPT_HASH:
                subtitle = getString(R.string.script_hash);
                break;
        }

        ((TextView)sendView.findViewById(R.id.subtitle)).setText(subtitle);

        // Wallet info
        ((TextView)sendView.findViewById(R.id.walletName)).setText(wallet.name);
        if(wallet.hasPending())
            ((TextView)sendView.findViewById(R.id.amountAvailableTitle)).setText(getString(R.string.pending_balance));
        else
            ((TextView)sendView.findViewById(R.id.amountAvailableTitle)).setText(getString(R.string.balance));
        ((TextView)sendView.findViewById(R.id.amountAvailable)).setText(
          Bitcoin.amountText(mPaymentRequest.amountAvailable(true), mExchangeType, mExchangeRate));
        ((TextView)sendView.findViewById(R.id.bitcoinAmountAvailable)).setText(
          Bitcoin.satoshiText(mPaymentRequest.amountAvailable(true)));

        // Insufficient funds/using pending messages
        if(mPaymentRequest.amount + mPaymentRequest.estimatedFee() >
          mPaymentRequest.amountAvailable(true))
        {
            sendView.findViewById(R.id.insufficientFunds).setVisibility(View.VISIBLE);
            sendView.findViewById(R.id.usingPending).setVisibility(View.GONE);
        }
        else if(mPaymentRequest.requiresPending())
        {
            sendView.findViewById(R.id.insufficientFunds).setVisibility(View.GONE);
            sendView.findViewById(R.id.usingPending).setVisibility(View.VISIBLE);
        }
        else
        {
            sendView.findViewById(R.id.insufficientFunds).setVisibility(View.GONE);
            sendView.findViewById(R.id.usingPending).setVisibility(View.GONE);
        }

        EditText amount = sendView.findViewById(R.id.sendAmount);
        TextView satoshiAmount = sendView.findViewById(R.id.satoshiAmount);
        Spinner units = sendView.findViewById(R.id.units);
        if(mPaymentRequest.amountSpecified)
        {
            amount.setText(amountText(Bitcoin.FIAT, mPaymentRequest.amount));
            satoshiAmount.setText(Bitcoin.satoshiText(mPaymentRequest.amount));

            sendView.findViewById(R.id.sendMax).setVisibility(View.GONE);

            // Align amount field to end of parent
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)units.getLayoutParams();
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_END);
            units.setLayoutParams(layoutParams);
        }
        else
            satoshiAmount.setText(Bitcoin.satoshiText(0));

        if(!mPaymentRequest.amountSpecified)
        {
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
                                mPaymentRequest.amount = Bitcoin.satoshisFromBitcoins(amount / mExchangeRate);
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
            amount.addTextChangedListener(mAmountWatcher);
        }

        units.setOnItemSelectedListener(this);
        units.setSelection(Bitcoin.FIAT); // Default to Fiat

        Spinner feeRates = sendView.findViewById(R.id.feeRates);
        feeRates.setOnItemSelectedListener(this);
        feeRates.setSelection(2); // Default to Low

        if(mPaymentRequest.amountSpecified)
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
        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);

        if(mPaymentRequest.protocolDetails != null && mPaymentRequest.protocolDetails.hasExpires())
            updateRequestExpires();

        if(!mPaymentRequest.amountSpecified)
        {
            focusOnText(amount);
            amount.selectAll();
        }

        mMode = Mode.ENTER_PAYMENT_DETAILS;
    }

    public synchronized void openTransaction(int pWalletOffset, String pTransactionHash)
    {
        if(mRequestedTransactionID != null && !mRequestedTransactionID.equals(pTransactionHash))
            mRequestedTransactionAttempts = 0;
        mRequestedTransactionWalletIndex = pWalletOffset;
        mRequestedTransactionID = pTransactionHash;

        if(mMode != Mode.TRANSACTION)
            displayProgress();

        mTransaction = new FullTransaction();
        mTransactionWalletIndex = pWalletOffset;
        GetTransactionTask task = new GetTransactionTask(getApplicationContext(), mBitcoin, pWalletOffset,
          pTransactionHash, mTransaction);
        task.execute();
    }

    public synchronized void displayTransaction()
    {
        mRequestedTransactionWalletIndex = -1;
        mRequestedTransactionID = null;

        if(mTransaction == null || mTransaction.hash == null)
        {
            displayWallets();
            return;
        }

        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(null);
            actionBar.setTitle(" " + getResources().getString(R.string.transaction));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup transactionView = (ViewGroup)inflater.inflate(R.layout.transaction, dialogView, false);

        if(mTransaction.block != null)
        {
            ((TextView)transactionView.findViewById(R.id.statusHeading)).setText(R.string.confirmed_in_block);
            ((TextView)transactionView.findViewById(R.id.blockID)).setText(mTransaction.block);
            if(mTransaction.count == -1)
                ((TextView)transactionView.findViewById(R.id.transactionStatus))
                  .setText(getString(R.string.not_available_abbreviation));
            else
                ((TextView)transactionView.findViewById(R.id.transactionStatus))
                  .setText(String.format(Locale.getDefault(), "%,d %s (%3$tY-%3$tm-%3$td %3$tH:%3$tM)",
                    mTransaction.count, getString(R.string.confirmations), mTransaction.date * 1000));
        }
        else
        {
            ((TextView)transactionView.findViewById(R.id.statusHeading)).setText(R.string.unconfirmed);
            transactionView.findViewById(R.id.blockIDScroll).setVisibility(View.GONE);
            if(mTransaction.count == -1)
                ((TextView)transactionView.findViewById(R.id.transactionStatus))
                  .setText(getString(R.string.not_available_abbreviation));
            else
                ((TextView)transactionView.findViewById(R.id.transactionStatus))
                  .setText(String.format(Locale.getDefault(), "%s %,d %s", getString(R.string.validated_by),
                    mTransaction.count, getString(R.string.peers_period)));
        }

        ((TextView)transactionView.findViewById(R.id.id)).setText(mTransaction.hash);
        ((TextView)transactionView.findViewById(R.id.size)).setText(String.format(Locale.getDefault(), "%,d %s",
          mTransaction.size, getString(R.string.bytes)));
        ((TextView)transactionView.findViewById(R.id.lockTime)).setText(mTransaction.lockTimeString(this));
        ((TextView)transactionView.findViewById(R.id.version)).setText(String.format(Locale.getDefault(), "%d",
          mTransaction.version));

        long amount = mTransaction.amount();
        TextView amountText = transactionView.findViewById(R.id.transactionAmount);
        TextView bitcoinsAmountText = transactionView.findViewById(R.id.bitcoinsAmount);
        amountText.setText(Bitcoin.amountText(amount, mExchangeType, mExchangeRate));
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

        long fee = mTransaction.fee();

        if(fee == -1)
        {
            transactionView.findViewById(R.id.feeGroup).setVisibility(View.GONE);
            transactionView.findViewById(R.id.feeBitcoinsGroup).setVisibility(View.GONE);
        }
        else
        {
            TextView feeText = transactionView.findViewById(R.id.transactionFee);
            TextView bitcoinsFeeText = transactionView.findViewById(R.id.bitcoinsFee);
            feeText.setText(Bitcoin.amountText(fee, mExchangeType, mExchangeRate));
            bitcoinsFeeText.setText(Bitcoin.satoshiText(fee));
        }

        // Inputs
        if(mTransaction.inputs.length > 1)
            ((TextView)transactionView.findViewById(R.id.inputsTitle)).setText(String.format(Locale.getDefault(),
              "%d %s", mTransaction.inputs.length, getString(R.string.inputs)));
        else
            ((TextView)transactionView.findViewById(R.id.inputsTitle)).setText(String.format(Locale.getDefault(),
              "%d %s", mTransaction.inputs.length, getString(R.string.input)));
        ViewGroup inputsView = transactionView.findViewById(R.id.inputs);
        for(Input input : mTransaction.inputs)
        {
            ViewGroup inputView = (ViewGroup)inflater.inflate(R.layout.input, inputsView, false);

            ((TextView)inputView.findViewById(R.id.outpointHash)).setText(input.outpointID);
            ((TextView)inputView.findViewById(R.id.outpointIndex)).setText(String.format(Locale.getDefault(),
              "%s %d", getString(R.string.index), input.outpointIndex));

            ((TextView)inputView.findViewById(R.id.sequence)).setText(String.format(Locale.getDefault(),
              "0x%08x", input.sequence));

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
                inputAmountText.setText(Bitcoin.amountText(input.amount, mExchangeType, mExchangeRate));
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
        if(mTransaction.outputs.length > 1)
            ((TextView)transactionView.findViewById(R.id.outputsTitle)).setText(String.format(Locale.getDefault(),
              "%d %s", mTransaction.outputs.length, getString(R.string.outputs)));
        else
            ((TextView)transactionView.findViewById(R.id.outputsTitle)).setText(String.format(Locale.getDefault(),
              "%d %s", mTransaction.outputs.length, getString(R.string.output)));
        ViewGroup outputsView = transactionView.findViewById(R.id.outputs);
        for(Output output : mTransaction.outputs)
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
            outputAmountText.setText(Bitcoin.amountText(output.amount, mExchangeType, mExchangeRate));
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
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.TRANSACTION;
    }

    public synchronized void displayEnterPaymentCode()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);
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

        EditText paymentCodeEntry = paymentCodeView.findViewById(R.id.paymentCode);

        dialogView.addView(paymentCodeView);

        TextView.OnEditorActionListener paymentCodeListener = new TextView.OnEditorActionListener()
        {
            @Override
            public boolean onEditorAction(TextView pView, int pActionId, KeyEvent pEvent)
            {
                if(pActionId == EditorInfo.IME_NULL || pActionId == EditorInfo.IME_ACTION_DONE ||
                  pActionId == EditorInfo.IME_ACTION_SEND ||
                  (pEvent != null && pEvent.getAction() == KeyEvent.ACTION_DOWN &&
                    pEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER))
                {
                    processClick(pView, R.id.enterPaymentDetails);
                    return true;
                }
                return false;
            }
        };
        paymentCodeEntry.setOnEditorActionListener(paymentCodeListener);

        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);
        focusOnText(paymentCodeEntry);
        mMode = Mode.ENTER_PAYMENT_CODE;
    }

    public synchronized void displayClipBoardPaymentCode()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_send_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.send_payment));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup paymentCodeView = (ViewGroup)inflater.inflate(R.layout.clipboard_payment_code, dialogView,
          false);

        TextView paymentCode = paymentCodeView.findViewById(R.id.clipboard);
        paymentCode.setText(mPaymentRequest.uri);

        dialogView.addView(paymentCodeView);

        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.CLIPBOARD_PAYMENT_CODE;
    }

    @Override
    public void onScannerResult(String pResult)
    {
        mPaymentRequest = mBitcoin.decodePaymentCode(pResult);
        if(mPaymentRequest != null && mPaymentRequest.format != PaymentRequest.FORMAT_INVALID)
            displayPaymentDetails();
        else
        {
            showMessage(getString(R.string.invalid_payment_code), 2000);
            displayWallets();
        }
    }

    @Override
    public void onScannerFailed(int pFailReason)
    {
        switch(pFailReason)
        {
        case Scanner.FAIL_ACCESS:
            showMessage(getString(R.string.failed_camera_access), 2000);
            break;
        case Scanner.FAIL_CREATION:
            showMessage(getString(R.string.failed_camera_create), 2000);
            break;
        default:
            showMessage(getString(R.string.failed_camera_general), 2000);
            break;
        }

        displayEnterPaymentCode();
    }

    public synchronized void displayScanPaymentCode()
    {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
              PackageManager.PERMISSION_GRANTED)
            {
                // Request permission
                String[] permissions = {Manifest.permission.CAMERA};
                requestPermissions(permissions, CAMERA_PERMISSION_REQUEST_CODE);
                displayProgress();
                return;
            }
        }

        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_send_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.scan_payment_code));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup scanView = (ViewGroup)inflater.inflate(R.layout.scan, dialogView, false);
        dialogView.addView(scanView);

        ScannerView cameraView = scanView.findViewById(R.id.camera);

        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.SCAN;

        cameraView.setCamera(mScanner);
        if(!mScanner.open(getApplicationContext(), cameraView.getHolder(), Scanner.FACING_BACK))
        {
            Log.w(logTag, "Camera failed to open");
            showMessage(getString(R.string.failed_camera_access), 2000);
            displayWallets();
        }
    }

    public synchronized void displayRequestPaymentCode()
    {
        if(mPaymentRequest == null || mPaymentRequest.uri == null || mQRCode == null)
            displayWallets();
        else
        {
            LayoutInflater inflater = getLayoutInflater();
            ViewGroup dialogView = findViewById(R.id.dialog);

            dialogView.removeAllViews();
            findViewById(R.id.main).setVisibility(View.GONE);
            findViewById(R.id.progress).setVisibility(View.GONE);
            findViewById(R.id.statusBar).setVisibility(View.GONE);
            findViewById(R.id.controls).setVisibility(View.GONE);

            ActionBar actionBar = getSupportActionBar();
            if(actionBar != null)
            {
                actionBar.setIcon(R.drawable.ic_add_circle_black_36dp);
                actionBar.setTitle(" " + getResources().getString(R.string.receive));
                actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
            }

            ViewGroup receiveView = (ViewGroup)inflater.inflate(R.layout.receive, dialogView, false);

            // Set image and text
            ((ImageView)receiveView.findViewById(R.id.qrImage)).setImageBitmap(mQRCode);
            ((TextView)receiveView.findViewById(R.id.paymentCode)).setText(mPaymentRequest.uri);
            dialogView.addView(receiveView);

            EditText amount = null;

            if(mIsSupportURI)
            {
                receiveView.findViewById(R.id.optionalReceive).setVisibility(View.GONE);
                ((TextView)receiveView.findViewById(R.id.receiveTitle)).setText(R.string.support_nextcash);
                ((TextView)receiveView.findViewById(R.id.receiveDescription))
                  .setText(R.string.support_nextcash_description);
            }
            else
            {
                receiveView.findViewById(R.id.optionalReceive).setVisibility(View.VISIBLE);

                // Set amount
                amount = receiveView.findViewById(R.id.requestAmount);
                TextView satoshiAmount = receiveView.findViewById(R.id.satoshiAmount);
                if(mPaymentRequest.amount != 0)
                {
                    amount.setText(Bitcoin.amountText(mPaymentRequest.amount, mExchangeType, mExchangeRate));
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
                                    mPaymentRequest.amount = Bitcoin.satoshisFromBitcoins(amount / mExchangeRate);
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
                TextView.OnEditorActionListener amountActionListener = new TextView.OnEditorActionListener()
                {
                    @Override
                    public boolean onEditorAction(TextView pView, int pActionId, KeyEvent pEvent)
                    {
                        if(pActionId == EditorInfo.IME_NULL || pActionId == EditorInfo.IME_ACTION_DONE || pActionId == EditorInfo.IME_ACTION_SEND || (pEvent != null && pEvent.getAction() == KeyEvent.ACTION_DOWN && pEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER))
                        {
                            processClick(pView, R.id.updateRequestPaymentCode);
                            return true;
                        }
                        return false;
                    }
                };
                amount.setOnEditorActionListener(amountActionListener);

                Spinner units = receiveView.findViewById(R.id.units);
                ArrayAdapter<CharSequence> unitAdapter = ArrayAdapter.createFromResource(this, R.array.amount_units, android.R.layout.simple_spinner_item);
                unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                units.setAdapter(unitAdapter);
                units.setOnItemSelectedListener(this);

                // Label
                if(mPaymentRequest.label != null && mPaymentRequest.label.length() > 0)
                    ((EditText)receiveView.findViewById(R.id.label)).setText(mPaymentRequest.label);
                TextView.OnEditorActionListener labelActionListener = new TextView.OnEditorActionListener()
                {
                    @Override
                    public boolean onEditorAction(TextView pView, int pActionId, KeyEvent pEvent)
                    {
                        if(pActionId == EditorInfo.IME_NULL || pActionId == EditorInfo.IME_ACTION_DONE || pActionId == EditorInfo.IME_ACTION_SEND || (pEvent != null && pEvent.getAction() == KeyEvent.ACTION_DOWN && pEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER))
                        {
                            processClick(pView, R.id.updateRequestPaymentCode);
                            return true;
                        }
                        return false;
                    }
                };
                ((EditText)receiveView.findViewById(R.id.label)).setOnEditorActionListener(labelActionListener);

                // Message
                if(mPaymentRequest.message != null && mPaymentRequest.message.length() > 0)
                    ((EditText)receiveView.findViewById(R.id.message)).setText(mPaymentRequest.message);
                TextView.OnEditorActionListener messageActionListener = new TextView.OnEditorActionListener()
                {
                    @Override
                    public boolean onEditorAction(TextView pView, int pActionId, KeyEvent pEvent)
                    {
                        if(pActionId == EditorInfo.IME_NULL || pActionId == EditorInfo.IME_ACTION_DONE || pActionId == EditorInfo.IME_ACTION_SEND || (pEvent != null && pEvent.getAction() == KeyEvent.ACTION_DOWN && pEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER))
                        {
                            processClick(pView, R.id.updateRequestPaymentCode);
                            return true;
                        }
                        return false;
                    }
                };
                ((EditText)receiveView.findViewById(R.id.message)).setOnEditorActionListener(messageActionListener);
            }

            dialogView.setVisibility(View.VISIBLE);
            findViewById(R.id.mainScroll).setScrollY(0);

            mMode = Mode.RECEIVE;
        }
    }

    public synchronized void displayAddOptions()
    {
        // Display options for adding wallets
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_add_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.title_add_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        inflater.inflate(R.layout.add_wallet, dialogView, true);

        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.ADD_WALLET;
    }

    public synchronized void displayImportWallet()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(null);
            actionBar.setTitle(" " + getResources().getString(R.string.import_text));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        inflater.inflate(R.layout.select_recover_date, dialogView, true);

        // Done button
        dialogView.findViewById(R.id.dateSelected).setId(R.id.enterImportKey);

        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.IMPORT_WALLET;
    }

    public synchronized void displayEnterImportKey()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);

        inflater.inflate(R.layout.import_bip32_key, dialogView, true);

        Spinner derivationMethod = findViewById(R.id.derivationMethodSpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.derivation_methods, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        derivationMethod.setAdapter(adapter);

        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);
        focusOnText((EditText)dialogView.findViewById(R.id.importText));
        mMode = Mode.IMPORT_WALLET;
    }

    public synchronized void displayCreateWallet()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_add_circle_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.create_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup createWallet = (ViewGroup)inflater.inflate(R.layout.view_seed, dialogView, false);
        dialogView.addView(createWallet);

        mSeedIsBackedUp = false;
        mDerivationPathMethodToLoad = Bitcoin.BIP0044_DERIVATION;
        mSeedBackupOnly = false;

        Spinner entropy = createWallet.findViewById(R.id.seedEntropy);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
          R.array.mnemonic_seed_length_titles, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        entropy.setAdapter(adapter);
        entropy.setOnItemSelectedListener(this);

        int entropyPosition = 0;
        if(mSeedEntropyBytes != 0)
        {
            int[] values = getResources().getIntArray(R.array.mnemonic_seed_length_values);
            int offset = 0;
            for(int value : values)
            {
                if(value == mSeedEntropyBytes)
                    entropyPosition = offset;
                offset++;
            }
        }
        entropy.setSelection(entropyPosition);

        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.CREATE_WALLET;
    }

    public String amountText(int pUnitType, long pSatoshis)
    {
        switch(pUnitType)
        {
            case Bitcoin.FIAT:
                return String.format(Locale.getDefault(), "%.2f",
                  Bitcoin.bitcoinsFromSatoshis(pSatoshis) * mExchangeRate);
            case Bitcoin.BITS:
                return String.format(Locale.getDefault(), "%.2f",
                  Bitcoin.bitsFromSatoshis(pSatoshis));
            case Bitcoin.BITCOINS:
                return String.format(Locale.getDefault(), "%.8f",
                  Bitcoin.bitcoinsFromSatoshis(pSatoshis));
            default:
                return getString(R.string.not_available_abbreviation);
        }
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
                if(mSeed == null || mSeedEntropyBytes != values[pPosition])
                {
                    mSeedEntropyBytes = values[pPosition];
                    mSeed = mBitcoin.generateMnemonicSeed(mSeedEntropyBytes);
                    mSeedIsBackedUp = false;
                }
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

            if(mPaymentRequest != null)
            {
                if(mPaymentRequest.amount == 0)
                    amountField.setText("");
                else
                    amountField.setText(amountText(pPosition, mPaymentRequest.amount));
            }

            if(!isRequest)
                updateFee();
        }
        else if(pParent.getId() == R.id.feeRates)
        {
            if(mPaymentRequest != null)
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
                Settings.getInstance(getFilesDir()).setIntValue(Bitcoin.SYNC_FREQUENCY_NAME,
                  frequencyValues[pPosition]);
                if(frequencyValues[pPosition] == -1)
                    Log.i(logTag, "Sync frequency set to never.");
                else if(frequencyValues[pPosition] >= 60)
                    Log.i(logTag, String.format("Sync frequency set to %d hours.", frequencyValues[pPosition] / 60));
                else
                    Log.i(logTag, String.format("Sync frequency set to %d minutes.", frequencyValues[pPosition]));
                scheduleJobs();
            }
        }
        else if(pParent.getId() == R.id.exchangeCurrency)
        {
            String[] exchangeTypes = getResources().getStringArray(R.array.exchange_types);
            if(pPosition >= exchangeTypes.length)
                Log.e(logTag, String.format("Invalid exchange type position selected : %d", pPosition));
            else if(mExchangeType != exchangeTypes[pPosition])
            {
                mExchangeType = exchangeTypes[pPosition];
                mExchangeRate = 0.0;

                Settings settings = Settings.getInstance(getFilesDir());
                settings.setValue(FiatRateRequestTask.EXCHANGE_TYPE_NAME, mExchangeType);
                settings.setDoubleValue(FiatRateRequestTask.EXCHANGE_RATE_NAME, mExchangeRate);
                Log.i(logTag, String.format("Exchange type set to %s", mExchangeType));

                scheduleExchangeRateUpdate();
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
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);

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

        if(mSeedBackupOnly)
            verifySeed.findViewById(R.id.skipCheckSeed).setVisibility(View.GONE);

        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.VERIFY_SEED;
    }

    public synchronized void displayRecoverWallet()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(null);
            actionBar.setTitle(" " + getResources().getString(R.string.recover_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        inflater.inflate(R.layout.select_recover_date, dialogView, true);

        // Done button
        dialogView.findViewById(R.id.dateSelected).setId(R.id.enterRecoverSeed);

        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.RECOVER_WALLET;
    }

    public synchronized void displayEnterRecoverSeed()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);

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

        focusOnText(seedWordEntry);
        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.RECOVER_WALLET;
    }

    public synchronized void displayBackupWallet(String pPassCode)
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_add_circle_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.backup_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup viewSeed = (ViewGroup)inflater.inflate(R.layout.view_seed, dialogView, false);
        mSeed = mBitcoin.seed(pPassCode, mCurrentWalletIndex);
        if(mSeed == null)
        {
            showMessage(getString(R.string.failed_retrieve_seed), 2000);
            displayWallets();
            return;
        }
        mSeedIsBackedUp = false;
        mSeedBackupOnly = true;
        ((TextView)viewSeed.findViewById(R.id.seed)).setText(mSeed);

        viewSeed.findViewById(R.id.seedEntropyContainer).setVisibility(View.GONE);

        dialogView.addView(viewSeed);

        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.BACKUP_WALLET;
    }

    public synchronized void displayAuthorize()
    {
        LinearLayout pinLayout = findViewById(R.id.pin);
        pinLayout.setVisibility(View.VISIBLE);

        mPIN = "";
        ((ViewGroup)findViewById(R.id.entryDots)).removeAllViews();

        // Message
        TextView messageView = pinLayout.findViewById(R.id.authorizeMessage);
        if(messageView != null)
        {
            switch(mAuthorizedTask)
            {
                case NONE:
                    break;
                case INITIALIZE:
                    messageView.setText(getString(R.string.create_pin));
                    break;
                case ADD_KEY:
                    messageView.setText(getString(R.string.add_wallet));
                    break;
                case BACKUP_KEY:
                    messageView.setText(getString(R.string.backup_wallet));
                    break;
                case REMOVE_KEY:
                    messageView.setText(getString(R.string.remove_wallet));
                    break;
                case SIGN_TRANSACTION:
                    messageView.setText(getString(R.string.send_payment));
                    break;
            }
        }

        if(mMode != Mode.AUTHORIZE)
            mPreviousMode = mMode;
        mMode = Mode.AUTHORIZE;
    }

    public synchronized void displayEditWallet()
    {
        Wallet wallet = mBitcoin.wallet(mCurrentWalletIndex);
        if(wallet == null)
            return;

        View button;
        ViewGroup editName;
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_edit_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.title_edit_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        // Edit Name
        editName = (ViewGroup)inflater.inflate(R.layout.edit_wallet, dialogView, false);
        ((EditText)editName.findViewById(R.id.name)).setText(wallet.name);

        dialogView.addView(editName);

        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.EDIT_WALLET;
    }

    public void alignColumns(ViewGroup pView, int pColumnCount)
    {
        ViewGroup row;
        int rowCount = pView.getChildCount();
        View entry;
        int widest[] = new int[pColumnCount];
        int width;
        boolean shade = false;

        // Find widest columns
        for(int rowOffset = 0; rowOffset < rowCount; rowOffset++)
        {
            row = (ViewGroup)pView.getChildAt(rowOffset);
            for(int columnOffset = 0; columnOffset < pColumnCount; columnOffset++)
            {
                entry = row.getChildAt(columnOffset);
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
                entry = row.getChildAt(columnOffset);
                entry.setMinimumWidth(widest[columnOffset]);
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
                    ((TextView)transactionView.findViewById(R.id.count)).setText(R.string.peers);
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

            transaction.updateView(this, transactionView, mExchangeType, mExchangeRate);

            // Set tag with transaction offset
            transactionView.setTag(transaction.hash);

            transactionViewGroup.addView(transactionView);
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
        Wallet wallet = mBitcoin.wallet(mCurrentWalletIndex);
        if(wallet == null)
            return;

        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);

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

        if(wallet.hasPending())
            historyView.findViewById(R.id.walletPendingGroup).setVisibility(View.VISIBLE);
        else
            historyView.findViewById(R.id.walletPendingGroup).setVisibility(View.GONE);

        ViewGroup transactions = historyView.findViewById(R.id.walletTransactions);
        populateTransactions(transactions, wallet.transactions,
          100);

        dialogView.addView(historyView);

        alignTransactions(transactions);

        dialogView.setVisibility(View.VISIBLE);
        findViewById(R.id.mainScroll).setScrollY(0);
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
        processClick(pView, pView.getId());
    }

    public synchronized void displayProgress()
    {
        findViewById(R.id.main).setVisibility(View.GONE);
        findViewById(R.id.dialog).setVisibility(View.GONE);
        findViewById(R.id.statusBar).setVisibility(View.GONE);
        findViewById(R.id.controls).setVisibility(View.GONE);
        findViewById(R.id.progress).setVisibility(View.VISIBLE);
        mMode = Mode.IN_PROGRESS;
    }

    public void processClick(View pView, int pID)
    {
        if(pID != R.id.seedWordButton)
        {
            View focus = getCurrentFocus();
            if(focus != null)
            {
                InputMethodManager imManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
                if(imManager != null)
                    imManager.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            }
        }

        switch(pID)
        {
        case R.id.addWallet:
            displayAddOptions();
            break;
        case R.id.buyFromCoinbase:
        {
            String coinbaseReferallUrl = "https://www.coinbase.com/join/597a904283ff1a00a71aae6c";
            Intent coinbaseIntent = new Intent(Intent.ACTION_VIEW);
            coinbaseIntent.setData(Uri.parse(coinbaseReferallUrl));
            startActivity(coinbaseIntent);
            showMessage(getString(R.string.opening_coinbase_website), 2000);
            break;
        }
        case R.id.supportNextCash:
        {
            displayProgress();

            // Some wallets don't support BIP-0021 ?message=Support%20NextCash");
            mPaymentRequest = mBitcoin.decodePaymentCode(
              "bitcoincash:qzy2cndws0c0cy8pvkxh6fmg5kzx0v47jq9gg6vczc");
            if(mQRCode == null)
                mQRCode = Bitmap.createBitmap(Bitcoin.QR_WIDTH, Bitcoin.QR_WIDTH, Bitmap.Config.ARGB_8888);
            mIsSupportURI = true;
            CreateAddressTask task = new CreateAddressTask(getApplicationContext(), mBitcoin, mPaymentRequest,
              mQRCode);
            task.execute();
            break;
        }
        case R.id.giveFeedBack:
        {
            String feedBackUrl = "mailto:beta_test@nextcash.tech?subject=Android%20Feedback";
            Intent feedBackIntent = new Intent(Intent.ACTION_VIEW);
            feedBackIntent.setData(Uri.parse(feedBackUrl));
            startActivity(feedBackIntent);
            showMessage(getString(R.string.opening_email), 2000);
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
                displayWallets();
            }
            else
            {
                mAuthorizedTask = AuthorizedTask.ADD_KEY;
                mKeyToLoad = null;
                mRecoverDate = System.currentTimeMillis() / 1000L;
                displayAuthorize();
            }
            break;
        case R.id.recoverWallet:
            displayRecoverWallet();
            break;
        case R.id.enterRecoverSeed:
        {
            // Get recover seed date
            DatePicker picker = findViewById(R.id.date);
            if(picker == null)
            {
                showMessage(getString(R.string.failed_to_get_recover_date), 2000);
                displayWallets();
                break;
            }

            Calendar date;
            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            {
                date = new Calendar.Builder().setDate(picker.getYear(), picker.getMonth(),
                  picker.getDayOfMonth()).build();
            }
            else
            {
                date = Calendar.getInstance();
                date.set(picker.getYear(), picker.getMonth(), picker.getDayOfMonth());
            }

            mRecoverDate = date.getTimeInMillis() / 1000L;
            displayEnterRecoverSeed();
            break;
        }
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
            displayImportWallet();
            break;
        case R.id.enterImportKey:
            // Get recover seed date
            DatePicker picker = findViewById(R.id.date);
            if(picker == null)
            {
                showMessage(getString(R.string.failed_to_get_recover_date), 2000);
                displayWallets();
                break;
            }

            Calendar date;
            if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            {
                date = new Calendar.Builder().setDate(picker.getYear(), picker.getMonth(),
                  picker.getDayOfMonth()).setTimeOfDay(0, 0, 0).build();
            }
            else
            {
                date = Calendar.getInstance();
                date.set(picker.getYear(), picker.getMonth(), picker.getDayOfMonth(), 0, 0, 0);
            }

            mRecoverDate = date.getTimeInMillis() / 1000L;
            displayEnterImportKey();
            break;
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
                displayWallets();
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
                    displayPaymentDetails();
                else
                {
                    showMessage(getString(R.string.invalid_payment_code), 2000);
                    displayWallets();
                }
            }
            else
            {
                showMessage(getString(R.string.failed_payment_code), 2000);
                displayWallets();
            }
            break;
        }
        case R.id.updateRequestPaymentCode:
        {
            displayProgress();

            String label = ((EditText)findViewById(R.id.label)).getText().toString();
            String message = ((EditText)findViewById(R.id.message)).getText().toString();
            if(mPaymentRequest != null)
            {
                mPaymentRequest.setLabel(label);
                mPaymentRequest.setMessage(message);
                if(mQRCode == null)
                    mQRCode = Bitmap.createBitmap(Bitcoin.QR_WIDTH, Bitcoin.QR_WIDTH, Bitmap.Config.ARGB_8888);
                CreatePaymentRequestTask task = new CreatePaymentRequestTask(getApplicationContext(), mBitcoin,
                  mPaymentRequest, mQRCode);
                task.execute();
            }
            break;
        }
        case R.id.sendPayment:
        {
            mDelayHandler.removeCallbacks(mRequestExpiresUpdater);

            if(mPaymentRequest != null && mPaymentRequest.protocolDetails != null &&
              mPaymentRequest.protocolDetails.hasExpires())
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
        case R.id.seedWordButton:
        {
            ViewGroup wordButtons = (ViewGroup)pView.getParent();
            if(wordButtons == null)
                break;

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
                            displayWallets();
                        }
                        else
                        {
                            mAuthorizedTask = AuthorizedTask.ADD_KEY;
                            mKeyToLoad = null;
                            mSeedIsBackedUp = true;
                            mRecoverDate = System.currentTimeMillis() / 1000L;
                            displayAuthorize();
                        }
                    }
                    else
                    {
                        showMessage(getString(R.string.seed_doesnt_match), 2000);
                        displayCreateWallet();
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
            ViewGroup walletView = (ViewGroup)pView.getParent(); // Wallet is parent of wallet header
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

                        // Make all others compressed
                        ViewGroup wallets = findViewById(R.id.wallets);
                        synchronized(this)
                        {
                            int walletCount = mBitcoin.walletCount();
                            for(int index = 0; index < walletCount; ++index)
                                if(mCurrentWalletIndex != index)
                                {
                                    View otherWalletView = wallets.getChildAt(index);
                                    otherWalletView.findViewById(R.id.walletDetails).setVisibility(View.GONE);
                                    ((ImageView)otherWalletView.findViewById(R.id.walletExpand))
                                      .setImageResource(R.drawable.ic_expand_more_white_36dp);
                                }
                        }
                    }
                    else
                    {
                        ((ImageView)pView.findViewById(R.id.walletExpand))
                          .setImageResource(R.drawable.ic_expand_more_white_36dp);
                        walletDetails.setVisibility(View.GONE);

                        mCurrentWalletIndex = -1;
                    }
                }
            }
            break;
        }
        case R.id.walletSend:
        {
            ViewGroup walletView = (ViewGroup)pView.getParent().getParent().getParent();
            mCurrentWalletIndex = (int)walletView.getTag();
            ClipboardManager manager = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(manager != null)
            {
                ClipData clip = manager.getPrimaryClip();
                if(clip != null && clip.getItemCount() > 0)
                {
                    String clipText = clip.getItemAt(0).coerceToText(getApplicationContext()).toString();
                    if(clipText.length() > 0)
                    {
                        mPaymentRequest = mBitcoin.decodePaymentCode(clipText);
                        if(mPaymentRequest != null && mPaymentRequest.format != PaymentRequest.FORMAT_INVALID)
                        {
                            displayClipBoardPaymentCode();
                            break;
                        }
                    }
                }
            }

            displayScanPaymentCode();
            break;
        }
        case R.id.scanPaymentCode:
            displayScanPaymentCode();
            break;
        case R.id.enterPaymentCode:
        {
            mScanner.close();
            displayEnterPaymentCode();
            break;
        }
        case R.id.useClipBoardPaymentCode:
            displayPaymentDetails();
            break;
        case R.id.walletReceive:
        {
            displayProgress();

            ViewGroup walletView = (ViewGroup)pView.getParent().getParent().getParent();
            mCurrentWalletIndex = (int)walletView.getTag();
            mPaymentRequest = new PaymentRequest();
            String address = mBitcoin.getNextReceiveAddress(mCurrentWalletIndex, 0);
            if(address == null)
            {
                showMessage(getString(R.string.failed_generate_address), 2000);
                displayWallets();
            }
            else if(!mPaymentRequest.setAddress(address, PaymentRequest.TYPE_PUB_KEY_HASH))
            {
                showMessage(getString(R.string.failed_generate_payment_code), 2000);
                displayWallets();
                break;
            }
            else
            {
                if(mQRCode == null)
                    mQRCode = Bitmap.createBitmap(Bitcoin.QR_WIDTH, Bitcoin.QR_WIDTH, Bitmap.Config.ARGB_8888);
                mIsSupportURI = false;
                CreateAddressTask task = new CreateAddressTask(getApplicationContext(), mBitcoin, mPaymentRequest,
                  mQRCode);
                task.execute();
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
            mCurrentWalletIndex = (int)pView.getTag();
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
        case R.id.qrImage:
        {
            TextView paymentCode = findViewById(R.id.paymentCode);
            ClipboardManager manager = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(paymentCode != null && manager != null)
            {
                ClipData clip = ClipData.newPlainText("Bitcoin Cash Payment Code",
                  paymentCode.getText().toString());
                manager.setPrimaryClip(clip);
                showMessage(getString(R.string.payment_code_clipboard), 2000);
            }
            break;
        }
        case R.id.sendMax:
        {
            if(mPaymentRequest != null && !mPaymentRequest.amountSpecified)
            {
                mPaymentRequest.sendMax = true;
                EditText sendAmount = findViewById(R.id.sendAmount);
                sendAmount.setEnabled(false);
                sendAmount.setFocusable(false);

                mPaymentRequest.amount = mPaymentRequest.amountAvailable(true) -
                  mPaymentRequest.estimatedFee();
                updateFee();
            }
            break;
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
            break;
        }
        case R.id.closeMessage:
            synchronized(this)
            {
                View messageView = (View)pView.getParent();
                ViewGroup headerView = findViewById(R.id.header);
                if(messageView != null && headerView != null)
                {
                    View otherMessageView;
                    for(int index = 0; index < mPersistentMessages.size(); index++)
                    {
                        otherMessageView = headerView.getChildAt(index);
                        if(otherMessageView == messageView)
                        {
                            mMessages.dismissMessage(mPersistentMessages.get(index));
                            mMessages.save(getApplicationContext());
                            mPersistentMessages.remove(index);
                            break;
                        }
                    }
                    headerView.removeView(messageView);
                }
            }
            break;
        case R.id.one:
            if(mPIN.length() >= 8)
                showMessage(getString(R.string.max_pin_length), 2000);
            else
            {
                mPIN += "1";
                addPINEntry();
            }
            break;
        case R.id.two:
            if(mPIN.length() >= 8)
                showMessage(getString(R.string.max_pin_length), 2000);
            else
            {
                mPIN += "2";
                addPINEntry();
            }
            break;
        case R.id.three:
            if(mPIN.length() >= 8)
                showMessage(getString(R.string.max_pin_length), 2000);
            else
            {
                mPIN += "3";
                addPINEntry();
            }
            break;
        case R.id.four:
            if(mPIN.length() >= 8)
                showMessage(getString(R.string.max_pin_length), 2000);
            else
            {
                mPIN += "4";
                addPINEntry();
            }
            break;
        case R.id.five:
            if(mPIN.length() >= 8)
                showMessage(getString(R.string.max_pin_length), 2000);
            else
            {
                mPIN += "5";
                addPINEntry();
            }
            break;
        case R.id.six:
            if(mPIN.length() >= 8)
                showMessage(getString(R.string.max_pin_length), 2000);
            else
            {
                mPIN += "6";
                addPINEntry();
            }
            break;
        case R.id.seven:
            if(mPIN.length() >= 8)
                showMessage(getString(R.string.max_pin_length), 2000);
            else
            {
                mPIN += "7";
                addPINEntry();
            }
            break;
        case R.id.eight:
            if(mPIN.length() >= 8)
                showMessage(getString(R.string.max_pin_length), 2000);
            else
            {
                mPIN += "8";
                addPINEntry();
            }
            break;
        case R.id.nine:
            if(mPIN.length() >= 8)
                showMessage(getString(R.string.max_pin_length), 2000);
            else
            {
                mPIN += "9";
                addPINEntry();
            }
            break;
        case R.id.zero:
            if(mPIN.length() >= 8)
                showMessage(getString(R.string.max_pin_length), 2000);
            else
            {
                mPIN += "0";
                addPINEntry();
            }
            break;
        case R.id.backspace:
            if(mPIN.length() > 0)
                mPIN = mPIN.substring(0, mPIN.length() - 1);
            removePINEntry();
            break;
        case R.id.authorize:
        {
            if(mPIN.length() < 4)
            {
                showMessage(getString(R.string.min_pin_length), 2000);
                break;
            }

            findViewById(R.id.pin).setVisibility(View.GONE);
            ((ViewGroup)findViewById(R.id.entryDots)).removeAllViews();
            String pin = mPIN;
            mPIN = null;
            switch(mAuthorizedTask)
            {
                case INITIALIZE:
                {
                    displayProgress();
                    String seed = mBitcoin.generateMnemonicSeed(24);
                    if(seed == null || seed.length() == 0)
                    {
                        showMessage(getString(R.string.failed_generate_key), 2000);
                        displayWallets();
                        mService.stop();
                        finish();
                    }
                    else
                    {
                        CreateKeyTask task = new CreateKeyTask(getApplicationContext(), mBitcoin, pin, seed,
                          Bitcoin.BIP0044_DERIVATION, false,
                          System.currentTimeMillis() / 1000L);
                        task.execute();
                    }
                    break;
                }
                case ADD_KEY:
                {
                    displayProgress();
                    if(mKeyToLoad != null)
                    {
                        ImportKeyTask task = new ImportKeyTask(getApplicationContext(), mBitcoin, pin, mKeyToLoad,
                          mDerivationPathMethodToLoad, mRecoverDate);
                        task.execute();
                        mKeyToLoad = null;
                    }
                    else
                    {
                        CreateKeyTask task = new CreateKeyTask(getApplicationContext(), mBitcoin, pin, mSeed,
                          mDerivationPathMethodToLoad, mSeedIsBackedUp, mRecoverDate);
                        task.execute();
                        mSeed = null;
                    }
                    break;
                }
                case BACKUP_KEY:
                    displayBackupWallet(pin);
                    break;
                case REMOVE_KEY:
                {
                    displayProgress();
                    RemoveKeyTask task = new RemoveKeyTask(this, mBitcoin, pin, mCurrentWalletIndex);
                    task.execute();
                    break;
                }
                case SIGN_TRANSACTION:
                {
                    if(mPaymentRequest != null && mPaymentRequest.protocolDetails != null &&
                      mPaymentRequest.protocolDetails.hasExpires())
                    {
                        if(mPaymentRequest.protocolDetails.getExpires() <= (System.currentTimeMillis() / 1000L) + 5)
                        {
                            showMessage(getString(R.string.request_expired), 2000);
                            mPaymentRequest = null;
                            displayWallets();
                            return;
                        }
                    }

                    displayProgress();
                    CreateTransactionTask task = new CreateTransactionTask(getApplicationContext(), mBitcoin, pin,
                      mCurrentWalletIndex, mPaymentRequest);
                    task.execute();
                    break;
                }
            }
            break;
        }
        default:
            break;
        }
    }

    public void addPINEntry()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dots = findViewById(R.id.entryDots);

        inflater.inflate(R.layout.pin_dot, dots, true);
    }

    public void removePINEntry()
    {
        ViewGroup dots = findViewById(R.id.entryDots);
        if(dots.getChildCount() > 0)
            dots.removeViewAt(dots.getChildCount() - 1);
    }

    public void showMessage(String pText, int pDelay)
    {
        ((TextView)findViewById(R.id.notificationText)).setText(pText);
        findViewById(R.id.notification).setVisibility(View.VISIBLE);
        mDelayHandler.postDelayed(mClearNotification, pDelay);
    }

    public synchronized void refreshPersistentMessages()
    {
        ArrayList<String> activeMessages = mMessages.activeMessages();
        for(String message : activeMessages)
            showPersistentMessage(message);
    }

    public synchronized void addPersistentMessage(String pText)
    {
        mMessages.addMessage(pText);
        mMessages.save(getApplicationContext());
        showPersistentMessage(pText);
    }

    private synchronized void showPersistentMessage(String pText)
    {
        mPersistentMessages.add(pText);
        ViewGroup headerView = findViewById(R.id.header);
        LayoutInflater inflater = getLayoutInflater();
        View messageView = inflater.inflate(R.layout.persistent_message, headerView, false);
        ((TextView)messageView.findViewById(R.id.messageText)).setText(pText);
        headerView.addView(messageView, 0);
    }

    @Override
    public void onBackPressed()
    {
        switch(mMode)
        {
        case LOADING_WALLETS:
        case LOADING_CHAIN:
        case WALLETS:
            if(mFinishOnBack)
            {
                Log.i(logTag, "Stopping because of back button");
                mService.stop();
                super.onBackPressed();
            }
            else
            {
                mFinishOnBack = true;
                showMessage(getString(R.string.double_tap_back), 1000);
                mDelayHandler.postDelayed(mClearFinishOnBack, 1000);
            }
            break;
        case IN_PROGRESS:
            break;
        case ADD_WALLET:
            mSeed = null;
            mKeyToLoad = null;
            mRecoverDate = 0L;
            mSeedEntropyBytes = 0;
            break;
        case CREATE_WALLET:
        case RECOVER_WALLET:
        case IMPORT_WALLET:
            mSeed = null;
            mKeyToLoad = null;
            mRecoverDate = 0L;
            mSeedEntropyBytes = 0;
            displayAddOptions();
            return;
        case VERIFY_SEED:
            displayCreateWallet();
            return;
        case BACKUP_WALLET:
            displayEditWallet();
            return;
        case EDIT_WALLET:
            displaySettings();
            return;
        case HISTORY:
            break;
        case TRANSACTION:
            break;
        case SCAN:
            break;
        case RECEIVE:
            mPaymentRequest = null;
            break;
        case ENTER_PAYMENT_CODE:
            break;
        case CLIPBOARD_PAYMENT_CODE:
                break;
        case ENTER_PAYMENT_DETAILS:
            mDelayHandler.removeCallbacks(mRequestExpiresUpdater);
            displayEnterPaymentCode();
            return;
        case AUTHORIZE:
            // Hide PIN entry
            switch(mAuthorizedTask)
            {
            case NONE:
                mSeed = null;
                mKeyToLoad = null;
                mRecoverDate = 0L;
                mSeedEntropyBytes = 0;
                break;
            case INITIALIZE:
                showMessage(getString(R.string.must_create_pin), 2000);
                if(mFinishOnBack)
                {
                    Log.i(logTag, "Stopping because of back button");
                    mService.stop();
                    super.onBackPressed();
                }
                else
                {
                    mFinishOnBack = true;
                    mDelayHandler.postDelayed(mClearFinishOnBack, 1000);
                }
                return;
            case ADD_KEY:
            case BACKUP_KEY:
            case REMOVE_KEY:
            case SIGN_TRANSACTION:
                break;
            }

            findViewById(R.id.pin).setVisibility(View.GONE);
            mMode = mPreviousMode;
            return;
        case INFO:
            break;
        case HELP:
            break;
        case SETTINGS:
            break;
        }

        displayWallets(); // Go back to main wallets view
    }
}
