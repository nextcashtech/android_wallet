/**************************************************************************
 * Copyright 2017-2018 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
package tech.nextcash.nextcashwallet;

import android.util.Log;


import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Vector;


public class ParseEntity implements Iterable<ParseEntity>
{
    private static final int version = 1;
    private static final String headerName = "PENT";
    private static final String logTag = "ParseEntity";
    private static HashSet<String> mLockedFileNames = new HashSet<>();

    private static char stringTerminator = 0;
    private static char childStart = 1;
    private static char valueStart = 2;
    private static char emptyFlag = 4;

    public static class NameException extends Exception
    {
        String description;

        public NameException(String pDescription)
        {
            description = pDescription;
        }

        @Override
        public String toString()
        {
            return super.toString() + " : " + description;
        }
    }

    public static class TypeException extends Exception
    {
        String description;

        public TypeException(String pDescription)
        {
            description = pDescription;
        }

        @Override
        public String toString()
        {
            return super.toString() + " : " + description;
        }
    }

    public static class ParseException extends Exception
    {
        String description;

        public ParseException(String pDescription)
        {
            description = pDescription;
        }

        @Override
        public String toString()
        {
            return super.toString() + " : " + description;
        }
    }

    public static void lock(String pFileName)
    {
        while(mLockedFileNames.contains(pFileName))
            Thread.yield();

        mLockedFileNames.add(pFileName);
    }

    public static void unlock(String pFileName)
    {
        mLockedFileNames.remove(pFileName);
    }

    private static String typeName(char pType)
    {
        switch(pType)
        {
            case 'n':
                return "null";
            case 's':
                return "string";
            case 'i':
                return "integer";
            case 'l':
                return "long";
            case 'f':
                return "float";
            case 'd':
                return "double";
            case 'b':
                return "boolean";
            case 'r':
                return "bytes";
            case 'a':
                return "array";
            default:
                return "unknown - " + pType;
        }
    }

    private static class Value
    {
        protected static final char nullType = 'n';
        protected static final char stringType = 's';
        protected static final char intType = 'i';
        protected static final char longType = 'l';
        protected static final char floatType = 'f';
        protected static final char doubleType = 'd';
        protected static final char boolType = 'b';
        protected static final char bytesType = 'r';
        protected static final char arrayType = 'a';

        protected char type;

        public Value()
        {
            type = nullType;
        }

        public void write(DataOutputStream pStream) throws IOException
        {
            pStream.writeByte(type);
        }

        public String toString()
        {
            return "null";
        }
    }

    private static class StringValue extends Value
    {
        String value;

        public StringValue(String pValue)
        {
            type = stringType;
            value = pValue;
        }

        @Override
        public void write(DataOutputStream pStream) throws IOException
        {
            pStream.writeByte(type);
            writeString(pStream, value);
        }

        public static StringValue read(DataInputStream pStream) throws IOException
        {
            return new StringValue(readString(pStream));
        }

        public String toString()
        {
            return value;
        }
    }

    private static class IntValue extends Value
    {
        int value;

        public IntValue(int pValue)
        {
            type = intType;
            value = pValue;
        }

        @Override
        public void write(DataOutputStream pStream) throws IOException
        {
            pStream.writeByte(type);
            pStream.writeInt(value);
        }

        public static IntValue read(DataInputStream pStream) throws IOException
        {
            return new IntValue(pStream.readInt());
        }

        public String toString()
        {
            return Integer.toString(value);
        }
    }

    private static class LongValue extends Value
    {
        long value;

        public LongValue(long pValue)
        {
            type = longType;
            value = pValue;
        }

        @Override
        public void write(DataOutputStream pStream) throws IOException
        {
            pStream.writeByte(type);
            pStream.writeLong(value);
        }

        public static LongValue read(DataInputStream pStream) throws IOException
        {
            return new LongValue(pStream.readLong());
        }

        public String toString()
        {
            return Long.toString(value);
        }
    }

    private static class FloatValue extends Value
    {
        float value;

        public FloatValue(float pValue)
        {
            type = floatType;
            value = pValue;
        }

        @Override
        public void write(DataOutputStream pStream) throws IOException
        {
            pStream.writeByte(type);
            pStream.writeFloat(value);
        }

        public static FloatValue read(DataInputStream pStream) throws IOException
        {
            return new FloatValue(pStream.readFloat());
        }

        public String toString()
        {
            return Float.toString(value);
        }
    }

    private static class DoubleValue extends Value
    {
        double value;

        public DoubleValue(double pValue)
        {
            type = doubleType;
            value = pValue;
        }

        @Override
        public void write(DataOutputStream pStream) throws IOException
        {
            pStream.writeByte(type);
            pStream.writeDouble(value);
        }

        public static DoubleValue read(DataInputStream pStream) throws IOException
        {
            return new DoubleValue(pStream.readDouble());
        }

        public String toString()
        {
            return Double.toString(value);
        }
    }

    private static class BoolValue extends Value
    {
        boolean value;

        public BoolValue(boolean pValue)
        {
            type = boolType;
            value = pValue;
        }

        @Override
        public void write(DataOutputStream pStream) throws IOException
        {
            pStream.writeByte(type);
            if(value)
                pStream.writeByte('T');
            else
                pStream.writeByte('F');
        }

        public static BoolValue read(DataInputStream pStream) throws IOException
        {
            return new BoolValue(pStream.readByte() == 'T');
        }

        public String toString()
        {
            if(value)
                return "true";
            else
                return "false";
        }
    }

    private static class BytesValue extends Value
    {
        byte[] value;

        public BytesValue(byte[] pValue)
        {
            type = bytesType;
            value = pValue;
        }

        @Override
        public void write(DataOutputStream pStream) throws IOException
        {
            pStream.writeByte(type);
            pStream.writeInt(value.length);
            pStream.write(value, 0, value.length);
        }

        public static BytesValue read(DataInputStream pStream) throws IOException
        {
            int length = pStream.readInt();
            byte[] bytes = new byte[length];
            pStream.read(bytes);
            return new BytesValue(bytes);
        }

        //public String toString()
        //{
        //    return Double.toString(value);
        //}
    }

    // TODO Add interface for arrays
    private static class ArrayValue extends Value
    {
        ArrayList<Value> value;

        public ArrayValue()
        {
            type = arrayType;
            value = new ArrayList<>();
        }

        @Override
        public void write(DataOutputStream pStream) throws IOException
        {
            pStream.writeByte(type);
            pStream.writeInt(value.size());
            for(Value item : value)
                item.write(pStream);
        }

        //public String toString()
        //{
        //    return Double.toString(value);
        //}
    }

    public String name;
    private Value mValue;

    protected Vector<ParseEntity> children;

    public ParseEntity()
    {
        name = null;
        mValue = null;
        children = null;
    }

    public ParseEntity(String pName)
    {
        name = pName;
        mValue = null;
        children = null;
    }

    public ParseEntity(String pName, String pValue)
    {
        name = pName;
        if(pValue == null)
            mValue = new Value();
        else
            mValue = new StringValue(pValue);
        children = null;
    }

    public ParseEntity(String pName, boolean pValue)
    {
        name = pName;
        mValue = new BoolValue(pValue);
        children = null;
    }

    public ParseEntity(String pName, int pValue)
    {
        name = pName;
        mValue = new IntValue(pValue);
        children = null;
    }

    public ParseEntity(String pName, long pValue)
    {
        name = pName;
        mValue = new LongValue(pValue);
        children = null;
    }

    public ParseEntity(String pName, float pValue)
    {
        name = pName;
        mValue = new FloatValue(pValue);
        children = null;
    }

    public ParseEntity(String pName, double pValue)
    {
        name = pName;
        mValue = new DoubleValue(pValue);
        children = null;
    }

    public ParseEntity(String pName, byte[] pValue)
    {
        name = pName;
        mValue = new BytesValue(pValue);
        children = null;
    }

    public enum Type { NONE, BOOLEAN, INTEGER, LONG, FLOAT, DOUBLE, STRING, BYTES }

    public Type type()
    {
        switch(mValue.type)
        {
            default:
            case 'n':
                return Type.NONE;
            case 's':
                return Type.STRING;
            case 'i':
                return Type.INTEGER;
            case 'l':
                return Type.LONG;
            case 'f':
                return Type.FLOAT;
            case 'd':
                return Type.DOUBLE;
            case 'b':
                return Type.BOOLEAN;
            case 'r':
                return Type.BYTES;
        }
    }

    private static String readString(DataInputStream pStream) throws IOException
    {
        String result = "";

        char nextChar = (char)pStream.readByte();
        while(nextChar != stringTerminator)
        {
            result += nextChar;
            nextChar = (char)pStream.readByte();
        }

        return result;
    }

    private static void writeString(DataOutputStream pStream, String pValue) throws IOException
    {
        if(pValue != null && pValue.length() > 0)
            pStream.writeBytes(pValue);
        pStream.writeByte(stringTerminator);
    }

    public void setValue(String pValue)
    {
        mValue = new StringValue(pValue);
    }

    public void setValue(int pValue)
    {
        mValue = new IntValue(pValue);
    }

    public void setValue(long pValue)
    {
        mValue = new LongValue(pValue);
    }

    public void setValue(float pValue)
    {
        mValue = new FloatValue(pValue);
    }

    public void setValue(double pValue)
    {
        mValue = new DoubleValue(pValue);
    }

    public void setValue(boolean pValue)
    {
        mValue = new BoolValue(pValue);
    }

    public void setValue(byte[] pValue)
    {
        mValue = new BytesValue(pValue);
    }

    public String stringValue() throws TypeException
    {
        if(mValue.type == Value.stringType)
            return ((StringValue)mValue).value;
        else if(mValue.type == Value.nullType)
            return null;
        else
            throw new TypeException(String.format("Value for %s not a string. It is a %s = %s", name, typeName(mValue.type), mValue.toString()));
    }

    public int intValue() throws TypeException
    {
        if(mValue.type == Value.intType)
            return ((IntValue)mValue).value;
        else
            throw new TypeException(String.format("Value for %s not an integer. It is a %s = %s", name, typeName(mValue.type), mValue.toString()));
    }

    public long longValue() throws TypeException
    {
        if(mValue.type == Value.longType)
            return ((LongValue)mValue).value;
        else
            throw new TypeException(String.format("Value for %s not a long. It is a %s = %s", name, typeName(mValue.type), mValue.toString()));
    }

    public float floatValue() throws TypeException
    {
        if(mValue.type == Value.floatType)
            return ((FloatValue)mValue).value;
        else
            throw new TypeException(String.format("Value for %s not a float. It is a %s = %s", name, typeName(mValue.type), mValue.toString()));
    }

    public double doubleValue() throws TypeException
    {
        if(mValue.type == Value.doubleType)
            return ((DoubleValue)mValue).value;
        else
            throw new TypeException(String.format("Value for %s not a double. It is a %s = %s", name, typeName(mValue.type), mValue.toString()));
    }

    public boolean boolValue() throws TypeException
    {
        if(mValue.type == Value.boolType)
            return ((BoolValue)mValue).value;
        else
            throw new TypeException(String.format("Value for %s not a boolean. It is a %s = %s", name, typeName(mValue.type), mValue.toString()));
    }

    public byte[] bytesValue() throws TypeException
    {
        if(mValue.type == Value.bytesType)
            return ((BytesValue)mValue).value;
        else
            throw new TypeException(String.format("Value for %s not a byte array. It is a %s = %s", name, typeName(mValue.type), mValue.toString()));
    }

    public void putParseEntity(ParseEntity pChild)
    {
        if(children == null)
            children = new Vector<>();
        children.add(pChild);
    }

    public void putParseEntity(String pName, ParseEntity pChild)
    {
        pChild.name = pName;
        putParseEntity(pChild);
    }

    public void putString(String pName, String pValue)
    {
        putParseEntity(new ParseEntity(pName, pValue));
    }

    public void putBoolean(String pName, boolean pValue)
    {
        putParseEntity(new ParseEntity(pName, pValue));
    }

    public void putInt(String pName, int pValue)
    {
        putParseEntity(new ParseEntity(pName, pValue));
    }

    public void putLong(String pName, long pValue)
    {
        putParseEntity(new ParseEntity(pName, pValue));
    }

    public void putFloat(String pName, float pValue)
    {
        putParseEntity(new ParseEntity(pName, pValue));
    }

    public void putDouble(String pName, double pValue)
    {
        putParseEntity(new ParseEntity(pName, pValue));
    }

    public void putBytes(String pName, byte[] pValue)
    {
        putParseEntity(new ParseEntity(pName, pValue));
    }

    public int childCount()
    {
        if(children != null)
            return children.size();
        else
            return 0;
    }

    public void remove(ParseEntity pChild)
    {
        children.remove(pChild);
    }

    protected class ChildIterator implements Iterator<ParseEntity>
    {
        private int mOffset;

        public ChildIterator()
        {
            mOffset = 0;
        }

        @Override
        public boolean hasNext()
        {
            return children != null && mOffset < children.size();
        }

        @Override
        public ParseEntity next()
        {
            return children.get(mOffset++);
        }
    }

    @Override
    public Iterator<ParseEntity> iterator()
    {
        return new ChildIterator();
    }

    public boolean containsKey(String pName)
    {
        if(children == null)
            return false;

        for(ParseEntity current : children)
        {
            if(current.name.equals(pName))
                return true;
        }

        return false;
    }

    public ParseEntity getParseEntity(String pName) throws ParseEntity.NameException
    {
        if(children == null)
            throw new ParseEntity.NameException("Missing " + pName + " (No children)");

        for(ParseEntity current : children)
        {
            if(current.name.equals(pName))
                return current;
        }

        throw new ParseEntity.NameException("Missing " + pName);
    }

    public String getString(String pName) throws ParseEntity.NameException, ParseEntity.TypeException
    {
        return getParseEntity(pName).stringValue();
    }

    public boolean getBoolean(String pName) throws ParseEntity.NameException, ParseEntity.TypeException
    {
        return getParseEntity(pName).boolValue();
    }

    public int getInt(String pName) throws ParseEntity.NameException, ParseEntity.TypeException
    {
        return getParseEntity(pName).intValue();
    }

    public long getLong(String pName) throws ParseEntity.NameException, ParseEntity.TypeException
    {
        return getParseEntity(pName).longValue();
    }

    public float getFloat(String pName) throws ParseEntity.NameException, ParseEntity.TypeException
    {
        return getParseEntity(pName).floatValue();
    }

    public double getDouble(String pName) throws ParseEntity.NameException, ParseEntity.TypeException
    {
        return getParseEntity(pName).doubleValue();
    }

    public byte[] getBytes(String pName) throws ParseEntity.NameException, ParseEntity.TypeException
    {
        return getParseEntity(pName).bytesValue();
    }

    private boolean write(DataOutputStream pStream) throws IOException
    {
        writeString(pStream, name);

        if(mValue != null)
        {
            pStream.writeByte(valueStart);
            mValue.write(pStream);
        }
        else if(children != null)
        {
            pStream.writeByte(childStart);
            pStream.writeInt(children.size());

            for(ParseEntity entity : children)
                entity.write(pStream);
        }
        else
            pStream.writeByte(emptyFlag);

        return true;
    }

    public boolean writeToFile(File pDirectory, String pFilePathName) throws IOException
    {
        Log.i(logTag, String.format("Writing file %s", pFilePathName));
        boolean success = false;

        lock(pFilePathName);

        if(pDirectory == null)
        {
            unlock(pFilePathName);
            throw new IOException("Null directory to write to");
        }

        if(!pDirectory.exists() && !pDirectory.mkdir())
        {
            unlock(pFilePathName);
            throw new IOException("Failed to create directory : " + pDirectory.getName());
        }

        try
        {
            // Write contents to temporary file
            File fileToWrite = new File(pDirectory, pFilePathName + ".writing");
            FileOutputStream fileOutputStream = new FileOutputStream(fileToWrite);
            DataOutputStream stream = new DataOutputStream(fileOutputStream);

            writeVersion(stream);

            success = write(stream);
            stream.close();

            if(!success)
                Log.e(logTag, "Failed to write file : " + pFilePathName);

            if(success)
            {
                // Rename file to permanent file name
                File writingFile = new File(pDirectory, pFilePathName + ".writing");
                File file = new File(pDirectory, pFilePathName);

                if(file.exists())
                {
                    if(!file.delete())
                    {
                        Log.e(logTag, "Failed to delete previous version of file " + pFilePathName);
                        success = false;
                    }
                }

                if(success)
                {
                    if(!writingFile.renameTo(file))
                    {
                        Log.e(logTag, "Failed to rename file to permanent name " + pFilePathName);
                        success = false;
                    }
                }
            }

            unlock(pFilePathName);
            return success;
        }
        catch(IOException pException)
        {
            unlock(pFilePathName);
            throw pException;
        }
    }

    private Value readValue(DataInputStream pStream) throws IOException, TypeException
    {
        char valueType = (char)pStream.readByte();
        switch(valueType)
        {
            case Value.nullType:
                return new Value();
            case Value.stringType:
                return StringValue.read(pStream);
            case Value.intType:
                return IntValue.read(pStream);
            case Value.longType:
                return LongValue.read(pStream);
            case Value.floatType:
                return FloatValue.read(pStream);
            case Value.doubleType:
                return DoubleValue.read(pStream);
            case Value.boolType:
                return BoolValue.read(pStream);
            case Value.bytesType:
                return BytesValue.read(pStream);
            case Value.arrayType:
            {
                ArrayValue result = new ArrayValue();
                int size = pStream.readInt();

                result.value.ensureCapacity(size);
                for(int i = 0; i < size; i++)
                    result.value.add(readValue(pStream));

                return result;
            }
            default:
                throw new TypeException("Invalid type found for value : " + valueType);
        }
    }

    public static String nameFromPartial(byte[] pData) throws IOException
    {
        ByteArrayInputStream buffer = new ByteArrayInputStream(pData);
        DataInputStream stream = new DataInputStream(buffer);
        return readString(stream);
    }

    private int read(DataInputStream pStream, int pChildCount) throws IOException, TypeException, ParseException
    {
        name = readString(pStream);

        // Read value or children
        char nextChar = (char)pStream.readByte();

        if(nextChar == valueStart)
        {
            mValue = readValue(pStream);
            children = null;
            return 0; // Return no children
        }
        else if(nextChar == childStart)
        {
            int childCount = pStream.readInt();
            children = new Vector<>();
            mValue = null;
            int childResult;

            // Limit number of children read
            if(pChildCount != 0 && childCount > pChildCount)
                childCount = pChildCount;

            for(int i=0;i<childCount;i++)
            {
                ParseEntity child = new ParseEntity();

                try
                {
                    childResult = child.read(pStream, 0);
                }
                catch(ParseException pException)
                {
                    Log.e(logTag, String.format(Locale.US, "Issue found in child %d of %s : %s",
                      i + 1, name, pException.toString()));
                    throw pException; // Propagate it up
                }

                children.add(child);
            }

            return children.size(); // Return child count
        }
        else if(nextChar == emptyFlag)
            return 0; // Return no children

        throw new ParseException(String.format("Did not contain value, children, or empty flag : %s", name));
    }

    public static ParseEntity readFromFile(File pDirectory, String pFilePathName) throws TypeException, ParseException,
      IOException
    {
        Log.d(logTag, String.format("Reading file %s", pFilePathName));
        ParseEntity result = new ParseEntity();
        lock(pFilePathName);

        if(pDirectory == null)
        {
            unlock(pFilePathName);
            throw new IOException("Null directory to read from");
        }

        if(!pDirectory.exists())
        {
            unlock(pFilePathName);
            throw new IOException("Directory doesn't exist : " + pDirectory.getName());
        }

        try
        {
            File file = new File(pDirectory, pFilePathName);
            FileInputStream fileInputStream = new FileInputStream(file);
            BufferedInputStream buffer = new BufferedInputStream(fileInputStream);
            DataInputStream stream = new DataInputStream(buffer);

            readVersion(stream);

            result.read(stream, 0);
            unlock(pFilePathName);
            return result;
        }
        catch(IOException|ParseException|TypeException pException)
        {
            unlock(pFilePathName);
            throw pException;
        }
    }

    public static ParseEntity readHeaderFromFile(File pDirectory, String pFilePathName) throws TypeException,
      ParseException, IOException
    {
        Log.i(logTag, String.format("Reading header from file %s", pFilePathName));
        ParseEntity result = new ParseEntity();
        lock(pFilePathName);

        if(pDirectory == null)
        {
            unlock(pFilePathName);
            throw new IOException("Null directory to read header from");
        }

        if(!pDirectory.exists())
        {
            unlock(pFilePathName);
            throw new IOException("Directory doesn't exist : " + pDirectory.getName());
        }

        try
        {
            File file = new File(pDirectory, pFilePathName);
            FileInputStream fileInputStream = new FileInputStream(file);
            BufferedInputStream buffer = new BufferedInputStream(fileInputStream);
            DataInputStream stream = new DataInputStream(buffer);

            readVersion(stream);

            result.read(stream, 1);
            unlock(pFilePathName);
            return result;
        }
        catch(IOException|ParseException|TypeException pException)
        {
            unlock(pFilePathName);
            throw pException;
        }
    }

    public byte[] toByteArray(boolean pIncludeVersion) throws IOException
    {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream stream = new DataOutputStream(buffer);

        if(pIncludeVersion)
            writeVersion(stream);

        write(stream);
        stream.flush();

        return buffer.toByteArray();
    }

    public void writeToStream(DataOutputStream pStream, boolean pIncludeVersion) throws IOException
    {
        if(pIncludeVersion)
            writeVersion(pStream);

        write(pStream);
        pStream.flush();
    }

    public ParseEntity(byte[] pData, boolean pIncludesVersion) throws TypeException, ParseException, IOException
    {
        name = null;
        mValue = null;
        children = null;

        ByteArrayInputStream buffer = new ByteArrayInputStream(pData);
        DataInputStream stream = new DataInputStream(buffer);

        if(pIncludesVersion)
            readVersion(stream);
        read(stream, 0);
    }

    public ParseEntity(DataInputStream pStream, boolean pIncludesVersion) throws TypeException, ParseException, IOException
    {
        name = null;
        mValue = null;
        children = null;

        if(pIncludesVersion)
            readVersion(pStream);
        read(pStream, 0);
    }

    private static void writeVersion(DataOutputStream pStream) throws IOException
    {
        pStream.writeBytes(headerName);
        pStream.writeInt(version);
    }

    private static void readVersion(DataInputStream pStream) throws ParseException, IOException
    {
        // Check header value
        String headerValue = "";
        byte[] headerBytes = new byte[headerName.length()];
        pStream.read(headerBytes);
        for(byte current : headerBytes)
            headerValue += (char) current;

        if(!headerValue.equals(headerName))
            throw new ParseException(String.format("Unsupported header value %s", headerValue));

        int fileVersion = pStream.readInt();

        if(fileVersion != version)
            throw new ParseException(String.format(Locale.US, "Unsupported version %d",
              fileVersion));
    }
}
