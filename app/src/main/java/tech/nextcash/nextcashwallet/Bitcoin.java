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

    public static final int FINISH_ON_REQUEST = 0; // Continue running until stop is requested.
    public static final int FINISH_ON_SYNC = 1; // Finish when block chain is in sync.

    private static final String logTag = "Bitcoin";
    private static Thread sMainThread, sGCThread;
    private static boolean sStop = false;
    private static int sFinishMode = FINISH_ON_REQUEST;

    private static Runnable sMainRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            Log.i(logTag, "Bitcoin thread starting");
            Bitcoin.run(sFinishMode);
            Log.i(logTag, "Bitcoin thread finished");
            if(sFinishMode == FINISH_ON_SYNC)
                Bitcoin.destroy();
        }
    };

    private static Runnable sGCRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            Log.i(logTag, "Garbage Collection thread starting");
            while(!sStop)
            {
                try
                {
                    Thread.sleep(250);
                }
                catch(InterruptedException pException)
                {
                    Log.d(logTag, String.format("Garbage collection sleep exception : %s", pException.toString()));
                }
                //System.gc(); // Manually run GC to clean up C++ memory
            }
            Log.i(logTag, "Garbage Collection thread finished");
        }
    };

    private static synchronized Thread getThread(boolean pCreate)
    {
        if((sMainThread == null || !sMainThread.isAlive()) && pCreate)
            sMainThread = new Thread(sMainRunnable, "BitcoinThread");
        return sMainThread;
    }

    private static synchronized Thread getGCThread(boolean pCreate)
    {
        if((sGCThread == null || !sGCThread.isAlive()) && pCreate)
            sGCThread = new Thread(sGCRunnable, "GarbageCollectionThread");
        return sGCThread;
    }

    public static synchronized boolean start(String pPath, int pFinishMode)
    {
        boolean result = true;
        Thread thread = getThread(true);
        if(!thread.isAlive())
        {
            Log.i(logTag, "Starting main thread");
            Bitcoin.setPath(pPath);
            sFinishMode = pFinishMode;
            thread.start();
        }
        else
            result = false;

        try
        {
            Thread.sleep(100);
        }
        catch(InterruptedException pException)
        {
            Log.d(logTag, String.format("Wait for start sleep exception : %s", pException.toString()));
        }

        Thread gcThread = getGCThread(true);
        if(!gcThread.isAlive())
        {
            Log.i(logTag, "Starting garbage collection thread");
            sStop = false;
            gcThread.start();
        }

        return result;
    }

    public static synchronized boolean requestStop()
    {
        boolean result = true;
        Thread thread = getThread(false);
        if(thread != null && thread.isAlive())
        {
            Log.i(logTag, "Stopping main thread");
            Bitcoin.stopDaemon();
        }
        else
            result = false;

        Thread gcThread = getGCThread(false);
        if(gcThread != null && gcThread.isAlive())
        {
            Log.i(logTag, "Stopping garbage collection thread");
            sStop = true;
        }

        return result;
    }

    public static void waitForStop()
    {
        requestStop();

        while(Bitcoin.isRunning())
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

    public static native String userAgent();
    public static native String networkName();

    public static native void setPath(String pPath);
    public static native void setIP(byte pIP[]);

    // Load the Bitcoin static instance.
    public static native boolean load();
    public static native boolean isLoaded();
    public static native boolean isRunning();
    public static native void destroy();

    // Run the daemon process.
    public static native void run(int pFinishMode);

    // Request the daemon process stop.
    public static native void stopDaemon();

    // Set the finish mode of a currently running daemon.
    public static native void setFinishMode(int pMode);

    // Return the number of peers connected to
    public static native int peerCount();

    // Return true if this node is "in sync" with it's peers
    public static native int status();

    // Block height of block chain.
    public static native int blockHeight();

    // Block height of latest key monitoring pass.
    public static native int merkleHeight();

    // Total balance of all keys.
    public static native long balance();

    // Add a key, from BIP-0032 encoded text to be monitored.
    public static final int SIMPLE_DERIVATION = 2;
    public static final int BIP0032_DERIVATION = 1;
    public static final int BIP0044_DERIVATION = 0;
    public static native boolean addKey(String pEncodedKey, int pDerivationPath);

    // Return the number of keys in the key store
    public static native int keyCount();

    // Return the balance of the key at the specified offset in the key store
    public static native long keyBalance(int pKeyOffset, boolean pIncludePending);

    // Return transactions for key
    // public static native Transaction[] getTransactions(int pKeyOffset);

    // Generate a mnemonic sentence that can be used to create an HD key.
    public static native String generateMnemonic();

    // Create an HD key from the mnemonic sentence and add it to be monitored.
    // If pRecovered is true then the entire block chain will be searched for related transactions.
    public static native void addKeyFromMnemonic(String pMnemonic, boolean pRecovered);

    //TODO Bitcoin JNI Functions
    // Get Transactions for key
    // Get New Transactions
    // Add Key
    // Get Keys

    public static float bitcoins(long pValue)
    {
        return (float)pValue / 100000000;
    }
}
