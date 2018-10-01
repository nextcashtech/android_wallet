package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class Messages
{
    public static final String logTag = "Messages";

    class Data
    {
        public Data()
        {
            received = 0;
            dismissed = 0;
        }

        public Data(String pText)
        {
            received = System.currentTimeMillis() / 1000;
            dismissed = 0;
            text = pText;
        }

        public void read(DataInputStream pStream) throws IOException
        {
            received = pStream.readLong();
            dismissed = pStream.readLong();
            int length = pStream.readInt();
            byte bytes[] = new byte[length];
            if(pStream.read(bytes, 0, length) != length)
                throw new IOException("Failed to read text bytes");
            text = new String(bytes, StandardCharsets.UTF_8);
        }

        public void write(DataOutputStream pStream) throws IOException
        {
            pStream.writeLong(received);
            pStream.writeLong(dismissed);
            pStream.writeInt(text.length());
            pStream.writeBytes(text);
        }

        long received, dismissed;
        String text;
    }

    private ArrayList<Data> mMessages;

    public Messages()
    {
        mMessages = new ArrayList<>();
    }

    public boolean load(Context pContext)
    {
        mMessages.clear();

        try
        {
            File file = new File(pContext.getFilesDir(), "messages");
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
            mMessages.ensureCapacity(count);
            Data newMessage;
            for(int i = 0; i < count; i++)
            {
                newMessage = new Data();
                newMessage.read(stream);
                mMessages.add(newMessage);
            }
        }
        catch(IOException pException)
        {
            Log.e(logTag, String.format("Failed to load : %s", pException.toString()));
            return false;
        }

        return true;
    }

    public boolean save(Context pContext)
    {
        try
        {
            File file = new File(pContext.getFilesDir(), "messages");
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            DataOutputStream stream = new DataOutputStream(fileOutputStream);

            // Version
            stream.writeInt(1);

            // Count
            stream.writeInt(mMessages.size());

            // Items
            for(Data message : mMessages)
                message.write(stream);

            stream.close(); // Flush any buffered data
        }
        catch(IOException pException)
        {
            Log.e(logTag, String.format("Failed to save : %s", pException.toString()));
            return false;
        }

        return true;
    }

    public void addMessage(String pText)
    {
        mMessages.add(new Data(pText));
    }

    public void dismissMessage(String pText)
    {
        for(Data message : mMessages)
            if(message.dismissed == 0 && message.text.equals(pText))
                message.dismissed = System.currentTimeMillis() / 1000;
    }

    public ArrayList<String> activeMessages()
    {
        ArrayList<String> result = new ArrayList<>();
        for(Data message : mMessages)
            if(message.dismissed == 0)
                result.add(message.text);
        return result;
    }
}
