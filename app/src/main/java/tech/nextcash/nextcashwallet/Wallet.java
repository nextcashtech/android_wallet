package tech.nextcash.nextcashwallet;


public class Wallet
{
    public String name;
    public boolean isPrivate;
    public boolean isSynchronized;
    public boolean isBackedUp;
    public long balance;
    public Transaction[] transactions, updatedTransactions;
    public long lastUpdated;
    public int blockHeight;

    public int id;

    private static int mNextID = 1;

    public Wallet()
    {
        isPrivate = false;
        isSynchronized = false;
        isBackedUp = false;
        balance = 0;
        lastUpdated = 0;
        blockHeight = 0;

        id = mNextID++;
    }

    public boolean hasPending()
    {
        for(Transaction transaction : transactions)
            if(transaction.block == null)
                return true;
        return false;
    }

    private static native void setupJNI();

    static
    {
        setupJNI();
    }
}
