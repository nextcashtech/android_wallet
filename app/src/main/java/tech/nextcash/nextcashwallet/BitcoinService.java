/**************************************************************************
 * Copyright 2017-2018 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
package tech.nextcash.nextcashwallet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;


public class BitcoinService extends Service
{
    private static final String logTag = "BitcoinService";

    private static final int sProgressNotificationID = 1;
    private static final int sIBDNotificationID = 2;

    public static final String SERVICE_ACTION = "tech.nextcash.nextcashwallet.SERVICE_ACTION";
    public static final String STOP_ACTION = "STOP";

    private Bitcoin mBitcoin;
    private Thread mBitcoinThread, mMonitorThread;
    private Runnable mBitcoinRunnable, mMonitorRunnable;
    private boolean mIsRegistered, mForegroundStarted;
    private int mNextNotificationID;
    private Notification mProgressNotification;
    private boolean mInitialBlockDownloadIsComplete;
    private int mStartBlockHeight, mStartMerkleHeight;
    private boolean mIsStopped, mRestart;
    private HashMap<String, Integer> mTransactionNotificationIDs;
    private Bitmap mIcon;


    public class LocalBinder extends Binder
    {
        BitcoinService getService()
        {
            return BitcoinService.this;
        }
    }
    private final IBinder mBinder = new LocalBinder();

    private BroadcastReceiver mReceiver;

    @Override
    public void onCreate()
    {
        super.onCreate();
        mBitcoin = ((MainApp)getApplication()).bitcoin;
        mIsRegistered = false;
        mForegroundStarted = false;
        mNextNotificationID = 3;
        mProgressNotification = null;
        mStartBlockHeight = 0;
        mStartMerkleHeight = 0;
        mBitcoinThread = null;
        mMonitorThread= null;
        mCallBacks = new CallBacks[0];
        mIsStopped = false;
        mRestart = false;
        mTransactionNotificationIDs = new HashMap<>();
        mIcon = null;
        mInitialBlockDownloadIsComplete = false;

        mBitcoinRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                Log.i(logTag, "Bitcoin thread starting");

                // Prepare notifications
                mIsStopped = false;
                updateProgressNotification();

                // Load daemon
                mBitcoin.setPath(getFilesDir().getPath() + "/bitcoin");

                // Start monitor thread
                if(mMonitorThread == null || !mMonitorThread.isAlive())
                {
                    Log.i(logTag, "Starting monitor thread.");
                    mMonitorThread = new Thread(mMonitorRunnable, "BitcoinMonitor");
                    mMonitorThread.start();
                }

                while(true)
                {
                    if(!mBitcoin.isStopping())
                    {
                        if(!mBitcoin.loadWallets())
                        {
                            Log.w(logTag, "Failed to load wallets");
                            break;
                        }
                        onWalletsLoaded();
                    }

                    if(!mBitcoin.isStopping())
                    {
                        if(!mBitcoin.loadChain())
                        {
                            Log.w(logTag, "Failed to load chain");
                            break;
                        }
                        onChainLoaded();
                    }

                    // Run daemon
                    if(!mBitcoin.isStopping())
                    {
                        mBitcoin.run();

                        if(mBitcoin.wasInSync())
                        {
                            Log.i(logTag, "Last sync time set");
                            Settings.getInstance(getFilesDir()).setLongValue(Bitcoin.LAST_SYNC_NAME,
                              System.currentTimeMillis() / 1000L);
                        }
                    }

                    if(mRestart)
                        mRestart = false;
                    else
                        break;
                }

                // Finish everything
                mIsStopped = true;
                onFinished();
                clearProgress();
                Log.i(logTag, "Bitcoin thread finished");
            }
        };

        mMonitorRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                monitor();
            }
        };

        mReceiver = new BroadcastReceiver()
        {
            @Override
            public void onReceive(Context pContext, Intent pIntent)
            {
                if(pIntent.getAction() != null && pIntent.getAction().equals(STOP_ACTION))
                {
                    Log.i(logTag, "Stop action received");
                    stop();
                }
            }
        };

        IntentFilter filter = new IntentFilter(SERVICE_ACTION);
        filter.addAction(STOP_ACTION);
        registerReceiver(mReceiver, filter);
        register();
        updateProgressNotification();
    }

    public synchronized void start(Intent pIntent)
    {
        Bundle extras = pIntent.getExtras();
        int finishMode;
        if(extras != null && extras.containsKey("FinishMode"))
            finishMode = extras.getInt("FinishMode");
        else
            finishMode = Bitcoin.FINISH_ON_SYNC;

        if(mBitcoinThread != null && mBitcoinThread.isAlive())
        {
            // Running
            if(mBitcoin.isStopping())
                mRestart = true;

            if(finishMode == Bitcoin.FINISH_ON_REQUEST && mBitcoin.finishMode() != finishMode)
            {
                Log.i(logTag, "Updating finish mode to on request");
                mBitcoin.setFinishMode(Bitcoin.FINISH_ON_REQUEST); // Upgrade to finish on request
            }
        }
        else
        {
            // Not running
            if(finishMode == Bitcoin.FINISH_ON_REQUEST)
                Log.i(logTag, "Starting Bitcoin thread in finish on request mode");
            else
            {
                Log.i(logTag, "Starting Bitcoin thread in finish on sync mode");
                mBitcoin.setFinishMode(finishMode);
            }
            mBitcoinThread = new Thread(mBitcoinRunnable, "BitcoinDaemon");
            mBitcoinThread.start();
        }

        updateProgressNotification();
    }

    public synchronized void stop()
    {
        mBitcoin.stop();
        updateProgressNotification();
    }

    @Override
    public int onStartCommand(Intent pIntent, int pFlags, int pStartId)
    {
        start(pIntent);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy()
    {
        clearProgress();
        unregisterReceiver(mReceiver);
        mBitcoin.destroy();
        super.onDestroy();
    }

    @Override
    public void onLowMemory()
    {
        Log.w(logTag, "Low Memory");
        super.onLowMemory();
    }

    @Override
    public void onTrimMemory(int pLevel)
    {
        Log.w(logTag, String.format(Locale.getDefault(), "Trim Memory : %d", pLevel));
        super.onTrimMemory(pLevel);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent pIntent)
    {
        start(pIntent);
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent pIntent)
    {
        return super.onUnbind(pIntent);
    }

    @Override
    public void onRebind(Intent pIntent)
    {
        super.onRebind(pIntent);
    }

    public interface CallBacks
    {
        // When enough data is loaded to display basic wallet information.
        void onWalletsLoad();

        // When full node/chain data is loaded.
        void onChainLoad();

        // New transaction or transaction state change
        boolean onTransactionUpdate(int pWalletOffset, Transaction pTransaction);

        // When there is a node/chain update (i.e. new block)
        boolean onUpdate();

        // When the node stops and everything should be cleaned up
        void onFinish();
    }

    private CallBacks[] mCallBacks;

    public void setCallBacks(CallBacks pCallBacks)
    {
        if(mBitcoin.walletsAreLoaded())
            pCallBacks.onWalletsLoad();

        if(mBitcoin.chainIsLoaded())
            pCallBacks.onChainLoad();

        CallBacks[] newCallBacks = new CallBacks[mCallBacks.length + 1];
        for(int offset = 0; offset < mCallBacks.length; offset++)
            newCallBacks[offset] = mCallBacks[offset];
        newCallBacks[newCallBacks.length - 1] = pCallBacks;
        mCallBacks = newCallBacks;
    }

    public void removeCallBacks(CallBacks pCallBacks)
    {
        boolean found = false;
        for(int offset = 0; offset < mCallBacks.length; offset++)
            if(mCallBacks[offset] == pCallBacks)
                found = true;

        if(!found)
            return;

        CallBacks[] newCallBacks = new CallBacks[mCallBacks.length - 1];
        int offset = 0;
        for(CallBacks callBacks : mCallBacks)
            if(callBacks != pCallBacks)
            {
                newCallBacks[offset] = callBacks;
                offset++;
            }
        mCallBacks = newCallBacks;
    }

    private synchronized void onWalletsLoaded()
    {
        mBitcoin.onWalletsLoaded();
        mStartMerkleHeight = mBitcoin.merkleHeight();
        mInitialBlockDownloadIsComplete = mBitcoin.initialBlockDownloadIsComplete();
        for(CallBacks callBacks : mCallBacks)
            callBacks.onWalletsLoad();
    }

    private synchronized void onChainLoaded()
    {
        mBitcoin.onChainLoaded();
        mStartMerkleHeight = mBitcoin.merkleHeight();
        mStartBlockHeight = mBitcoin.headerHeight();
        for(CallBacks callBacks : mCallBacks)
            callBacks.onChainLoad();
    }

    private void onFinished()
    {
        for(CallBacks callBacks : mCallBacks)
            callBacks.onFinish();
        stopSelf();
    }

    private static final String sProgressNotificationChannel = "Progress";
    private static final String sStatusNotificationChannel = "Status";
    private static final String sTransactionsNotificationChannel = "Transactions";

    private void register()
    {
        if(mIsRegistered)
            return;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            // Register channels.
            NotificationManager notificationManager =
              (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel;

            if(notificationManager != null)
            {
                channel = new NotificationChannel(sProgressNotificationChannel,
                  getString(R.string.channel_progress_name), NotificationManager.IMPORTANCE_LOW);
                channel.setDescription(getString(R.string.channel_progress_description));
                notificationManager.createNotificationChannel(channel);

                channel = new NotificationChannel(sStatusNotificationChannel,
                  getString(R.string.channel_status_name), NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription(getString(R.string.channel_status_description));
                notificationManager.createNotificationChannel(channel);

                channel = new NotificationChannel(sTransactionsNotificationChannel,
                  getString(R.string.channel_transactions_name), NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription(getString(R.string.channel_transactions_description));
                notificationManager.createNotificationChannel(channel);
            }
        }

        mIsRegistered = true;
    }

    private synchronized void updateProgressNotification()
    {
        if(mIsStopped)
            return;

        boolean isChainLoaded = mBitcoin.chainIsLoaded();
        boolean isInSync = mBitcoin.isInSync();
        int merkleHeight = mBitcoin.merkleHeight();
        int blockHeight = mBitcoin.headerHeight();

        if(mIcon == null)
            mIcon = BitmapFactory.decodeResource(getResources(), R.drawable.icon_notification_large);

        // Setup intent to open activity
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingOpenIntent = PendingIntent.getActivity(this, 0, openIntent, 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, sProgressNotificationChannel)
          .setSmallIcon(R.drawable.icon_notification_small)
          .setLargeIcon(mIcon)
          .setContentIntent(pendingOpenIntent)
          .setPriority(NotificationCompat.PRIORITY_LOW);

        if(!mIsStopped && !mBitcoin.isStopping() && mBitcoin.finishMode() != Bitcoin.FINISH_ON_REQUEST)
        {
            // Setup an intent to stop synchronization
            Intent stopIntent = new Intent(SERVICE_ACTION);
            stopIntent.setAction(STOP_ACTION);
            PendingIntent pendingStopIntent = PendingIntent.getBroadcast(this, 0, stopIntent, 0);
            builder.addAction(R.drawable.ic_stop_black_24dp, getString(R.string.stop), pendingStopIntent);
        }

        int max = 0;
        int progress = 0;
        boolean indeterminate = false;
        if(mIsStopped || mBitcoin.isStopping())
        {
            indeterminate = true;
            builder.setContentTitle(getString(R.string.stopping));
        }
        else if(!isChainLoaded)
        {
            indeterminate = true;
            builder.setContentTitle(getString(R.string.loading));
        }
        else if(isInSync)
        {
            if(mBitcoin.walletCount() == 0 || merkleHeight == blockHeight)
            {
                indeterminate = true;
                builder.setContentTitle(getString(R.string.monitoring));
                builder.setContentText(getString(R.string.watching_for_transactions));
            }
            else
            {
                if(mStartMerkleHeight > merkleHeight)
                    mStartMerkleHeight = merkleHeight;
                max = blockHeight - mStartMerkleHeight;
                progress = merkleHeight - mStartMerkleHeight;
                builder.setContentTitle(getString(R.string.synchronizing));
                builder.setContentText(getString(R.string.requesting_transactions));
            }
        }
        else
        {
            int estimatedHeight = mBitcoin.estimatedHeight();
            max = estimatedHeight - mStartBlockHeight;
            progress = blockHeight - mStartBlockHeight;
            builder.setContentTitle(getString(R.string.synchronizing));
            builder.setContentText(getString(R.string.requesting_blocks));
        }

        if(!indeterminate && (max <= 1 || progress < 1 || max - progress < 2))
        {
            indeterminate = true;
            max = 0;
            progress = 0;
        }

        builder.setProgress(max, progress, indeterminate);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // notificationId is a unique int for each notification that you must define
        mProgressNotification = builder.build();
        notificationManager.notify(sProgressNotificationID, mProgressNotification);

        if(!mForegroundStarted)
        {
            startForeground(sProgressNotificationID, mProgressNotification);
            mForegroundStarted = true;
        }
    }

    private void notify(String pTitle, String pText, int pWalletOffset, String pTransactionHash)
    {
        Settings settings = Settings.getInstance(getFilesDir());
        if(settings.containsValue("notify_transactions") &&
          !Settings.getInstance(getFilesDir()).boolValue("notify_transactions"))
            return;

        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction("Transaction");
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.encodedPath(pTransactionHash);
        uriBuilder.appendEncodedPath(String.format(Locale.getDefault(), "%d", pWalletOffset));
        intent.setData(uriBuilder.build());
        intent.putExtra("Wallet", pWalletOffset);
        intent.putExtra("Transaction", pTransactionHash);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        if(mIcon == null)
            mIcon = BitmapFactory.decodeResource(getResources(), R.drawable.icon_notification_large);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,
          sTransactionsNotificationChannel)
          .setSmallIcon(R.drawable.icon_notification_small)
          .setLargeIcon(mIcon)
          .setContentTitle(pTitle)
          .setContentText(pText)
          .setContentIntent(pendingIntent)
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        int notificationID;
        String id = String.format(Locale.getDefault(), "%s_%d", pTransactionHash, pWalletOffset);
        if(mTransactionNotificationIDs.containsKey(id))
            notificationID = mTransactionNotificationIDs.get(id);
        else
        {
            notificationID = mNextNotificationID++;
            mTransactionNotificationIDs.put(id, notificationID);
        }

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(notificationID, builder.build());
    }

    private synchronized void clearProgress()
    {
        Log.d(logTag, "Canceling progress notification");
        NotificationManagerCompat.from(this).cancel(sProgressNotificationID);
        mProgressNotification = null;
        if(mForegroundStarted)
        {
            Log.d(logTag, "Stopping foreground");
            stopForeground(true);
            mForegroundStarted = false;
        }
    }

    // Returns true if hash was added to the file.
    private boolean addToPendingFile(String pHash, int pWalletOffset)
    {
        boolean found = false;
        String id = String.format(Locale.getDefault(), "%s_%d", pHash, pWalletOffset);

        try
        {
            // Look in file for hash
            BufferedReader inFile =
              new BufferedReader(new FileReader(getFilesDir().getPath().concat("/pending_hashes")));

            int lineNo = 1;
            String line;
            while(true)
            {
                line = inFile.readLine();
                if(line == null)
                    break;

                if(line.equals(id))
                {
                    Log.d(logTag, String.format(Locale.getDefault(), "Pending hash found at line %d : %s",
                      lineNo, id));
                    found = true;
                    break;
                }

                lineNo++;
            }
        }
        catch(IOException pException)
        {
            found = false;
        }

        if(found)
            return false;
        else
        {
            Log.d(logTag,String.format("Adding pending hash : %s", id));

            // Add hash to file
            try
            {
                FileOutputStream outFile =
                  new FileOutputStream(getFilesDir().getPath().concat("/pending_hashes"), true);
                outFile.write(id.getBytes());
                outFile.write('\n');
            }
            catch(IOException pException)
            {
                Log.e(logTag, String.format("Failed to write pending hashes file : %s", pException.toString()));
                return false;
            }

            return true;
        }
    }

    private void removeFromPendingFile(String pHash, int pWalletOffset)
    {
        Vector<String> hashes = new Vector<String>();
        boolean found = false;
        String id = String.format(Locale.getDefault(), "%s_%d", pHash, pWalletOffset);

        try
        {
            // Look in file for hash
            BufferedReader inFile =
              new BufferedReader(new FileReader(getFilesDir().getPath().concat("/pending_hashes")));

            String line;
            while(true)
            {
                line = inFile.readLine();
                if(line == null)
                    break;

                if(line.equals(id))
                    found = true;
                else
                    hashes.add(line);
            }
        }
        catch(IOException pException)
        {
            found = false;
        }

        if(found)
        {
            Log.d(logTag,String.format("Removing pending hash not found. Adding. : %s", id));

            // Delete and rewrite file
            File file = new File(getFilesDir().getPath().concat("/pending_hashes"));
            if(file.isFile())
                file.delete();
            file = null;

            try
            {
                FileOutputStream outFile =
                  new FileOutputStream(getFilesDir().getPath().concat("/pending_hashes"), true);

                for(String hash : hashes)
                {
                    outFile.write(hash.getBytes());
                    outFile.write('\n');
                }
            }
            catch(IOException pException)
            {
                Log.e(logTag, String.format("Failed to write pending hashes file during remove : %s",
                  pException.toString()));
            }
        }
    }

    private void monitor()
    {
        String title;

        Log.i(logTag, "Starting monitoring.");

        while(!mIsStopped)
        {
            if(mBitcoin.update(false))
            {
                if(!mInitialBlockDownloadIsComplete && mBitcoin.initialBlockDownloadIsComplete())
                {
                    // Display initial block download complete notification
                    Intent intent = new Intent(this, MainActivity.class);
                    PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

                    if(mIcon == null)
                        mIcon = BitmapFactory.decodeResource(getResources(), R.drawable.icon_notification_large);

                    NotificationCompat.Builder builder = new NotificationCompat.Builder(this,
                      sStatusNotificationChannel)
                      .setSmallIcon(R.drawable.icon_notification_small)
                      .setLargeIcon(mIcon)
                      .setContentTitle(getString(R.string.initial_block_download_complete_title))
                      .setContentText(getString(R.string.initial_block_download_complete_message))
                      .setContentIntent(pendingIntent)
                      .setPriority(NotificationCompat.PRIORITY_HIGH)
                      .setAutoCancel(true);

                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
                    notificationManager.notify(sIBDNotificationID, builder.build());
                    mInitialBlockDownloadIsComplete = true;
                }

                int offset = 0;
                Wallet[] wallets = mBitcoin.wallets();
                if(wallets != null)
                    for(Wallet wallet : wallets)
                    {
                        // Check for new transactions and notify
                        if(wallet.updatedTransactions != null)
                        {
                            // Notify of new transaction
                            for(Transaction transaction : wallet.updatedTransactions)
                            {
                                if(transaction.block == null)
                                {
                                    Log.d(logTag, String.format("Updated pending transaction found : %s",
                                      transaction.hash));

                                    // Pending
                                    if(!addToPendingFile(transaction.hash, offset))
                                        continue; // Already notified about this pending transaction

                                    if(transaction.amount > 0)
                                        title = getString(R.string.pending_receive_title);
                                    else
                                        title = getString(R.string.pending_send_title);
                                }
                                else
                                {
                                    Log.d(logTag, String.format("Updated confirmed transaction found : %s",
                                      transaction.hash));

                                    // Confirmed
                                    removeFromPendingFile(transaction.hash, offset);

                                    if(transaction.amount > 0)
                                        title = getString(R.string.confirmed_receive_title);
                                    else
                                        title = getString(R.string.confirmed_send_title);
                                }

                                if(wallet.isSynchronized || transaction.block != null)
                                    for(CallBacks callBacks : mCallBacks)
                                        if(callBacks.onTransactionUpdate(offset, transaction))
                                            break;

                                if(wallet.isSynchronized)
                                    notify(title, transaction.description(getApplicationContext(), mBitcoin), offset,
                                      transaction.hash);
                            }
                        }

                        offset++;
                    }

                for(CallBacks callBacks : mCallBacks)
                    if(callBacks.onUpdate())
                        break;
            }

            updateProgressNotification();

            try
            {
                Thread.sleep(1000); // One second
            }
            catch(InterruptedException pException)
            {
                Log.w(logTag, String.format("Monitor thread sleep exception : %s", pException.toString()));
            }
        }

        Log.i(logTag, "Stopping monitoring.");
    }
}
