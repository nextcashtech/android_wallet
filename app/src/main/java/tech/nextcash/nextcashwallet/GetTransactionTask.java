package tech.nextcash.nextcashwallet;

import android.os.AsyncTask;


public class GetTransactionTask extends AsyncTask<String, Integer, Integer>
{
    private MainActivity mActivity;
    private Bitcoin mBitcoin;
    private int mWalletOffset;
    private String mID;
    private FullTransaction mTransaction;

    public GetTransactionTask(MainActivity pActivity, Bitcoin pBitcoin, int pWalletOffset, String pID)
    {
        mActivity = pActivity;
        mBitcoin = pBitcoin;
        mWalletOffset = pWalletOffset;
        mID = pID;
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        mTransaction = mBitcoin.getTransaction(mWalletOffset, mID);
        return 0;
    }

    @Override
    protected void onPostExecute(Integer pResult)
    {
        if(!mActivity.isDestroyed() && !mActivity.isFinishing() && mBitcoin.isLoaded())
            mActivity.displayTransaction(mTransaction);

        super.onPostExecute(pResult);
    }
}
