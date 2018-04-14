package tech.nextcash.nextcashwallet;


import android.util.Log;


// Define Top Level Bitcoin JNI Interface Functions.
public class Bitcoin
{
    // Used to load the native libraries on application startup.
    static
    {
        System.loadLibrary("nextcash");
        System.loadLibrary("bitcoin");
        System.loadLibrary("nextcash_jni");
    }

    private static final String logTag = "Bitcoin";
    private static Thread sMainThread;

    private long mHandle;
    private String mPath;

    public static final int FINISH_ON_REQUEST = 0; // Continue running until stop is requested.
    public static final int FINISH_ON_SYNC = 1; // Finish when block chain is in sync.

    public static native String userAgent();
    public static native String networkName();

    public static float bitcoins(long pValue)
    {
        return (float)pValue / 100000000;
    }

    Bitcoin(String pPath) { mHandle = 0; mPath = pPath; }

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

    public native void destroy();

    public native void setPath(String pPath);

    public native boolean load();

    public native boolean isLoaded();

    public native boolean isRunning();

    public native void setFinishMode(int pFinishMode);
    public native void setFinishModeNoCreate(int pFinishMode);

    // Run the daemon process.
    public native void run(int pFinishMode);

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

    // Total balance of all keys.
    public native long balance();

    // Add a key, from BIP-0032 encoded text to be monitored.
    public static final int SIMPLE_DERIVATION = 2;
    public static final int BIP0032_DERIVATION = 1;
    public static final int BIP0044_DERIVATION = 0;
    public native int addKey(String pEncodedKey, int pDerivationPath);

    // Return the number of keys in the key store
    public native int keyCount();

    public native String keyName(int pKeyOffset);
    public native boolean setKeyName(int pKeyOffset, String pName);
    public native String keySeed(int pKeyOffset);
    public native boolean setKeySeed(int pKeyOffset, String pSeed);

    // Return the balance of the key at the specified offset in the key store
    public native long keyBalance(int pKeyOffset, boolean pIncludePending);

    // Return transactions for key
    // public static native Transaction[] getTransactions(int pKeyOffset);

    // Generate a mnemonic sentence that can be used to create an HD key.
    //public native String generateMnemonic();

    // Create an HD key from the mnemonic sentence and add it to be monitored.
    // If pRecovered is true then the entire block chain will be searched for related transactions.
    //public native void addKeyFromMnemonic(String pMnemonic, boolean pRecovered);

    //TODO Bitcoin JNI Functions
    // Get Transactions for key
    // Get New Transactions
    // Get Keys

    public native Transaction[] transactions(int pKeyOffset);
}
