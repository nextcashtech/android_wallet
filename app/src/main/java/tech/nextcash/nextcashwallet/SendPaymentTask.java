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
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;


public class SendPaymentTask extends AsyncTask<String, Integer, Integer>
{
    public static final String logTag = "SendPaymentTask";

    private static final String PAYMENT_TYPE = "application/bitcoincash-payment";
    private static final String ACK_TYPE = "application/bitcoincash-paymentack";

    private Context mContext;
    private Bitcoin mBitcoin;
    private int mWalletOffset;
    private PaymentRequest mRequest;
    private String mPassCode;
    private SendResult mCreateResult;

    private HttpURLConnection mConnection;
    private String mMessage;

    // Create transaction to send payment.
    public SendPaymentTask(Context pContext, Bitcoin pBitcoin, String pPassCode, int pWalletOffset,
      PaymentRequest pRequest)
    {
        mContext = pContext;
        mBitcoin = pBitcoin;
        mPassCode = pPassCode;
        mWalletOffset = pWalletOffset;
        mRequest = pRequest;
    }

    private boolean sendPaymentTransaction()
    {
        if(mCreateResult.rawTransaction == null)
        {
            Log.e(logTag, "Returned null raw transaction when needed for payment URL");
            return false;
        }

        if(!mRequest.protocolDetails.hasPaymentUrl())
        {
            Log.e(logTag, "Missing payment URL");
            return false;
        }

        try
        {
            // Build payment message
            PaymentRequestBufferProtocols.Payment.Builder paymentBuilder =
              PaymentRequestBufferProtocols.Payment.newBuilder();
            if(mRequest.protocolDetails.hasMerchantData())
                paymentBuilder.setMerchantData(mRequest.protocolDetails.getMerchantData());

            if(mCreateResult.rawTransaction == null)
            {
                mMessage = mContext.getString(R.string.failed_transaction);
                Log.e(logTag, "Failed to find payment transaction");
                return false;
            }

            paymentBuilder.addTransactions(ByteString.copyFrom(mCreateResult.rawTransaction));

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
            refundBuilder.setAmount(mRequest.amount);
            refundBuilder.setScript(ByteString.copyFrom(refundOutput));

            paymentBuilder.addRefundTo(refundBuilder.build());

            PaymentRequestBufferProtocols.Payment payment = paymentBuilder.build();

            // Send payment info
            URL url = new URL(mRequest.protocolDetails.getPaymentUrl());
            if(url.getProtocol().equals("https"))
                mConnection = (HttpsURLConnection)url.openConnection();
            else if(url.getProtocol().equals("http"))
                mConnection = (HttpURLConnection)url.openConnection();
            mConnection.setRequestProperty("content-type", PAYMENT_TYPE);
            mConnection.setRequestProperty("accept", ACK_TYPE);
            mConnection.setRequestProperty("user-agent", Bitcoin.userAgent());
            mConnection.setRequestMethod("POST");

            payment.writeTo(mConnection.getOutputStream());
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

    private boolean checkPaymentAcknowledge()
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

            PaymentRequestBufferProtocols.PaymentACK acknowledge =
              PaymentRequestBufferProtocols.PaymentACK.parseFrom(mConnection.getInputStream());

            if(acknowledge.hasMemo())
                mMessage = acknowledge.getMemo();

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
        if(mRequest.protocolDetails != null && mRequest.specifiedOutputs != null)
            mCreateResult = mBitcoin.sendOutputsPayment(mWalletOffset, mPassCode, mRequest.specifiedOutputs,
              mRequest.feeRate, mRequest.requiresPending(), !mRequest.protocolDetails.hasPaymentUrl());
        else if(mRequest.type == PaymentRequest.TYPE_PUB_KEY_HASH || mRequest.type == PaymentRequest.TYPE_SCRIPT_HASH)
            mCreateResult = mBitcoin.sendStandardPayment(mWalletOffset, mPassCode, mRequest.address, mRequest.amount,
              mRequest.feeRate, mRequest.requiresPending(), mRequest.sendMax);
        else
            mCreateResult = new SendResult(3);

        if(mCreateResult.result != 0)
            return mCreateResult.result;

        // Update transaction data
        if(mCreateResult.transaction != null)
        {
            boolean updated = mBitcoin.updateTransactionData(mCreateResult.transaction, mWalletOffset);

            String description = mRequest.description(" : ");
            if(description != null && description.length() > 0)
            {
                if(mCreateResult.transaction.data.comment == null)
                {
                    mCreateResult.transaction.data.comment = description;
                    updated = true;
                }
            }

            if(updated)
                mBitcoin.saveTransactionData();
        }

        // Send BIP-0070 payment transaction.
        if(mRequest.protocolDetails != null && mRequest.protocolDetails.hasPaymentUrl())
        {
            if(!sendPaymentTransaction())
                return 1;

            if(!checkPaymentAcknowledge())
                return 1;

            return 0;
        }
        else
            return mCreateResult.result;
    }

    @Override
    protected void onPostExecute(Integer pResult)
    {
        Intent finishIntent = new Intent(MainActivity.ACTIVITY_ACTION);

        finishIntent.setAction(MainActivity.ACTION_CLEAR_PAYMENT);

        if(mMessage != null)
        {
            finishIntent.putExtra(MainActivity.ACTION_MESSAGE_STRING_FIELD, mMessage);
            finishIntent.putExtra(MainActivity.ACTION_MESSAGE_PERSISTENT_FIELD, true);
        }
        else
        {
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
            case 7: // Invalid outputs specified
                finishIntent.putExtra(MainActivity.ACTION_MESSAGE_ID_FIELD, R.string.failed_outputs);
                break;
            }
        }

        mContext.sendBroadcast(finishIntent);
        super.onPostExecute(pResult);
    }
}
