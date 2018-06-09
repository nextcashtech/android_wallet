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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.zxing.integration.android.IntentIntegrator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;


public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener, TextWatcher
{
    public static final String logTag = "MainActivity";
    public static final int SETTINGS_REQUEST_CODE = 20;
    public static final int SCAN_REQUEST_CODE = 30;

    private Handler mDelayHandler;
    private Runnable mStatusUpdateRunnable, mRateUpdateRunnable, mClearFinishOnBack, mClearNotification;
    private enum Mode { LOADING, IN_PROGRESS, WALLETS, ADD_WALLET, CREATE_WALLET, VERIFY_SEED, BACKUP_WALLET,
      RECOVER_WALLET, EDIT_WALLET, HISTORY, TRANSACTION, RECEIVE, SEND, SETUP, AUTHORIZE }
    private Mode mMode;
    private enum AuthorizedTask { NONE, ADD_KEY, BACKUP_KEY, REMOVE_KEY, SIGN_TRANSACTION }
    private AuthorizedTask mAuthorizedTask;
    private String mKeyToLoad, mSeed;
    private boolean mSeedIsRecovered;
    private int mCurrentWalletViewID;
    private boolean mSeedBackupOnly;
    private int mDerivationPathMethodToLoad;
    private double mFiatRate;
    private Bitcoin mBitcoin;
    private boolean mFinishOnBack;
    private BitcoinService.CallBacks mServiceCallBacks;
    private BitcoinService mService;
    private boolean mServiceIsBound;
    private ServiceConnection mServiceConnection;
    private IntentIntegrator mQRScanner;
    private long mPaymentAmount;
    private PaymentRequest mPaymentRequest;


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
        mMode = Mode.LOADING;
        mAuthorizedTask = AuthorizedTask.NONE;
        mFinishOnBack = false;
        mFiatRate = 0.0;

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
        mService = null;
        mServiceConnection = new ServiceConnection()
        {
            @Override
            public void onServiceConnected(ComponentName pComponentName, IBinder pBinder)
            {
                Log.d(logTag, "Bitcoin service connected");
                mService = ((BitcoinService.LocalBinder)pBinder).getService();
                mService.setCallBacks(mServiceCallBacks);
            }

            @Override
            public void onServiceDisconnected(ComponentName pComponentName)
            {
                Log.d(logTag, "Bitcoin service disconnected");
                mService.removeCallBacks(mServiceCallBacks);
                mService = null;
            }
        };

        mQRScanner = new IntentIntegrator(this);
        //mQRScanner.addExtra(); // TODO Find extra value that can switch to portrait mode

        scheduleJobs();
    }

    private void startBitcoinService()
    {
        Intent intent = new Intent(this, BitcoinService.class);
        intent.putExtra("FinishMode", Bitcoin.FINISH_ON_REQUEST);
        startService(intent);

        if(!mServiceIsBound)
        {
            Log.d(logTag, "Binding Bitcoin service");
            bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
            mServiceIsBound = true;
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
            openTransaction(extras.getInt("Wallet"), extras.getString("Transaction"));

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
        updateStatus();
        super.onResume();
    }

    @Override
    public void onPause()
    {
        mDelayHandler.removeCallbacks(mStatusUpdateRunnable);
        mDelayHandler.removeCallbacks(mRateUpdateRunnable);
        super.onPause();
    }

    @Override
    public void onStop()
    {
        mBitcoin.setFinishMode(Bitcoin.FINISH_ON_SYNC);
        if(mServiceIsBound)
        {
            Log.d(logTag, "Unbinding Bitcoin service");
            unbindService(mServiceConnection);
            mServiceIsBound = false;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
    }

    private void startUpdateRates()
    {
        new FiatRateRequestTask(getFilesDir()).execute();

        // Run again in 60 seconds
        mDelayHandler.postDelayed(mRateUpdateRunnable, 60000);
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
                if(mMode != Mode.LOADING)
                {
                    ((ViewGroup)findViewById(R.id.content)).removeAllViews();
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
                ViewGroup content = findViewById(R.id.content);
                content.removeAllViews();
                findViewById(R.id.progress).setVisibility(View.VISIBLE);
                mMode = Mode.LOADING;
            }
        }

        if(exchangeRateUpdated && mMode == Mode.WALLETS)
            updateWallets();

        // Run again in 2 seconds
        mDelayHandler.postDelayed(mStatusUpdateRunnable, 2000);
    }

    public void clear()
    {
        ((ViewGroup)findViewById(R.id.content)).removeAllViews();
        findViewById(R.id.progress).setVisibility(View.VISIBLE);
        mMode = Mode.IN_PROGRESS;
    }

    public synchronized void updateWallets()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup content = findViewById(R.id.content), transactions;
        View view;
        boolean addButtonNeeded = false, addView, rebuild;

        synchronized(mBitcoin)
        {
            for(Wallet wallet : mBitcoin.wallets)
                if(wallet.viewID == 0)
                    rebuild = true;

            mFiatRate = Settings.getInstance(getFilesDir()).doubleValue("usd_rate");

            findViewById(R.id.progress).setVisibility(View.GONE);

            rebuild = mMode != Mode.WALLETS;

            if(rebuild)
            {
                Log.i(logTag, String.format("Rebuilding %d wallets", mBitcoin.wallets.length));

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
                Log.i(logTag, String.format("Updating %d wallets", mBitcoin.wallets.length));

                for(Wallet wallet : mBitcoin.wallets)
                {
                    addView = false;
                    view = null;

                    if(!rebuild && wallet.viewID != 0)
                        view = content.findViewById(wallet.viewID);

                    if(view == null)
                    {
                        // Remove add wallet button
                        view = content.findViewWithTag(R.id.addWallet);
                        if(view != null)
                            content.removeView(view);
                        addButtonNeeded = true;

                        // Add wallet
                        view = inflater.inflate(R.layout.wallet_item, content, false);
                        wallet.viewID = View.generateViewId();
                        view.setId(wallet.viewID);
                        addView = true;

                        view.findViewById(R.id.walletDetails).setVisibility(View.GONE);
                    }

                    ((TextView)view.findViewById(R.id.walletBalance)).setText(Bitcoin.amountText(wallet.balance,
                      mFiatRate));

                    if(mFiatRate != 0.0)
                    {
                        ((TextView)view.findViewById(R.id.walletBitcoinBalance)).setText(String.format(Locale.getDefault(),
                          "%,.5f BCH", Bitcoin.bitcoins(wallet.balance)));
                    }

                    ((TextView)view.findViewById(R.id.walletName)).setText(wallet.name);

                    if(!wallet.isPrivate)
                    {
                        view.findViewById(R.id.walletLocked).setVisibility(View.VISIBLE);
                        view.findViewById(R.id.walletScan).setVisibility(View.GONE);
                        view.findViewById(R.id.walletSend).setVisibility(View.GONE);
                        view.findViewById(R.id.walletLockedMessage).setVisibility(View.VISIBLE);
                    }

                    transactions = view.findViewById(R.id.walletTransactions);
                    populateTransactions(transactions, wallet.transactions, 3);

                    if(addView)
                        content.addView(view);

                    alignTransactions(transactions);
                }
            }
        }

        if(addButtonNeeded)
        {
            view = inflater.inflate(R.layout.button, content, false);
            view.setTag(R.id.addWallet);
            ((TextView)view.findViewById(R.id.text)).setText(R.string.add_wallet);
            content.addView(view);
        }

        findViewById(R.id.statusBar).setVisibility(View.VISIBLE);

        mMode = Mode.WALLETS;
    }

    public void focusOnText(int pTextID)
    {
        EditText text = findViewById(pTextID);
        if(text.requestFocus())
        {
            InputMethodManager inputManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if(inputManager != null)
            {
                inputManager.showSoftInput(text, InputMethodManager.SHOW_IMPLICIT);
                inputManager.updateSelection(text, 0, text.getText().length(), 0, 0);
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
            case SCAN_REQUEST_CODE:
            case IntentIntegrator.REQUEST_CODE:
                if(pResultCode == RESULT_OK && pData != null)
                    displaySendPayment(pData.getStringExtra("SCAN_RESULT"));
                break;
            default:
                super.onActivityResult(pRequestCode, pResultCode, pData);
                break;
        }
    }

    public void displaySendPayment(String pPaymentCode)
    {
        Log.i(logTag, String.format("Displaying Payment Code : %s", pPaymentCode));

        mPaymentRequest = mBitcoin.decodePaymentCode(pPaymentCode);

        mFiatRate = Settings.getInstance(getFilesDir()).doubleValue("usd_rate");

        if(mPaymentRequest.format == PaymentRequest.FORMAT_INVALID ||
          mPaymentRequest.protocol == PaymentRequest.PROTOCOL_NONE)
        {
            showMessage(getString(R.string.invalid_payment_code), 2000);
            updateWallets();
        }
        else
        {
            synchronized(this)
            {
                LayoutInflater inflater = getLayoutInflater();
                ViewGroup contentView = findViewById(R.id.content);

                contentView.removeAllViews();
                findViewById(R.id.statusBar).setVisibility(View.GONE);

                findViewById(R.id.progress).setVisibility(View.GONE);

                ActionBar actionBar = getSupportActionBar();
                if(actionBar != null)
                {
                    actionBar.setIcon(R.drawable.ic_send_black_36dp);
                    actionBar.setTitle(" " + getResources().getString(R.string.send_payment));
                    actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
                }

                ViewGroup sendView = (ViewGroup)inflater.inflate(R.layout.send_payment, contentView, false);

                // Display payment details
                String formatText;
                switch(mPaymentRequest.format)
                {
                default:
                case PaymentRequest.FORMAT_LEGACY:
                    formatText = getString(R.string.legacy);
                    findViewById(R.id.legacyWarning).setVisibility(View.VISIBLE);
                    break;
                case PaymentRequest.FORMAT_CASH:
                    formatText = getString(R.string.cash);
                    break;
                }

                String protocolText;
                switch(mPaymentRequest.protocol)
                {
                default:
                case PaymentRequest.PROTOCOL_ADDRESS:
                    protocolText = getString(R.string.address);
                    break;
                case PaymentRequest.PROTOCOL_REQUEST_AMOUNT:
                    protocolText = getString(R.string.amount_request);
                    break;
                }

                String title;
                if(mPaymentRequest.secure)
                    title = String.format("%s %s %s", getString(R.string.secure), formatText, protocolText);
                else
                    title = String.format("%s %s", formatText, protocolText);

                ((TextView)sendView.findViewById(R.id.title)).setText(title);

                EditText amount = sendView.findViewById(R.id.amount);
                if(mPaymentRequest.amount != 0)
                    amount.setText(Bitcoin.amountText(mPaymentRequest.amount, mFiatRate));

                Spinner units = sendView.findViewById(R.id.units);
                ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.amount_units,
                  android.R.layout.simple_spinner_item);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                units.setAdapter(adapter);
                units.setOnItemSelectedListener(this);

                if(mPaymentRequest.protocol == PaymentRequest.PROTOCOL_REQUEST_AMOUNT)
                {
                    // Make amount not modifiable
                    amount.setInputType(InputType.TYPE_NULL);
                    amount.setEnabled(false);
                    amount.setFocusable(false);
                    units.setEnabled(false);
                }

                // TODO Update amount conversion text
                TextView amountConverted = sendView.findViewById(R.id.amountConverted);

                ((TextView)sendView.findViewById(R.id.paymentCode)).setText(mPaymentRequest.code.toLowerCase());

                // Description
                if(mPaymentRequest.description != null && mPaymentRequest.description.length() > 0)
                {


                }
                else
                {
                    sendView.findViewById(R.id.descriptionTitle).setVisibility(View.GONE);
                    sendView.findViewById(R.id.description).setVisibility(View.GONE);
                }

                contentView.addView(sendView);

                // Verify button
                View button = inflater.inflate(R.layout.button, contentView, false);
                button.setTag(R.id.sendPayment);
                ((TextView)button.findViewById(R.id.text)).setText(R.string.continue_string);
                contentView.addView(button);

                if(mPaymentRequest.protocol != PaymentRequest.PROTOCOL_REQUEST_AMOUNT)
                {
                    focusOnText(R.id.amount);
                    amount.selectAll();
                }

                mMode = Mode.SEND;
            }
        }
    }

    public void openTransaction(int pWalletOffset, String pTransactionHash)
    {
        ViewGroup contentView = findViewById(R.id.content);
        contentView.removeAllViews();
        findViewById(R.id.progress).setVisibility(View.VISIBLE);

        GetTransactionTask task = new GetTransactionTask(this, mBitcoin, pWalletOffset, pTransactionHash);
        task.execute();
    }

    public void displayTransaction(FullTransaction pTransaction)
    {
        synchronized(this)
        {
            LayoutInflater inflater = getLayoutInflater();
            ViewGroup contentView = findViewById(R.id.content);

            contentView.removeAllViews();
            findViewById(R.id.statusBar).setVisibility(View.GONE);

            findViewById(R.id.progress).setVisibility(View.GONE);

            ActionBar actionBar = getSupportActionBar();
            if(actionBar != null)
            {
                actionBar.setIcon(null);
                actionBar.setTitle(" " + getResources().getString(R.string.transaction));
                actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
            }

            ViewGroup transactionView = (ViewGroup)inflater.inflate(R.layout.transaction, contentView, false);

            ((TextView)transactionView.findViewById(R.id.id)).setText(pTransaction.hash);
            ((TextView)transactionView.findViewById(R.id.lockTime)).setText(pTransaction.lockTimeString(this));
            ((TextView)transactionView.findViewById(R.id.version)).setText(String.format(Locale.getDefault(), "%d",
              pTransaction.version));

            long amount = pTransaction.amount();
            TextView amountText = transactionView.findViewById(R.id.amount);
            TextView bitcoinsAmountText = transactionView.findViewById(R.id.bitcoinsAmount);
            amountText.setText(Bitcoin.amountText(amount, mFiatRate));
            bitcoinsAmountText.setText(Bitcoin.satoshiText(amount));
            if(amount > 0)
            {
                amountText.setTextColor(getResources().getColor(R.color.colorPositive));
                bitcoinsAmountText.setTextColor(getResources().getColor(R.color.colorPositive));
            }
            else
            {
                amountText.setTextColor(getResources().getColor(R.color.colorNegative));
                bitcoinsAmountText.setTextColor(getResources().getColor(R.color.colorNegative));
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

                if(input.amount != 0)
                {
                    inputView.setBackgroundColor(getResources().getColor(R.color.highlight));
                    TextView inputAmountText = inputView.findViewById(R.id.amount);
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

                TextView outputAmountText = outputView.findViewById(R.id.amount);
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

            contentView.addView(transactionView);

            mMode = Mode.TRANSACTION;
        }
    }

    public void displayEnterPaymentCode()
    {
        synchronized(this)
        {
            LayoutInflater inflater = getLayoutInflater();
            ViewGroup contentView = findViewById(R.id.content);

            contentView.removeAllViews();
            findViewById(R.id.statusBar).setVisibility(View.GONE);

            findViewById(R.id.progress).setVisibility(View.GONE);

            ActionBar actionBar = getSupportActionBar();
            if(actionBar != null)
            {
                actionBar.setIcon(R.drawable.ic_send_black_36dp);
                actionBar.setTitle(" " + getResources().getString(R.string.send_payment));
                actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
            }

            ViewGroup paymentCodeView = (ViewGroup)inflater.inflate(R.layout.enter_payment_code, contentView,
              false);
            contentView.addView(paymentCodeView);

            // Continue button
            View continueButton = inflater.inflate(R.layout.button, contentView, false);
            continueButton.setTag(R.id.enterPaymentDetails);
            ((TextView)continueButton.findViewById(R.id.text)).setText(R.string.continue_string);
            contentView.addView(continueButton);

            // Scan button
            View scanButton = inflater.inflate(R.layout.button, contentView, false);
            scanButton.setTag(R.id.openScanner);
            ImageView scanIcon = scanButton.findViewById(R.id.image);
            scanIcon.setImageResource(R.drawable.ic_scan_white_36dp);
            scanIcon.setVisibility(View.VISIBLE);
            ((TextView)scanButton.findViewById(R.id.text)).setText(R.string.scan);
            contentView.addView(scanButton);

            focusOnText(R.id.paymentCode);

            mMode = Mode.SEND;
        }
    }

    public void displayAddress(String pText, Bitmap pQRCode)
    {
        if(pText == null || pQRCode == null)
            updateWallets();
        else
        {
            synchronized(this)
            {
                LayoutInflater inflater = getLayoutInflater();
                ViewGroup contentView = findViewById(R.id.content);

                contentView.removeAllViews();

                findViewById(R.id.progress).setVisibility(View.GONE);
                findViewById(R.id.statusBar).setVisibility(View.GONE);

                ViewGroup receiveView = (ViewGroup)inflater.inflate(R.layout.receive, contentView, false);
                ((ImageView)receiveView.findViewById(R.id.addressImage)).setImageBitmap(pQRCode);
                ((TextView)receiveView.findViewById(R.id.addressText)).setText(pText);
                contentView.addView(receiveView);

                ActionBar actionBar = getSupportActionBar();
                if(actionBar != null)
                {
                    actionBar.setIcon(R.drawable.ic_add_circle_black_36dp);
                    actionBar.setTitle(" " + getResources().getString(R.string.receive));
                    actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
                }

                mMode = Mode.RECEIVE;
            }
        }
    }

    public synchronized void displayCreateWallet()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup contentView = findViewById(R.id.content);

        contentView.removeAllViews();
        findViewById(R.id.statusBar).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_add_circle_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.create_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup createWallet = (ViewGroup)inflater.inflate(R.layout.view_seed, contentView, false);
        mSeed = mBitcoin.generateMnemonicSeed(28);
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

        contentView.addView(createWallet);

        // Verify button
        View button = inflater.inflate(R.layout.button, contentView, false);
        button.setTag(R.id.verifySeedSaved);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.continue_string);
        contentView.addView(button);

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
                ((TextView)findViewById(R.id.seed)).setText(mSeed);
            }
        }
        else if(pParent.getId() == R.id.units)
        {
            // TODO Handle changing units
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent)
    {

    }

    public synchronized void displayVerifySeed()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup contentView = findViewById(R.id.content);

        contentView.removeAllViews();
        findViewById(R.id.statusBar).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_add_circle_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.create_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup verifySeed = (ViewGroup)inflater.inflate(R.layout.verify_seed, contentView, false);
        LinearLayout wordButtons = verifySeed.findViewById(R.id.seedButtons);
        ArrayList<String> words = new ArrayList<>();
        TextView wordButton;

        contentView.addView(verifySeed);

        Collections.addAll(words, mSeed.split(" "));
        Collections.shuffle(words);

        for(String word : words)
        {
            wordButton = (TextView)inflater.inflate(R.layout.seed_word_button, wordButtons, false);
            wordButton.setText(word);
            wordButtons.addView(wordButton);
        }

        // Skip button
        View button = inflater.inflate(R.layout.negative_button, contentView, false);
        button.setTag(R.id.skipCheckSeed);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.skip_check_seed);
        contentView.addView(button);

        mMode = Mode.VERIFY_SEED;
    }

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

    public synchronized void displayRecoverWallet()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup contentView = findViewById(R.id.content);

        contentView.removeAllViews();
        findViewById(R.id.statusBar).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(null);
            actionBar.setTitle(" " + getResources().getString(R.string.recover_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup enterSeed = (ViewGroup)inflater.inflate(R.layout.enter_seed, contentView, false);
        ((EditText)enterSeed.findViewById(R.id.seedWordEntry)).addTextChangedListener(this);

        contentView.addView(enterSeed);

        // Done button
        View button = inflater.inflate(R.layout.button, contentView, false);
        button.setTag(R.id.importSeed);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.okay);
        contentView.addView(button);

        focusOnText(R.id.seedWordEntry);
        mSeedIsRecovered = true;

        mMode = Mode.RECOVER_WALLET;
    }

    public synchronized void displayBackupWallet(String pPassCode)
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup contentView = findViewById(R.id.content);

        contentView.removeAllViews();
        findViewById(R.id.statusBar).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_add_circle_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.backup_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup createWallet = (ViewGroup)inflater.inflate(R.layout.view_seed, contentView, false);
        mSeed = mBitcoin.seed(pPassCode, mBitcoin.walletOffsetForViewID(mCurrentWalletViewID));
        if(mSeed == null)
        {
            showMessage(getString(R.string.failed_retrieve_seed), 2000);
            updateWallets();
            return;
        }
        mSeedIsRecovered = false;
        mSeedBackupOnly = true;
        ((TextView)createWallet.findViewById(R.id.seed)).setText(mSeed);

        createWallet.findViewById(R.id.seedEntropyContainer).setVisibility(View.GONE);

        contentView.addView(createWallet);

        // Verify button
        View button = inflater.inflate(R.layout.button, contentView, false);
        button.setTag(R.id.verifySeedSaved);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.continue_string);
        contentView.addView(button);

        mMode = Mode.BACKUP_WALLET;
    }

    public synchronized void displayAuthorize()
    {
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup contentView = findViewById(R.id.content);

        contentView.removeAllViews();
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
        ViewGroup passcode = (ViewGroup)inflater.inflate(R.layout.passcode, contentView, false);
        if(!mBitcoin.hasPassCode())
            passcode.findViewById(R.id.createDescription).setVisibility(View.VISIBLE);
        contentView.addView(passcode);

        // Authorize button
        View button = inflater.inflate(R.layout.button, contentView, false);
        button.setTag(R.id.authorize);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.authorize);
        contentView.addView(button);

        focusOnText(R.id.passcode);

        mMode = Mode.AUTHORIZE;
    }

    public synchronized void displayEditWallet(int pViewID)
    {
        Wallet wallet = mBitcoin.walletForViewID(pViewID);

        if(wallet == null)
            return;

        View button;
        ViewGroup editName;
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup contentView = findViewById(R.id.content);

        contentView.removeAllViews();
        findViewById(R.id.statusBar).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_edit_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.title_edit_wallet));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        mCurrentWalletViewID = pViewID;

        // Edit Name
        editName = (ViewGroup)inflater.inflate(R.layout.edit_name, contentView, false);
        ((EditText)editName.findViewById(R.id.name)).setText(wallet.name);

        contentView.addView(editName);

        // Update button
        button = inflater.inflate(R.layout.button, contentView, false);
        button.setTag(R.id.updateWalletName);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.update_name);
        contentView.addView(button);

        // Backup button
        button = inflater.inflate(R.layout.button, contentView, false);
        button.setTag(R.id.backupWallet);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.backup_wallet);
        contentView.addView(button);

        // Remove button
        button = inflater.inflate(R.layout.button, contentView, false);
        button.setTag(R.id.removeWallet);
        ((TextView)button.findViewById(R.id.text)).setText(R.string.remove_wallet);
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

    public synchronized void displayWalletHistory(int pViewID)
    {
        Wallet wallet = mBitcoin.walletForViewID(pViewID);
        if(wallet == null)
            return;

        mCurrentWalletViewID = pViewID;

        LayoutInflater inflater = getLayoutInflater();
        ViewGroup contentView = findViewById(R.id.content);

        mFiatRate = Settings.getInstance(getFilesDir()).doubleValue("usd_rate");

        contentView.removeAllViews();
        findViewById(R.id.statusBar).setVisibility(View.GONE);

        // Title
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null)
        {
            actionBar.setIcon(R.drawable.ic_history_black_36dp);
            actionBar.setTitle(" " + getResources().getString(R.string.title_wallet_history));
            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
        }

        ViewGroup historyView = (ViewGroup)inflater.inflate(R.layout.wallet_history, contentView, false);
        ((TextView)historyView.findViewById(R.id.title)).setText(wallet.name);

        ViewGroup transactions = historyView.findViewById(R.id.walletTransactions);
        populateTransactions(transactions, wallet.transactions,
          100);

        contentView.addView(historyView);

        alignTransactions(transactions);

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
                        ViewGroup contentView = findViewById(R.id.content);

                        contentView.removeAllViews();

                        button = inflater.inflate(R.layout.button, contentView, false);
                        button.setTag(R.id.createWallet);
                        ((TextView)button.findViewById(R.id.text)).setText(R.string.create_new_key);
                        contentView.addView(button);

                        button = inflater.inflate(R.layout.button, contentView, false);
                        button.setTag(R.id.recoverWallet);
                        ((TextView)button.findViewById(R.id.text)).setText(R.string.recover_wallet);
                        contentView.addView(button);

                        button = inflater.inflate(R.layout.button, contentView, false);
                        button.setTag(R.id.importWallet);
                        ((TextView)button.findViewById(R.id.text)).setText(R.string.import_bip0032_key);
                        contentView.addView(button);

                        ActionBar actionBar = getSupportActionBar();
                        if(actionBar != null)
                        {
                            actionBar.setIcon(R.drawable.ic_add_black_36dp);
                            actionBar.setTitle(" " + getResources().getString(R.string.title_add_wallet));
                            actionBar.setDisplayHomeAsUpEnabled(true); // Show the Up button in the action bar.
                        }

                        mMode = Mode.ADD_WALLET;
                    }
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
                        ViewGroup contentView = findViewById(R.id.content);

                        contentView.removeAllViews();
                        inflater.inflate(R.layout.import_bip32_key, contentView, true);
                        focusOnText(R.id.importText);

                        Spinner derivationMethod = findViewById(R.id.derivationMethodSpinner);
                        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.derivation_methods, android.R.layout.simple_spinner_item);
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                        derivationMethod.setAdapter(adapter);

                        // Load button
                        View button = inflater.inflate(R.layout.button, contentView, false);
                        button.setTag(R.id.loadKey);
                        ((TextView)button.findViewById(R.id.text)).setText(R.string.import_text);
                        contentView.addView(button);
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
                    ViewGroup contentView = findViewById(R.id.content);
                    EditText nameView = contentView.findViewById(R.id.name);
                    String name = nameView.getText().toString();
                    int walletOffset = mBitcoin.walletOffsetForViewID(mCurrentWalletViewID);
                    if(walletOffset != -1 && mBitcoin.setName(walletOffset, name))
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
                        displaySendPayment(paymentCode.getText().toString());
                    else
                    {
                        showMessage(getString(R.string.failed_payment_code), 2000);
                        updateWallets();
                    }
                    break;
                }
                case R.id.openScanner:
                    mQRScanner.initiateScan(IntentIntegrator.QR_CODE_TYPES);
                    break;
                case R.id.sendPayment:
                {
                    mAuthorizedTask = AuthorizedTask.SIGN_TRANSACTION;

                    if(mPaymentRequest.protocol == PaymentRequest.PROTOCOL_REQUEST_AMOUNT)
                        mPaymentAmount = mPaymentRequest.amount;
                    else
                    {
                        EditText amount = findViewById(R.id.amount);
                        if(amount == null)
                        {
                            Log.e(logTag, "Failed to find amount edit text view");
                            showMessage(getString(R.string.failed_payment_amount), 2000);
                            updateWallets();
                            return;
                        }

                        String paymentAmountString = amount.getText().toString();
                        if(paymentAmountString == null || paymentAmountString.length() == 0)
                        {
                            Log.e(logTag, "Amount string empty");
                            showMessage(getString(R.string.failed_payment_amount), 2000);
                            updateWallets();
                            return;
                        }

                        Spinner units = findViewById(R.id.units);
                        if(units == null)
                        {
                            Log.e(logTag, "Failed to find units spinner");
                            showMessage(getString(R.string.failed_payment_amount), 2000);
                            updateWallets();
                            return;
                        }

                        double rawAmount;
                        try
                        {
                            rawAmount = Double.parseDouble(paymentAmountString);
                        }
                        catch(Exception pException)
                        {
                            Log.e(logTag, String.format("Failed to parse amount \"%s\" : %s", paymentAmountString,
                              pException.toString()));
                            showMessage(getString(R.string.failed_payment_amount), 2000);
                            updateWallets();
                            return;
                        }

                        switch(units.getSelectedItemPosition())
                        {
                            case 0: // USD
                                mPaymentAmount = Bitcoin.satoshisFromBitcoins(rawAmount / mFiatRate);
                                break;
                            case 1: // bits
                                mPaymentAmount = Bitcoin.satoshisFromBits(rawAmount);
                                break;
                            case 2: // bitcoins
                                mPaymentAmount = Bitcoin.satoshisFromBitcoins(rawAmount);
                                break;
                        }
                    }

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
                                  mDerivationPathMethodToLoad, mSeedIsRecovered);
                                task.execute();
                                mSeed = null;
                            }

                            synchronized(this)
                            {
                                ViewGroup contentView = findViewById(R.id.content);
                                contentView.removeAllViews();

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
                              mBitcoin.walletOffsetForViewID(mCurrentWalletViewID));
                            task.execute();

                            synchronized(this)
                            {
                                ViewGroup contentView = findViewById(R.id.content);
                                contentView.removeAllViews();

                                findViewById(R.id.progress).setVisibility(View.VISIBLE);
                                mMode = Mode.IN_PROGRESS;
                            }
                            break;
                        }
                        case SIGN_TRANSACTION:
                        {
                            CreateTransactionTask task = new CreateTransactionTask(this, mBitcoin, passcode,
                              mBitcoin.walletOffsetForViewID(mCurrentWalletViewID), mPaymentRequest.address,
                              mPaymentAmount);
                            task.execute();

                            synchronized(this)
                            {
                                ViewGroup contentView = findViewById(R.id.content);
                                contentView.removeAllViews();

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
                            showMessage(getString(R.string.seed_matches), 2000);
                            updateWallets();
                        }
                        else
                        {
                            mAuthorizedTask = AuthorizedTask.ADD_KEY;
                            mKeyToLoad = null;
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
            // Remove word from seed
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
                        mCurrentWalletViewID = walletView.getId();

                        ((ImageView)pView.findViewById(R.id.walletExpand))
                          .setImageResource(R.drawable.ic_expand_less_white_36dp);
                        walletDetails.setVisibility(View.VISIBLE);

                        // Make all others gone
                        ViewGroup content = findViewById(R.id.content);

                        synchronized(this)
                        {
                            synchronized(mBitcoin)
                            {
                                for(Wallet wallet : mBitcoin.wallets)
                                    if(wallet.viewID != 0 && wallet.viewID != mCurrentWalletViewID)
                                    {
                                        ViewGroup otherWalletView = content.findViewById(wallet.viewID);
                                        otherWalletView.findViewById(R.id.walletDetails).setVisibility(View.GONE);
                                        ((ImageView)otherWalletView.findViewById(R.id.walletExpand))
                                          .setImageResource(R.drawable.ic_expand_more_white_36dp);
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
            ViewGroup wallet = (ViewGroup)pView.getParent().getParent().getParent();
            mCurrentWalletViewID = wallet.getId();
            displayEnterPaymentCode();
            break;
        }
        case R.id.walletScan:
        {
            ViewGroup wallet = (ViewGroup)pView.getParent().getParent().getParent();
            mCurrentWalletViewID = wallet.getId();
            mQRScanner.initiateScan(IntentIntegrator.QR_CODE_TYPES);
            break;
        }
        case R.id.walletReceive:
        {
            ViewGroup walletView = (ViewGroup)pView.getParent().getParent().getParent();
            mCurrentWalletViewID = walletView.getId();
            int walletOffset = mBitcoin.walletOffsetForViewID(mCurrentWalletViewID);
            if(walletOffset == -1)
                showMessage(getString(R.string.failed_receive_address_qr), 2000);
            else
            {
                CreateAddressTask task = new CreateAddressTask(this, mBitcoin, walletOffset, 0);
                task.execute();

                synchronized(this)
                {
                    ViewGroup contentView = findViewById(R.id.content);
                    contentView.removeAllViews();

                    findViewById(R.id.progress).setVisibility(View.VISIBLE);
                    mMode = Mode.IN_PROGRESS;
                }
            }
            break;
        }
        case R.id.walletHistory:
        {
            ViewGroup wallet = (ViewGroup)pView.getParent().getParent().getParent();
            displayWalletHistory(wallet.getId());
            break;
        }
        case R.id.walletEdit:
        {
            ViewGroup wallet = (ViewGroup)pView.getParent().getParent().getParent();
            displayEditWallet(wallet.getId());
            break;
        }
        case R.id.walletTransaction:
        {
            openTransaction(mBitcoin.walletOffsetForViewID(mCurrentWalletViewID), (String)pView.getTag());
            break;
        }
        case R.id.addressText:
        {
            ClipboardManager manager = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(manager != null)
            {
                ClipData clip = ClipData.newPlainText("Bitcoin Cash Address",
                  ((TextView)pView).getText().toString());
                manager.setPrimaryClip(clip);
                showMessage(getString(R.string.address_clipboard), 2000);
            }
            break;
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
