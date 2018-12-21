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
        mItemsHaveBeenAdded = false;
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
        double exchangeRate;
        String exchangeType;

        // Cost basis
        //   Not a rate.
        //   Amount for whole transaction.
        //   Always positive.
        //   For "receives" represents original cost.
        //   For "sends" represents value sent.
        double cost;
        String costType;
        long costDate;

        long notifiedDate; // Time/Date of confirmation by app

        public Item()
        {
            hash = null;
            amount = 0L;
            comment = null;
            date = 0L;
            exchangeRate = 0.0;
            exchangeType = null;
            cost = 0.0;
            costType = null;
            costDate = 0L;
            notifiedDate = 0L;
        }

        public Item(String pTransactionID, long pAmount)
        {
            hash = pTransactionID;
            amount = pAmount;
            comment = null;
            date = System.currentTimeMillis() / 1000L;
            exchangeRate = 0.0;
            exchangeType = null;
            cost = 0.0;
            costType = null;
            costDate = 0L;
            notifiedDate = 0L;
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
        }
    }

    private ArrayList<Item> mItems;
    private boolean mItemsHaveBeenAdded;

    public boolean itemsAdded()
    {
        return mItemsHaveBeenAdded;
    }

    public synchronized Item getData(String pTransactionID, long pAmount)
    {
        for(Item item : mItems)
            if(item.hash.equals(pTransactionID))
            {
                if(pAmount == 0)
                    return item; // Allow get when amount is not known.
                else if(item.amount == 0)
                {
                    // Backwards compatible to before amount was retained.
                    item.amount = pAmount;
                    return item;
                }
                else if(item.amount == pAmount)
                    return item;
            }

        // Create new entry.
        Item result = new Item(pTransactionID, pAmount);
        mItems.add(result);
        mItemsHaveBeenAdded = true;
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
            //   3 - Add cost date
            //   4 - Add notified date
            int version = stream.readInt();
            if(version < 0 || version > 4)
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

        mItemsHaveBeenAdded = false;
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
            stream.writeInt(4);

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

        mItemsHaveBeenAdded = false;
        return true;
    }
}
