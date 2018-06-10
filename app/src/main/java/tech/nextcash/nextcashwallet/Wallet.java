package tech.nextcash.nextcashwallet;


public class Wallet
{
    public boolean isPrivate;
    public String name;
    public boolean isSynchronized;
    public boolean isBackedUp;
    public long balance;
    public Transaction[] transactions, updatedTransactions;
    public long lastUpdated;
    public int blockHeight;
    public int viewID;

    public Wallet()
    {
        isPrivate = false;
        isSynchronized = false;
        isBackedUp = false;
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
