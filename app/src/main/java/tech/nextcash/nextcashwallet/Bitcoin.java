package tech.nextcash.nextcashwallet;


import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
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


// Define Top Level Bitcoin JNI Interface Functions.
public class Bitcoin
{
    private static final String logTag = "Bitcoin";
    private static Thread sMainThread;

    private long mHandle;
    private String mPath;
    private int mChangeID;
    private boolean mIsRegistered;
    private int mNextNotificationID;

    Bitcoin(String pPath)
    {
        mHandle = 0;
        mPath = pPath;
        mChangeID = -1;
        mIsRegistered = false;
        mNextNotificationID = 1;
    }

    public static float bitcoins(long pValue)
    {
        return (float)pValue / 100000000;
    }

    public static native String userAgent();
    public static native String networkName();

    public native void setPath(String pPath);

    public native boolean load();

    public native boolean isLoaded();

    public native boolean isRunning();

    public native void setFinishMode(int pFinishMode);
    public native void setFinishModeNoCreate(int pFinishMode);

    public static final int FINISH_ON_REQUEST = 0; // Continue running until stop is requested.
    public static final int FINISH_ON_SYNC = 1; // Finish when block chain is in sync.

    // Run the daemon process.
    public native void run(int pFinishMode);

    public native void destroy();

    // Request the daemon process stop.
    public native void stop();

    // Return the number of peers connected to
    public native int peerCount();

    // Return true if this node is "in sync" with it's peers
    public native int status();

    // Block height of block chain.
    public native int blockHeight();

    // Block height of latest key monitoring pass.
    public native int merkleHeight();

    // Add a key, from BIP-0032 encoded text to be monitored.
    public static final int BIP0044_DERIVATION = 0;
    public static final int BIP0032_DERIVATION = 1;
    public static final int SIMPLE_DERIVATION  = 2;
    public native int addKey(String pEncodedKey, int pDerivationPath);

    public Wallet[] wallets;

    private static final String transactionsNotificationChannel = "TRANS";

    private void register(Context pContext)
    {
        if(mIsRegistered)
            return;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            CharSequence name = pContext.getString(R.string.channel_transactions_name);
            String description = pContext.getString(R.string.channel_transactions_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(transactionsNotificationChannel, name, importance);
            channel.setDescription(description);

            // Register the channel with the system
            NotificationManager notificationManager =
              (NotificationManager)pContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if(notificationManager != null)
                notificationManager.createNotificationChannel(channel);
        }

        mIsRegistered = true;
    }

    private void notify(Context pContext, String pChannel, String pTitle, String pText)
    {
        Settings settings = Settings.getInstance(pContext.getFilesDir());
        if(settings.containsValue("notify_transactions") &&
          !Settings.getInstance(pContext.getFilesDir()).boolValue("notify_transactions"))
            return;

        Intent intent = new Intent(pContext, MainActivity.class);
        // TODO Add intent extra to show specific wallet/transaction
        PendingIntent pendingIntent = PendingIntent.getActivity(pContext, 0, intent, 0);
        Bitmap iconBitmap = BitmapFactory.decodeResource(pContext.getResources(), R.drawable.icon);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(pContext, pChannel)
          .setSmallIcon(R.drawable.icon_notification)
          .setLargeIcon(iconBitmap)
          .setContentTitle(pTitle)
          .setContentText(pText)
          .setContentIntent(pendingIntent)
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(pContext);

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(mNextNotificationID++, mBuilder.build());
    }

