package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;

public class CreateQRCode extends AsyncTask<String, Integer, Integer>
{
    private Context mContext;
    private Bitcoin mBitcoin;
    private String mText;
    private Bitmap mQRCode;

    public enum Type {PAYMENT_CODE, RECEIVING_CHAIN, CHANGE_CHAIN }

    private Type mType;

    public CreateQRCode(Context pContext, Bitcoin pBitcoin, String pText, Bitmap pQRCode, Type pType)
    {
        mContext = pContext;
        mBitcoin = pBitcoin;
        mText = pText;
        mQRCode = pQRCode;
        mType = pType;
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        if(mBitcoin.generateQRCode(mText, mQRCode))
            return 0;
        else
            return 1;
    }

    @Override
    protected void onPostExecute(Integer pResult)
    {
        Intent finishIntent = new Intent(MainActivity.ACTIVITY_ACTION);

        // Send intent back to activity
        switch(pResult)
        {
        case 0: // Success
            switch(mType)
            {
            case PAYMENT_CODE:
                finishIntent.setAction(MainActivity.ACTION_PAYMENT_QR);
                break;
            case RECEIVING_CHAIN:
                finishIntent.setAction(MainActivity.ACTION_RECEIVING_QR);
                break;
            case CHANGE_CHAIN:
                finishIntent.setAction(MainActivity.ACTION_CHANGE_QR);
                break;
            }
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
