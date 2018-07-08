package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;


public class GetTransactionTask extends AsyncTask<String, Integer, Integer>
{
    private Context mContext;
    private Bitcoin mBitcoin;
    private int mWalletOffset;
    private String mID;
    private FullTransaction mTransaction;

    public GetTransactionTask(Context pContext, Bitcoin pBitcoin, int pWalletOffset, String pID,
      FullTransaction pTransaction)
    {
        mContext = pContext;
        mBitcoin = pBitcoin;
        mWalletOffset = pWalletOffset;
        mID = pID;
        mTransaction = pTransaction;
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        mBitcoin.getTransaction(mWalletOffset, mID, mTransaction);
        return 0;
    }

    @Override
    protected void onPostExecute(Integer pResult)
    {
        Intent finishIntent = new Intent(MainActivity.ACTIVITY_ACTION);
        finishIntent.setAction(MainActivity.ACTION_DISPLAY_TRANSACTION);

        mContext.sendBroadcast(finishIntent);
        super.onPostExecute(pResult);
    }
}
