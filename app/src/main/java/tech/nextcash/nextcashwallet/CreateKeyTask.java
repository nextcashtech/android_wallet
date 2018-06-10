package tech.nextcash.nextcashwallet;

import android.os.AsyncTask;

import java.util.Locale;


public class CreateKeyTask extends AsyncTask<String, Integer, Integer>
{
    private MainActivity mActivity;
    private Bitcoin mBitcoin;
    private String mPasscode;
    private String mSeed;
    private int mDerivationMethod;
    private boolean mStartNewPass;
    private boolean mIsBackedUp;

    public CreateKeyTask(MainActivity pActivity, Bitcoin pBitcoin, String pPasscode, String pSeed,
      int pDerivationMethod, boolean pStartNewPass, boolean pIsBackedUp)
    {
        mActivity = pActivity;
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
        String name = String.format(Locale.getDefault(), "%s %d", mActivity.getString(R.string.wallet),
          mBitcoin.wallets.length + 1);
        int result = mBitcoin.addSeed(mPasscode, mSeed, mDerivationMethod, name, mStartNewPass, mIsBackedUp);

        if(result == 0)
            mBitcoin.update(true);

        return result;
    }

    @Override
    protected void onPostExecute(Integer pResult)
    {
        if(!mActivity.isDestroyed() && !mActivity.isFinishing())
        {
            // Send intent back to activity
            switch(pResult)
            {
                case 0: // Success
                    mActivity.showMessage(mActivity.getString(R.string.success_create_wallet), 2000);
                    break;
                case 1: // Unknown error
                    mActivity.showMessage(mActivity.getString(R.string.failed_create_wallet), 2000);
                    break;
                case 2: // Invalid format
                    mActivity.showMessage(mActivity.getString(R.string.failed_key_import_format), 2000);
                    break;
                case 3: // Already exists
                    mActivity.showMessage(mActivity.getString(R.string.failed_key_import_exists), 2000);
                    break;
                case 4: // Invalid derivation method
                    mActivity.showMessage(mActivity.getString(R.string.failed_key_import_method), 2000);
                    break;
                case 5: // Invalid pass code
                    mActivity.showMessage(mActivity.getString(R.string.failed_invalid_passcode), 2000);
                    break;
                case 6: // Failed to load seed
                    mActivity.showMessage(mActivity.getString(R.string.failed_invalid_seed), 2000);
                    break;
            }

            if(mBitcoin.isLoaded())
                mActivity.updateWallets();
        }

        super.onPostExecute(pResult);
    }
}
