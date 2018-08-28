package tech.nextcash.nextcashwallet;


import android.graphics.Bitmap;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitArray;
import com.google.zxing.common.BitMatrix;

import java.util.Locale;


// Define Top Level Bitcoin JNI Interface Functions.
public class Bitcoin
{
    public static final String LAST_SYNC_NAME = "last_sync"; // Time in seconds since epoch of last synchronization
    public static final String SYNC_FREQUENCY_NAME = "sync_frequency"; // Frequency in minutes for background sync jobs
    public static final int QR_WIDTH = 200;

    private static final String logTag = "Bitcoin";
    private static final int sSampleBlockHeight = 526256;
    private static final long sSampleTime = 1523978805;
    private static final long sSecondsPerBlock = 600;

    private long mHandle; // Used by JNI
    private boolean mWalletsLoaded, mChainLoaded;
    private boolean mNeedsUpdate;
    private int mChangeID;

    public boolean appIsOpen;
    public boolean walletsModified;

    private Wallet[] mWallets;

    Bitcoin()
    {
        appIsOpen = false;
        walletsModified = true;
        mHandle = 0;
        mWalletsLoaded = false;
        mChainLoaded = false;
        mNeedsUpdate = false;
        mChangeID = -1;
        mWallets = new Wallet[0];
    }

    public static double bitcoinsFromSatoshis(long pSatoshis)
    {
        return (double)pSatoshis / 100000000;
    }

    public static double bitsFromSatoshis(long pSatoshis)
    {
        return (double)pSatoshis / 1000000;
    }

    public static long satoshisFromBitcoins(double pBitcoins)
    {
        return (long)(pBitcoins * 100000000.0);
    }

    public static double bitsFromBitcoins(double pBitcoins)
    {
        return pBitcoins * 1000000.0;
    }

    public static long satoshisFromBits(double pBits)
    {
        return (long)(pBits * 100.0);
    }

    public static String amountText(long pAmount, double pFiatRate)
    {
        if(pFiatRate != 0.0)
            return String.format(Locale.getDefault(), "$%,.2f",
              Bitcoin.bitcoinsFromSatoshis(Math.abs(pAmount)) * pFiatRate);
        else
            return String.format(Locale.getDefault(), "%,.5f",
              Bitcoin.bitcoinsFromSatoshis(Math.abs(pAmount)));
    }

    public static String satoshiText(long pAmount)
    {
        long remaining = Math.abs(pAmount);
        long bitcoins = remaining / 100000000;
        remaining -= (bitcoins * 100000000);
        long sub1 = remaining / 1000000;
        remaining -= (sub1 * 1000000);
        long sub2 = remaining / 1000;
        remaining -= (sub2 * 1000);
        long sub3 = remaining;

        return String.format(Locale.getDefault(), "%d %02d %03d %03d", bitcoins, sub1, sub2, sub3);
    }

    public int estimatedHeight()
    {
        int height = headerHeight();
        if(height < sSampleBlockHeight)
            return sSampleBlockHeight + (int)(((System.currentTimeMillis() / 1000) - sSampleTime) / sSecondsPerBlock);
        else
        {
            Block block = getBlockFromHeight(height);
            return height + (int)(((System.currentTimeMillis() / 1000) - block.time) / sSecondsPerBlock);
        }
    }

    // Estimated P2PKH transaction size based on input count
    static public int estimatedP2PKHSize(int pInputCount, int pOutputCount)
    {
        // P2PKH input size
        //   Previous Transaction ID = 32 bytes
        //   Previous Transction Output Index = 4 bytes
        //   Signature push to stack = 75
        //       push size = 1 byte
        //       signature up to = 73 bytes
        //       signature hash type = 1 byte
        //   Public key push to stack = 34
        //       push size = 1 byte
        //       public key size = 33 bytes
        int inputSize = 32 + 4 + 75 + 34;

        // P2PKH output size
        //   amount = 8 bytes
        //   push size = 1 byte
        //   Script (24 bytes) OP_DUP OP_HASH160 <PUB KEY HASH (20 bytes)> OP_EQUALVERIFY OP_CHECKSIG
        int outputSize = 8 + 25;

        return (inputSize * pInputCount) + (pOutputCount * outputSize);
    }

    public static native String userAgent();
    public static native String networkName();

    public native void setPath(String pPath);

    public native boolean loadWallets();
    public native boolean loadChain();

    public boolean walletsAreLoaded() { return mWalletsLoaded; }
    public boolean chainIsLoaded() { return mChainLoaded; }

    public native boolean isRunning();
    public native boolean isStopping();

    public native boolean initialBlockDownloadIsComplete();

    public native boolean isInSync();
    public native boolean wasInSync();

    public static final int FINISH_ON_REQUEST = 0; // Continue running until stop is requested.
    public static final int FINISH_ON_SYNC = 1; // Finish when block chain is in sync.

    public native int finishMode();
    public native void setFinishMode(int pFinishMode);
    public native void setFinishTime(int pSecondsFromNow);
    public native void clearFinishTime();

    // Run the daemon process.
    public native void run();

    public native void destroy();

    // Request the daemon process stop.
    public native boolean stop();

    // Return the number of peers connected to
    public native int peerCount();

    // Return true if this node is "in sync" with it's peers
    public native int status();

    // Block height of block chain.
    public native int headerHeight();

    public native Block getBlockFromHeight(int pHeight);
    public native Block getBlockFromHash(String pHash);