    // Returns true if hash was added to the file.
    private boolean addToPendingFile(Context pContext, String pHash)
    {
        boolean found = false;
        try
        {
            // Look in file for hash
            BufferedReader inFile =
              new BufferedReader(new FileReader(pContext.getFilesDir().getPath().concat("/pending_hashes")));

            String line;
            while(true)
            {
                line = inFile.readLine();
                if(line == null)
                    break;

                if(line.equals(pHash))
                {
                    found = true;
                    break;
                }
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
            // Add hash to file
            try
            {
                FileOutputStream outFile =
                  new FileOutputStream(pContext.getFilesDir().getPath().concat("/pending_hashes"), true);
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

    private void removeFromPendingFile(Context pContext, String pHash)
    {
        Vector<String> hashes = new Vector<String>();
        boolean found = false;
        try
        {
            // Look in file for hash
            BufferedReader inFile =
              new BufferedReader(new FileReader(pContext.getFilesDir().getPath().concat("/pending_hashes")));

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
            // Delete and rewrite file
            File file = new File(pContext.getFilesDir().getPath().concat("/pending_hashes"));
            if(file.isFile())
                file.delete();
            file = null;

            try
            {
                FileOutputStream outFile =
                  new FileOutputStream(pContext.getFilesDir().getPath().concat("/pending_hashes"), true);

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

    public boolean update(Context pContext)
    {
        register(pContext);

        int changeID = getChangeID();
        if(changeID == mChangeID)
            return false; // No changes detected

        int count = keyCount();
        if(count == 0)
        {
            mChangeID = changeID;
            return false;
        }

        // Check for initial creation of wallets
        if(wallets == null || (wallets.length == 0 && count > 0))
        {
            wallets = new Wallet[count];
            for(int i=0;i<wallets.length;i++)
                wallets[i] = new Wallet();
        }

        // Check if wallets needs to be expanded
        if(wallets.length < count)
        {
            Wallet[] newWallets = new Wallet[count];

            // Copy pre-existing wallets
            for(int i=0;i<wallets.length;i++)
                newWallets[i] = wallets[i];

            wallets = newWallets;

            // Initialize new wallets
            for(int i=0;i<wallets.length;i++)
            {
                if(wallets[i] == null)
                    wallets[i] = new Wallet();
            }
        }

        // Update wallets
        boolean result = true;
        int offset = 0;
        for(Wallet wallet : wallets)
        {
            if(!updateWallet(offset))
                result = false;
            else
            {
                // Check for new transactions and notify
                if(wallet.updatedTransactions != null && wallet.updatedTransactions.length > 0)
                {
                    //TODO Don't notify for wallets not yet fully "recovered"
                    // Notify of new transaction
                    String title, description;
                    int startString, endString;

                    for(Transaction transaction : wallet.updatedTransactions)
                    {
                        if(transaction.amount > 0)
                            startString = R.string.receive_description_start;
                        else
                            startString = R.string.send_description_start;

                        if(transaction.block == null)
                        {
                            // Pending
                            if(!addToPendingFile(pContext, transaction.hash))
                                continue; // Already notified about this pending transaction

                            if(transaction.amount > 0)
                                title = pContext.getString(R.string.pending_receive_title);
                            else
                                title = pContext.getString(R.string.pending_send_title);
                            endString = R.string.pending_notification_description_end;
                        }
                        else
                        {
                            // Confirmed
                            removeFromPendingFile(pContext, transaction.hash);

                            if(transaction.amount > 0)
                                title = pContext.getString(R.string.confirmed_receive_title);
                            else
                                title = pContext.getString(R.string.confirmed_send_title);
                            endString = R.string.confirmed_notification_description_end;
                        }

                        description = String.format(Locale.getDefault(), "%s %,.5f %s",
                          pContext.getString(startString), bitcoins(transaction.amount), pContext.getString(endString));

                        notify(pContext, transactionsNotificationChannel, title, description);
                    }
                }
            }
            offset++;
        }

        if(result)
            mChangeID = changeID;
        return result;
    }

    private native int getChangeID();

    // Return the number of keys in the key store
    private native int keyCount();

    private native boolean updateWallet(int pOffset);

    public native boolean setName(int pOffset, String pName);

    public native String seed(int pOffset);

    //TODO Generate a mnemonic sentence that can be used to create an HD key.
    //public native String generateMnemonic();

    //TODO Create an HD key from the mnemonic sentence and add it to be monitored.
    // If pRecovered is true then the entire block chain will be searched for related transactions.
    //public native void addKeyFromMnemonic(String pMnemonic, boolean pRecovered);


    private synchronized Thread getThread(boolean pCreate, int pFinishMode)
    {
        if((sMainThread == null || !sMainThread.isAlive()) && pCreate)
            sMainThread = new Thread(new BitcoinRunnable(this, mPath, pFinishMode), "BitcoinDaemon");
        return sMainThread;
    }

    public synchronized boolean start(int pFinishMode)
    {
        boolean result = true;
        Thread thread = getThread(true, pFinishMode);
        if(!thread.isAlive())
            thread.start();
        else
        {
            Log.v(logTag, "Bitcoin daemon already running");
            result = false;
        }

        return result;
    }

    public synchronized boolean requestStop()
    {
        Thread thread = getThread(false,0);
        if(thread != null && thread.isAlive())
        {
            stop();
            return true;
        }
        else
            return false;
    }

    public void waitForStop()
    {
        requestStop();

        while(isRunning())
        {
            try
            {
                Thread.sleep(100);
            }
            catch(InterruptedException pException)
            {
                Log.d(logTag, String.format("Wait for stop sleep exception : %s", pException.toString()));
            }
        }
    }

    private static native void setupJNI();

    static
    {
        System.loadLibrary("nextcash_jni");
        setupJNI();
    }
}
