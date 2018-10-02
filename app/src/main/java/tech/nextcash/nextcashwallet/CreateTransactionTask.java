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


public class CreateTransactionTask extends AsyncTask<String, Integer, Integer>
{
    private Context mContext;
    private Bitcoin mBitcoin;
    private int mWalletOffset;
    private PaymentRequest mRequest;
    private String mPassCode;

    public CreateTransactionTask(Context pContext, Bitcoin pBitcoin, String pPassCode, int pWalletOffset,
      PaymentRequest pRequest)
    {
        mContext = pContext;
        mBitcoin = pBitcoin;
        mPassCode = pPassCode;
        mWalletOffset = pWalletOffset;
        mRequest = pRequest;
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        int result;
        if(mRequest.paymentScript != null)
            result = mBitcoin.sendOutputPayment(mWalletOffset, mPassCode, mRequest.paymentScript, mRequest.amount,
              mRequest.feeRate, mRequest.usePending);
        else if(mRequest.type == PaymentRequest.TYPE_PUB_KEY_HASH || mRequest.type == PaymentRequest.TYPE_SCRIPT_HASH)
            result = mBitcoin.sendStandardPayment(mWalletOffset, mPassCode, mRequest.address, mRequest.amount,
              mRequest.feeRate, mRequest.usePending, mRequest.sendMax);
        else
            result = 3;

        return result;
    }

    @Override
    protected void onPostExecute(Integer pResult)
    {
        Intent finishIntent = new Intent(MainActivity.ACTIVITY_ACTION);

        if(pResult == 0 && mRequest.protocolDetails != null && mRequest.protocolDetails.hasPaymentUrl())
            finishIntent.setAction(MainActivity.ACTION_ACKNOWLEDGE_PAYMENT);
        else
            finishIntent.setAction(MainActivity.ACTION_DISPLAY_WALLETS); // Return from "in progress"

        // Send intent back to activity
        switch(pResult)
        {
            case 0: // Success
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.sent_payment);
                break;
            default:
            case 1: // Unknown error
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.failed_send_payment);
                break;
            case 2: // Insufficient Funds
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.failed_insufficient_funds);
                break;
            case 3: // Invalid Address
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.failed_invalid_address);
                break;
            case 4: // No Change Address
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.failed_change_address);
                break;
            case 5: // Signing Failed
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.failed_signing);
                break;
            case 6: // Below dust
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.failed_dust);
                break;
        }

        mContext.sendBroadcast(finishIntent);
        super.onPostExecute(pResult);
    }
}
