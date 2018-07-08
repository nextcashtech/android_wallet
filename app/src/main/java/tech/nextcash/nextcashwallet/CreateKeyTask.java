package tech.nextcash.nextcashwallet;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.v4.content.LocalBroadcastManager;

import java.util.Locale;


public class CreateKeyTask extends AsyncTask<String, Integer, Integer>
{
    private Context mContext;
    private Bitcoin mBitcoin;
    private String mPasscode;
    private String mSeed;
    private int mDerivationMethod;
    private boolean mStartNewPass;
    private boolean mIsBackedUp;

    public CreateKeyTask(Context pContext, Bitcoin pBitcoin, String pPasscode, String pSeed,
      int pDerivationMethod, boolean pStartNewPass, boolean pIsBackedUp)
    {
        mContext = pContext;
        mBitcoin = pBitcoin;
        mPasscode = pPasscode;
        mSeed = pSeed;
        mDerivationMethod = pDerivationMethod;
        mStartNewPass = pStartNewPass;
        mIsBackedUp = pIsBackedUp;
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        String name = String.format(Locale.getDefault(), "%s %d", mContext.getString(R.string.wallet),
          mBitcoin.walletCount() + 1);
        return mBitcoin.addSeed(mPasscode, mSeed, mDerivationMethod, name, mStartNewPass, mIsBackedUp);
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
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.success_create_wallet);
                break;
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
