package tech.nextcash.nextcashwallet;


// Define Top Level Bitcoin JNI Interface Functions
public class Bitcoin
{
    public static final String logTag = "Bitcoin";

    public static native String userAgent();
    public static native String networkName();

    public static native void setPath(String pPath);
    public static native void setIP(byte pIP[]);

    public static final int FINISH_ON_REQUEST = 0; // Continue running until stop is requested
    public static final int FINISH_ON_SYNC = 1; // Finish when block chain is in sync

    // Run the daemon process
    public static native void run(int pMode);

    // Request the daemon process stop
    public static native void stop();

    // Set the finish mode of a currently running daemon
    public static native void setFinishMode(int pMode);

    //TODO Bitcoin JNI Functions
    // Block Height
    // Merkle Block Height
    // Get Transactions
    // Get New Transactions
    // Balance
    // Add Key
    // Get Keys
}
