package tech.nextcash.nextcashwallet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.util.Locale;
import java.util.Vector;


public class BitcoinService extends Service
{
    private static final String logTag = "BitcoinService";

    private static final int sProgressNotificationID = 1;

    private Bitcoin mBitcoin;
    private int mFinishMode;
    private Thread mBitcoinThread, mMonitorThread;
    private Runnable mBitcoinRunnable, mMonitorRunnable;
    private boolean mIsRegistered, mIsBound;
    private int mNextNotificationID;
    private Notification mProgressNotification;
    private boolean mProgressDisplayed;
    private int mStartBlockHeight, mStartMerkleHeight;

    public class LocalBinder extends Binder
    {
        BitcoinService getService()
        {
            return BitcoinService.this;
        }
    }
    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate()
    {
        super.onCreate();
        mBitcoin = ((MainApp)getApplication()).bitcoin;
        mIsRegistered = false;
        mNextNotificationID = 2;
        mProgressDisplayed = false;
        mStartBlockHeight = 0;
        mStartMerkleHeight = 0;
        mIsBound = false;
        mFinishMode = Bitcoin.FINISH_ON_REQUEST;
        mBitcoinThread = null;
        mMonitorThread= null;
        mCallBacks = new CallBacks[0];

        mBitcoinRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                Log.i(logTag, "Bitcoin thread starting");
                register();
                updateProgressNotification();
                startForeground(sProgressNotificationID, mProgressNotification);
                mBitcoin.setPath(getFilesDir().getPath() + "/bitcoin");
                mBitcoin.load();
                onLoaded();
                mBitcoin.run(mFinishMode);
                onFinished();
                clearProgress();
                stopForeground(true);
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
    }

