package tech.nextcash.nextcashwallet;


// Define Top Level Bitcoin JNI Interface Functions.
public class Bitcoin
{
    public static final String logTag = "Bitcoin";

    public static native String userAgent();
    public static native String networkName();

    public static native void setPath(String pPath);
    public static native void setIP(byte pIP[]);

    // Load the Bitcoin static instance.
    public static native boolean load();
    public static native boolean isLoaded();

    public static final int FINISH_ON_REQUEST = 0; // Continue running until stop is requested.
    public static final int FINISH_ON_SYNC = 1; // Finish when block chain is in sync.

    // Run the daemon process.
    public static native void run(int pMode);

    // Request the daemon process stop.
    public static native void stop();

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
    public static final int SIMPLE_DERIVATION = 0;
    public static final int BIP0032_DERIVATION = 1;
    public static final int BIP0044_DERIVATION = 2;
    public static native void addKey(String pEncodedKey, int pDerivationPath);

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
}
