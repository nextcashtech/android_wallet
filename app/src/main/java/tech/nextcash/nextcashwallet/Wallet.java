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

    public boolean updateTransactionData(Bitcoin pBitcoin, int pWalletOffset)
    {
        boolean result = false;
        for(Transaction transaction : transactions)
        {
            transaction.data = pBitcoin.getTransactionData(transaction.hash, transaction.amount);

            if(transaction.data.exchangeRate == 0.0 && pBitcoin.exchangeRate() != 0.0)
            {
                // First time this transaction has been seen.
                transaction.data.exchangeRate = pBitcoin.exchangeRate();
                transaction.data.exchangeType = pBitcoin.exchangeType();

                FullTransaction fullTransaction = new FullTransaction();
                if(pBitcoin.getTransaction(pWalletOffset, transaction.hash, fullTransaction))
                {
                    // Check if it pays to any labeled addresses.
                    AddressLabel.Item addressLabel;
                    for(Output output : fullTransaction.outputs)
                        if(output.related)
                        {
                            addressLabel = pBitcoin.lookupAddressLabel(output.address, output.amount);
                            if(addressLabel != null)
                                transaction.data.comment = addressLabel.label;
                        }

                    // Check if it pays to any address book entries.
                    AddressBook.Item addressItem;
                    for(Output output : fullTransaction.outputs)
                    {
                        addressItem = pBitcoin.lookupAddress(output.address);
                        if(addressItem != null)
                        {
                            transaction.data.comment = addressItem.name;
                            break;
                        }
                    }
                }

                result = true;
            }

            if(transaction.date != 0 && transaction.data.date > transaction.date)
            {
                transaction.data.date = transaction.date;
                result = true;
            }

            // TODO Temporary Fix for issue with bad date being tagged on transaction.
            if(transaction.date != 0 && transaction.block != null &&
              Math.abs(transaction.date - transaction.data.date) > 864000) // One day
                transaction.data.date = transaction.date;
        }

        return result;
    }

    private static native void setupJNI();

    static
    {
        setupJNI();
    }
}
