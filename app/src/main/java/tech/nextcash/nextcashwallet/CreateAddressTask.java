package tech.nextcash.nextcashwallet;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import java.util.Locale;


public class CreateAddressTask extends AsyncTask<String, Integer, Integer>
{
    private MainActivity mActivity;
    private Bitcoin mBitcoin;
    private int mWalletOffset, mChainIndex;
    private String mText;
    private Bitmap mQRCode;

    public CreateAddressTask(MainActivity pActivity, Bitcoin pBitcoin, int pWalletOffset, int pChainIndex)
    {
        mActivity = pActivity;
        mBitcoin = pBitcoin;
        mWalletOffset = pWalletOffset;
        mChainIndex = pChainIndex;
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        mText = mBitcoin.getNextReceiveAddress(mWalletOffset, mChainIndex);
        if(mText == null)
            return 1;

        mQRCode = mBitcoin.qrCode(mText);
        if(mQRCode == null)
            return 1;

        return 0;
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
                    mActivity.displayAddress(mText, mQRCode);
                    break;
                case 1: // Unknown error
                    mActivity.showMessage(mActivity.getString(R.string.failed_generate_address), 2000);
                    break;
            }
        }

        super.onPostExecute(pResult);
    }
}
