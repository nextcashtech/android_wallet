package tech.nextcash.nextcashwallet;

import android.graphics.Bitmap;
import android.os.AsyncTask;


public class CreatePaymentRequestTask extends AsyncTask<String, Integer, Integer>
{
    private MainActivity mActivity;
    private Bitcoin mBitcoin;
    private PaymentRequest mPaymentRequest;
    private String mText;
    private Bitmap mQRCode;

    public CreatePaymentRequestTask(MainActivity pActivity, Bitcoin pBitcoin, PaymentRequest pPaymentRequest)
    {
        mActivity = pActivity;
        mBitcoin = pBitcoin;
        mPaymentRequest = pPaymentRequest;
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        if(!mPaymentRequest.encode())
            return 1;

        mQRCode = mBitcoin.qrCode(mPaymentRequest.code);
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
                    mActivity.displayPaymentCode(mPaymentRequest, mQRCode);
                    break;
                case 1: // Unknown error
                    mActivity.showMessage(mActivity.getString(R.string.failed_generate_payment_code), 2000);
                    break;
            }
        }

        super.onPostExecute(pResult);
    }
}
