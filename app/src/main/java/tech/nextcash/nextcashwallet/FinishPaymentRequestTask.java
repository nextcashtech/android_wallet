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
import android.util.Log;

import com.google.protobuf.ByteString;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;


public class FinishPaymentRequestTask extends AsyncTask<String, Integer, Integer>
{
    public static final String logTag = "FinishPaymentRequest";

    private static final String PAYMENT_TYPE = "application/bitcoincash-payment";
    private static final String ACK_TYPE = "application/bitcoincash-paymentack";

    private Context mContext;
    private Bitcoin mBitcoin;
    private int mWalletOffset;
    private PaymentRequest mPaymentRequest;
    private HttpURLConnection mConnection;
    private PaymentRequestBufferProtocols.Payment mProtocolPayment;
    private PaymentRequestBufferProtocols.PaymentACK mProtocolAcknowledge;
    private String mMessage;
    private byte mRawTransaction[];


    public FinishPaymentRequestTask(Context pContext, Bitcoin pBitcoin, int pWalletOffset,
      PaymentRequest pPaymentRequest, byte pRawTransaction[])
    {
        mContext = pContext;
        mBitcoin = pBitcoin;
        mWalletOffset = pWalletOffset;
        mPaymentRequest = pPaymentRequest;
        mMessage = null;
        mRawTransaction = pRawTransaction;
    }

    private boolean sendPayment()
    {
        if(!mPaymentRequest.protocolDetails.hasPaymentUrl())
            return true;

        try
        {
            // Build payment message
            PaymentRequestBufferProtocols.Payment.Builder paymentBuilder =
              PaymentRequestBufferProtocols.Payment.newBuilder();
            if(mPaymentRequest.protocolDetails.hasMerchantData())
                paymentBuilder.setMerchantData(mPaymentRequest.protocolDetails.getMerchantData());

            if(mRawTransaction == null)
            {
                mMessage = mContext.getString(R.string.failed_transaction);
                Log.e(logTag, "Failed to find payment transaction");
                return false;
            }

            paymentBuilder.addTransactions(ByteString.copyFrom(mRawTransaction));

            // Build refund output
            byte refundOutput[] = mBitcoin.getNextReceiveOutput(mWalletOffset, 0);
            if(refundOutput == null)
            {
                mMessage = mContext.getString(R.string.failed_transaction);
                Log.e(logTag, "Failed to generate refund output");
                return false;
            }

            PaymentRequestBufferProtocols.Output.Builder refundBuilder =
              PaymentRequestBufferProtocols.Output.newBuilder();
            refundBuilder.setAmount(mPaymentRequest.amount);
            refundBuilder.setScript(ByteString.copyFrom(refundOutput));

            paymentBuilder.addRefundTo(refundBuilder.build());

            mProtocolPayment = paymentBuilder.build();

            // Send payment info
            URL url = new URL(mPaymentRequest.protocolDetails.getPaymentUrl());
            if(url.getProtocol().equals("https"))
                mConnection = (HttpsURLConnection)url.openConnection();
            else if(url.getProtocol().equals("http"))
                mConnection = (HttpURLConnection)url.openConnection();
            mConnection.setRequestProperty("content-type", PAYMENT_TYPE);
            mConnection.setRequestProperty("accept", ACK_TYPE);
            mConnection.setRequestProperty("user-agent", Bitcoin.userAgent());
            mConnection.setRequestMethod("POST");

            mProtocolPayment.writeTo(mConnection.getOutputStream());
            return true;
        }
        catch(MalformedURLException pException)
        {
            mMessage = mContext.getString(R.string.failed_url) + " : " + pException.toString();
            Log.e(logTag, String.format("Invalid Payment URL : %s", pException.toString()));
            return false;
        }
        catch(IOException pException)
        {
            mMessage = mContext.getString(R.string.failed_connection) + " : " + pException.toString();
            Log.e(logTag, String.format("Payment Connection Error : %s", pException.toString()));
            return false;
        }
    }

    private boolean checkAcknowledge()
    {
        try
        {
            int responseCode = mConnection.getResponseCode();
            if(responseCode != 200)
            {
                Log.e(logTag, String.format("Invalid payment acknowledge response code : %d", responseCode));
                mMessage = mContext.getString(R.string.failed_connection) + String.format(Locale.getDefault(),
                  " : %d", responseCode);

                BufferedReader reader = new BufferedReader(new InputStreamReader(mConnection.getErrorStream()));
                String line;
                while(true)
                {
                    line = reader.readLine();

                    if(line == null)
                        break;

                    Log.e(logTag, String.format("Error Message : %s", line));
                }
                return false;
            }

            String contentType = mConnection.getContentType();
            if(!contentType.equals(ACK_TYPE))
            {
                Log.e(logTag, String.format("Invalid payment acknowledge content type : %s", contentType));
                mMessage = mContext.getString(R.string.failed_invalid_message);

                if(contentType.startsWith("text/"))
                {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(mConnection.getInputStream()));
                    String line;
                    while(true)
                    {
                        line = reader.readLine();

                        if(line == null)
                            break;

                        Log.e(logTag, String.format("Content : %s", line));
                    }
                }

                return false;
            }

            mProtocolAcknowledge = PaymentRequestBufferProtocols.PaymentACK.parseFrom(mConnection.getInputStream());

            if(mProtocolAcknowledge.hasMemo())
                mMessage = mProtocolAcknowledge.getMemo();

            return true;
        }
        catch(IOException pException)
        {
            Log.e(logTag, String.format("Error reading payment request : %s",
              pException.toString()));
            mMessage = mContext.getString(R.string.failed_invalid_acknowledge);
            return false;
        }
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        if(!sendPayment())
            return 1;

        if(!checkAcknowledge())
            return 1;

        return 0;
    }

    @Override
    protected void onPostExecute(Integer pResult)
    {
        Intent finishIntent = new Intent(MainActivity.ACTIVITY_ACTION);
        if(mMessage != null)
        {
            finishIntent.putExtra(MainActivity.ACTION_MESSAGE_STRING_FIELD, mMessage);
            finishIntent.putExtra(MainActivity.ACTION_MESSAGE_PERSISTENT_FIELD, true);
        }
        finishIntent.setAction(MainActivity.ACTION_CLEAR_PAYMENT);

        mContext.sendBroadcast(finishIntent);
        super.onPostExecute(pResult);
    }
}
