package tech.nextcash.nextcashwallet;

import android.os.AsyncTask;


public class CreateTransactionTask extends AsyncTask<String, Integer, Integer>
{
    private MainActivity mActivity;
    private Bitcoin mBitcoin;
    private int mWalletOffset;
    private String mAddress, mPassCode;
    private long mAmount;
    private double mFeeRate;
    private boolean mSendAll;

    public CreateTransactionTask(MainActivity pActivity, Bitcoin pBitcoin, String pPassCode, int pWalletOffset,
      String pAddress, long pAmount, double pFeeRate, boolean pSendAll)
    {
        mActivity = pActivity;
        mBitcoin = pBitcoin;
        mPassCode = pPassCode;
        mWalletOffset = pWalletOffset;
        mAddress = pAddress;
        mAmount = pAmount;
        mFeeRate = pFeeRate;
        mSendAll = pSendAll;
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        return mBitcoin.sendPayment(mWalletOffset, mPassCode, mAddress, mAmount, mFeeRate, mSendAll);
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
                    mActivity.showMessage(mActivity.getString(R.string.sent_payment), 2000);
                    break;
                default:
                case 1: // Unknown error
                    mActivity.showMessage(mActivity.getString(R.string.failed_send_payment), 2000);
                    break;
                case 2: // Insufficient Funds
                    mActivity.showMessage(mActivity.getString(R.string.failed_insufficient_funds), 2000);
                    break;
                case 3: // Invalid Address
                    mActivity.showMessage(mActivity.getString(R.string.failed_invalid_address), 2000);
                    break;
                case 4: // No Change Address
                    mActivity.showMessage(mActivity.getString(R.string.failed_change_address), 2000);
                    break;
                case 5: // Signing Failed
                    mActivity.showMessage(mActivity.getString(R.string.failed_signing), 2000);
                    break;
                case 6: // Below dust
                    mActivity.showMessage(mActivity.getString(R.string.failed_dust), 2000);
                    break;
            }

            mActivity.updateWallets();
        }

        super.onPostExecute(pResult);
    }
}
