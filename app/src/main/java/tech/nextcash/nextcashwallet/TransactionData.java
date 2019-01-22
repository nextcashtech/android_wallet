/**************************************************************************
 * Copyright 2017-2019 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
package tech.nextcash.nextcashwallet;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;


public class TransactionData
{
    private static String logTag = "TransactionData";

    public TransactionData(File pDirectory)
    {
        mItems = new ArrayList<>();
        mIsModified = false;
        read(pDirectory);
    }

    public boolean save(File pDirectory)
    {
        return write(pDirectory);
    }

    private static String readString(DataInputStream pStream) throws IOException
    {
        int length = pStream.readInt();
        if(length == 0)
            return null;
        byte bytes[] = new byte[length];
        if(pStream.read(bytes, 0, length) != length)
            throw new IOException("Failed to read text bytes");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeString(DataOutputStream pStream, String pString) throws IOException
    {
        if(pString == null)
            pStream.writeInt(0);
        else
        {
            pStream.writeInt(pString.length());
            pStream.writeBytes(pString);
        }
    }

    public static class ID
    {
        public ID(String pHash, long pAmount)
        {
            hash = pHash;
            amount = pAmount;
        }

        @Override
        public boolean equals(Object pOtherID)
        {
            if(pOtherID == null || pOtherID.getClass() != this.getClass())
                return false;
            return hash.equals(((ID)pOtherID).hash) && amount == ((ID)pOtherID).amount;
        }

        public String hash;
        public long amount;
    }

    public class Item
    {
        String hash;

        long date; // Older of first seen or block time.
        long amount; // For identification purposes if more than one wallet is affected.
        String comment; // User added message.

        // Exchange rate and type at time of first seen
        int chainID;
        double exchangeRate;
        String exchangeType;

        // Cost basis
        //   Not a rate.
        //   Amount for whole transaction.
        //   Always positive.
        //   Represents value sent.
        double cost;
        String costType;
        long costDate;

        long notifiedDate; // Time/Date of confirmation by app

        public Item()
        {
            hash = null;
            date = 0L;
            amount = 0L;
            comment = null;

            chainID = Bitcoin.CHAIN_UNKNOWN;
            exchangeRate = 0.0;
            exchangeType = null;

            cost = 0.0;
            costType = null;
            costDate = 0L;

            notifiedDate = 0L;
        }

        public Item(Item pCopy)
        {
            hash = pCopy.hash;
            date = pCopy.date;
            amount = pCopy.amount;
            comment = pCopy.comment;

            chainID = pCopy.chainID;
            exchangeRate = pCopy.exchangeRate;
            exchangeType = pCopy.exchangeType;

            cost = pCopy.cost;
            costType = pCopy.costType;
            costDate = pCopy.costDate;

            notifiedDate = pCopy.notifiedDate;
        }

        public Item(String pTransactionID, long pAmount, int pChainID)
        {
            hash = pTransactionID;
            date = System.currentTimeMillis() / 1000L;
            amount = pAmount;
            comment = null;

            chainID = pChainID;
            exchangeRate = 0.0;
            exchangeType = null;

            cost = 0.0;
            costType = null;
            costDate = 0L;

            notifiedDate = 0L;
        }

        public double effectiveCost()
        {
            if(cost != 0.0)
                return Math.abs(cost);
            else
                return Math.abs(exchangeRate * Bitcoin.bitcoinsFromSatoshis(amount));
        }

        public long effectiveDate()
        {
            if(costDate != 0L)
                return costDate;
            else
                return date;
        }

        public String effectiveType()
        {
            if(costType != null)
                return costType;
            else
                return exchangeType;
        }

        public void read(DataInputStream pStream, int pVersion) throws IOException
        {
            hash = readString(pStream);
            if(pVersion > 1)
                amount = pStream.readLong();
            else
                amount = 0L;
            date = pStream.readLong();
            comment = readString(pStream);
            exchangeRate = pStream.readDouble();
            exchangeType = readString(pStream);
            cost = pStream.readDouble();
            costType = readString(pStream);
            if(pVersion > 2)
                costDate = pStream.readLong();
            else
                costDate = 0L;
            if(pVersion > 3)
                notifiedDate = pStream.readLong();
            else
                notifiedDate = date;
            if(pVersion > 4)
                chainID = pStream.readInt();
            else
                chainID = Bitcoin.CHAIN_UNKNOWN;
        }

        public void write(DataOutputStream pStream) throws IOException
        {
            writeString(pStream, hash);
            pStream.writeLong(amount);
            pStream.writeLong(date);
            writeString(pStream, comment);
            pStream.writeDouble(exchangeRate);
            writeString(pStream, exchangeType);
            pStream.writeDouble(cost);
            writeString(pStream, costType);
            pStream.writeLong(costDate);
            pStream.writeLong(notifiedDate);
            pStream.writeInt(Bitcoin.CHAIN_UNKNOWN);
        }
    }

    private Item copyForChain(Item pItem, int pChainID)
    {
        if(pItem.chainID == Bitcoin.CHAIN_UNKNOWN)
        {
            pItem.chainID = pChainID;
            mIsModified = true;
            return pItem;
        }

        Item result = new Item(pItem);
        result.chainID = pChainID;

        result.exchangeRate = 0.0;
        result.exchangeType = null;

        result.cost = 0.0;
        result.costType = null;
        result.costDate = 0L;

        mItems.add(result);
        mIsModified = true;
        return result;
    }

    private ArrayList<Item> mItems;
    private boolean mIsModified;

    public boolean isModified()
    {
        return mIsModified;
    }

    public synchronized Item getData(String pTransactionID, long pAmount, int pChainID)
    {
        if(pTransactionID == null)
            return null;

        Item result = null;
        for(Item item : mItems)
            if(item.hash.equals(pTransactionID) && (result == null || item.chainID == pChainID))
            {
                if(pAmount == 0 || item.amount == 0 || item.amount == pAmount)
                {
                    // Backwards compatible to before amount was retained.
                    if(item.amount == 0 && pAmount != 0)
                        item.amount = pAmount;
                    result = item; // Allow get when amount is not known.
                }
            }

        if(result != null)
        {
            if(result.chainID == pChainID)
                return result;
            else
                return copyForChain(result, pChainID);
        }

        // Create new entry.
        result = new Item(pTransactionID, pAmount, pChainID);
        mItems.add(result);
        mIsModified = true;
        return result;
    }

    private synchronized boolean read(File pDirectory)
    {
        mItems.clear();

        try
        {
            File file = new File(pDirectory, "transactions.data");
            FileInputStream fileInputStream = new FileInputStream(file);
            BufferedInputStream buffer = new BufferedInputStream(fileInputStream);
            DataInputStream stream = new DataInputStream(buffer);

            // Version
            //   2 - Add amount
            //   3 - Add cost sendDate
            //   4 - Add notified sendDate
            //   5 - Add chain ID
            int version = stream.readInt();
            if(version < 0 || version > 5)
            {
                Log.e(logTag, String.format("Unknown version : %d", version));
                return false;
            }

            // Count
            int count = stream.readInt();

            // Items
            mItems.ensureCapacity(count);
            Item newItem;
            for(int i = 0; i < count; i++)
            {
                newItem = new Item();
                newItem.read(stream, version);
                mItems.add(newItem);
            }
        }
        catch(IOException pException)
        {
            Log.e(logTag, String.format("Failed to load : %s", pException.toString()));
            return false;
        }

        mIsModified = false;
        return true;
    }

    private synchronized boolean write(File pDirectory)
    {
        File tempFile = new File(pDirectory, "transactions.data.temp");

        try
        {
            FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
            DataOutputStream stream = new DataOutputStream(fileOutputStream);

            // Version
            stream.writeInt(5);

            // Count
            stream.writeInt(mItems.size());

            // Items
            for(Item item : mItems)
                item.write(stream);

            stream.close(); // Flush any buffered data
        }
        catch(IOException pException)
        {
            Log.e(logTag, String.format("Failed to save : %s", pException.toString()));
            return false;
        }

        File actualFile = new File(pDirectory, "transactions.data");
        if(!tempFile.renameTo(actualFile))
        {
            Log.e(logTag, "Failed to rename temp file");
            return false;
        }

        mIsModified = false;
        return true;
    }
}
