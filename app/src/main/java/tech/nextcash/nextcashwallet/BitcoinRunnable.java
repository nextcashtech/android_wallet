package tech.nextcash.nextcashwallet;

import android.util.Log;


public class BitcoinRunnable implements Runnable
{
    private static final String logTag = "BitcoinRunnable";

    private Bitcoin mBitcoin;
    private String mPath;
    private int mFinishMode;

    BitcoinRunnable(Bitcoin pBitcoin, String pPath, int pFinishMode)
    {
        mBitcoin = pBitcoin;
        mPath = pPath;
        mFinishMode = pFinishMode;
    }

    @Override
    public void run()
    {
        Log.i(logTag, "Bitcoin thread starting");
        mBitcoin.setPath(mPath);
        mBitcoin.run(mFinishMode);
        mBitcoin.destroy();
        Log.i(logTag, "Bitcoin thread finished");
    }
}
