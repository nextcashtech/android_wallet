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

import java.util.Locale;


public class CreateKeyTask extends AsyncTask<String, Integer, Integer>
{
    private Context mContext;
    private Bitcoin mBitcoin;
    private String mPasscode;
    private String mSeed;
    private int mDerivationMethod;
    private boolean mIsBackedUp;
    private long mRecoverDate;

    public CreateKeyTask(Context pContext, Bitcoin pBitcoin, String pPasscode, String pSeed,
      int pDerivationMethod, boolean pIsBackedUp, long pRecoverDate)
    {
        mContext = pContext;
        mBitcoin = pBitcoin;
        mPasscode = pPasscode;
        mSeed = pSeed;
        mDerivationMethod = pDerivationMethod;
        mIsBackedUp = pIsBackedUp;
        mRecoverDate = pRecoverDate;
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        String name = String.format(Locale.getDefault(), "%s %d", mContext.getString(R.string.wallet),
          mBitcoin.walletCount() + 1);
        return mBitcoin.addSeed(mPasscode, mSeed, mDerivationMethod, name, mIsBackedUp, mRecoverDate);
    }

    @Override
    protected void onPostExecute(Integer pResult)
    {
        Intent finishIntent = new Intent(MainActivity.ACTIVITY_ACTION);
        finishIntent.setAction(MainActivity.ACTION_DISPLAY_WALLETS);

        // Send intent back to activity
        switch(pResult)
        {
            case 0: // Success
            {
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.success_create_wallet);
                Settings settings = Settings.getInstance(mContext.getFilesDir());
                if(!settings.containsValue(Bitcoin.PIN_CREATED_NAME))
                {
                    settings.setLongValue(Bitcoin.PIN_CREATED_NAME, System.currentTimeMillis() / 1000L);
                    mBitcoin.setName(0, mContext.getString(R.string.default_wallet_name));
                }
                break;
            }
            case 1: // Unknown error
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.failed_create_wallet);
                break;
            case 2: // Invalid format
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.failed_key_import_format);
                break;
            case 3: // Already exists
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.failed_key_import_exists);
                break;
            case 4: // Invalid derivation method
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.failed_key_import_method);
                break;
            case 5: // Invalid pass code
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.failed_invalid_passcode);
                break;
            case 6: // Failed to load seed
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.failed_invalid_seed);
                break;
        }

        mContext.sendBroadcast(finishIntent);
        super.onPostExecute(pResult);
    }
}
