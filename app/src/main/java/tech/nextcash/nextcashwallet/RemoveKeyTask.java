package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;


public class RemoveKeyTask extends AsyncTask<String, Integer, Integer>
{
    public static final String logTag = "RemoveKeyTask";

    private Context mContext;
    private Bitcoin mBitcoin;
    private String mPassCode;
    private int mOffset;

    public RemoveKeyTask(Context pContext, Bitcoin pBitcoin, String pPassCode, int pOffset)
    {
        mContext = pContext;
        mBitcoin = pBitcoin;
        mPassCode = pPassCode;
        mOffset = pOffset;
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        return mBitcoin.removeKey(mPassCode, mOffset);
    }

    @Override
    protected void onPostExecute(Integer pResult)
    {
        Intent finishIntent = new Intent(MainActivity.ACTIVITY_ACTION);
        finishIntent.setAction(MainActivity.ACTION_DISPLAY_SETTINGS);

        // Send intent back to activity
        switch(pResult)
        {
            case 0: // Success
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.success_remove_wallet);
                break;
            case 1: // Unknown error
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.failed_remove_wallet);
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
