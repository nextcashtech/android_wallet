package tech.nextcash.nextcashwallet;

public class PaymentRequest
{
    // Format
    public static final int FORMAT_INVALID = 0;
    public static final int FORMAT_LEGACY  = 1;
    public static final int FORMAT_CASH    = 2;

    // Protocol
    public static final int PROTOCOL_NONE           = 0;
    public static final int PROTOCOL_ADDRESS        = 1;
    public static final int PROTOCOL_REQUEST_AMOUNT = 2;

    int format;
    int protocol;
    String code;
    String address;
    long amount;
    boolean secure;
    String description;

    PaymentRequest()
    {
        format = FORMAT_INVALID;
        protocol = PROTOCOL_NONE;
        amount = 0;
        secure = false;
    }
}
