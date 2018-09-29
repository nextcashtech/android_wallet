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
import android.graphics.Bitmap;
import android.os.AsyncTask;


public class CreateAddressTask extends AsyncTask<String, Integer, Integer>
{
    private Context mContext;
    private Bitcoin mBitcoin;
    private PaymentRequest mPaymentRequest;
    private Bitmap mQRCode;

    public CreateAddressTask(Context pContext, Bitcoin pBitcoin, PaymentRequest pPaymentRequest, Bitmap pQRCode)
    {
        mContext = pContext;
        mBitcoin = pBitcoin;
        mPaymentRequest = pPaymentRequest;
        mQRCode = pQRCode;

        mPaymentRequest.clear();
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        if(!mBitcoin.generateQRCode(mPaymentRequest.uri, mQRCode))
            return 1;

        return 0;
    }

    @Override
    protected void onPostExecute(Integer pResult)
    {
        Intent finishIntent = new Intent(MainActivity.ACTIVITY_ACTION);

        // Send intent back to activity
        switch(pResult)
        {
            case 0: // Success
                finishIntent.setAction(MainActivity.ACTION_DISPLAY_REQUEST_PAYMENT);
                break;
            default:
            case 1: // Unknown error
                finishIntent.setAction(MainActivity.ACTION_DISPLAY_WALLETS); // Return from "in progress"
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.failed_generate_qr);
                break;
        }

        mContext.sendBroadcast(finishIntent);
        super.onPostExecute(pResult);
    }
}
