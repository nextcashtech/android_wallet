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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
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
    public static final String ACTION_RAW_TRANSACTION_FIELD = "RAW_TRANSACTION";
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
      mRequestExpiresUpdater, mRequestTransactionRunnable, mUndoDeleteRunnable, mConfirmDeleteRunnable;

    private enum Mode { LOADING_WALLETS, LOADING_CHAIN, IN_PROGRESS, WALLETS, ADD_WALLET, CREATE_WALLET, RECOVER_WALLET,
      IMPORT_PRIVATE_KEY, IMPORT_WALLET, VERIFY_SEED, BACKUP_WALLET, EDIT_WALLET, TRANSACTION_HISTORY, TRANSACTION,
      SCAN, RECEIVE, ENTER_PAYMENT_CODE, CLIPBOARD_PAYMENT_CODE, ENTER_PAYMENT_DETAILS, AUTHORIZE, INFO, HELP,
      SETTINGS, ADDRESS_LABELS, ADDRESS_BOOK }
    private enum TextEntryMode { NONE, SAVE_ADDRESS, LABEL_ADDRESS, RECEIVE_AMOUNT, RECEIVE_LABEL, RECEIVE_MESSAGE,
      TRANSACTION_COMMENT, TRANSACTION_COST_AMOUNT, TRANSACTION_COST_DATE }
    private Mode mMode, mPreviousMode, mPreviousTransactionMode, mPreviousReceiveMode;
    private TextEntryMode mTextEntryMode;
    private boolean mWalletsNeedUpdated;
    private enum AuthorizedTask { NONE, INITIALIZE, ADD_KEY, BACKUP_KEY, REMOVE_KEY, SIGN_TRANSACTION }
    private enum ScanMode {SCAN_PAYMENT_CODE, SCAN_PRIVATE_KEY}
    private AuthorizedTask mAuthorizedTask;
    private String mKeyToLoad, mEncodedPrivateKey, mSeed;
    private int mSeedEntropyBytes;
    private long mRecoverDate;
    private boolean mSeedIsBackedUp;
    private int mCurrentWalletIndex;
    private boolean mSeedBackupOnly;
    private int mDerivationPathMethodToLoad;
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
    private TextWatcher mSeedWordWatcher, mAmountWatcher, mRequestAmountWatcher, mEnteredAmountWatcher;
    private int mRequestedTransactionWalletIndex, mHistoryToShowWalletIndex;
    private String mRequestedTransactionID;
    private int mRequestedTransactionAttempts;
    private FullTransaction mTransaction, mTransactionToModify;
    private int mTransactionWalletIndex;
    private ArrayList<String> mPersistentMessages;
    private Messages mMessages;
    private Scanner mScanner;
    private ScanMode mScanMode;
    private String mPIN;
    private AddressBookAdapter mAddressBookAdapter;
    private AddressLabelAdapter mAddressLabelAdapter;
    private Drawable mDeleteIcon;
    private int mSmallIconSize;
    private Paint mNegativeColorPaint;
    private Animation mLargeButtonDownAnimation, mLargeButtonUpAnimation;
    private Animation mButtonDownAnimation, mButtonUpAnimation;
    private View.OnTouchListener mButtonTouchListener, mLargeButtonTouchListener;


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
            showMessage(mTransaction.description(getApplicationContext(), mBitcoin), 2000);
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
        mUndoDeleteRunnable = null;
        mConfirmDeleteRunnable = null;
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
                openTransaction(mRequestedTransactionWalletIndex, mRequestedTransactionID, 0L);
            }
        };
        mMode = Mode.LOADING_WALLETS;
        mPreviousMode = Mode.LOADING_WALLETS;
        mPreviousTransactionMode = Mode.LOADING_WALLETS;
        mAuthorizedTask = AuthorizedTask.NONE;
        mFinishOnBack = false;
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
        mEncodedPrivateKey = null;
        mKeyToLoad = null;
        mSeed = null;
        mAddressBookAdapter = null;
        mAddressLabelAdapter = null;
        mDeleteIcon = getResources().getDrawable(R.drawable.baseline_delete_white_36dp);
        mSmallIconSize = getResources().getDimensionPixelSize(R.dimen.small_icon_size);
        mNegativeColorPaint = new Paint();
        mNegativeColorPaint.setColor(getResources().getColor(R.color.colorNegative));
        mEnteredAmountWatcher = null;
        mTextEntryMode = TextEntryMode.NONE;

        mLargeButtonDownAnimation = new ScaleAnimation(1.0f, 0.97f, 1.0f, 0.90f,
          Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mLargeButtonDownAnimation.setDuration(250L);

        mLargeButtonUpAnimation = new ScaleAnimation(0.97f, 1.0f, 0.90f, 1.0f,
          Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mLargeButtonUpAnimation.setDuration(250L);

        mButtonDownAnimation = new ScaleAnimation(1.0f, 0.90f, 1.0f, 0.90f,
          Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mButtonDownAnimation.setDuration(250L);

        mButtonUpAnimation = new ScaleAnimation(0.90f, 1.0f, 0.90f, 1.0f,
          Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        mButtonUpAnimation.setDuration(250L);

        mLargeButtonTouchListener = new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View pView, MotionEvent pEvent)
            {
                switch(pEvent.getAction())
                {
                case MotionEvent.ACTION_DOWN:
                    pView.startAnimation(mLargeButtonDownAnimation);
                    break;
                case MotionEvent.ACTION_UP:
                    pView.startAnimation(mLargeButtonUpAnimation);
                    pView.performClick();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    pView.clearAnimation();
                    break;
                default:
                    break;
                }
                return true;
            }
        };

        mButtonTouchListener = new View.OnTouchListener()
        {
            @Override
            public boolean onTouch(View pView, MotionEvent pEvent)
            {
                switch(pEvent.getAction())
                {
                    case MotionEvent.ACTION_DOWN:
                        pView.startAnimation(mButtonDownAnimation);
                        break;
                    case MotionEvent.ACTION_UP:
                        pView.startAnimation(mButtonUpAnimation);
                        pView.performClick();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        pView.clearAnimation();
                        break;
                    default:
                        break;
                }
                return true;
            }
        };

        // Set listener for control buttons.
        LinearLayout controls = findViewById(R.id.controls);
        for(int index = 0; index < controls.getChildCount(); index++)
            controls.getChildAt(index).setOnTouchListener(mButtonTouchListener);

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
                    displayReceive();
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
                        byte rawTransaction[] = pIntent.getExtras().getByteArray(ACTION_RAW_TRANSACTION_FIELD);
                        FinishPaymentRequestTask finishPaymentRequestTask =
                          new FinishPaymentRequestTask(getApplicationContext(), mBitcoin, mCurrentWalletIndex,
                            mPaymentRequest, rawTransaction);
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

        LayoutInflater inflater = getLayoutInflater();

        RelativeLayout root = findViewById(R.id.root);

        View pinView = inflater.inflate(R.layout.pin_entry, root, false);
        pinView.setVisibility(View.GONE);
        pinView.findViewById(R.id.one).setOnTouchListener(mButtonTouchListener);
        pinView.findViewById(R.id.two).setOnTouchListener(mButtonTouchListener);
        pinView.findViewById(R.id.three).setOnTouchListener(mButtonTouchListener);
        pinView.findViewById(R.id.four).setOnTouchListener(mButtonTouchListener);
        pinView.findViewById(R.id.five).setOnTouchListener(mButtonTouchListener);
        pinView.findViewById(R.id.six).setOnTouchListener(mButtonTouchListener);
        pinView.findViewById(R.id.seven).setOnTouchListener(mButtonTouchListener);
        pinView.findViewById(R.id.eight).setOnTouchListener(mButtonTouchListener);
        pinView.findViewById(R.id.nine).setOnTouchListener(mButtonTouchListener);
        pinView.findViewById(R.id.backspace).setOnTouchListener(mButtonTouchListener);
        pinView.findViewById(R.id.zero).setOnTouchListener(mButtonTouchListener);
        pinView.findViewById(R.id.authorize).setOnTouchListener(mButtonTouchListener);
        root.addView(pinView);

        View textDialog = inflater.inflate(R.layout.text_dialog, root, false);
        textDialog.setVisibility(View.GONE);
        textDialog.findViewById(R.id.textDialogOkay).setOnTouchListener(mButtonTouchListener);
        root.addView(textDialog);

        View amountDialog = inflater.inflate(R.layout.amount_dialog, root, false);
        amountDialog.setVisibility(View.GONE);
        amountDialog.findViewById(R.id.textDialogOkay).setOnTouchListener(mButtonTouchListener);
        root.addView(amountDialog);

        View dateDialog = inflater.inflate(R.layout.date_dialog, root, false);
        dateDialog.setVisibility(View.GONE);
        dateDialog.findViewById(R.id.dateDialogOkay).setOnTouchListener(mButtonTouchListener);
        root.addView(dateDialog);

        showView(R.id.progress);
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
            openTransaction(mRequestedTransactionWalletIndex, mRequestedTransactionID, 0L);
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
            case IMPORT_PRIVATE_KEY:
                break;
            case VERIFY_SEED:
                break;
            case BACKUP_WALLET:
                break;
            case EDIT_WALLET:
                break;
            case TRANSACTION_HISTORY:
                displayWalletHistory(); // Reload
                break;
            case TRANSACTION:
                if(mTransaction != null)
                    openTransaction(mTransactionWalletIndex, mTransaction.hash, mTransaction.amount()); // Reload
                break;
            case SCAN:
                break;
            case RECEIVE:
                break;
            case ADDRESS_LABELS:
                break;
            case ADDRESS_BOOK:
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
            {
                if(extras.containsKey("Amount"))
                    openTransaction(extras.getInt("Wallet"), extras.getString("Transaction"),
                      extras.getLong("Amount"));
                else
                    openTransaction(extras.getInt("Wallet"), extras.getString("Transaction"), 0L);
            }
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
            case IMPORT_PRIVATE_KEY:
                break;
            case VERIFY_SEED:
                break;
            case BACKUP_WALLET:
                break;
            case EDIT_WALLET:
                break;
            case TRANSACTION_HISTORY:
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
            case ADDRESS_LABELS:
                break;
            case ADDRESS_BOOK:
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

        new ExchangeRateRequestTask(getApplicationContext(), mBitcoin).execute();
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
        if(mBitcoin.exchangeRate() == 0.0)
            exchangeRate.setText("");
        else
            exchangeRate.setText(String.format(Locale.getDefault(), "%d BCH = %s %s", 1,
              mBitcoin.amountText(100000000), mBitcoin.exchangeType()));

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
            actionBar.setTitle(getString(R.string.app_name));
            actionBar.setDisplayHomeAsUpEnabled(false); // Show the Up button in the action bar.
        }

        showView(R.id.dialog);
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
            actionBar.setTitle(getString(R.string.app_name));
            actionBar.setDisplayHomeAsUpEnabled(false); // Show the Up button in the action bar.
        }

        showView(R.id.main);
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

            walletView.findViewById(R.id.walletHeader).setOnTouchListener(mLargeButtonTouchListener);

            ((TextView)walletView.findViewById(R.id.walletBalance)).setText(mBitcoin.amountText(wallet.balance));

            if(mBitcoin.exchangeRate() != 0.0)
            {
                ((TextView)walletView.findViewById(R.id.walletBitcoinBalance)).setText(
                  Bitcoin.satoshiText(wallet.balance));
            }

            ((TextView)walletView.findViewById(R.id.walletName)).setText(wallet.name);

            if(wallet.hasPending())
            {
                walletView.findViewById(R.id.walletPendingIcon).setVisibility(View.VISIBLE);

                ((TextView)walletView.findViewById(R.id.walletPendingBalance)).setText(mBitcoin.amountText(
                  wallet.pendingBalance));

                if(mBitcoin.exchangeRate() != 0.0)
                    ((TextView)walletView.findViewById(R.id.walletBitcoinPendingBalance)).setText(
                      Bitcoin.satoshiText(wallet.pendingBalance));
            }
            else
                walletView.findViewById(R.id.walletPendingIcon).setVisibility(View.GONE);

            if(!wallet.isPrivate)
            {
                walletView.findViewById(R.id.walletLocked).setVisibility(View.VISIBLE);
                walletView.findViewById(R.id.walletLockedMessage).setVisibility(View.VISIBLE);
            }

            // Set listener for wallet controls
            RelativeLayout controls = walletView.findViewById(R.id.walletControls);
            for(int index = 0; index < controls.getChildCount(); index++)
                controls.getChildAt(index).setOnTouchListener(mButtonTouchListener);

            if(!wallet.isBackedUp && wallet.isPrivate)
                walletView.findViewById(R.id.walletBackup).setVisibility(View.VISIBLE);
            else
                walletView.findViewById(R.id.walletBackup).setVisibility(View.GONE);

            if(wallet.isPrivate && wallet.isSynchronized && mBitcoin.chainIsLoaded() &&
              mBitcoin.initialBlockDownloadIsComplete() && mBitcoin.isInRoughSync())
                ((ImageView)walletView.findViewById(R.id.walletSend))
                  .setColorFilter(getResources().getColor(R.color.enabled));
            else
                ((ImageView)walletView.findViewById(R.id.walletSend))
                  .setColorFilter(getResources().getColor(R.color.disabled));

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
            populateTransactions(transactions, wallet.transactions, 3, false);
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
        Settings settings = Settings.getInstance(getFilesDir());
        switch(pButtonView.getId())
        {
        case R.id.notifyTransactionsToggle:
            settings.setBoolValue("notify_transactions", pIsChecked);
            if(pIsChecked)
                Log.i(logTag, "Transaction notifications turned on");
            else
                Log.i(logTag, "Transaction notifications turned off");
            break;
        case R.id.coinBasePriceToggle:
            settings.setBoolValue(ExchangeRateRequestTask.USE_COINBASE_RATE_NAME, pIsChecked);
            if(pIsChecked)
                Log.i(logTag, "CoinBase pricing turned on");
            else
                Log.i(logTag, "CoinBase pricing turned off");
            break;
        case R.id.coinMarketCapPriceToggle:
            settings.setBoolValue(ExchangeRateRequestTask.USE_COINMARKETCAP_RATE_NAME, pIsChecked);
            if(pIsChecked)
                Log.i(logTag, "CoinMarketCap pricing turned on");
            else
                Log.i(logTag, "CoinMarketCap pricing turned off");
            break;
        case R.id.coinLibPriceToggle:
            settings.setBoolValue(ExchangeRateRequestTask.USE_COINLIB_RATE_NAME, pIsChecked);
            if(pIsChecked)
                Log.i(logTag, "CoinLib pricing turned on");
            else
                Log.i(logTag, "CoinLib pricing turned off");
            break;
        }
    }

    public synchronized void displaySettings()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_settings_black_36dp);
            actionBar.setTitle(" " + getString(R.string.title_settings));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        Settings settings = Settings.getInstance(getFilesDir());
        View settingsView = inflater.inflate(R.layout.settings, dialogView, false);
        dialogView.addView(settingsView);

        Spinner exchangeCurrency = settingsView.findViewById(R.id.exchangeCurrency);
        String[] exchangeTypes = getResources().getStringArray(R.array.exchange_types);
        exchangeCurrency.setOnItemSelectedListener(this);
        for(int i = 0; i < exchangeTypes.length; i++)
            if(mBitcoin.exchangeType().equals(exchangeTypes[i]))
            {
                exchangeCurrency.setSelection(i);
                break;
            }

        // Configure sync frequency options
        int currentFrequency = settings.intValue(Bitcoin.SYNC_FREQUENCY_NAME);
        if(currentFrequency == 0)
            currentFrequency = 360; // Default to 6 hours

        Spinner syncFrequency = settingsView.findViewById(R.id.syncFrequencySpinner);
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

        // Configure pricing source toggles
        Switch coinBaseToggle = settingsView.findViewById(R.id.coinBasePriceToggle);
        coinBaseToggle.setOnCheckedChangeListener(this);
        if(settings.containsValue(ExchangeRateRequestTask.USE_COINBASE_RATE_NAME))
            coinBaseToggle.setChecked(settings.boolValue(ExchangeRateRequestTask.USE_COINBASE_RATE_NAME));
        else
            coinBaseToggle.setChecked(true);

        Switch coinMarketCapToggle = settingsView.findViewById(R.id.coinMarketCapPriceToggle);
        coinMarketCapToggle.setOnCheckedChangeListener(this);
        if(settings.containsValue(ExchangeRateRequestTask.USE_COINMARKETCAP_RATE_NAME))
            coinMarketCapToggle.setChecked(settings.boolValue(ExchangeRateRequestTask.USE_COINMARKETCAP_RATE_NAME));
        else
            coinMarketCapToggle.setChecked(true);

        Switch coinLibToggle = settingsView.findViewById(R.id.coinLibPriceToggle);
        coinLibToggle.setOnCheckedChangeListener(this);
        if(settings.containsValue(ExchangeRateRequestTask.USE_COINLIB_RATE_NAME))
            coinLibToggle.setChecked(settings.boolValue(ExchangeRateRequestTask.USE_COINLIB_RATE_NAME));
        else
            coinLibToggle.setChecked(true);

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
            button.setOnTouchListener(mButtonTouchListener);
            walletsView.addView(button);
            offset++;
        }

        showView(R.id.dialog);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.SETTINGS;
    }

    public synchronized void displayHelp()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_info_outline_black_36dp);
            actionBar.setTitle(" " + getString(R.string.instructions));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        inflater.inflate(R.layout.instructions, dialogView, true);
        dialogView.setVisibility(View.VISIBLE);

        showView(R.id.dialog);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.HELP;
    }

    public synchronized void displayInfo()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_info_outline_black_36dp);
            actionBar.setTitle(" " + getString(R.string.information));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        LinearLayout infoView = (LinearLayout)inflater.inflate(R.layout.info, dialogView, false);
        dialogView.addView(infoView);

        ((TextView)findViewById(R.id.nodeUserAgentValue)).setText(Bitcoin.userAgent());
        ((TextView)findViewById(R.id.networkValue)).setText(Bitcoin.networkName());

        showView(R.id.dialog);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.INFO;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId())
        {
            case android.R.id.home:
                if(mConfirmDeleteRunnable != null)
                {
                    mDelayHandler.removeCallbacks(mConfirmDeleteRunnable);
                    mConfirmDeleteRunnable.run();
                    mConfirmDeleteRunnable = null;
                    mUndoDeleteRunnable = null;
                }

                if(mTextEntryMode != TextEntryMode.NONE)
                {
                    findViewById(R.id.amountDialog).setVisibility(View.GONE);
                    findViewById(R.id.textDialog).setVisibility(View.GONE);
                    findViewById(R.id.dateDialog).setVisibility(View.GONE);
                    mTextEntryMode = TextEntryMode.NONE;
                }

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
                displayScanner(ScanMode.SCAN_PAYMENT_CODE);
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

        if(mPaymentRequest.secureURL != null && mPaymentRequest.protocolDetails == null)
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

        dialogView.removeAllViews();

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_send_black_36dp);
            actionBar.setTitle(" " + getString(R.string.send_payment));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup sendView = (ViewGroup)inflater.inflate(R.layout.send_payment, dialogView, false);
        dialogView.addView(sendView);

        sendView.findViewById(R.id.saveAddress).setOnTouchListener(mButtonTouchListener);
        sendView.findViewById(R.id.sendMax).setOnTouchListener(mButtonTouchListener);
        sendView.findViewById(R.id.sendPayment).setOnTouchListener(mButtonTouchListener);

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
          mBitcoin.amountText(mPaymentRequest.amountAvailable(true)));
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
                            amount = Bitcoin.parseAmount(amountField.getText().toString());
                        }
                        catch(Exception pException)
                        {
                            amount = 0.0;
                        }

                        switch(units.getSelectedItemPosition())
                        {
                            case 0: // USD
                                mPaymentRequest.amount = Bitcoin.satoshisFromBitcoins(amount /
                                  mBitcoin.exchangeRate());
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

        showView(R.id.dialog);
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

    public synchronized void openTransaction(int pWalletOffset, String pTransactionHash, long pAmount)
    {
        if(mRequestedTransactionID != null && !mRequestedTransactionID.equals(pTransactionHash))
            mRequestedTransactionAttempts = 0;
        mRequestedTransactionWalletIndex = pWalletOffset;
        mRequestedTransactionID = pTransactionHash;

        if(mMode != Mode.TRANSACTION)
        {
            mPreviousTransactionMode = mMode;
            displayProgress();
        }

        mTransaction = new FullTransaction();
        mTransactionWalletIndex = pWalletOffset;
        GetTransactionTask task = new GetTransactionTask(getApplicationContext(), mBitcoin, pWalletOffset,
          pTransactionHash, pAmount, mTransaction);
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

        mTransactionToModify = mTransaction;

        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);
        ViewGroup transactionView;
        long amount = mTransaction.amount();
        boolean update;

        if(mMode == Mode.TRANSACTION)
        {
            transactionView = (ViewGroup)dialogView.getChildAt(0);
            update = true;
        }
        else
        {
            dialogView.removeAllViews();

            ActionBar actionBar = getSupportActionBar();
            if(actionBar != null)
            {
                actionBar.setIcon(null);
                actionBar.setTitle(" " + getString(R.string.transaction));
                actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
            }

            transactionView = (ViewGroup)inflater.inflate(R.layout.transaction, dialogView, false);
            update = false;
            dialogView.addView(transactionView);
            showView(R.id.dialog);
            findViewById(R.id.mainScroll).setScrollY(0);

            transactionView.findViewById(R.id.modifyTransactionComment).setOnTouchListener(mButtonTouchListener);
            transactionView.findViewById(R.id.addTransactionComment).setOnTouchListener(mButtonTouchListener);
            transactionView.findViewById(R.id.modifyTransactionCostBasis).setOnTouchListener(mButtonTouchListener);
        }

        if(mTransaction.data.comment == null)
        {
            transactionView.findViewById(R.id.commentGroup).setVisibility(View.GONE);
            transactionView.findViewById(R.id.addTransactionComment).setVisibility(View.VISIBLE);
        }
        else
        {
            transactionView.findViewById(R.id.commentGroup).setVisibility(View.VISIBLE);
            transactionView.findViewById(R.id.addTransactionComment).setVisibility(View.GONE);
            ((TextView)transactionView.findViewById(R.id.commentValue)).setText(mTransaction.data.comment);
        }

        TextView costBasisAmount = transactionView.findViewById(R.id.costBasisAmount);
        if(mTransaction.data.cost != 0.0)
            costBasisAmount.setText(String.format("%s %s", Bitcoin.formatAmount(mTransaction.data.cost,
              mTransaction.data.costType), mTransaction.data.costType));
        else
            costBasisAmount.setText(String.format("%s %s", mBitcoin.amountText(amount, mTransaction.data),
              mTransaction.data.exchangeType));

        TextView costBasisDate = transactionView.findViewById(R.id.costBasisDate);
        if(mTransaction.data.costDate == 0)
            costBasisDate.setText(String.format(Locale.getDefault(), "%1$tY-%1$tm-%1$td",
              mTransaction.data.date * 1000L));
        else
            costBasisDate.setText(String.format(Locale.getDefault(), "%1$tY-%1$tm-%1$td",
              mTransaction.data.costDate * 1000L));

        ((TextView)transactionView.findViewById(R.id.id)).setText(mTransaction.hash);
        ((TextView)transactionView.findViewById(R.id.size)).setText(String.format(Locale.getDefault(),
          "%,d %s", mTransaction.size, getString(R.string.bytes)));
        ((TextView)transactionView.findViewById(R.id.lockTime)).setText(mTransaction.lockTimeString(this));
        ((TextView)transactionView.findViewById(R.id.version)).setText(String.format(Locale.getDefault(),
          "%d", mTransaction.version));

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

        TextView amountText = transactionView.findViewById(R.id.transactionAmount);
        TextView bitcoinsAmountText = transactionView.findViewById(R.id.bitcoinsAmount);
        amountText.setText(mBitcoin.amountText(amount, mTransaction.data));
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
            feeText.setText(mBitcoin.amountText(fee, mTransaction.data));
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
        ViewGroup inputView;
        int inputIndex = 0;
        for(Input input : mTransaction.inputs)
        {
            if(update)
                inputView = (ViewGroup)inputsView.getChildAt(inputIndex);
            else
            {
                inputView = (ViewGroup)inflater.inflate(R.layout.input, inputsView, false);
                inputsView.addView(inputView);
            }

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
                inputAmountText.setText(mBitcoin.amountText(input.amount, mTransaction.data));
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

            inputIndex++;
        }

        // Outputs
        if(mTransaction.outputs.length > 1)
            ((TextView)transactionView.findViewById(R.id.outputsTitle)).setText(String.format(Locale.getDefault(),
              "%d %s", mTransaction.outputs.length, getString(R.string.outputs)));
        else
            ((TextView)transactionView.findViewById(R.id.outputsTitle)).setText(String.format(Locale.getDefault(),
              "%d %s", mTransaction.outputs.length, getString(R.string.output)));
        ViewGroup outputsView = transactionView.findViewById(R.id.outputs);
        ViewGroup outputView;
        int outputIndex = 0;
        for(Output output : mTransaction.outputs)
        {
            if(update)
                outputView = (ViewGroup)outputsView.getChildAt(outputIndex);
            else
            {
                outputView = (ViewGroup)inflater.inflate(R.layout.output, outputsView, false);
                outputsView.addView(outputView);
            }

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
            outputAmountText.setText(mBitcoin.amountText(output.amount, mTransaction.data));
            outputBitcoinsAmountText.setText(Bitcoin.satoshiText(output.amount));
            if(output.related)
            {
                outputAmountText.setTextColor(getResources().getColor(R.color.colorPositive));
                outputBitcoinsAmountText.setTextColor(getResources().getColor(R.color.colorPositive));
            }

            outputIndex++;
        }

        showView(R.id.dialog);
        mMode = Mode.TRANSACTION;
    }

    public synchronized void displayEnterPaymentCode()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_send_black_36dp);
            actionBar.setTitle(" " + getString(R.string.send_payment));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup paymentCodeView = (ViewGroup)inflater.inflate(R.layout.enter_payment_code, dialogView,
          false);

        paymentCodeView.findViewById(R.id.enterPaymentDetails).setOnTouchListener(mButtonTouchListener);
        paymentCodeView.findViewById(R.id.scanPaymentCode).setOnTouchListener(mButtonTouchListener);
        paymentCodeView.findViewById(R.id.openAddressBook).setOnTouchListener(mButtonTouchListener);

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

        showView(R.id.dialog);
        findViewById(R.id.mainScroll).setScrollY(0);
        focusOnText(paymentCodeEntry);
        mMode = Mode.ENTER_PAYMENT_CODE;
    }

    public synchronized void displayClipBoardPaymentCode()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_send_black_36dp);
            actionBar.setTitle(" " + getString(R.string.send_payment));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup paymentCodeView = (ViewGroup)inflater.inflate(R.layout.clipboard_payment_code, dialogView,
          false);

        TextView paymentCode = paymentCodeView.findViewById(R.id.clipboard);
        paymentCode.setText(mPaymentRequest.uri);

        paymentCodeView.findViewById(R.id.useClipBoardPaymentCode).setOnTouchListener(mButtonTouchListener);
        paymentCodeView.findViewById(R.id.scanPaymentCode).setOnTouchListener(mButtonTouchListener);
        paymentCodeView.findViewById(R.id.openAddressBook).setOnTouchListener(mButtonTouchListener);

        dialogView.addView(paymentCodeView);

        showView(R.id.dialog);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.CLIPBOARD_PAYMENT_CODE;
    }

    @Override
    public void onScannerResult(String pResult)
    {
        if(mScanMode == ScanMode.SCAN_PAYMENT_CODE)
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
        else
        {
            mEncodedPrivateKey = pResult;
            switch(mBitcoin.isValidPrivateKey(mEncodedPrivateKey))
            {
                case 0: // Valid
                    displaySelectRecoverDate();
                    break;
                case 1 : // Invalid
                    showMessage(getString(R.string.invalid_private_key), 2000);
                    mEncodedPrivateKey = null;
                    displayWallets();
                    break;
                case 2: // Not mainnet
                    showMessage(getString(R.string.invalid_private_network), 2000);
                    mEncodedPrivateKey = null;
                    displayWallets();
                    break;
            }
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

        if(mScanMode == ScanMode.SCAN_PAYMENT_CODE)
            displayEnterPaymentCode();
        else
            displayAddOptions();
    }

    public synchronized void displayScanner(ScanMode pMode)
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

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            if(pMode == ScanMode.SCAN_PAYMENT_CODE)
            {
                actionBar.setIcon(R.drawable.ic_send_black_36dp);
                actionBar.setTitle(" " + getString(R.string.scan_payment_code));
            }
            else
            {
                actionBar.setIcon(R.drawable.ic_scan_black_36dp);
                actionBar.setTitle(" " + getString(R.string.scan_private_key));
            }
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup scanView = (ViewGroup)inflater.inflate(R.layout.scan, dialogView, false);
        dialogView.addView(scanView);

        scanView.findViewById(R.id.enterPaymentCode).setOnTouchListener(mButtonTouchListener);
        scanView.findViewById(R.id.openAddressBook).setOnTouchListener(mButtonTouchListener);

        if(pMode != ScanMode.SCAN_PAYMENT_CODE)
        {
            ((TextView)scanView.findViewById(R.id.scanTitle)).setText(R.string.scan_private_key);
            scanView.findViewById(R.id.enterPaymentCode).setVisibility(View.GONE);
            scanView.findViewById(R.id.openAddressBook).setVisibility(View.GONE);
        }

        showView(R.id.dialog);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.SCAN;
        mScanMode = pMode;

        ScannerView cameraView = scanView.findViewById(R.id.camera);
        cameraView.setCamera(mScanner);
        if(!mScanner.open(getApplicationContext(), cameraView.getHolder(), Scanner.FACING_BACK))
        {
            Log.w(logTag, "Camera failed to open");
            showMessage(getString(R.string.failed_camera_access), 2000);
            displayWallets();
        }
    }

    public synchronized void displayReceive()
    {
        if(mPaymentRequest == null || mPaymentRequest.uri == null || mQRCode == null)
            displayWallets();
        else
        {
            LayoutInflater inflater = getLayoutInflater();
            ViewGroup dialogView = findViewById(R.id.dialog);
            ViewGroup receiveView;
            boolean rebuild = mMode != Mode.RECEIVE;

            if(rebuild)
            {
                dialogView.removeAllViews();

                ActionBar actionBar = getSupportActionBar();
                if(actionBar != null)
                {
                    actionBar.setIcon(R.drawable.ic_add_circle_black_36dp);
                    actionBar.setTitle(" " + getString(R.string.receive));
                    actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
                }

                receiveView = (ViewGroup)inflater.inflate(R.layout.receive, dialogView, false);
                dialogView.addView(receiveView);

                receiveView.findViewById(R.id.addAddressLabel).setOnTouchListener(mButtonTouchListener);
                receiveView.findViewById(R.id.openAddressLabels).setOnTouchListener(mButtonTouchListener);
                receiveView.findViewById(R.id.modifyReceiveAmount).setOnTouchListener(mButtonTouchListener);
                receiveView.findViewById(R.id.modifyReceiveLabel).setOnTouchListener(mButtonTouchListener);
                receiveView.findViewById(R.id.modifyReceiveMessage).setOnTouchListener(mButtonTouchListener);
                receiveView.findViewById(R.id.specifyReceiveAmount).setOnTouchListener(mButtonTouchListener);
                receiveView.findViewById(R.id.specifyReceiveLabel).setOnTouchListener(mButtonTouchListener);
                receiveView.findViewById(R.id.specifyReceiveMessage).setOnTouchListener(mButtonTouchListener);
            }
            else
                receiveView = (ViewGroup)dialogView.getChildAt(0);

            // Set image and text
            ((ImageView)receiveView.findViewById(R.id.qrImage)).setImageBitmap(mQRCode);
            ((TextView)receiveView.findViewById(R.id.paymentCode)).setText(mPaymentRequest.uri);

            if(mIsSupportURI)
            {
                receiveView.findViewById(R.id.optionalReceive).setVisibility(View.GONE);

                ((TextView)receiveView.findViewById(R.id.receiveTitle)).setText(R.string.support_nextcash);
                ((TextView)receiveView.findViewById(R.id.receiveDescription))
                  .setText(R.string.support_nextcash_description);
            }
            else
            {
                AddressLabel.Item customLabel = mBitcoin.lookupAddressLabel(mPaymentRequest.address,
                  mPaymentRequest.amount);
                TextView addressTitle = receiveView.findViewById(R.id.receiveAddressTitle);
                if(customLabel != null)
                {
                    addressTitle.setText(customLabel.label);
                    addressTitle.setVisibility(View.VISIBLE);
                }
                else
                    addressTitle.setVisibility(View.GONE);

                receiveView.findViewById(R.id.optionalReceive).setVisibility(View.VISIBLE);

                // Amount
                if(mPaymentRequest.amount == 0)
                {
                    receiveView.findViewById(R.id.specifyReceiveAmount).setVisibility(View.VISIBLE);
                    receiveView.findViewById(R.id.specifiedAmountGroup).setVisibility(View.GONE);
                }
                else
                {
                    receiveView.findViewById(R.id.specifyReceiveAmount).setVisibility(View.GONE);
                    receiveView.findViewById(R.id.specifiedAmountGroup).setVisibility(View.VISIBLE);

                    ((TextView)receiveView.findViewById(R.id.amountValue))
                      .setText(String.format(Locale.getDefault(), "%s %s",
                        mBitcoin.amountText(mPaymentRequest.amount), mBitcoin.exchangeType()));
                    ((TextView)receiveView.findViewById(R.id.satoshiAmountValue))
                      .setText(Bitcoin.satoshiText(mPaymentRequest.amount));
                }

                // Label
                if(mPaymentRequest.label == null || mPaymentRequest.label.length() == 0)
                {
                    receiveView.findViewById(R.id.specifyReceiveLabel).setVisibility(View.VISIBLE);
                    receiveView.findViewById(R.id.specifiedLabelGroup).setVisibility(View.GONE);
                }
                else
                {
                    receiveView.findViewById(R.id.specifyReceiveLabel).setVisibility(View.GONE);
                    receiveView.findViewById(R.id.specifiedLabelGroup).setVisibility(View.VISIBLE);
                    ((TextView)receiveView.findViewById(R.id.labelValue)).setText(mPaymentRequest.label);
                }

                // Message
                if(mPaymentRequest.message == null || mPaymentRequest.message.length() == 0)
                {
                    receiveView.findViewById(R.id.specifyReceiveMessage).setVisibility(View.VISIBLE);
                    receiveView.findViewById(R.id.specifiedMessageGroup).setVisibility(View.GONE);
                }
                else
                {
                    receiveView.findViewById(R.id.specifyReceiveMessage).setVisibility(View.GONE);
                    receiveView.findViewById(R.id.specifiedMessageGroup).setVisibility(View.VISIBLE);
                    ((TextView)receiveView.findViewById(R.id.messageValue)).setText(mPaymentRequest.message);
                }
            }

            showView(R.id.dialog);
            mMode = Mode.RECEIVE;
        }
    }

    public synchronized void displayAddressLabels()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);
        ViewGroup nonScrollView = findViewById(R.id.nonScroll);

        dialogView.removeAllViews();
        nonScrollView.removeAllViews();

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(null);
            actionBar.setTitle(getString(R.string.labeled_addresses));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup addressLabelView = (ViewGroup)inflater.inflate(R.layout.address_labels, nonScrollView,
          false);
        nonScrollView.addView(addressLabelView);

        ((TextView)addressLabelView.findViewById(R.id.walletName)).setText(mBitcoin.wallet(mCurrentWalletIndex).name);

        RecyclerView recyclerView = addressLabelView.findViewById(R.id.addressLabelItems);
        mUndoDeleteRunnable = null;
        mConfirmDeleteRunnable = null;
        LinearLayoutManager layoutManager = new LinearLayoutManager(this); // Vertical list
        mAddressLabelAdapter = new AddressLabelAdapter(mBitcoin, layoutManager, mCurrentWalletIndex,
          getResources().getColor(R.color.rowShade), getResources().getColor(R.color.rowNotShade));
        ItemTouchHelper.SimpleCallback touchCallBack = new ItemTouchHelper.SimpleCallback(0,
          ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT)
        {
            @Override
            public boolean onMove(@NonNull RecyclerView pRecyclerView, @NonNull RecyclerView.ViewHolder pViewHolder,
              @NonNull RecyclerView.ViewHolder pViewHolderLower)
            {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder pViewHolder, int pDirection)
            {
                findViewById(R.id.undoDelete).setVisibility(View.VISIBLE);
                mAddressLabelAdapter.remove(pViewHolder.getAdapterPosition());
                if(mConfirmDeleteRunnable != null)
                    mDelayHandler.removeCallbacks(mConfirmDeleteRunnable);
                mUndoDeleteRunnable = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        mAddressLabelAdapter.undoDelete();
                        findViewById(R.id.undoDelete).setVisibility(View.GONE);
                        mDelayHandler.postDelayed(mConfirmDeleteRunnable, 3000);
                    }
                };
                mConfirmDeleteRunnable = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        mAddressLabelAdapter.confirmDelete();
                        findViewById(R.id.undoDelete).setVisibility(View.GONE);
                    }
                };
                mDelayHandler.postDelayed(mConfirmDeleteRunnable, 3000);
            }

            @Override
            public void onChildDraw(@NonNull Canvas pCanvas, @NonNull RecyclerView pRecyclerView,
              @NonNull RecyclerView.ViewHolder pViewHolder, float pDeltaX, float pDeltaY, int pActionState,
              boolean pIsCurrentlyActive)
            {
                View itemView = pViewHolder.itemView;

                // Draw the red delete background
                Rect rect;
                if(pDeltaX < 0.0f)
                    rect = new Rect(itemView.getRight() + (int)pDeltaX, itemView.getTop(), itemView.getRight(),
                      itemView.getBottom());
                else
                    rect = new Rect(itemView.getLeft(), itemView.getTop(), itemView.getLeft() + (int)pDeltaX,
                      itemView.getBottom());
                pCanvas.drawRect(rect, mNegativeColorPaint);

                // Calculate position of delete icon
                int iconTop = itemView.getTop() + (itemView.getHeight() - mSmallIconSize) / 2;
                int iconBottom = iconTop + mSmallIconSize;
                int iconMargin = (itemView.getHeight() - mSmallIconSize) / 2;
                int iconLeft, iconRight;
                if(pDeltaX < 0.0f)
                {
                    iconLeft = itemView.getRight() - iconMargin - mSmallIconSize;
                    iconRight = itemView.getRight() - iconMargin;
                }
                else
                {
                    iconLeft = itemView.getLeft() + iconMargin;
                    iconRight = itemView.getLeft() + iconMargin + mSmallIconSize;
                }

                // Draw the delete icon
                mDeleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                mDeleteIcon.draw(pCanvas);

                super.onChildDraw(pCanvas, pRecyclerView, pViewHolder, pDeltaX, pDeltaY, pActionState,
                  pIsCurrentlyActive);
            }
        };

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(mAddressLabelAdapter);
        new ItemTouchHelper(touchCallBack).attachToRecyclerView(recyclerView);

        showView(R.id.nonScroll);
        mMode = Mode.ADDRESS_LABELS;
    }

    public synchronized void displayAddressBook()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);
        ViewGroup nonScrollView = findViewById(R.id.nonScroll);

        dialogView.removeAllViews();
        nonScrollView.removeAllViews();

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(null);
            actionBar.setTitle(getString(R.string.address_book));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup addressBookView = (ViewGroup)inflater.inflate(R.layout.address_book, nonScrollView, false);
        nonScrollView.addView(addressBookView);

        RecyclerView recyclerView = addressBookView.findViewById(R.id.addressBookItems);
        mUndoDeleteRunnable = null;
        mConfirmDeleteRunnable = null;
        LinearLayoutManager layoutManager = new LinearLayoutManager(this); // Vertical list
        mAddressBookAdapter = new AddressBookAdapter(mBitcoin, layoutManager, getResources().getColor(R.color.rowShade),
          getResources().getColor(R.color.rowNotShade));
        ItemTouchHelper.SimpleCallback touchCallBack = new ItemTouchHelper.SimpleCallback(0,
          ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT)
        {
            @Override
            public boolean onMove(@NonNull RecyclerView pRecyclerView, @NonNull RecyclerView.ViewHolder pViewHolder,
              @NonNull RecyclerView.ViewHolder pViewHolderLower)
            {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder pViewHolder, int pDirection)
            {
                findViewById(R.id.undoDelete).setVisibility(View.VISIBLE);
                mAddressBookAdapter.remove(pViewHolder.getAdapterPosition());
                if(mConfirmDeleteRunnable != null)
                    mDelayHandler.removeCallbacks(mConfirmDeleteRunnable);
                mUndoDeleteRunnable = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        mAddressBookAdapter.undoDelete();
                        findViewById(R.id.undoDelete).setVisibility(View.GONE);
                        mDelayHandler.postDelayed(mConfirmDeleteRunnable, 3000);
                    }
                };
                mConfirmDeleteRunnable = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        mAddressBookAdapter.confirmDelete();
                        findViewById(R.id.undoDelete).setVisibility(View.GONE);
                    }
                };
                mDelayHandler.postDelayed(mConfirmDeleteRunnable, 3000);
            }

            @Override
            public void onChildDraw(@NonNull Canvas pCanvas, @NonNull RecyclerView pRecyclerView,
              @NonNull RecyclerView.ViewHolder pViewHolder, float pDeltaX, float pDeltaY, int pActionState,
              boolean pIsCurrentlyActive)
            {
                View itemView = pViewHolder.itemView;

                // Draw the red delete background
                Rect rect;
                if(pDeltaX < 0.0f)
                    rect = new Rect(itemView.getRight() + (int)pDeltaX, itemView.getTop(), itemView.getRight(),
                      itemView.getBottom());
                else
                    rect = new Rect(itemView.getLeft(), itemView.getTop(), itemView.getLeft() + (int)pDeltaX,
                      itemView.getBottom());
                pCanvas.drawRect(rect, mNegativeColorPaint);

                // Calculate position of delete icon
                int iconTop = itemView.getTop() + (itemView.getHeight() - mSmallIconSize) / 2;
                int iconBottom = iconTop + mSmallIconSize;
                int iconMargin = (itemView.getHeight() - mSmallIconSize) / 2;
                int iconLeft, iconRight;
                if(pDeltaX < 0.0f)
                {
                    iconLeft = itemView.getRight() - iconMargin - mSmallIconSize;
                    iconRight = itemView.getRight() - iconMargin;
                }
                else
                {
                    iconLeft = itemView.getLeft() + iconMargin;
                    iconRight = itemView.getLeft() + iconMargin + mSmallIconSize;
                }

                // Draw the delete icon
                mDeleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                mDeleteIcon.draw(pCanvas);

                super.onChildDraw(pCanvas, pRecyclerView, pViewHolder, pDeltaX, pDeltaY, pActionState,
                  pIsCurrentlyActive);
            }
        };

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(mAddressBookAdapter);
        new ItemTouchHelper(touchCallBack).attachToRecyclerView(recyclerView);

        showView(R.id.nonScroll);
        mMode = Mode.ADDRESS_BOOK;
    }

    public synchronized void displayAddOptions()
    {
        // Display options for adding wallets
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_add_black_36dp);
            actionBar.setTitle(" " + getString(R.string.title_add_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        LinearLayout addView = (LinearLayout)inflater.inflate(R.layout.add_wallet, dialogView, false);
        dialogView.addView(addView);

        // Set listener for buttons
        for(int index = 0; index < addView.getChildCount(); index++)
            addView.getChildAt(index).setOnTouchListener(mButtonTouchListener);

        showView(R.id.dialog);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.ADD_WALLET;
    }

    public synchronized void displayImportWallet()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(null);
            actionBar.setTitle(" " + getString(R.string.import_text));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        LinearLayout dateView = (LinearLayout)inflater.inflate(R.layout.select_recover_date, dialogView,
          false);
        dialogView.addView(dateView);

        dateView.findViewById(R.id.dateSelected).setOnTouchListener(mButtonTouchListener);

        // Done button
        dialogView.findViewById(R.id.dateSelected).setId(R.id.enterImportKey);

        showView(R.id.dialog);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.IMPORT_WALLET;
    }

    public synchronized void displayEnterImportKey()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();

        LinearLayout importView = (LinearLayout)inflater.inflate(R.layout.import_bip32_key, dialogView,
          false);
        dialogView.addView(importView);

        importView.findViewById(R.id.loadKey).setOnTouchListener(mButtonTouchListener);

        showView(R.id.dialog);
        findViewById(R.id.mainScroll).setScrollY(0);
        focusOnText((EditText)dialogView.findViewById(R.id.importText));
        mMode = Mode.IMPORT_WALLET;
    }

    public synchronized void displayCreateWallet()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_add_circle_black_36dp);
            actionBar.setTitle(" " + getString(R.string.create_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup createWallet = (ViewGroup)inflater.inflate(R.layout.view_seed, dialogView, false);
        dialogView.addView(createWallet);

        createWallet.findViewById(R.id.verifySeedSaved).setOnTouchListener(mButtonTouchListener);

        mSeedIsBackedUp = false;
        mDerivationPathMethodToLoad = Bitcoin.BIP0044_DERIVATION;
        mSeedBackupOnly = false;

        Spinner entropy = createWallet.findViewById(R.id.seedEntropy);
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

        showView(R.id.dialog);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.CREATE_WALLET;
    }

    public String amountText(int pUnitType, long pSatoshis)
    {
        switch(pUnitType)
        {
            case Bitcoin.FIAT:
                return mBitcoin.amountText(pSatoshis);
            case Bitcoin.BITS:
                return String.format(Locale.getDefault(), "%.2f", Bitcoin.bitsFromSatoshis(pSatoshis));
            case Bitcoin.BITCOINS:
                return String.format(Locale.getDefault(), "%.8f", Bitcoin.bitcoinsFromSatoshis(pSatoshis));
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
            if(mPaymentRequest != null)
            {
                if(mPaymentRequest.amount == 0)
                    amountField.setText("");
                else
                    amountField.setText(amountText(pPosition, mPaymentRequest.amount));
            }

            updateFee();
        }
        else if(pParent.getId() == R.id.enterAmountUnits)
        {
            TextView satoshiField = findViewById(R.id.enteredSatoshiAmountValue);
            EditText amountField = findViewById(R.id.enteredAmount);
            if(satoshiField != null && amountField != null)
            {
                String satoshiText = satoshiField.getText().toString();
                String split[] = satoshiText.split(" ");
                StringBuilder builder = new StringBuilder();
                for(String item : split)
                    builder.append(item);
                satoshiText = builder.toString();
                long satoshiAmount;
                try
                {
                    satoshiAmount = Long.parseLong(satoshiText);
                }
                catch(Exception pException)
                {
                    satoshiAmount = 0;
                }
                if(satoshiAmount == 0)
                    amountField.setText("");
                else
                    amountField.setText(amountText(pPosition, satoshiAmount));
            }
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
            else if(mBitcoin.exchangeType() != exchangeTypes[pPosition])
            {
                mBitcoin.setExchangeRate(0.0, exchangeTypes[pPosition]);

                Settings settings = Settings.getInstance(getFilesDir());
                settings.setValue(Bitcoin.EXCHANGE_TYPE_NAME, exchangeTypes[pPosition]);
                settings.setDoubleValue(Bitcoin.EXCHANGE_RATE_NAME, 0.0);
                Log.i(logTag, String.format("Exchange type set to %s", exchangeTypes[pPosition]));

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

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_add_circle_black_36dp);
            actionBar.setTitle(" " + getString(R.string.verify_seed));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup verifySeed = (ViewGroup)inflater.inflate(R.layout.verify_seed, dialogView, false);
        ViewGroup wordRows = verifySeed.findViewById(R.id.seedRows);

        verifySeed.findViewById(R.id.removeSeedWord).setOnTouchListener(mButtonTouchListener);
        verifySeed.findViewById(R.id.skipCheckSeed).setOnTouchListener(mButtonTouchListener);

        dialogView.addView(verifySeed);

        ArrayList<String> words = new ArrayList<>();
        Collections.addAll(words, mSeed.split(" "));
        Collections.sort(words);

        int rowCount = 0;
        LinearLayout row = null;
        TextButton wordButton;
        for(String word : words)
        {
            if(rowCount == 0)
            {
                // Create row
                row = (LinearLayout)inflater.inflate(R.layout.seed_word_row, wordRows, false);
                wordRows.addView(row);
            }
            wordButton = (TextButton)inflater.inflate(R.layout.seed_word_button, row, false);
            wordButton.setText(word);
            wordButton.setOnTouchListener(mButtonTouchListener);
            row.addView(wordButton);
            rowCount++;
            if(rowCount == 4)
                rowCount = 0;
        }

        if(mSeedBackupOnly)
            verifySeed.findViewById(R.id.skipCheckSeed).setVisibility(View.GONE);

        showView(R.id.dialog);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.VERIFY_SEED;
    }

    public synchronized void displaySelectRecoverDate()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(null);
            actionBar.setTitle(" " + getString(R.string.recover_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        LinearLayout dateView = (LinearLayout)inflater.inflate(R.layout.select_recover_date, dialogView,
          false);
        dialogView.addView(dateView);

        dateView.findViewById(R.id.dateSelected).setOnTouchListener(mButtonTouchListener);

        // Done button
        dialogView.findViewById(R.id.dateSelected).setId(R.id.addPrivateKey);

        showView(R.id.dialog);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.IMPORT_PRIVATE_KEY;
    }

    public synchronized void displayRecoverWallet()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(null);
            actionBar.setTitle(" " + getString(R.string.recover_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        LinearLayout dateView = (LinearLayout)inflater.inflate(R.layout.select_recover_date, dialogView,
          false);
        dialogView.addView(dateView);

        dateView.findViewById(R.id.dateSelected).setOnTouchListener(mButtonTouchListener);

        // Done button
        dialogView.findViewById(R.id.dateSelected).setId(R.id.enterRecoverSeed);

        showView(R.id.dialog);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.RECOVER_WALLET;
    }

    // When text is typed or modified while entering a seed to recover a wallet.
    private synchronized void onSeedEntryModified(String pText)
    {
        LinearLayout wordButtons = findViewById(R.id.seedButtons);
        EditText textField = findViewById(R.id.seedWordEntry);
        TextView seed = findViewById(R.id.seed);
        String[] matchingWords;
        ArrayList<String> words = new ArrayList<>();
        boolean seedModified = false;

        if(wordButtons == null || textField == null || seed == null)
            return;

        wordButtons.removeAllViews();

        if(pText.length() == 0)
            return;

        // Find all seed words that start with the text field
        Collections.addAll(words, pText.split(" "));
        if(words.size() == 1)
        {
            if(pText.endsWith(" "))
            {
                // One word with a space after.
                // Add seed word if it matches.
                if(mBitcoin.isValidSeedWord(words.get(0)))
                {
                    // Add word to seed.
                    if(seed.getText().length() > 0)
                        seed.setText(String.format("%s %s", seed.getText(), words.get(0)));
                    else
                        seed.setText(words.get(0));

                    // Remove all word buttons.
                    ((ViewGroup)findViewById(R.id.seedButtons)).removeAllViews();

                    // Clear text entry.
                    textField.setText("");

                    seedModified = true;
                }
            }
            else
            {
                // Check for partial matches and show buttons with matching words.
                matchingWords = mBitcoin.getMnemonicWords(pText);
                if(matchingWords.length < 20)
                {
                    // Put those words into the word buttons group
                    LayoutInflater inflater = getLayoutInflater();
                    TextButton wordButton;

                    for(String word : matchingWords)
                    {
                        wordButton = (TextButton)inflater.inflate(R.layout.seed_word_button, wordButtons,
                          false);
                        wordButton.setText(word);
                        wordButton.setOnTouchListener(mButtonTouchListener);
                        wordButtons.addView(wordButton);
                    }
                }
            }
        }
        else if(words.size() > 1)
        {
            boolean matchFound = false;
            for(String word : words)
            {
                // Add seed word if it matches.
                if(mBitcoin.isValidSeedWord(word))
                {
                    // Add word to seed.
                    if(seed.getText().length() > 0)
                        seed.setText(String.format("%s %s", seed.getText(), word));
                    else
                        seed.setText(word);

                    matchFound = true;
                }
            }

            if(matchFound)
            {
                // Clear text entry.
                textField.setText("");

                // Remove all word buttons.
                ((ViewGroup)findViewById(R.id.seedButtons)).removeAllViews();

                seedModified = true;
            }
        }

        if(seedModified)
        {
            if(mMode == Mode.RECOVER_WALLET)
            {
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
                ViewGroup wordsGroup = (ViewGroup)wordButtons.getParent();
                int wordCount = 0;
                for(int i = 0; i < wordsGroup.getChildCount(); i++)
                    wordCount += ((ViewGroup)wordsGroup.getChildAt(i)).getChildCount();
                if(wordCount == 0)
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
                            mEncodedPrivateKey = null;
                            mSeedIsBackedUp = true;
                            mRecoverDate = System.currentTimeMillis() / 1000L;
                            displayAuthorize();
                        }
                    }
                    else
                    {
                        showMessage(getString(R.string.seed_doesnt_match), 2000);
                        if(mSeedBackupOnly)
                            displayWallets();
                        else
                            displayCreateWallet();
                    }
                }
            }
        }
    }

    public synchronized void displayEnterRecoverSeed()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(null);
            actionBar.setTitle(" " + getString(R.string.recover_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup enterSeed = (ViewGroup)inflater.inflate(R.layout.enter_seed, dialogView, false);
        dialogView.addView(enterSeed);

        enterSeed.findViewById(R.id.importSeed).setOnTouchListener(mButtonTouchListener);

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
                    onSeedEntryModified(pString.toString().toLowerCase());
                }

                @Override
                public void afterTextChanged(Editable pString)
                {

                }
            };
        }

        EditText seedWordEntry = enterSeed.findViewById(R.id.seedWordEntry);
        seedWordEntry.addTextChangedListener(mSeedWordWatcher);

        showView(R.id.dialog);
        focusOnText(seedWordEntry);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.RECOVER_WALLET;
    }

    public synchronized void displayBackupWallet(String pPassCode)
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_add_circle_black_36dp);
            actionBar.setTitle(" " + getString(R.string.backup_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup viewSeed = (ViewGroup)inflater.inflate(R.layout.view_seed, dialogView, false);
        dialogView.addView(viewSeed);

        viewSeed.findViewById(R.id.verifySeedSaved).setOnTouchListener(mButtonTouchListener);

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

        showView(R.id.dialog);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.BACKUP_WALLET;
    }

    private synchronized void displayTextDialog(TextEntryMode pMode, String pDefaultText)
    {
        RelativeLayout entryView;
        EditText entry;

        if(pMode == TextEntryMode.RECEIVE_AMOUNT)
        {
            entryView = findViewById(R.id.amountDialog);
            entry = entryView.findViewById(R.id.enteredAmount);
            Spinner units = entryView.findViewById(R.id.enterAmountUnits);

            units.setOnItemSelectedListener(this);
            units.setSelection(Bitcoin.FIAT); // Default to Fiat

            if(mEnteredAmountWatcher == null)
            {
                mEnteredAmountWatcher = new TextWatcher()
                {
                    @Override
                    public void beforeTextChanged(CharSequence pString, int pStart, int pCount, int pAfter)
                    {

                    }

                    @Override
                    public void onTextChanged(CharSequence pString, int pStart, int pBefore, int pCount)
                    {
                        Spinner units = findViewById(R.id.enterAmountUnits);
                        EditText amountField = findViewById(R.id.enteredAmount);
                        TextView satoshiAmount = findViewById(R.id.enteredSatoshiAmountValue);
                        double enteredAmount;
                        long amount;

                        if(units == null || amountField == null || satoshiAmount == null)
                            return;

                        try
                        {
                            enteredAmount = Bitcoin.parseAmount(amountField.getText().toString());
                        }
                        catch(Exception pException)
                        {
                            enteredAmount = 0.0;
                        }

                        switch(units.getSelectedItemPosition())
                        {
                            case 0: // USD
                                amount = Bitcoin.satoshisFromBitcoins(enteredAmount / mBitcoin.exchangeRate());
                                break;
                            case 1: // bits
                                amount = Bitcoin.satoshisFromBits(enteredAmount);
                                break;
                            case 2: // bitcoins
                                amount = Bitcoin.satoshisFromBitcoins(enteredAmount);
                                break;
                            default:
                                amount = 0;
                                break;
                        }

                        satoshiAmount.setText(Bitcoin.satoshiText(amount));
                    }

                    @Override
                    public void afterTextChanged(Editable pString)
                    {

                    }
                };
            }
            entry.addTextChangedListener(mEnteredAmountWatcher);
        }
        else
        {
            entryView = findViewById(R.id.textDialog);
            entry = entryView.findViewById(R.id.enteredText);
        }

        switch(pMode)
        {
        case SAVE_ADDRESS:
            entry.setHint(R.string.save_address_hint);
            break;
        case LABEL_ADDRESS:
            entry.setHint(R.string.address_label_hint);
            break;
        case RECEIVE_AMOUNT:
            entry.setHint(R.string.request_amount_hint);
            break;
        case RECEIVE_LABEL:
            entry.setHint(R.string.request_label_hint);
            break;
        case RECEIVE_MESSAGE:
            entry.setHint(R.string.request_message_hint);
            break;
        case TRANSACTION_COMMENT:
            entry.setHint(R.string.comment_hint);
            break;
        case TRANSACTION_COST_AMOUNT:
            entry.setHint(R.string.cost_basis_hint);
            break;
        case TRANSACTION_COST_DATE:
            entry.setHint(R.string.cost_date_hint);
            break;
        }

        if(pDefaultText != null && pDefaultText.length() > 0)
            entry.setText(pDefaultText);
        else
            entry.setText(null);
        entryView.setVisibility(View.VISIBLE);

        TextView.OnEditorActionListener textListener = new TextView.OnEditorActionListener()
        {
            @Override
            public boolean onEditorAction(TextView pView, int pActionId, KeyEvent pEvent)
            {
                if(pActionId == EditorInfo.IME_NULL || pActionId == EditorInfo.IME_ACTION_DONE ||
                  pActionId == EditorInfo.IME_ACTION_SEND ||
                  (pEvent != null && pEvent.getAction() == KeyEvent.ACTION_DOWN &&
                    pEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER))
                {
                    processClick(pView, R.id.textDialogOkay);
                    return true;
                }
                return false;
            }
        };
        entry.setOnEditorActionListener(textListener);

        focusOnText(entry);
        mTextEntryMode = pMode;
    }

    private synchronized void displayDateDialog(TextEntryMode pMode, long pDate)
    {
        if(pMode != TextEntryMode.TRANSACTION_COST_DATE)
        {
            mTextEntryMode = TextEntryMode.NONE;
            displayWallets();
            return;
        }

        RelativeLayout entryView = findViewById(R.id.dateDialog);
        DatePicker entry = entryView.findViewById(R.id.enteredDate);

        ((TextView)entryView.findViewById(R.id.title)).setText(getString(R.string.cost_date_hint));

        Calendar date;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            date = new Calendar.Builder().setInstant(pDate * 1000L).build();
        }
        else
        {
            date = Calendar.getInstance();
            date.setTimeInMillis(pDate * 1000L);
        }

        entry.updateDate(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH));
        entryView.setVisibility(View.VISIBLE);

        mTextEntryMode = pMode;
    }

    public synchronized void displayAuthorize()
    {
        ConstraintLayout pinLayout = findViewById(R.id.pinEntry);
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

        LayoutInflater inflater = getLayoutInflater();
        ViewGroup dialogView = findViewById(R.id.dialog);

        dialogView.removeAllViews();

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_edit_black_36dp);
            actionBar.setTitle(" " + getString(R.string.title_edit_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup editWallet = (ViewGroup)inflater.inflate(R.layout.edit_wallet, dialogView, false);

        editWallet.findViewById(R.id.updateWalletName).setOnTouchListener(mButtonTouchListener);
        editWallet.findViewById(R.id.backupWallet).setOnTouchListener(mButtonTouchListener);
        editWallet.findViewById(R.id.removeWallet).setOnTouchListener(mButtonTouchListener);

        // Set Name
        ((EditText)editWallet.findViewById(R.id.name)).setText(wallet.name);

        if(!wallet.isPrivate)
            editWallet.findViewById(R.id.backupWallet).setVisibility(View.GONE);

        dialogView.addView(editWallet);

        showView(R.id.dialog);
        findViewById(R.id.mainScroll).setScrollY(0);
        mMode = Mode.EDIT_WALLET;
    }

    private void alignTransactionColumns(ViewGroup pView)
    {
        ViewGroup row;
        int rowCount = pView.getChildCount();
        View entry;
        int widest[] = new int[3];
        int width;
        boolean shade = false;

        // Find widest columns
        for(int rowOffset = 0; rowOffset < rowCount; rowOffset++)
        {
            row = (ViewGroup)pView.getChildAt(rowOffset);

            entry = row.findViewById(R.id.amount);
            entry.measure(0, 0);
            width = entry.getMeasuredWidth();
            if(width > widest[0])
                widest[0] = width;

            if(rowOffset != 0) // Header row doesn't have bitcoin amount
            {
                entry = row.findViewById(R.id.bitcoinAmount);
                entry.measure(0, 0);
                width = entry.getMeasuredWidth();
                if(width > widest[0])
                    widest[0] = width;
            }

            entry = row.findViewById(R.id.time);
            entry.measure(0, 0);
            width = entry.getMeasuredWidth();
            if(width > widest[1])
                widest[1] = width;

            entry = row.findViewById(R.id.count);
            entry.measure(0, 0);
            width = entry.getMeasuredWidth();
            if(width > widest[2])
                widest[2] = width;
        }

        // Set column widths to align
        for(int rowOffset = 0; rowOffset < rowCount; rowOffset++)
        {
            row = (ViewGroup)pView.getChildAt(rowOffset);

            entry = row.findViewById(R.id.amount);
            entry.setMinimumWidth(widest[0]);
            if(rowOffset != 0) // Header row doesn't have bitcoin amount
            {
                entry = row.findViewById(R.id.bitcoinAmount);
                entry.setMinimumWidth(widest[0]);
            }

            entry = row.findViewById(R.id.time);
            entry.setMinimumWidth(widest[1]);

            entry = row.findViewById(R.id.count);
            entry.setMinimumWidth(widest[2]);

            if(shade)
                row.setBackgroundColor(getResources().getColor(R.color.rowShade));
            else
                row.setBackgroundColor(getResources().getColor(R.color.rowNotShade));

            shade = !shade;
        }
    }

    private void alignTransactions(ViewGroup pView)
    {
        ViewGroup pending = pView.findViewById(R.id.walletPending);
        if(pending.getVisibility() != View.GONE)
            alignTransactionColumns(pending);

        ViewGroup recent = pView.findViewById(R.id.walletRecent);
        if(recent.getVisibility() != View.GONE)
            alignTransactionColumns(recent);
    }

    private void populateTransactions(ViewGroup pView, Transaction[] pTransactions, int pLimit, boolean pRebuild)
    {
        LayoutInflater inflater = getLayoutInflater();
        int pendingCount = 0, recentCount = 0;
        ViewGroup recentViewGroup, pendingViewGroup, transactionViewGroup;
        ViewGroup transactionView;
        boolean pendingWasEmpty = false, recentWasEmpty = false;

        // Add/Update transactions
        recentViewGroup = pView.findViewById(R.id.walletRecent);
        pendingViewGroup = pView.findViewById(R.id.walletPending);

        if(pRebuild)
        {
            recentViewGroup.removeAllViews();
            pendingViewGroup.removeAllViews();
        }

        if(pendingViewGroup.getChildCount() == 0)
        {
            pendingWasEmpty = true;
            pView.findViewById(R.id.walletPendingGroup).setVisibility(View.GONE);
        }
        if(recentViewGroup.getChildCount() == 0)
        {
            recentWasEmpty = true;
            pView.findViewById(R.id.walletRecentTitle).setVisibility(View.GONE);
            recentViewGroup.setVisibility(View.GONE);
        }

        for(Transaction transaction : pTransactions)
        {
            if(transaction.block == null)
            {
                transactionViewGroup = pendingViewGroup;
                pendingCount++;
                if(transactionViewGroup.getChildCount() == 0)
                {
                    // Make pending section visible
                    pView.findViewById(R.id.walletPendingGroup).setVisibility(View.VISIBLE);

                    // Add header line
                    transactionView = (ViewGroup)inflater.inflate(R.layout.wallet_transaction_header,
                      transactionViewGroup, false);
                    ((TextView)transactionView.findViewById(R.id.count)).setText(R.string.peers);
                    transactionViewGroup.addView(transactionView);
                }
            }
            else
            {
                transactionViewGroup = recentViewGroup;
                recentCount++;
                if(transactionViewGroup.getChildCount() == 0)
                {
                    // Make recent section visible
                    pView.findViewById(R.id.walletRecentTitle).setVisibility(View.VISIBLE);
                    transactionViewGroup.setVisibility(View.VISIBLE);

                    // Add header line
                    transactionView = (ViewGroup)inflater.inflate(R.layout.wallet_transaction_header,
                      transactionViewGroup, false);
                    ((TextView)transactionView.findViewById(R.id.count)).setText(R.string.confirms_title);
                    transactionViewGroup.addView(transactionView);
                }
            }

            if(pRebuild)
            {
                transactionView = (ViewGroup)inflater.inflate(R.layout.wallet_transaction, transactionViewGroup,
                  false);
                transactionViewGroup.addView(transactionView);
            }
            else
            {
                transactionView = transactionViewGroup.findViewWithTag(transaction.tag());
                if(transactionView == null)
                {
                    // Check other group
                    if(transactionViewGroup == pendingViewGroup)
                        transactionView = recentViewGroup.findViewWithTag(transaction.tag());
                    else
                        transactionView = pendingViewGroup.findViewWithTag(transaction.tag());

                    if(transactionView == null)
                    {
                        if(recentCount > pLimit)
                            continue;

                        // Create new view for transaction.
                        transactionView = (ViewGroup)inflater.inflate(R.layout.wallet_transaction, transactionViewGroup,
                          false);
                    }
                    else
                    {
                        // Move from one group to the other.
                        ((ViewGroup)transactionView.getParent()).removeView(transactionView);

                        // Apparently Android will fail if this view is immediately added back to a new parent below.
                        // So create a new view.
                        transactionView = (ViewGroup)inflater.inflate(R.layout.wallet_transaction, transactionViewGroup,
                          false);
                    }

                    if(transactionViewGroup == pendingViewGroup)
                    {
                        if(pendingWasEmpty)
                            transactionViewGroup.addView(transactionView); // Add in original order
                        else
                            transactionViewGroup.addView(transactionView, 1); // Insert new at front
                    }
                    else
                    {
                        if(recentWasEmpty)
                            transactionViewGroup.addView(transactionView); // Add in original order
                        else
                            transactionViewGroup.addView(transactionView, 1); // Insert new at front
                    }
                }
            }

            transaction.updateView(getApplicationContext(), mBitcoin, transactionView, false);
        }

        if(pendingCount == 0)
            pView.findViewById(R.id.walletPendingGroup).setVisibility(View.GONE);

        if(recentCount == 0)
        {
            pView.findViewById(R.id.walletRecentTitle).setVisibility(View.GONE);
            recentViewGroup.setVisibility(View.GONE);
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
        ViewGroup nonScrollView = findViewById(R.id.nonScroll);
        ViewGroup historyView;
        boolean update;

        if(mMode == Mode.TRANSACTION_HISTORY)
        {
            historyView = (ViewGroup)nonScrollView.getChildAt(0);
            update = true;
        }
        else
        {
            nonScrollView.removeAllViews();

            // Title
            ActionBar actionBar = getSupportActionBar();
            if(actionBar != null)
            {
                actionBar.setIcon(R.drawable.ic_history_black_36dp);
                actionBar.setTitle(" " + getString(R.string.title_wallet_history));
                actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
            }

            historyView = (ViewGroup)inflater.inflate(R.layout.wallet_history, nonScrollView, false);
            ((TextView)historyView.findViewById(R.id.title)).setText(wallet.name);
            nonScrollView.addView(historyView);

            update = false;
        }

        RecyclerView recyclerView = historyView.findViewById(R.id.transactionItems);

        if(update)
        {
            TransactionAdapter transactionAdapter = (TransactionAdapter)recyclerView.getAdapter();
            if(transactionAdapter != null)
                transactionAdapter.updateTransactions();
        }
        else
        {
            LinearLayoutManager layoutManager = new LinearLayoutManager(this); // Vertical list
            recyclerView.setLayoutManager(layoutManager);

            RelativeLayout header = historyView.findViewById(R.id.header);
            TextView countView = header.findViewById(R.id.count);
            countView.measure(0, 0);
            int countWidth = countView.getMeasuredWidth();

            TransactionAdapter transactionAdapter = new TransactionAdapter(getApplicationContext(), mBitcoin,
              mCurrentWalletIndex, layoutManager, getResources().getColor(R.color.rowShade),
              getResources().getColor(R.color.rowNotShade), countWidth);
            recyclerView.setAdapter(transactionAdapter);
        }

        showView(R.id.nonScroll);
        mMode = Mode.TRANSACTION_HISTORY;
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

    // Set one view visible and hide the rest.
    public synchronized void showView(int pViewID)
    {
        switch(pViewID)
        {
        default:
        case R.id.main:
            findViewById(R.id.main).setVisibility(View.VISIBLE);
            findViewById(R.id.statusBar).setVisibility(View.VISIBLE);
            findViewById(R.id.controls).setVisibility(View.VISIBLE);
            findViewById(R.id.dialog).setVisibility(View.GONE);
            findViewById(R.id.progress).setVisibility(View.GONE);
            findViewById(R.id.nonScroll).setVisibility(View.GONE);
            break;
        case R.id.progress:
            findViewById(R.id.main).setVisibility(View.GONE);
            findViewById(R.id.statusBar).setVisibility(View.GONE);
            findViewById(R.id.controls).setVisibility(View.GONE);
            findViewById(R.id.dialog).setVisibility(View.GONE);
            findViewById(R.id.progress).setVisibility(View.VISIBLE);
            findViewById(R.id.nonScroll).setVisibility(View.GONE);
            break;
        case R.id.dialog:
            findViewById(R.id.main).setVisibility(View.GONE);
            findViewById(R.id.statusBar).setVisibility(View.GONE);
            findViewById(R.id.controls).setVisibility(View.GONE);
            findViewById(R.id.dialog).setVisibility(View.VISIBLE);
            findViewById(R.id.progress).setVisibility(View.GONE);
            findViewById(R.id.nonScroll).setVisibility(View.GONE);
            break;
        case R.id.nonScroll:
            findViewById(R.id.main).setVisibility(View.GONE);
            findViewById(R.id.statusBar).setVisibility(View.GONE);
            findViewById(R.id.controls).setVisibility(View.GONE);
            findViewById(R.id.dialog).setVisibility(View.GONE);
            findViewById(R.id.progress).setVisibility(View.GONE);
            findViewById(R.id.nonScroll).setVisibility(View.VISIBLE);
            break;
        }
    }

    public synchronized void displayProgress()
    {
        showView(R.id.progress);
        mMode = Mode.IN_PROGRESS;
    }

    public boolean processClick(View pView, int pID)
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
            mPreviousReceiveMode = mMode;
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
                mEncodedPrivateKey = null;
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
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                date = new Calendar.Builder().setDate(picker.getYear(), picker.getMonth(), picker.getDayOfMonth())
                  .setTimeOfDay(0, 0, 0).build();
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
        case R.id.addPrivateKey:
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
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                date = new Calendar.Builder().setDate(picker.getYear(), picker.getMonth(), picker.getDayOfMonth())
                  .setTimeOfDay(0, 0, 0).build();
            }
            else
            {
                date = Calendar.getInstance();
                date.set(picker.getYear(), picker.getMonth(), picker.getDayOfMonth());
            }

            mRecoverDate = date.getTimeInMillis() / 1000L;
            mAuthorizedTask = AuthorizedTask.ADD_KEY;
            displayAuthorize();
            break;
        }
        case R.id.importSeed:
        {
            mSeed = ((TextView)findViewById(R.id.seed)).getText().toString();
            mDerivationPathMethodToLoad = Bitcoin.BIP0044_DERIVATION; // TODO Add options to specify this
            mKeyToLoad = null;
            mEncodedPrivateKey = null;
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
        case R.id.scanPrivateKey:
            displayScanner(ScanMode.SCAN_PRIVATE_KEY);
            break;
        case R.id.enterImportKey:
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
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                date = new Calendar.Builder().setDate(picker.getYear(), picker.getMonth(), picker.getDayOfMonth())
                  .setTimeOfDay(0, 0, 0).build();
            }
            else
            {
                date = Calendar.getInstance();
                date.set(picker.getYear(), picker.getMonth(), picker.getDayOfMonth(), 0, 0, 0);
            }

            mRecoverDate = date.getTimeInMillis() / 1000L;
            displayEnterImportKey();
            break;
        }
        case R.id.loadKey: // Import BIP-0032 encoded key
        {
            mAuthorizedTask = AuthorizedTask.ADD_KEY;
            mKeyToLoad = ((EditText)findViewById(R.id.importText)).getText().toString();
            mDerivationPathMethodToLoad = ((Spinner)findViewById(R.id.derivationMethodSpinner)).getSelectedItemPosition();
            mEncodedPrivateKey = null;
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
        case R.id.specifyReceiveAmount:
        case R.id.modifyReceiveAmount:
            if(mPaymentRequest.amount == 0)
                displayTextDialog(TextEntryMode.RECEIVE_AMOUNT, null);
            else
                displayTextDialog(TextEntryMode.RECEIVE_AMOUNT, mBitcoin.amountText(mPaymentRequest.amount));
            break;
        case R.id.specifyReceiveLabel:
        case R.id.modifyReceiveLabel:
            displayTextDialog(TextEntryMode.RECEIVE_LABEL, mPaymentRequest.label);
            break;
        case R.id.specifyReceiveMessage:
        case R.id.modifyReceiveMessage:
            displayTextDialog(TextEntryMode.RECEIVE_MESSAGE, mPaymentRequest.message);
            break;
        case R.id.modifyTransactionComment:
        case R.id.addTransactionComment:
            displayTextDialog(TextEntryMode.TRANSACTION_COMMENT, mTransactionToModify.data.comment);
            break;
        case R.id.modifyTransactionCostBasis:
        {
            if(mTransactionToModify.data.cost == 0.0)
                displayTextDialog(TextEntryMode.TRANSACTION_COST_AMOUNT,
                  mBitcoin.amountText(mTransactionToModify.amount()));
            else
                displayTextDialog(TextEntryMode.TRANSACTION_COST_AMOUNT,
                  Bitcoin.formatAmount(mTransactionToModify.data.cost, mTransactionToModify.data.costType));
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
                    break;
                }
            }

            mAuthorizedTask = AuthorizedTask.SIGN_TRANSACTION;
            displayAuthorize();
            break;
        }
        case R.id.seedWordButton:
        {
            ViewGroup wordRow = (ViewGroup)pView.getParent();
            if(wordRow == null)
                break;

            // Add word to seed
            TextView seed = findViewById(R.id.seed);
            if(seed.getText().length() > 0)
                seed.setText(String.format("%s %s", seed.getText(), ((TextButton)pView).getText()));
            else
                seed.setText(((TextButton)pView).getText());

            wordRow.removeView(pView);

            if(mMode == Mode.RECOVER_WALLET)
            {
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
                ViewGroup words = (ViewGroup)wordRow.getParent();
                int wordCount = 0;
                for(int i = 0; i < words.getChildCount(); i++)
                    wordCount += ((ViewGroup)words.getChildAt(i)).getChildCount();
                if(wordCount == 0)
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
                            mEncodedPrivateKey = null;
                            mSeedIsBackedUp = true;
                            mRecoverDate = System.currentTimeMillis() / 1000L;
                            displayAuthorize();
                        }
                    }
                    else
                    {
                        showMessage(getString(R.string.seed_doesnt_match), 2000);
                        if(mSeedBackupOnly)
                            displayWallets();
                        else
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
                    LinearLayout wordRows = findViewById(R.id.seedRows);
                    LinearLayout wordRow;
                    TextButton wordButton;

                    for(int i = wordRows.getChildCount() - 1; i >= 0; i--)
                    {
                        wordRow = (LinearLayout)wordRows.getChildAt(i);
                        if(wordRow.getChildCount() < 4)
                        {
                            wordButton = (TextButton)inflater.inflate(R.layout.seed_word_button, wordRow,
                              false);
                            wordButton.setText(words[words.length - 1]);
                            wordRow.addView(wordButton);
                            break;
                        }
                    }
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
            Wallet wallet = mBitcoin.wallet(mCurrentWalletIndex);
            if(!wallet.isPrivate)
                showMessage(getString(R.string.wallet_view_only), 2000);
            else if(wallet.isSynchronized && mBitcoin.chainIsLoaded() && mBitcoin.initialBlockDownloadIsComplete() &&
              mBitcoin.isInRoughSync())
            {
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

                displayScanner(ScanMode.SCAN_PAYMENT_CODE);
            }
            else
                showMessage(getString(R.string.still_syncing), 2000);
            break;
        }
        case R.id.walletBackup:
        {
            ViewGroup walletView = (ViewGroup)pView.getParent().getParent().getParent();
            mCurrentWalletIndex = (int)walletView.getTag();
            mSeedBackupOnly = true;
            mAuthorizedTask = AuthorizedTask.BACKUP_KEY;
            displayAuthorize();
            break;
        }
        case R.id.scanPaymentCode:
            displayScanner(ScanMode.SCAN_PAYMENT_CODE);
            break;
        case R.id.enterPaymentCode:
            mScanner.close();
            displayEnterPaymentCode();
            break;
        case R.id.useClipBoardPaymentCode:
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            {
                ClipboardManager manager = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                if(manager != null)
                    manager.clearPrimaryClip();
            }
            displayPaymentDetails();
            break;
        case R.id.walletReceive:
        {
            mPreviousReceiveMode = mMode;
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
            TransactionData.ID id = (TransactionData.ID)pView.getTag();
            openTransaction(mCurrentWalletIndex, id.hash, id.amount);
            break;
        }
        case R.id.qrImage:
        case R.id.paymentCode:
        {
            ClipboardManager manager = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(manager != null)
            {
                ClipData clip = ClipData.newPlainText("Bitcoin Cash Payment Code", mPaymentRequest.uri);
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
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                Intent settingsIntent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                //.putExtra(Settings.EXTRA_CHANNEL_ID, MY_CHANNEL_ID);
                startActivity(settingsIntent);
            }
            break;
        case R.id.addAddressLabel:
        {
            AddressLabel.Item item = mBitcoin.lookupAddressLabel(mPaymentRequest.address);
            String defaultText = null;
            if(item != null)
                defaultText = item.label;
            displayTextDialog(TextEntryMode.LABEL_ADDRESS, defaultText);
            break;
        }
        case R.id.openAddressLabels:
            displayAddressLabels();
            break;
        case R.id.addressLabelItem:
        {
            TextView address = pView.findViewById(R.id.addressLabelAddress);
            if(address != null)
            {
                AddressLabel.Item item = mBitcoin.lookupAddressLabel(address.getText().toString());
                if(item != null)
                {
                    mPreviousReceiveMode = mMode;
                    displayProgress();

                    mPaymentRequest = new PaymentRequest();
                    mPaymentRequest.address = item.address;
                    mPaymentRequest.amount = item.amount;
                    if(mQRCode == null)
                        mQRCode = Bitmap.createBitmap(Bitcoin.QR_WIDTH, Bitcoin.QR_WIDTH, Bitmap.Config.ARGB_8888);
                    CreatePaymentRequestTask task = new CreatePaymentRequestTask(getApplicationContext(), mBitcoin,
                      mPaymentRequest, mQRCode);
                    task.execute();
                }
            }
            break;
        }
        case R.id.addManualAddressLabel:
        {
            EditText addressView = findViewById(R.id.manualAddress);
            EditText labelView = findViewById(R.id.manualAddressLabel);
            if(addressView != null && labelView != null)
            {
                AddressLabel.Item item = new AddressLabel.Item();
                item.address = addressView.getText().toString();
                item.label = labelView.getText().toString();

                if(item.address.length() == 0 || item.label.length() == 0)
                {
                    showMessage(getString(R.string.must_specify_address_label), 2000);
                    break;
                }

                PaymentRequest request = mBitcoin.decodePaymentCode(item.address);
                if(request.format == PaymentRequest.FORMAT_INVALID || request.type == PaymentRequest.TYPE_NONE)
                {
                    showMessage(getString(R.string.failed_invalid_address), 2000);
                    break;
                }

                item.address = request.address;
                if(!mBitcoin.containsAddress(mCurrentWalletIndex, item.address))
                {
                    showMessage(getString(R.string.address_not_found), 2000);
                    break;
                }

                mBitcoin.addAddressLabel(item, mCurrentWalletIndex);
                mBitcoin.saveAddressLabels();
                displayAddressLabels();
            }
            break;
        }
        case R.id.saveAddress:
        {
            // Ask for name for address book entry.
            TextView addressView = findViewById(R.id.address);
            String defaultText = null;
            if(addressView != null)
            {
                AddressBook.Item item = mBitcoin.lookupAddress(addressView.getText().toString());
                if(item != null)
                    defaultText = item.name;
            }
            displayTextDialog(TextEntryMode.SAVE_ADDRESS, defaultText);
            break;
        }
        case R.id.openAddressBook:
            displayAddressBook();
            break;
        case R.id.addressBookItem:
        {
            // Open payment view for specified address
            TextView nameView = pView.findViewById(R.id.addressBookName);
            TextView addressView = pView.findViewById(R.id.addressBookAddress);
            if(nameView != null && addressView != null)
            {
                    mPreviousReceiveMode = mMode;
                    mPaymentRequest = mBitcoin.decodePaymentCode(addressView.getText().toString());
                    mPaymentRequest.label = nameView.getText().toString();
                    mPaymentRequest.encode();
                    displayPaymentDetails();
            }
            break;
        }
        case R.id.addManualAddressBook:
        {
            EditText addressView = findViewById(R.id.manualAddressBookAddress);
            EditText nameView = findViewById(R.id.manualAddressBookName);
            if(addressView != null && nameView != null)
            {
                String address = addressView.getText().toString();
                String name = nameView.getText().toString();

                if(address.length() == 0 || name.length() == 0)
                {
                    showMessage(getString(R.string.must_specify_address_name), 2000);
                    break;
                }

                mBitcoin.addAddress(address, name);
                mBitcoin.saveAddressBook();
                displayAddressBook();
            }
            break;
        }
        case R.id.undoDelete:
            if(mUndoDeleteRunnable != null)
            {
                mUndoDeleteRunnable.run();
                mUndoDeleteRunnable = null;
                mDelayHandler.removeCallbacks(mConfirmDeleteRunnable);
                mConfirmDeleteRunnable = null;
            }
            break;
        case R.id.textDialogOkay:
        {
            String enteredText;
            if(mTextEntryMode == TextEntryMode.RECEIVE_AMOUNT)
            {
                EditText textView = findViewById(R.id.enteredAmount);
                if(textView == null)
                {
                    mTextEntryMode = TextEntryMode.NONE;
                    break;
                }

                enteredText = textView.getText().toString();
            }
            else
            {
                EditText textView = findViewById(R.id.enteredText);
                if(textView == null)
                {
                    mTextEntryMode = TextEntryMode.NONE;
                    break;
                }

                enteredText = textView.getText().toString();
            }

            switch(mTextEntryMode)
            {
            case SAVE_ADDRESS:
            {
                if(enteredText.length() == 0)
                {
                    showMessage(getString(R.string.must_enter_name), 2000);
                    break;
                }
                enteredText = enteredText.trim();

                TextView addressView = findViewById(R.id.address);
                if(addressView != null)
                {
                    String address = addressView.getText().toString();
                    if(address.length() != 0)
                    {
                        mBitcoin.addAddress(address, enteredText);
                        mBitcoin.saveAddressBook();
                        showMessage(getString(R.string.address_saved), 2000);
                    }
                }

                break;
            }
            case LABEL_ADDRESS:
            {
                if(enteredText.length() == 0)
                {
                    showMessage(getString(R.string.must_enter_name), 2000);
                    mTextEntryMode = TextEntryMode.NONE;
                    break;
                }
                enteredText = enteredText.trim();

                if(mPaymentRequest != null && mPaymentRequest.address != null &&
                  mPaymentRequest.address.length() > 0)
                {
                    AddressLabel.Item item = new AddressLabel.Item();
                    item.address = mPaymentRequest.address;
                    item.label = enteredText;

                    if(!mBitcoin.containsAddress(mCurrentWalletIndex, item.address))
                    {
                        showMessage(getString(R.string.address_not_found), 2000);
                        mTextEntryMode = TextEntryMode.NONE;
                        break;
                    }

                    mBitcoin.addAddressLabel(item, mCurrentWalletIndex);
                    mBitcoin.saveAddressLabels();
                    showMessage(getString(R.string.address_label_updated), 2000);
                    displayReceive(); // Refresh address title
                }

                break;
            }
            case RECEIVE_AMOUNT:
            {
                double enteredAmount;
                try
                {
                    enteredAmount = Bitcoin.parseAmount(enteredText);
                }
                catch(Exception pException)
                {
                    enteredAmount = 0.0;
                }

                if(enteredAmount == 0.0)
                    mPaymentRequest.setAmount(0);
                else
                {
                    Spinner units = findViewById(R.id.enterAmountUnits);
                    switch(units.getSelectedItemPosition())
                    {
                        default:
                        case Bitcoin.FIAT:
                            mPaymentRequest.setAmount(mBitcoin.satoshisFromFiat(enteredAmount));
                            break;
                        case Bitcoin.BITS:
                            mPaymentRequest.setAmount(Bitcoin.satoshisFromBits(enteredAmount));
                            break;
                        case Bitcoin.BITCOINS:
                            mPaymentRequest.setAmount(Bitcoin.satoshisFromBitcoins(enteredAmount));
                            break;
                    }
                }

                if(mQRCode == null)
                    mQRCode = Bitmap.createBitmap(Bitcoin.QR_WIDTH, Bitcoin.QR_WIDTH, Bitmap.Config.ARGB_8888);
                CreatePaymentRequestTask amountTask = new CreatePaymentRequestTask(getApplicationContext(), mBitcoin,
                  mPaymentRequest, mQRCode);
                amountTask.execute();
                break;
            }
            case RECEIVE_LABEL:
                enteredText = enteredText.trim();
                mPaymentRequest.setLabel(enteredText);
                if(mQRCode == null)
                    mQRCode = Bitmap.createBitmap(Bitcoin.QR_WIDTH, Bitcoin.QR_WIDTH, Bitmap.Config.ARGB_8888);
                CreatePaymentRequestTask labelTask = new CreatePaymentRequestTask(getApplicationContext(), mBitcoin,
                  mPaymentRequest, mQRCode);
                labelTask.execute();
                break;
            case RECEIVE_MESSAGE:
                enteredText = enteredText.trim();
                mPaymentRequest.setMessage(enteredText);
                if(mQRCode == null)
                    mQRCode = Bitmap.createBitmap(Bitcoin.QR_WIDTH, Bitcoin.QR_WIDTH, Bitmap.Config.ARGB_8888);
                CreatePaymentRequestTask messageTask = new CreatePaymentRequestTask(getApplicationContext(), mBitcoin,
                  mPaymentRequest, mQRCode);
                messageTask.execute();
                break;
            case TRANSACTION_COMMENT:
                enteredText = enteredText.trim();
                if(enteredText.length() == 0)
                    mTransactionToModify.data.comment = null;
                else
                    mTransactionToModify.data.comment = enteredText;
                mBitcoin.saveTransactionData();
                mBitcoin.triggerUpdate();
                displayTransaction();
                break;
            case TRANSACTION_COST_AMOUNT:
                double enteredAmount;
                try
                {
                    enteredAmount = Bitcoin.parseAmount(enteredText);
                }
                catch(Exception pException)
                {
                    showMessage(getString(R.string.cost_must_be_number), 2000);
                    break;
                }

                if(enteredAmount == 0.0)
                {
                    mTransactionToModify.data.cost = 0.0;
                    mTransactionToModify.data.costType = null;
                }
                else
                {
                    mTransactionToModify.data.cost = enteredAmount;
                    mTransactionToModify.data.costType = mBitcoin.exchangeType();
                }
                break;
            case TRANSACTION_COST_DATE:
                break;
            }

            if(mTextEntryMode == TextEntryMode.TRANSACTION_COST_AMOUNT)
            {
                if(mTransaction.data.costDate == 0L)
                    displayDateDialog(TextEntryMode.TRANSACTION_COST_DATE, mTransactionToModify.data.date);
                else
                    displayDateDialog(TextEntryMode.TRANSACTION_COST_DATE, mTransactionToModify.data.costDate);
            }
            else
                mTextEntryMode = TextEntryMode.NONE;

            findViewById(R.id.amountDialog).setVisibility(View.GONE);
            findViewById(R.id.textDialog).setVisibility(View.GONE);
            break;
        }
        case R.id.dateDialogOkay:
        {
            if(mTextEntryMode != TextEntryMode.TRANSACTION_COST_DATE)
            {
                findViewById(R.id.dateDialog).setVisibility(View.GONE);
                mTextEntryMode = TextEntryMode.NONE;
                break;
            }

            DatePicker picker = findViewById(R.id.enteredDate);
            if(picker == null)
            {
                showMessage(getString(R.string.failed_to_get_date), 2000);
                findViewById(R.id.dateDialog).setVisibility(View.GONE);
                mTextEntryMode = TextEntryMode.NONE;
                break;
            }

            Calendar date;
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            {
                date = new Calendar.Builder().setDate(picker.getYear(), picker.getMonth(), picker.getDayOfMonth())
                  .setTimeOfDay(0, 0, 0).build();
            }
            else
            {
                date = Calendar.getInstance();
                date.set(picker.getYear(), picker.getMonth(), picker.getDayOfMonth());
            }

            mTransactionToModify.data.costDate = date.getTimeInMillis() / 1000L;
            mBitcoin.saveTransactionData();
            mBitcoin.triggerUpdate();
            showMessage(getString(R.string.cost_basis_updated), 2000);

            findViewById(R.id.dateDialog).setVisibility(View.GONE);
            mTextEntryMode = TextEntryMode.NONE;
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

            findViewById(R.id.pinEntry).setVisibility(View.GONE);
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
                    else if(mEncodedPrivateKey != null)
                    {
                        ImportEncodedKeyTask task = new ImportEncodedKeyTask(getApplicationContext(), mBitcoin, pin,
                          mEncodedPrivateKey, mRecoverDate);
                        task.execute();
                        mEncodedPrivateKey = null;
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
                            break;
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
            return false;
        }

        return true;
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
        messageView.findViewById(R.id.closeMessage).setOnTouchListener(mButtonTouchListener);
        headerView.addView(messageView, 0);
    }

    @Override
    public void onBackPressed()
    {
        if(mTextEntryMode != TextEntryMode.NONE)
        {
            findViewById(R.id.amountDialog).setVisibility(View.GONE);
            findViewById(R.id.textDialog).setVisibility(View.GONE);
            findViewById(R.id.dateDialog).setVisibility(View.GONE);
            mTextEntryMode = TextEntryMode.NONE;
            return;
        }

        if(mAddressBookAdapter != null || mAddressLabelAdapter != null)
        {
            if(mConfirmDeleteRunnable != null)
            {
                mDelayHandler.removeCallbacks(mConfirmDeleteRunnable);
                mConfirmDeleteRunnable.run();
                mConfirmDeleteRunnable = null;
                mUndoDeleteRunnable = null;
            }

            mAddressBookAdapter = null;
            mAddressLabelAdapter = null;
        }

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
            mEncodedPrivateKey = null;
            mRecoverDate = 0L;
            mSeedEntropyBytes = 0;
            break;
        case CREATE_WALLET:
        case RECOVER_WALLET:
        case IMPORT_WALLET:
        case IMPORT_PRIVATE_KEY:
            mSeed = null;
            mKeyToLoad = null;
            mEncodedPrivateKey = null;
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
        case TRANSACTION_HISTORY:
            break;
        case TRANSACTION:
            if(mPreviousTransactionMode == Mode.TRANSACTION_HISTORY)
            {
                displayWalletHistory();
                return;
            }
            break;
        case SCAN:
            break;
        case RECEIVE:
            if(mPreviousReceiveMode == Mode.ADDRESS_LABELS)
            {
                mPaymentRequest = null;
                displayAddressLabels();
                return;
            }
            break;
        case ADDRESS_LABELS:
            break;
        case ADDRESS_BOOK:
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
                mEncodedPrivateKey = null;
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

            findViewById(R.id.pinEntry).setVisibility(View.GONE);
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
