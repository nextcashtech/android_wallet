/**************************************************************************
 * Copyright 2017-2018 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
package tech.nextcash.nextcashwallet;


public class Wallet
{
    public String name;
    public boolean isPrivate;
    public boolean isSynchronized;
    public boolean isBackedUp;
    public long balance, pendingBalance;
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

    public boolean updateTransactionData(TransactionData pData, double pExchangeRate, String pExchangeType)
    {
        boolean result = false;
        for(Transaction transaction : transactions)
        {
            transaction.data = pData.getData(transaction.hash);
            if(transaction.data.exchangeRate == 0.0 && pExchangeRate != 0.0)
            {
                transaction.data.exchangeRate = pExchangeRate;
                transaction.data.exchangeType = pExchangeType;
                result = true;
            }
            if(transaction.data.date > transaction.date)
            {
                transaction.data.date = transaction.date;
                result = true;
            }
        }
        return result;
    }

    private static native void setupJNI();

    static
    {
        setupJNI();
    }
}
