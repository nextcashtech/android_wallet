package tech.nextcash.nextcashwallet;

import android.os.AsyncTask;


public class CreateTransactionTask extends AsyncTask<String, Integer, Integer>
{
    private MainActivity mActivity;
    private Bitcoin mBitcoin;
    private int mWalletOffset;
    public PaymentRequest mRequest;
    private String mPassCode;

    public CreateTransactionTask(MainActivity pActivity, Bitcoin pBitcoin, String pPassCode, int pWalletOffset,
      PaymentRequest pRequest)
    {
        mActivity = pActivity;
        mBitcoin = pBitcoin;
        mPassCode = pPassCode;
        mWalletOffset = pWalletOffset;
        mRequest = pRequest;
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        int result = 1;
        if(mRequest.paymentScript != null)
            result = mBitcoin.sendOutputPayment(mWalletOffset, mPassCode, mRequest.paymentScript, mRequest.amount,
              mRequest.feeRate, mRequest.usePending);
        else
            result = mBitcoin.sendP2PKHPayment(mWalletOffset, mPassCode, mRequest.address, mRequest.amount,
              mRequest.feeRate, mRequest.usePending, mRequest.sendMax);

        if(result == 0)
            mActivity.acknowledgePaymentSent();

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
