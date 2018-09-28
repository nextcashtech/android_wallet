package tech.nextcash.nextcashwallet;

import android.net.Uri;

import java.io.ByteArrayInputStream;
import java.util.Locale;

import com.google.protobuf.ByteString;
import com.google.protobuf.compiler.PluginProtos;


public class PaymentRequest
{
    // Format
    public static final int FORMAT_INVALID = 0;
    public static final int FORMAT_LEGACY  = 1;
    public static final int FORMAT_CASH    = 2;

    // Protocol
    public static final int TYPE_NONE         = 0;
    public static final int TYPE_PUB_KEY_HASH = 1;
    public static final int TYPE_SCRIPT_HASH  = 2;
    public static final int TYPE_PRIVATE_KEY  = 3;
    public static final int TYPE_BIP0700 = 4;

    public int format;
    public int type;
    public String uri;
    public String address;
    public long amount;
    public boolean amountSpecified;
    public String label;
    public String message;

    // BIP-0070
    public boolean secure;
    public String secureURL;
    public String site;
    public byte[] paymentScript;
    public String transactionID;
    public long expires;

    public PaymentRequestBufferProtocols.PaymentDetails protocolDetails;

    // For calculations
    public boolean usePending, sendMax;
    public double feeRate;
    public Outpoint[] outpoints;

    PaymentRequest()
    {
        format = FORMAT_INVALID;
        type = TYPE_NONE;
        amount = 0;
        amountSpecified = false;
        secure = false;
        expires = 0;
        usePending = false;
        sendMax = false;
        feeRate = 1.0;
    }

    public String description()
    {
        String result = "";

        if(type != TYPE_BIP0700 && label != null && label.length() > 0)
            result += label;

        if(message != null && message.length() > 0)
        {
            if(result.length() > 0)
                result += "\n";
            result += message;
        }

        if(result.length() > 0)
            return result;
        else
            return null;
    }

    public void clear()
    {
        format = FORMAT_INVALID;
        type = TYPE_NONE;
        amountSpecified = false;
        amount = 0;
        secure = false;
        label = null;
        message = null;
        secureURL = null;
        site = null;
        paymentScript = null;
        expires = 0;
        transactionID = null;
        usePending = false;
        sendMax = false;
        outpoints = null;
    }

    public boolean setAddress(String pAddress)
    {
        address = pAddress;
        type = TYPE_PUB_KEY_HASH;
        return encode();
    }

    public boolean setLabel(String pLabel)
    {
        label = pLabel;
        return encode();
    }

    public boolean setMessage(String pMessage)
    {
        message = pMessage;
        return encode();
    }

    public boolean setAmount(long pSatoshis)
    {
        amount = pSatoshis;
        return encode();
    }

//    public boolean decode(String pPaymentCode)
//    {
//        clear();
//
//        Uri theURI = Uri.parse(pPaymentCode);
//        try
//        {
//            String amountString = theURI.getQueryParameter("amount");
//            amount = Bitcoin.satoshisFromBitcoins(Double.parseDouble(amountString));
//        }
//        catch(Exception pException)
//        {
//            clear();
//            return false;
//        }
//        label = theURI.getQueryParameter("label");
//        message = theURI.getQueryParameter("message");
//        secureURL = theURI.getQueryParameter("r");
//        uri = pPaymentCode;
//
//        for(String key : theURI.getQueryParameterNames())
//            if(key.startsWith("req-"))
//            {
//                // Unknown required parameter
//                clear();
//                return false;
//            }
//
//        return true;
//    }

    public boolean encode()
    {
        Uri.Builder uriBuilder = new Uri.Builder();

        uriBuilder.encodedPath("bitcoincash:" + address);

        if(label != null && label.length() > 0)
            uriBuilder.appendQueryParameter("label", label);

        if(message != null && message.length() > 0)
            uriBuilder.appendQueryParameter("message", message);

        if(amount != 0)
            uriBuilder.appendQueryParameter("amount",
              String.format(Locale.getDefault(), "%.8f", Bitcoin.bitcoinsFromSatoshis(amount)));

        uri = uriBuilder.toString();

        if(format == FORMAT_INVALID)
            format = FORMAT_CASH;
        return true;
    }

    // Parse a BIP-0070 message
    public boolean parseDetailsMessage(ByteArrayInputStream pData)
    {
        //PluginProtos.CodeGeneratorRequest.
        return false;
    }

    public long amountAvailable()
    {
        if(outpoints == null)
            return 0L;

        long result = 0L;
        for(Outpoint outpoint : outpoints)
            if(usePending || outpoint.confirmations > 0)
                result += outpoint.output.amount;
        return result;
    }

    public long estimatedTransactionSize()
    {
        if(outpoints == null)
            return (int)((double)Bitcoin.estimatedP2PKHSize(1, sendMax ? 1 : 2) * feeRate);

        long inputAmount = 0;
        int inputCount = 0;
        for(Outpoint outpoint : outpoints)
            if(usePending || outpoint.confirmations > 0)
            {
                inputAmount += outpoint.output.amount;
                inputCount++;

                if(inputAmount > amount + (Bitcoin.estimatedP2PKHSize(inputCount, 2) *
                  feeRate))
                    break;
            }

        return Bitcoin.estimatedP2PKHSize(inputCount == 0 ? 1 : inputCount, sendMax ? 1 : 2);
    }

    public long estimatedFee()
    {
        return (long)((double)estimatedTransactionSize() * feeRate);
    }
}
