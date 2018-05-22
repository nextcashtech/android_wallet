package tech.nextcash.nextcashwallet;


public class Wallet
{
    public boolean isPrivate;
    public String name;
    public long balance;
    public Transaction[] transactions, updatedTransactions;
    public long lastUpdated;
    public int blockHeight;
    public int viewID;

    public Wallet()
    {
        isPrivate = false;
        balance = 0;
        lastUpdated = 0;
        blockHeight = 0;
        viewID = 0;
    }

    private static native void setupJNI();

    static
    {
        setupJNI();
    }
}
