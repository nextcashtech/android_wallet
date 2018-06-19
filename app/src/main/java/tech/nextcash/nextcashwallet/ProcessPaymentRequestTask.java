package tech.nextcash.nextcashwallet;

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
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.auth.x500.X500Principal;


public class ProcessPaymentRequestTask extends AsyncTask<String, Integer, Integer>
{
    public static final String logTag = "ProcessPaymentRequest";

    private MainActivity mActivity;
    private int mWalletOffset;
    private PaymentRequest mPaymentRequest;
    private URL mURL;
    private HttpURLConnection mConnection;
    private PaymentRequestBufferProtocols.PaymentRequest mProtocolRequest;

    private static final String REQUEST_TYPE = "application/bitcoincash-paymentrequest";

    private String mName, mMessage;
    private boolean mIsFailed;

    public ProcessPaymentRequestTask(MainActivity pActivity, int pWalletOffset, PaymentRequest pPaymentRequest)
    {
        mActivity = pActivity;
        mWalletOffset = pWalletOffset;
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
            //.setRequestProperty("accept-transfer-encoding", "binary");
            //mConnection.setRequestProperty("accept-encoding", "binary");
            //mConnection.setRequestMethod("GET");
        }
        catch(MalformedURLException pException)
        {
            mIsFailed = true;
            mMessage = mActivity.getString(R.string.failed_url) + " : " + pException.toString();
            Log.e(logTag, String.format("Invalid URL : %s", pException.toString()));
            return false;
        }
        catch(IOException pException)
        {
            mIsFailed = true;
            mMessage = mActivity.getString(R.string.failed_connection) + " : " + pException.toString();
            Log.e(logTag, String.format("Connection Error : %s", pException.toString()));
            return false;
        }

        try
        {
            int responseCode = mConnection.getResponseCode();
            if(responseCode != 200)
            {
                mIsFailed = true;
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
                    mMessage = mActivity.getString(R.string.failed_ssl_verify) + " : No certificates";
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
                mName = null;
                for(String field : fields)
                    if(field.startsWith("CN="))
                    {
                        mName = field.substring(3);
                        mPaymentRequest.label = mName;
                        break;
                    }
                if(mName != null)
                    Log.d(logTag, String.format("Certificate name : %s", mName));
            }

            return true;
        }
        catch(SSLPeerUnverifiedException|CertificateException pException)
        {
            mIsFailed = true;
            mMessage = mActivity.getString(R.string.failed_ssl_verify) + " : " + pException.toString();
            Log.e(logTag, String.format("Invalid Certificate : %s", pException.toString()));
            return false;
        }
        catch(IOException pException)
        {
            mIsFailed = true;
            mMessage = mActivity.getString(R.string.failed_connection) + " : " + pException.toString();
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

            mProtocolRequest = PaymentRequestBufferProtocols.PaymentRequest.parseFrom(mConnection.getInputStream());
            mPaymentRequest.protocolDetails =
              PaymentRequestBufferProtocols.PaymentDetails.parseFrom(mProtocolRequest.getSerializedPaymentDetails());
        }
        catch(IOException pException)
        {
            Log.e(logTag, String.format("Error reading payment request : %s",
              pException.toString()));
            mIsFailed = true;
            mMessage = mActivity.getString(R.string.failed_invalid_message);
            return false;
        }

        // Check network
        if(!mPaymentRequest.protocolDetails.getNetwork().equals("main"))
        {
            Log.e(logTag, String.format("Payment request not for main network : %s",
              mPaymentRequest.protocolDetails.getNetwork()));
            mIsFailed = true;
            mMessage = mActivity.getString(R.string.failed_not_main_network);
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
            mMessage = mActivity.getString(R.string.failed_request_expired);
            return false;
        }

        boolean paymentMethodSpecified = false;
        for(PaymentRequestBufferProtocols.Output output : mPaymentRequest.protocolDetails.getOutputsList())
            if(output.hasAmount() && output.hasScript())
            {
                mPaymentRequest.amount = output.getAmount();
                mPaymentRequest.amountSpecified = true;
                mPaymentRequest.paymentScript = output.getScript().toByteArray();
                paymentMethodSpecified = true;
                break;
            }

        if(!paymentMethodSpecified)
        {
            Log.e(logTag, "Payment request does not contain payment method");
            mIsFailed = true;
            mMessage = mActivity.getString(R.string.failed_invalid_request);
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
        if(!mActivity.isDestroyed() && !mActivity.isFinishing())
        {
            if(mIsFailed)
                mActivity.clearPaymentProcess();
            else
                mActivity.displayEnterPaymentDetails();
            if(mMessage != null)
                mActivity.showMessage(mMessage, 2000);
        }

        super.onPostExecute(pResult);
    }
}
