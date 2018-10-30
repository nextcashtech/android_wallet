/**************************************************************************
 * Copyright 2017-2018 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
package tech.nextcash.nextcashwallet;


import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitArray;
import com.google.zxing.common.BitMatrix;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;


// Define Top Level Bitcoin JNI Interface Functions.
public class Bitcoin
{
    public static final String LAST_SYNC_NAME = "last_sync"; // Time in seconds since epoch of last synchronization
    public static final String SYNC_FREQUENCY_NAME = "sync_frequency"; // Frequency in minutes for background sync jobs
    public static final String PIN_CREATED_NAME = "pin_created";
    public static final String EXCHANGE_TYPE_NAME = "exchange_type";
    public static final String EXCHANGE_RATE_NAME = "exchange_rate";
    public static final int QR_WIDTH = 200;

    private static final String logTag = "Bitcoin";
    private static final int sSampleBlockHeight = 526256;
    private static final long sSampleTime = 1523978805;
    private static final long sSecondsPerBlock = 600;

    private long mHandle; // Used by JNI
    private File mDirectory;
    private Settings mSettings;
    private TransactionData mTransactionData;
    private AddressLabel mAddressLabels;
    private AddressBook mAddressBook;
    private boolean mWalletsLoaded, mChainLoaded;
    private boolean mNeedsUpdate;
    private int mChangeID;
    private double mExchangeRate;
    private String mExchangeType;

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
        mDirectory = null;
        mSettings = null;
        mTransactionData = null;
        mAddressLabels = null;
        mAddressBook = null;
        mExchangeRate = 0.0;
        mExchangeType = null;
    }

    public synchronized void initialize(Context pContext)
    {
        mDirectory = pContext.getFilesDir();
        mTransactionData = new TransactionData(mDirectory);
        mAddressLabels = new AddressLabel(mDirectory);
        mAddressBook = new AddressBook(mDirectory);

        mSettings = Settings.getInstance(mDirectory);
        if(mSettings.containsValue(EXCHANGE_RATE_NAME))
            mExchangeRate = mSettings.doubleValue(EXCHANGE_RATE_NAME);
        if(mSettings.containsValue(EXCHANGE_TYPE_NAME))
            mExchangeType = mSettings.value(EXCHANGE_TYPE_NAME);
        else
            mExchangeType = "USD";
    }

    public synchronized double exchangeRate()
    {
        return mExchangeRate;
    }

    public synchronized String exchangeType()
    {
        return mExchangeType;
    }

    public synchronized void setExchangeRate(double pRate, String pType)
    {
        mExchangeRate = pRate;
        mExchangeType = pType;
        if(mSettings != null)
        {
            mSettings.setDoubleValue(EXCHANGE_RATE_NAME, pRate);
            mSettings.setValue(EXCHANGE_TYPE_NAME, pType);
        }
    }

    // Units
    public static final int FIAT = 0;
    public static final int BITS = 1;
    public static final int BITCOINS = 2;

    public static double bitcoinsFromSatoshis(long pSatoshis)
    {
        return (double)pSatoshis / 100000000.0;
    }

    public static double bitsFromSatoshis(long pSatoshis)
    {
        return (double)pSatoshis / 100.0;
    }

    public static long satoshisFromBitcoins(double pBitcoins)
    {
        return (long)(pBitcoins * 100000000.0);
    }

    public static double bitsFromBitcoins(double pBitcoins)
    {
        return pBitcoins * 0.000001;
    }

    public static long satoshisFromBits(double pBits)
    {
        return (long)(pBits * 100.0);
    }

    public String amountText(long pAmount, String pExchangeType, double pExchangeRate)
    {
        double exchangeRate = pExchangeRate;
        String exchangeType = pExchangeType;
        if(exchangeRate == 0.0 && mExchangeRate != 0.0)
        {
            exchangeRate = mExchangeRate;
            exchangeType = mExchangeType;
        }

        if(exchangeRate != 0.0)
            return formatAmount(Bitcoin.bitcoinsFromSatoshis(Math.abs(pAmount)) * exchangeRate, exchangeType);
        else
            return String.format(Locale.getDefault(), "%,.8f",
              Bitcoin.bitcoinsFromSatoshis(Math.abs(pAmount)));
    }

    public String amountText(long pAmount, TransactionData.Item pTransactionData)
    {
        if(pTransactionData != null)
        {
            if(pTransactionData.cost != 0.0)
                return formatAmount(pTransactionData.cost, pTransactionData.costType);
            else
                return amountText(pAmount, pTransactionData.exchangeType, pTransactionData.exchangeRate);
        }
        else
            return amountText(pAmount);
    }

    public String amountText(long pAmount)
    {
        return amountText(pAmount, null, 0.0);
    }

    public static String formatAmount(double pAmount, String pExchangeType)
    {
        if(pExchangeType == null)
            return null;

        switch(pExchangeType)
        {
        default:
        case "AUD":
        case "CAD":
        case "USD":
            return String.format(Locale.getDefault(), "$%,.2f", pAmount);
        case "EUR":
            return String.format(Locale.getDefault(), "€%,.2f", pAmount);
        case "GBP":
            return String.format(Locale.getDefault(), "£%,.2f", pAmount);
        case "JPY":
            return String.format(Locale.getDefault(), "¥%,d", (int)pAmount);
        case "KRW":
            return String.format(Locale.getDefault(), "₩%,d", (int)pAmount);
        }
    }

    public static double parseAmount(String pAmountText) throws NumberFormatException
    {
        if(pAmountText.length() == 0)
            return 0.0;

        switch(pAmountText.charAt(0))
        {
            case '$':
            case '€':
            case '£':
            case '¥':
            case '₩':
                pAmountText = pAmountText.substring(1);
                break;
            default:
                break;
        }

        return Math.abs(Double.parseDouble(pAmountText));
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
            return sSampleBlockHeight + (int)(((System.currentTimeMillis() / 1000L) - sSampleTime) / sSecondsPerBlock);
        else
        {
            Block block = getBlockFromHeight(height);
            return height + (int)(((System.currentTimeMillis() / 1000L) - block.time) / sSecondsPerBlock);
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
    public native boolean isInRoughSync();

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

    public native boolean containsAddress(int pWalletOffset, String pAddress);

    public native boolean markAddressUsed(int pWalletOffset, String pAddress);

    public boolean generateQRCode(String pURI, Bitmap pBitmap)
    {
        MultiFormatWriter multiFormatWriter = new MultiFormatWriter();

        try
        {
            HashMap<EncodeHintType,String> hints = new HashMap<>();
            hints.put(EncodeHintType.MARGIN, "0");
            BitMatrix bitMatrix = multiFormatWriter.encode(pURI, BarcodeFormat.QR_CODE, QR_WIDTH, QR_WIDTH, hints);
            BitArray rowBits = new BitArray(QR_WIDTH);
            int row[] = new int[QR_WIDTH];

            for(int y = 0; y < QR_WIDTH; y++)
            {
                rowBits = bitMatrix.getRow(y, rowBits);
                for(int x = 0; x < QR_WIDTH; x++)
                {
                    if(rowBits.get(x))
                        row[x] = 0xff000000;
                    else
                        row[x] = 0x00000000;
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

    // Return true if this is a valid encoded private key
    // Return values :
    //   0 = valid main net private key
    //   1 = invalid encoding
    //   2 = not main net
    public native int isValidPrivateKey(String pPrivateKey);

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

    public TransactionData.Item getTransactionData(String pTransactionID, long pAmount)
    {
        if(mTransactionData == null)
            return null;
        return mTransactionData.getData(pTransactionID, pAmount);
    }

    public boolean saveTransactionData()
    {
        return mTransactionData.save(mDirectory);
    }

    public AddressLabel.Item lookupAddressLabel(String pAddress, long pAmount)
    {
        return mAddressLabels.lookup(pAddress, pAmount);
    }

    public AddressLabel.Item lookupAddressLabel(String pAddress)
    {
        return mAddressLabels.lookup(pAddress);
    }

    public boolean addAddressLabel(AddressLabel.Item pItem)
    {
        if(mAddressLabels == null)
            return false;
        return mAddressLabels.add(pItem);
    }

    public ArrayList<AddressLabel.Item> getAddressLabels(int pWalletOffset)
    {
        ArrayList<AddressLabel.Item> allItems = mAddressLabels.getAll();
        ArrayList<AddressLabel.Item> result = new ArrayList<>();
        for(AddressLabel.Item item : allItems)
            if(containsAddress(pWalletOffset, item.address))
                result.add(item);
        return result;
    }

    public boolean saveAddressLabels()
    {
        return mAddressLabels.save(mDirectory);
    }

    public void addAddress(String pAddress, String pName)
    {
        mAddressBook.addAddress(pAddress, pName);
        mAddressBook.save(mDirectory);
    }

    public AddressBook.Item lookupAddress(String pAddress)
    {
        return mAddressBook.lookup(pAddress);
    }

    public ArrayList<AddressBook.Item> getAddresses()
    {
        return mAddressBook.getAll();
    }

    public boolean saveAddressBook()
    {
        return mAddressBook.save(mDirectory);
    }

    public synchronized void triggerUpdate()
    {
        mNeedsUpdate = true;
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
        boolean dataUpdated = false;
        for(int offset = 0; offset < mWallets.length; offset++)
        {
            if(updateWallet(mWallets[offset], offset))
            {
                for(Transaction transaction : mWallets[offset].transactions)
                    if(updateTransactionData(transaction, offset))
                        dataUpdated = true;
            }
            else
            {
                mNeedsUpdate = true;
                result = false;
            }
        }

        if(mDirectory != null && mTransactionData != null && (dataUpdated || mTransactionData.itemsAdded()))
        {
            mTransactionData.save(mDirectory);
            mAddressLabels.save(mDirectory);
        }

        if(result)
        {
            mNeedsUpdate = false;
            mChangeID = changeID;
        }

        return result;
    }

    public synchronized boolean updateTransactionData(Transaction pTransaction, int pWalletOffset)
    {
        boolean result = false;

        pTransaction.data = getTransactionData(pTransaction.hash, pTransaction.amount);

        if(pTransaction.data.exchangeRate == 0.0 && exchangeRate() != 0.0)
        {
            // First time this transaction has been seen.
            pTransaction.data.exchangeRate = exchangeRate();
            pTransaction.data.exchangeType = exchangeType();

            FullTransaction fullTransaction = new FullTransaction();
            if(getTransaction(pWalletOffset, pTransaction.hash, fullTransaction))
            {
                // Check if it pays to any labeled addresses.
                AddressLabel.Item addressLabel;
                for(Output output : fullTransaction.outputs)
                    if(output.related)
                    {
                        addressLabel = lookupAddressLabel(output.address, output.amount);
                        if(addressLabel != null)
                            pTransaction.data.comment = addressLabel.label;
                    }

                // Check if it pays to any address book entries.
                AddressBook.Item addressItem;
                for(Output output : fullTransaction.outputs)
                {
                    addressItem = lookupAddress(output.address);
                    if(addressItem != null)
                    {
                        pTransaction.data.comment = addressItem.name;
                        break;
                    }
                }
            }

            result = true;
        }

        if(pTransaction.date != 0 && pTransaction.data.date > pTransaction.date)
        {
            pTransaction.data.date = pTransaction.date;
            result = true;
        }

        // TODO Temporary Fix for issue with bad date being tagged on transaction.
        if(pTransaction.date != 0 && pTransaction.block != null &&
          Math.abs(pTransaction.date - pTransaction.data.date) > 864000) // One day
            pTransaction.data.date = pTransaction.date;

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
    public native int loadKey(String pPassCode, String pEncodedKey, int pDerivationPath, String pName,
      long pRecoverTime);

    public native boolean isValidSeedWord(String pWord);
    public native String[] getMnemonicWords(String pStartingWith);

    // Add a key from a mnemonic seed.
    public native int addSeed(String pPassCode, String pMnemonicSeed, int pDerivationPath, String pName,
      boolean pIsBackedUp, long pRecoverTime);

    // Add a key from an encoded private key.
    public native int addPrivateKey(String pPassCode, String pPrivateKey, String pName, long pRecoverTime);

    public native int removeKey(String pPassCode, int pOffset);

    public native String generateMnemonicSeed(int pEntropy);
    public native String seed(String pPasscode, int pOffset);
    public native boolean seedIsValid(String pSeed);

    public native boolean hasPassCode();

    // Send a P2PKH (Pay to Public Key Hash) payment
    // Amount in satoshis
    // Fee rate in satoshis per byte of transaction size
    public native SendResult sendStandardPayment(int pWalletOffset, String pPassCode, String pAddress, long pAmount,
      double pFeeRate, boolean pUsePending, boolean pSendAll);

    // Send a payment given specific output(s) to pay.
    // pFeeRate is satoshis per byte of transaction size.
    // pUsePending allows spending of unconfirmed UTXOs.
    // pTransmit specifies if the transaction should be sent directly to the Bitcoin node network.
    //   If not then it will be transmitted directly to the recipient for them to transmit on approval.
    public native SendResult sendOutputsPayment(int pWalletOffset, String pPassCode, Output[] pOutputs, double pFeeRate,
      boolean pUsePending, boolean pTransmit);

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
