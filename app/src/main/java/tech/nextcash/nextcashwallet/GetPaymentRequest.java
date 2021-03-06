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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.auth.x500.X500Principal;


// Fetch BIP-0070 payment request data from server.
public class GetPaymentRequest extends AsyncTask<String, Integer, Integer>
{
    public static final String logTag = "GetPaymentRequest";

    private Context mContext;
    private PaymentRequest mPaymentRequest;
    private URL mURL;
    private HttpURLConnection mConnection;

    private static final String REQUEST_TYPE = "application/bitcoincash-paymentrequest";

    private String mMessage;
    private boolean mIsFailed;

    // pPaymentRequest payment request in which to put received data.
    public GetPaymentRequest(Context pContext, PaymentRequest pPaymentRequest)
    {
        mContext = pContext;
        mPaymentRequest = pPaymentRequest;

        // Clear any previous values from request
        mPaymentRequest.amountSpecified = false;
        mPaymentRequest.amount = 0;
        mPaymentRequest.label = null;
        mPaymentRequest.message = null;
    }

    private boolean connect()
    {
        try
        {
            mURL = new URL(mPaymentRequest.secureURL);
            mPaymentRequest.site = mURL.getHost();
            if(mURL.getProtocol().equals("https"))
                mConnection = (HttpsURLConnection)mURL.openConnection();
            else if(mURL.getProtocol().equals("http"))
                mConnection = (HttpURLConnection)mURL.openConnection();
            mConnection.setRequestProperty("accept", REQUEST_TYPE);
            mConnection.setRequestProperty("user-agent", Bitcoin.userAgent());
        }
        catch(MalformedURLException pException)
        {
            mIsFailed = true;
            mMessage = mContext.getString(R.string.failed_url) + " : " + pException.toString();
            Log.e(logTag, String.format("Invalid URL : %s", pException.toString()));
            return false;
        }
        catch(IOException pException)
        {
            mIsFailed = true;
            mMessage = mContext.getString(R.string.failed_connection) + " : " + pException.toString();
            Log.e(logTag, String.format("Connection Error : %s", pException.toString()));
            return false;
        }

        try
        {
            int responseCode = mConnection.getResponseCode();
            if(responseCode != 200)
            {
                mIsFailed = true;
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

            Log.d(logTag, String.format(Locale.US, "Response Code : %d", responseCode));

            if(mURL.getProtocol().equals("https"))
            {
                Log.d(logTag, String.format(Locale.US, "Cipher Suite : %s",
                  ((HttpsURLConnection)mConnection).getCipherSuite()));

                CertificateFactory factory = CertificateFactory.getInstance("X509");
                Certificate[] certificates = ((HttpsURLConnection)mConnection).getServerCertificates();

                if(certificates.length == 0)
                {
                    mIsFailed = true;
                    mMessage = mContext.getString(R.string.failed_ssl_verify) + " : No certificates";
                    return false;
                }

                X509Certificate x509;
                X500Principal principal;
                Log.d(logTag, String.format(Locale.US, "Certificate (type - algo - format) : %s - %s - %s",
                  certificates[0].getType(), certificates[0].getPublicKey().getAlgorithm(),
                  certificates[0].getPublicKey().getFormat()));

                x509 = (X509Certificate)factory.generateCertificate(new ByteArrayInputStream(certificates[0].getEncoded()));
                principal = x509.getSubjectX500Principal();

                // Parse out CN
                String fields[] = principal.getName().split(",");
                String name = null;
                for(String field : fields)
                    if(field.startsWith("CN="))
                    {
                        name = field.substring(3);
                        mPaymentRequest.label = name;
                        break;
                    }
                if(name != null)
                    Log.d(logTag, String.format("Certificate name : %s", name));
            }

            return true;
        }
        catch(SSLPeerUnverifiedException|CertificateException pException)
        {
            mIsFailed = true;
            mMessage = mContext.getString(R.string.failed_ssl_verify) + " : " + pException.toString();
            Log.e(logTag, String.format("Invalid Certificate : %s", pException.toString()));
            return false;
        }
        catch(IOException pException)
        {
            mIsFailed = true;
            mMessage = mContext.getString(R.string.failed_connection) + " : " + pException.toString();
            Log.e(logTag, String.format("Connection Failed : %s", pException.toString()));
            return false;
        }
    }

    private boolean getRequest()
    {
        try
        {
            String contentType = mConnection.getContentType();
            if(!contentType.equals(REQUEST_TYPE))
            {
                Log.e(logTag, String.format("Invalid payment request content type : %s", contentType));
                mIsFailed = true;
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

            PaymentRequestBufferProtocols.PaymentRequest request =
              PaymentRequestBufferProtocols.PaymentRequest.parseFrom(mConnection.getInputStream());
            mPaymentRequest.protocolDetails =
              PaymentRequestBufferProtocols.PaymentDetails.parseFrom(request.getSerializedPaymentDetails());

            // TODO Verify BIP-0070 PKI certificate
            //if(request.hasPkiType())
        }
        catch(IOException pException)
        {
            Log.e(logTag, String.format("Error reading payment request : %s",
              pException.toString()));
            mIsFailed = true;
            mMessage = mContext.getString(R.string.failed_invalid_message);
            return false;
        }

        // Check network
        if(!mPaymentRequest.protocolDetails.getNetwork().equals("main"))
        {
            Log.e(logTag, String.format("Payment request not for main network : %s",
              mPaymentRequest.protocolDetails.getNetwork()));
            mIsFailed = true;
            mMessage = mContext.getString(R.string.failed_not_main_network);
            return false;
        }

        // Check expires
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        if(!mPaymentRequest.protocolDetails.hasExpires() ||
          mPaymentRequest.protocolDetails.getExpires() < calendar.getTimeInMillis() / 1000L)
        {
            Log.e(logTag, String.format("Payment request expired at %1$tY-%1$tm-%1$td %1$tH:%1$tM",
              mPaymentRequest.protocolDetails.getExpires()));
            mIsFailed = true;
            mMessage = mContext.getString(R.string.failed_request_expired);
            return false;
        }

        Output newOutput;
        mPaymentRequest.amount = 0;
        mPaymentRequest.specifiedOutputs = null;
        ArrayList<Output> outputs = new ArrayList<>();
        for(PaymentRequestBufferProtocols.Output output : mPaymentRequest.protocolDetails.getOutputsList())
            if(output.hasAmount() && output.hasScript())
            {
                mPaymentRequest.amount += output.getAmount();
                mPaymentRequest.amountSpecified = true;
                newOutput = new Output();
                newOutput.amount = output.getAmount();
                newOutput.scriptData = output.getScript().toByteArray();
                outputs.add(newOutput);
                break;
            }

        if(outputs.size() > 0)
        {
            mPaymentRequest.specifiedOutputs = new Output[outputs.size()];
            outputs.toArray(mPaymentRequest.specifiedOutputs);
        }
        else
        {
            Log.e(logTag, "Payment request does not contain supported payment method");
            mIsFailed = true;
            mMessage = mContext.getString(R.string.failed_invalid_request);
            return false;
        }

        mPaymentRequest.message = mPaymentRequest.protocolDetails.getMemo();

        if(mURL.getProtocol().equals("https"))
            mPaymentRequest.secure = true;

        return true;
    }

    @Override
    protected Integer doInBackground(String... pStrings)
    {
        if(!connect())
        {
            mIsFailed = true;
            return 1;
        }

        if(!getRequest())
        {
            mIsFailed = true;
            return 1;
        }

        return 0;
    }

    @Override
    protected void onPostExecute(Integer pResult)
    {
        Intent finishIntent = new Intent(MainActivity.ACTIVITY_ACTION);

        if(mMessage != null)
            finishIntent.putExtra(MainActivity.ACTION_MESSAGE_STRING_FIELD, mMessage);

        if(mIsFailed)
            finishIntent.setAction(MainActivity.ACTION_CLEAR_PAYMENT);
        else
            finishIntent.setAction(MainActivity.ACTION_DISPLAY_ENTER_PAYMENT);

        mContext.sendBroadcast(finishIntent);
        super.onPostExecute(pResult);
    }
}
