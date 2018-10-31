package tech.nextcash.nextcashwallet;

import android.support.annotation.NonNull;
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


// Used to store outgoing addresses with names so they can be reused.
public class AddressBook
{
    private static String logTag = "AddressBook";

    public AddressBook(File pDirectory)
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

    // Do not change the order or Type enumeration. The file format depends on the ordinals not changing.
    public enum Type { NONE, ADDRESS, CHAIN_KEY }

    public class Item implements Comparable
    {
        Type type;
        String address;
        String name;

        public Item()
        {
            type = Type.NONE;
            address = null;
            name = null;
        }

        public void read(DataInputStream pStream, int pVersion) throws IOException
        {
            int typeValue = pStream.readByte();
            if(typeValue >= Type.values().length)
                type = Type.NONE;
            else
                type = Type.values()[typeValue];
            address = readString(pStream);
            name = readString(pStream);
        }

        public void write(DataOutputStream pStream) throws IOException
        {
            pStream.writeByte(type.ordinal());
            writeString(pStream, address);
            writeString(pStream, name);
        }

        @Override
        public int compareTo(@NonNull Object pOther)
        {
            if(pOther == null || pOther.getClass() != Item.class)
                return 0;
            return name.compareTo(((Item)pOther).name);
        }
    }

    private ArrayList<Item> mItems;
    private boolean mIsModified;

    public boolean isModified()
    {
        return mIsModified;
    }

    public synchronized void addAddress(String pAddress, String pName)
    {
        for(Item item : mItems)
            if(item.address.equals(pAddress))
            {
                item.type = Type.ADDRESS;
                item.name = pName;
                mIsModified = true;
                return;
            }

        Item result = new Item();
        result.type = Type.ADDRESS;
        result.address = pAddress;
        result.name = pName;
        mItems.add(result);
        mIsModified = true;
    }

    public synchronized boolean removeAddress(String pAddress)
    {
        for(Item item : mItems)
            if(item.address.equals(pAddress))
            {
                mItems.remove(item);
                mIsModified = true;
                return true;
            }

        return false;
    }

    public synchronized Item lookup(String pAddress)
    {
        for(Item item : mItems)
            if(item.address.equals(pAddress))
                return item;
        return null;
    }

    public ArrayList<Item> getAll()
    {
        return new ArrayList<>(mItems);
    }

    private synchronized boolean read(File pDirectory)
    {
        mItems.clear();

        try
        {
            File file = new File(pDirectory, "address_book.data");
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
        File tempFile = new File(pDirectory, "address_book.data.temp");

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

        File actualFile = new File(pDirectory, "address_book.data");
        if(!tempFile.renameTo(actualFile))
        {
            Log.e(logTag, "Failed to rename temp file");
            return false;
        }

        mIsModified = false;
        return true;
    }
}
