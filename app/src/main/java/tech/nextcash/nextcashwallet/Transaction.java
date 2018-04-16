package tech.nextcash.nextcashwallet;


public class Transaction
{
    public String hash; // Transaction ID
    public String block; // Block Hash in which transaction was confirmed
    public long date; // Date/Time in seconds since epoch of transaction
    public long amount; // Amount of transaction in satoshis. Negative for send.

    public Transaction()
    {
        date = 0;
        amount = 0;
    }

    private static native void setupJNI();

    static
    {
        setupJNI();
    }
}
