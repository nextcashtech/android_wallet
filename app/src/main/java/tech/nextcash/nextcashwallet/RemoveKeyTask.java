package tech.nextcash.nextcashwallet;

import android.os.AsyncTask;
import android.util.Log;


public class RemoveKeyTask extends AsyncTask<String, Integer, Integer>
{
    public static final String logTag = "RemoveKeyTask";

    private MainActivity mActivity;
    private Bitcoin mBitcoin;
    private String mPassCode;
    private int mOffset;

    public RemoveKeyTask(MainActivity pActivity, Bitcoin pBitcoin, String pPassCode, int pOffset)
    {
        mActivity = pActivity;
        mBitcoin = pBitcoin;
        mPassCode = pPassCode;
        mOffset = pOffset;
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        int result = mBitcoin.removeKey(mPassCode, mOffset);

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
                    mActivity.showMessage(mActivity.getString(R.string.success_remove_wallet), 2000);
                    break;
                case 1: // Unknown error
                    mActivity.showMessage(mActivity.getString(R.string.failed_remove_wallet), 2000);
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
            {
                Log.i(logTag, "Clearing after removing wallet");
                mActivity.clear();
                mActivity.updateWallets();
            }
        }

        super.onPostExecute(pResult);
    }
}
