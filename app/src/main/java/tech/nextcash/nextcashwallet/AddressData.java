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

// Used to tag incoming addresses/payment requests with comments.
// So when a payment is received on that address the comment is tagged to the transaction.
public class AddressData
{
    private static String logTag = "AddressData";

    public AddressData(File pDirectory)
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

    public static class Item
    {
        String address;
        long amount; // For specific amount requests. Amount of zero means all payments on that address are tagged.
        String comment;

        public Item()
        {
            address = null;
            amount = 0L;
            comment = null;
        }

        public void assign(Item pItem)
        {
            address = pItem.address;
            amount = pItem.amount;
            comment = pItem.comment;
        }

        public void read(DataInputStream pStream, int pVersion) throws IOException
        {
            address = readString(pStream);
            amount = pStream.readLong();
            comment = readString(pStream);
        }

        public void write(DataOutputStream pStream) throws IOException
        {
            writeString(pStream, address);
            pStream.writeLong(amount);
            writeString(pStream, comment);
        }
    }

    private ArrayList<Item> mItems;
    private boolean mIsModified;

    public boolean isModified()
    {
        return mIsModified;
    }

    // Returns true if an item was replaced.
    // Only allow one item per address.
    public synchronized boolean add(Item pItem)
    {
        for(Item item : mItems)
            if(item.address.equals(pItem.address))
            {
                item.assign(pItem);
                mIsModified = true;
                return true;
            }

        mItems.add(pItem);
        mIsModified = true;
        return false;
    }

    public synchronized Item lookup(String pAddress, long pAmount)
    {
        for(Item item : mItems)
            if(item.address.equals(pAddress))
            {
                if(item.amount == 0 || item.amount == pAmount)
                    return item;
                else
                    return null;
            }
        return null;
    }

    private synchronized boolean read(File pDirectory)
    {
        mItems.clear();

        try
        {
            File file = new File(pDirectory, "addresses.data");
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
        File tempFile = new File(pDirectory, "addresses.data.temp");

        try
        {
            FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
            DataOutputStream stream = new DataOutputStream(fileOutputStream);

            // Version
            stream.writeInt(1);

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

        File actualFile = new File(pDirectory, "addresses.data");
        if(!tempFile.renameTo(actualFile))
        {
            Log.e(logTag, "Failed to rename temp file");
            return false;
        }

        mIsModified = false;
        return true;
    }
}
