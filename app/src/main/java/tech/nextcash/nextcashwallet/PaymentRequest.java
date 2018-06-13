package tech.nextcash.nextcashwallet;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;

public class PaymentRequest
{
    // Format
    public static final int FORMAT_INVALID = 0;
    public static final int FORMAT_LEGACY  = 1;
    public static final int FORMAT_CASH    = 2;

    // Protocol
    public static final int TYPE_NONE         = 0;
    public static final int TYPE_PUB_KEY_HASH = 1;

    public int format;
    public int type;
    public String code;
    public String address;
    public long amount;
    public String label;
    public String message;
    public boolean secure;

    PaymentRequest()
    {
        format = FORMAT_INVALID;
        type = TYPE_NONE;
        amount = 0;
        secure = false;
    }

    public String description()
    {
        String result = "";
        if(label != null && label.length() > 0)
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
        amount = 0;
        secure = false;
        label = null;
        message = null;
    }

    public boolean setAddress(String pAddress)
    {
        address = pAddress;
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

    public boolean decode(String pPaymentCode)
    {
        clear();

        String segments[] = pPaymentCode.split("\\?");
        if(segments.length > 2)
        {
            clear();
            return false;
        }

        address = segments[0];
        String parameters[] = segments[1].split("&");

        for(String parameter : parameters)
        {
            String pair[] = parameter.split("=");
            if(pair.length != 2)
            {
                clear();
                return false;
            }

            switch(pair[0])
            {
            case "amount":
                try
                {
                    amount = Bitcoin.satoshisFromBitcoins(Double.parseDouble(pair[1]));
                }
                catch(Exception pException)
                {
                    clear();
                    return false;
                }
                break;
            case "label":
                try
                {
                    label = URLDecoder.decode(pair[1], "UTF-8");
                }
                catch(Exception pException)
                {
                    clear();
                    return false;
                }
                break;
            case "message":
                try
                {
                    message = URLDecoder.decode(pair[1], "UTF-8");
                }
                catch(Exception pException)
                {
                    clear();
                    return false;
                }
                break;
            default:
                if(pair[0].startsWith("req-"))
                {
                    clear();
                    return false;
                }
                break;
            }
        }

        return true;
    }

    public boolean encode()
    {
        boolean isFirstParameter = true;

        code = address;
        type = TYPE_PUB_KEY_HASH;

        if(label != null && label.length() > 0)
        {
            if(isFirstParameter)
            {
                isFirstParameter = false;
                code += "?";
            }
            else
                code += "&";

            code += "label=";
            try
            {
                code += URLEncoder.encode(label, "UTF-8");
            }
            catch(Exception pException)
            {
                return false;
            }
        }

        if(message != null && message.length() > 0)
        {
            if(isFirstParameter)
            {
                isFirstParameter = false;
                code += "?";
            }
            else
                code += "&";

            code += "message=";
            try
            {
                code += URLEncoder.encode(message, "UTF-8");
            }
            catch(Exception pException)
            {
                return false;
            }
        }

        if(amount != 0)
        {
            if(isFirstParameter)
            {
                isFirstParameter = false;
                code += "?";
            }
            else
                code += "&";

            code += String.format(Locale.getDefault(), "amount=%.8f", Bitcoin.bitcoinsFromSatoshis(amount));
        }

        if(format == FORMAT_INVALID)
            format = FORMAT_CASH;
        return true;
    }
}
