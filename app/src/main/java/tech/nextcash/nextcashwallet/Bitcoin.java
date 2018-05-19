package tech.nextcash.nextcashwallet;


import android.graphics.Bitmap;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.Locale;


// Define Top Level Bitcoin JNI Interface Functions.
public class Bitcoin
{
    private static final String logTag = "Bitcoin";
    private static final int sExampleBlockHeight = 526256;
    private static final long sExampleTime = 1523978805;
    private static final long sSecondsPerBlock = 600;

    private long mHandle; // Used by JNI
    private boolean mLoaded;
    private int mChangeID;

    Bitcoin()
    {
        mHandle = 0;
        mLoaded = false;
        mChangeID = -1;
    }

    public static float bitcoins(long pValue)
    {
        return (float)pValue / 100000000;
    }

    public static String amountText(long pAmount, double pFiatRate)
    {
        if(pFiatRate != 0.0)
        {
            return String.format(Locale.getDefault(), "$%,.2f",
              Bitcoin.bitcoins(Math.abs(pAmount)) * pFiatRate);
//            if(pAmount > 0)
//            {
//                if(pAddSign)
//                    return String.format(Locale.getDefault(), "+$%,.2f",
//                      Bitcoin.bitcoins(pAmount) * pFiatRate);
//                else
//                    return String.format(Locale.getDefault(), "$%,.2f",
//                      Bitcoin.bitcoins(pAmount) * pFiatRate);
//            }
//            else
//                return String.format(Locale.getDefault(), "-$%,.2f",
//                  Bitcoin.bitcoins(pAmount) * -pFiatRate);
        }
        else
            return String.format(Locale.getDefault(), "%,.5f",
              Bitcoin.bitcoins(Math.abs(pAmount)));
    }

    public int estimatedBlockHeight()
    {
        int height = blockHeight();
        if(height < sExampleBlockHeight)
            return sExampleBlockHeight + (int)(((System.currentTimeMillis() / 1000) - sExampleTime) / sSecondsPerBlock);
        else
        {
            Block block = getBlockFromHeight(height);
            return height + (int)(((System.currentTimeMillis() / 1000) - block.time) / sSecondsPerBlock);
        }
    }

    public static native String userAgent();
    public static native String networkName();

    public native void setPath(String pPath);

    public native boolean load();

    public boolean isLoaded() { return mLoaded; }

    public native boolean isRunning();

    public native boolean isInSync();

    public static final int FINISH_ON_REQUEST = 0; // Continue running until stop is requested.
    public static final int FINISH_ON_SYNC = 1; // Finish when block chain is in sync.

    public native int finishMode();
    public native void setFinishMode(int pFinishMode);
    public native void setFinishModeNoCreate(int pFinishMode);

    // Run the daemon process.
    public native void run(int pFinishMode);

    public native void destroy();

    // Request the daemon process stop.
    public native boolean stop();

    // Return the number of peers connected to
    public native int peerCount();

    // Return true if this node is "in sync" with it's peers
    public native int status();

    // Block height of block chain.
    public native int blockHeight();

    public native Block getBlockFromHeight(int pHeight);
    public native Block getBlockFromHash(String pHash);

    // Block height of latest key monitoring pass.
    public native int merkleHeight();

    public native String getNextReceiveAddress(int pWalletOffset, int pChainIndex);

    public Bitmap qrCode(String pText)
    {
        Bitmap result;
        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();

        try
        {
            BitMatrix bitMatrix = multiFormatWriter.encode(pText, BarcodeFormat.QR_CODE,
              200,200);
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            result = barcodeEncoder.createBitmap(bitMatrix);
        }
        catch (WriterException pException)
        {
            Log.e(logTag, String.format("Failed to create QR Code : %s", pException.toString()));
            return null;
        }

        return result;
    }

    public Wallet[] wallets;

    public void onLoaded()
    {
        mLoaded = true;
    }

    public boolean update(boolean pForce)
    {
        if(!mLoaded)
            return false;

        int changeID = getChangeID();
        int count = keyCount();
        if(((count == 0 && wallets == null) || (wallets != null && count == wallets.length)) &&
          changeID == mChangeID && !pForce)
            return false; // No changes detected

        // Check for initial creation of wallets
        if(wallets == null || (wallets.length == 0 && count > 0))
        {
            wallets = new Wallet[count];
            for(int i=0;i<wallets.length;i++)
                wallets[i] = new Wallet();
        }

        // Check if wallets needs to be expanded
        if(wallets.length != count)
        {
            Wallet[] newWallets = new Wallet[count];

            // Copy pre-existing wallets
            for(int i = 0; i < wallets.length && i < newWallets.length; i++)
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
        for(int offset = 0; offset < wallets.length; offset++)
            if(!updateWallet(offset))
                result = false;

        if(result)
            mChangeID = changeID;
        return result;
    }

    private native int getChangeID();

    // Return the number of keys in the key store
    public native int keyCount();

    private native boolean updateWallet(int pOffset);

    public native boolean setName(int pOffset, String pName);

    // Add a key, from BIP-0032 encoded text to be monitored.
    public static final int BIP0044_DERIVATION = 0;
    public static final int BIP0032_DERIVATION = 1;
    public static final int SIMPLE_DERIVATION  = 2;
    public native int addKey(String pPasscode, String pEncodedKey, int pDerivationPath);

    public native String seed(String pPasscode, int pOffset);

    public native boolean hasPassCode();

    //TODO Generate a mnemonic sentence that can be used to create an HD key.
    //public native String generateMnemonic();

    //TODO Create an HD key from the mnemonic sentence and add it to be monitored.
    // If pRecovered is true then the entire block chain will be searched for related transactions.
    //public native void addKeyFromMnemonic(String pMnemonic, boolean pRecovered);

    private static native void setupJNI();

    static
    {
        System.loadLibrary("nextcash_jni");
        setupJNI();
    }
}
