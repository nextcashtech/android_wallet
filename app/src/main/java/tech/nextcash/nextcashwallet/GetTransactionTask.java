/**************************************************************************
 * Copyright 2017-2018 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
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
        if(mBitcoin.getTransaction(mWalletOffset, mID, mTransaction))
            return 0;
        else
            return 1;
    }

    @Override
    protected void onPostExecute(Integer pResult)
    {
        Intent finishIntent = new Intent(MainActivity.ACTIVITY_ACTION);
        if(pResult == 0)
            finishIntent.setAction(MainActivity.ACTION_DISPLAY_TRANSACTION);
        else
            finishIntent.setAction(MainActivity.ACTION_TRANSACTION_NOT_FOUND);

        mContext.sendBroadcast(finishIntent);
        super.onPostExecute(pResult);
    }
}