    // Block height of latest key monitoring pass.
    public native int merkleHeight();

    public native String getNextReceiveAddress(int pWalletOffset, int pChainIndex);
    public native byte[] getNextReceiveOutput(int pWalletOffset, int pChainIndex);

    public boolean generateQRCode(String pURI, Bitmap pBitmap)
    {
        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();

        try
        {
            BitMatrix bitMatrix = multiFormatWriter.encode(pURI, BarcodeFormat.QR_CODE, QR_WIDTH, QR_WIDTH);
            BitArray rowBits = new BitArray(QR_WIDTH);
            int row[] = new int[QR_WIDTH];
            int on = 0xffffffff;

            for(int y = 0; y < QR_WIDTH; y++)
            {
                rowBits = bitMatrix.getRow(y, rowBits);
                for(int x = 0; x < QR_WIDTH; x++)
                {
                    if(rowBits.get(x))
                        row[x] = on;
                    else
                        row[x] = 0;
                }
                pBitmap.setPixels(row, 0, QR_WIDTH, 0, y, QR_WIDTH, 1);
            }

            return true;
        }
        catch (WriterException pException)
        {
            Log.e(logTag, String.format("Failed to create QR Code : %s", pException.toString()));
            return false;
        }
    }

    public native String encodePaymentCode(String pAddressHash, int pFormat, int pProtocol);
    public native PaymentRequest decodePaymentCode(String pPaymentCode);

    public void onWalletsLoaded()
    {
        mWalletsLoaded = true;
        update(true); // Load wallets array
    }

    public void onChainLoaded()
    {
        mChainLoaded = true;
        update(true); // Update chain related data in wallets
    }

    public synchronized int walletCount()
    {
        return mWallets.length;
    }

    public synchronized Wallet wallet(int pOffset)
    {
        if(mWallets != null && mWallets.length > pOffset)
            return mWallets[pOffset];
        return null;
    }

    public synchronized Wallet[] wallets()
    {
        return mWallets;
    }

    public synchronized boolean update(boolean pForce)
    {
        if(!mWalletsLoaded)
            return false;

        int changeID = getChangeID();
        int count = keyCount();
        if(!mNeedsUpdate && !pForce && count == mWallets.length && changeID == mChangeID)
            return false; // No changes detected

        // Check if wallets needs to be expanded
        if(mWallets.length != count)
        {
            Log.i(logTag, String.format("Allocating %d wallets", count));

            // Initialize wallets
            walletsModified = true;
            mWallets = new Wallet[count];
            for(int i=0;i<mWallets.length;i++)
                mWallets[i] = new Wallet();
        }

        // Update wallets
        boolean result = true;
        for(int offset = 0; offset < mWallets.length; offset++)
            if(!updateWallet(mWallets[offset], offset))
            {
                mNeedsUpdate = true;
                result = false;
            }

        if(result)
        {
            mNeedsUpdate = false;
            mChangeID = changeID;
        }

        return result;
    }

    public native Outpoint[] getUnspentOutputs(int pOffset);

    private native int getChangeID();

    // Return the number of keys in the key store
    public native int keyCount();

    private native synchronized boolean updateWallet(Wallet pWallet, int pOffset);

    public native boolean getTransaction(int pWalletOffset, String pID, FullTransaction pTransaction);

    public native boolean setName(int pOffset, String pName);

    public native boolean setIsBackedUp(int pOffset);

    public static final int BIP0044_DERIVATION = 0;
    public static final int BIP0032_DERIVATION = 1;
    public static final int SIMPLE_DERIVATION  = 2;

    // Load a key from BIP-0032 encoded text.
    public native int loadKey(String pPassCode, String pEncodedKey, int pDerivationPath, String pName);

    public native String[] getMnemonicWords(String pStartingWith);

    // Add a key from a mnemonic seed.
    public native int addSeed(String pPassCode, String pMnemonicSeed, int pDerivationPath, String pName,
      boolean pStartNewPass, boolean pIsBackedUp);

    public native int removeKey(String pPassCode, int pOffset);

    public native String generateMnemonicSeed(int pEntropy);
    public native String seed(String pPasscode, int pOffset);
    public native boolean seedIsValid(String pSeed);

    public native boolean hasPassCode();

    // Send a P2PKH (Pay to Public Key Hash) payment
    // Amount in satoshis
    // Fee rate in satoshis per byte of transaction size
    public native int sendP2PKHPayment(int pWalletOffset, String pPassCode, String pAddress, long pAmount,
      double pFeeRate, boolean pUsePending, boolean pSendAll);

    // Send a payment given a specific output script to pay
    // Amount in satoshis
    // Fee rate in satoshis per byte of transaction size
    public native int sendOutputPayment(int pWalletOffset, String pPassCode, byte[] pOutputScript, long pAmount,
      double pFeeRate, boolean pUsePending);

    // Get the raw data for the transaction paying the specified output script
    public native byte[] getRawTransaction(byte[] pPayingOutputScript, long pAmount);

    //TODO Generate a mnemonic sentence that can be used to create an HD key.
    //public native String generateMnemonic();

    //TODO Create an HD key from the mnemonic sentence and add it to be monitored.
    // If pRecovered is true then the entire block chain will be searched for related transactions.
    //public native void addKeyFromMnemonic(String pMnemonic, boolean pRecovered);

    public native boolean test();

    private static native void setupJNI();

    static
    {
        System.loadLibrary("nextcash_jni");
        setupJNI();
    }
}
