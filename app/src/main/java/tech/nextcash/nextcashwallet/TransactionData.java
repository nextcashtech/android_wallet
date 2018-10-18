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
        {
            pStream.writeInt(0);
            return;
        }
        pStream.writeInt(pString.length());
        pStream.writeBytes(pString);
    }

    public class ItemData
    {
        String hash;

        long date; // Older of first seen or block time.
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

        public ItemData()
        {
            hash = null;
            comment = null;
            date = 0;
            exchangeRate = 0.0;
            exchangeType = null;
            cost = 0.0;
            costType = null;
        }

        public ItemData(String pTransactionID)
        {
            hash = pTransactionID;
            comment = null;
            date = System.currentTimeMillis() / 1000L;
            exchangeRate = 0.0;
            exchangeType = null;
            cost = 0.0;
            costType = null;
        }

        public void read(DataInputStream pStream) throws IOException
        {
            hash = readString(pStream);
            date = pStream.readLong();
            comment = readString(pStream);
            exchangeRate = pStream.readDouble();
            exchangeType = readString(pStream);
            cost = pStream.readDouble();
            costType = readString(pStream);
        }

        public void write(DataOutputStream pStream) throws IOException
        {
            writeString(pStream, hash);
            pStream.writeLong(date);
            writeString(pStream, comment);
            pStream.writeDouble(exchangeRate);
            writeString(pStream, exchangeType);
            pStream.writeDouble(cost);
            writeString(pStream, costType);
        }
    }

    private ArrayList<ItemData> mItems;
    private boolean mItemsHaveBeenAdded;

    public boolean itemsAdded()
    {
        return mItemsHaveBeenAdded;
    }

    public ItemData getData(String pTransactionID)
    {
        for(ItemData item : mItems)
            if(item.hash.equals(pTransactionID))
                return item;
        ItemData result = new ItemData(pTransactionID);
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
            int version = stream.readInt();
            if(version != 1)
            {
                Log.e(logTag, String.format("Unknown version : %d", version));
                return false;
            }

            // Count
            int count = stream.readInt();

            // Items
            mItems.ensureCapacity(count);
            ItemData newItem;
            for(int i = 0; i < count; i++)
            {
                newItem = new ItemData();
                newItem.read(stream);
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
            stream.writeInt(1);

            // Count
            stream.writeInt(mItems.size());

            // Items
            for(ItemData item : mItems)
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
