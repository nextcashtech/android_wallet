package tech.nextcash.nextcashwallet;


import android.util.Log;


// Define Top Level Bitcoin JNI Interface Functions.
public class Bitcoin
{
    private static final String logTag = "Bitcoin";
    private static Thread sMainThread;

    private long mHandle;
    private String mPath;
    private int mChangeID;

    Bitcoin(String pPath)
    {
        mHandle = 0;
        mPath = pPath;
        mChangeID = -1;
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
    public static final int SIMPLE_DERIVATION = 2;
    public static final int BIP0032_DERIVATION = 1;
    public static final int BIP0044_DERIVATION = 0;
    public native int addKey(String pEncodedKey, int pDerivationPath);

    public Wallet[] wallets;

    public boolean update()
    {
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
                if(wallet.newTransactions != null && wallet.newTransactions.length > 0)
                {
                    //TODO Notify of new transaction
                    //TODO Don't notify for wallets not yet fully "recovered"
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