    @Override
    public int onStartCommand(Intent pIntent, int pFlags, int pStartId)
    {
        Bundle extras = pIntent.getExtras();
        int finishMode;
        if(extras != null && extras.containsKey("FinishMode"))
            finishMode = extras.getInt("FinishMode");
        else
            finishMode = Bitcoin.FINISH_ON_SYNC;

        if(mBitcoinThread == null || !mBitcoinThread.isAlive())
        {
            if(finishMode == Bitcoin.FINISH_ON_REQUEST)
                Log.i(logTag, "Starting Bitcoin thread in finish on request mode");
            else
                Log.i(logTag, "Starting Bitcoin thread in finish on sync mode");
            mFinishMode = finishMode;
            mBitcoinThread = new Thread(mBitcoinRunnable, "BitcoinDaemon");
            mBitcoinThread.start();
        }

        if(finishMode == Bitcoin.FINISH_ON_REQUEST && mBitcoin.finishMode() != finishMode)
        {
            Log.i(logTag, "Updating finish mode to on request");
            mBitcoin.setFinishMode(finishMode); // Upgrade to finish on request
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy()
    {
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
        mIsBound = true;
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent pIntent)
    {
        mIsBound = false;
        return super.onUnbind(pIntent);
    }

    @Override
    public void onRebind(Intent pIntent)
    {
        super.onRebind(pIntent);
    }

    public interface CallBacks
    {
        void onLoad();
        boolean onTransactionUpdate(int pWalletOffset, Transaction pTransaction);
        boolean onUpdate();
        void onFinish();
    }

    private CallBacks[] mCallBacks;

    public void setCallBacks(CallBacks pCallBacks)
    {
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

    public void onLoaded()
    {
        mBitcoin.onLoaded();
        mBitcoin.update(false);
        mStartMerkleHeight = mBitcoin.merkleHeight();
        mStartBlockHeight = mBitcoin.blockHeight();
        for(CallBacks callBacks : mCallBacks)
            callBacks.onLoad();

        // Start monitor thread
        if(mMonitorThread == null || !mMonitorThread.isAlive())
        {
            Log.i(logTag, "Starting monitor thread.");
            mMonitorThread = new Thread(mMonitorRunnable, "BitcoinMonitor");
            mMonitorThread.start();
        }
    }

    public void onFinished()
    {
        for(CallBacks callBacks : mCallBacks)
            callBacks.onFinish();
        if(!mIsBound)
            stopSelf();
    }

    private static final String sProgressNotificationChannel = "Progress";
    private static final String sTransactionsNotificationChannel = "Transactions";

    private void register()
    {
        if(mIsRegistered)
            return;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            NotificationChannel channel = new NotificationChannel(sTransactionsNotificationChannel,
              getString(R.string.channel_transactions_name), NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(getString(R.string.channel_transactions_description));

            // Register the channel with the system
            NotificationManager notificationManager =
              (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            if(notificationManager != null)
                notificationManager.createNotificationChannel(channel);

            channel = new NotificationChannel(sProgressNotificationChannel,
              getString(R.string.channel_progress_name), NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.channel_progress_description));

            // Register the channel with the system
            if(notificationManager != null)
                notificationManager.createNotificationChannel(channel);
        }

        mIsRegistered = true;
    }

    private void updateProgressNotification()
    {
        boolean sync = mBitcoin.isInSync();
        if(sync && mBitcoin.merkleHeight() == mBitcoin.blockHeight())
        {
            if(mProgressDisplayed)
                NotificationManagerCompat.from(this).cancel(sProgressNotificationID);
            mProgressDisplayed = false;
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        Intent stopIntent = new Intent();
        stopIntent.setAction(getString(R.string.stop));
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
        Bitmap iconBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.icon);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, sProgressNotificationChannel)
          .setSmallIcon(R.drawable.icon_notification)
          .setLargeIcon(iconBitmap)
          .setContentTitle(getString(R.string.progress_title))
          .setContentIntent(pendingIntent)
          .setPriority(NotificationCompat.PRIORITY_LOW);

        if(mBitcoin.finishMode() != mBitcoin.FINISH_ON_REQUEST)
            builder.addAction(R.drawable.ic_stop_black_24dp, getString(R.string.stop), stopPendingIntent);

        int max = 0;
        int progress = 0;
        boolean indeterminate = false;

        if(!mBitcoin.isLoaded())
        {
            indeterminate = true;
            builder.setContentText(getString(R.string.loading_for_synchronize));
        }
        else if(mBitcoin.isInSync())
        {
            max = mBitcoin.blockHeight() - mStartMerkleHeight;
            progress = mBitcoin.merkleHeight() - mStartMerkleHeight;
            builder.setContentText(getString(R.string.looking_for_transactions));
        }
        else
        {
            int estimatedHeight = mBitcoin.estimatedBlockHeight();
            max = estimatedHeight - mStartBlockHeight;
            progress = mBitcoin.blockHeight() - mStartBlockHeight;
            builder.setContentText(getString(R.string.looking_for_blocks));
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
        mProgressDisplayed = true;
    }

    private void notify(String pTitle, String pText, String pTransactionHash)
    {
        Settings settings = Settings.getInstance(getFilesDir());
        if(settings.containsValue("notify_transactions") &&
          !Settings.getInstance(getFilesDir()).boolValue("notify_transactions"))
            return;

        Intent intent = new Intent(this, MainActivity.class);
        // TODO Add intent extra to show specific wallet/transaction
        intent.putExtra("Transaction", pTransactionHash);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        Bitmap iconBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.icon);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,
          sTransactionsNotificationChannel)
          .setSmallIcon(R.drawable.icon_notification)
          .setLargeIcon(iconBitmap)
          .setContentTitle(pTitle)
          .setContentText(pText)
          .setContentIntent(pendingIntent)
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(mNextNotificationID++, builder.build());
    }

    private void clearProgress()
    {
        NotificationManagerCompat.from(this).cancel(sProgressNotificationID);
    }

    // Returns true if hash was added to the file.
    private boolean addToPendingFile(String pHash)
    {
        boolean found = false;

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

                if(line.equals(pHash))
                {
                    Log.d(logTag, String.format(Locale.getDefault(), "Pending hash found at line %d : %s",
                      lineNo, pHash));
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
            Log.d(logTag,String.format("Adding pending hash : %s", pHash));

            // Add hash to file
            try
            {
                FileOutputStream outFile =
                  new FileOutputStream(getFilesDir().getPath().concat("/pending_hashes"), true);
                outFile.write(pHash.getBytes());
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

    private void removeFromPendingFile(String pHash)
    {
        Vector<String> hashes = new Vector<String>();
        boolean found = false;
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

                if(line.equals(pHash))
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
            Log.d(logTag,String.format("Removing pending hash not found. Adding. : %s", pHash));

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
        boolean force = false;
        int progressUpdate = 0;

        while(mBitcoin.isRunning())
        {
            progressUpdate++;
            if(progressUpdate >= 5)
            {
                progressUpdate = 0;
                updateProgressNotification();
            }

            if(mBitcoin.update(force))
            {
                int offset = 0;
                for(Wallet wallet : mBitcoin.wallets)
                {
                    // Check for new transactions and notify
                    if(wallet.updatedTransactions != null && wallet.updatedTransactions.length > 0)
                    {
                        //TODO Don't notify for wallets not yet fully "recovered"
                        // Notify of new transaction
                        for(Transaction transaction : wallet.updatedTransactions)
                        {
                            if(transaction.block == null)
                            {
                                Log.d(logTag, String.format("Updated pending transaction found : %s",
                                  transaction.hash));

                                // Pending
                                if(!addToPendingFile(transaction.hash))
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
                                removeFromPendingFile(transaction.hash);

                                if(transaction.amount > 0)
                                    title = getString(R.string.confirmed_receive_title);
                                else
                                    title = getString(R.string.confirmed_send_title);
                            }

                            for(CallBacks callBacks : mCallBacks)
                                callBacks.onTransactionUpdate(offset, transaction);

                            notify(title, transaction.description(this), transaction.hash);
                        }
                    }
                }

                for(CallBacks callBacks : mCallBacks)
                    callBacks.onUpdate();
            }

            try
            {
                Thread.sleep(1000);
            }
            catch(InterruptedException pException)
            {
                Log.w(logTag, String.format("Monitor thread sleep exception : %s", pException.toString()));
            }
        }
    }
}
