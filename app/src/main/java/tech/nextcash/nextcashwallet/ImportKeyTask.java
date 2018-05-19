package tech.nextcash.nextcashwallet;

import android.os.AsyncTask;


public class ImportKeyTask extends AsyncTask<String, Integer, Integer>
{
    private MainActivity mActivity;
    private Bitcoin mBitcoin;
    private String mPasscode;
    private String mKey;
    private int mDerivationMethod;

    public ImportKeyTask(MainActivity pActivity, Bitcoin pBitcoin, String pPasscode, String pKey, int pDerivationMethod)
    {
        mActivity = pActivity;
        mBitcoin = pBitcoin;
        mPasscode = pPasscode;
        mKey = pKey;
        mDerivationMethod = pDerivationMethod;
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        return mBitcoin.addKey(mPasscode, mKey, mDerivationMethod);
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
                    mActivity.showMessage(mActivity.getString(R.string.success_key_import), 2000);
                    break;
                case 1: // Unknown error
                    mActivity.showMessage(mActivity.getString(R.string.failed_key_import), 2000);
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
            }

            if(mBitcoin.isLoaded())
                mActivity.updateWallets();
        }

        super.onPostExecute(pResult);
    }
}
