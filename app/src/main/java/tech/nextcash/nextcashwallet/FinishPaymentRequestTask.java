package tech.nextcash.nextcashwallet;

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


public class FinishPaymentRequestTask extends AsyncTask<String, Integer, Integer>
{
    public static final String logTag = "FinishPaymentRequest";

    private static final String PAYMENT_TYPE = "application/bitcoincash-payment";
    private static final String ACK_TYPE = "application/bitcoincash-paymentack";

    private MainActivity mActivity;
    private int mWalletOffset;
    private PaymentRequest mPaymentRequest;
    private HttpURLConnection mConnection;
    private PaymentRequestBufferProtocols.Payment mProtocolPayment;
    private PaymentRequestBufferProtocols.PaymentACK mProtocolAcknowledge;
    private String mMessage;


    public FinishPaymentRequestTask(MainActivity pActivity, int pWalletOffset, PaymentRequest pPaymentRequest)
    {
        mActivity = pActivity;
        mWalletOffset = pWalletOffset;
        mPaymentRequest = pPaymentRequest;
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

            Bitcoin bitcoin = ((MainApp)mActivity.getApplication()).bitcoin;

            // Get sent transaction
            byte paymentScript[] = null;
            long amount = 0;
            for(PaymentRequestBufferProtocols.Output output : mPaymentRequest.protocolDetails.getOutputsList())
                if(output.hasAmount() && output.hasScript())
                {
                    paymentScript = output.getScript().toByteArray();
                    amount = output.getAmount();
                    break;
                }

            if(paymentScript == null)
            {
                mMessage = mActivity.getString(R.string.failed_transaction);
                Log.e(logTag, "Failed to find payment output");
                return false;
            }

            byte rawTransaction[] = bitcoin.getRawTransaction(paymentScript, amount);
            if(rawTransaction == null)
            {
                mMessage = mActivity.getString(R.string.failed_transaction);
                Log.e(logTag, "Failed to find payment transaction");
                return false;
            }

            paymentBuilder.addTransactions(ByteString.copyFrom(rawTransaction));

            // Build refund output
            byte refundOutput[] = bitcoin.getNextReceiveOutput(mWalletOffset, 0);
            if(refundOutput == null)
            {
                mMessage = mActivity.getString(R.string.failed_transaction);
                Log.e(logTag, "Failed to generate refund output");
                return false;
            }

            PaymentRequestBufferProtocols.Output.Builder refundBuilder =
              PaymentRequestBufferProtocols.Output.newBuilder();
            refundBuilder.setAmount(amount);
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
            mMessage = mActivity.getString(R.string.failed_url) + " : " + pException.toString();
            Log.e(logTag, String.format("Invalid Payment URL : %s", pException.toString()));
            return false;
        }
        catch(IOException pException)
        {
            mMessage = mActivity.getString(R.string.failed_connection) + " : " + pException.toString();
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
                mMessage = mActivity.getString(R.string.failed_connection) + String.format(Locale.getDefault(),
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
                mMessage = mActivity.getString(R.string.failed_invalid_message);

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
            mMessage = mActivity.getString(R.string.failed_invalid_acknowledge);
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
        if(!mActivity.isDestroyed() && !mActivity.isFinishing())
        {
            if(mMessage != null)
                mActivity.showMessage(mMessage, 2000);
            mActivity.clearPaymentProcess();
        }

        super.onPostExecute(pResult);
    }
}
