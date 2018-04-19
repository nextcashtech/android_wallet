package tech.nextcash.nextcashwallet;

import android.util.Log;

import java.io.File;
import java.io.IOException;


public class Settings
{
    private static Settings theInstance = null;
    public static synchronized Settings getInstance(File pDirectory)
    {
        if(theInstance == null)
            theInstance = new Settings(pDirectory);
        else if(theInstance.mDirectory == null || !theInstance.mDirectory.equals(pDirectory))
            theInstance = new Settings(pDirectory);

        return theInstance;
    }

    public static void clear()
    {
        theInstance = null;
    }

    private static String logTag = "Settings";

    private File mDirectory;
    private ParseEntity mValues;

    private Settings(File pDirectory)
    {
        mDirectory = pDirectory;

        if(mDirectory == null)
        {
            mValues = null;
            return;
        }

        try
        {
            mValues = ParseEntity.readFromFile(mDirectory, "current.settings");
        }
        catch(ParseEntity.ParseException|ParseEntity.TypeException pException)
        {
            Log.e(logTag, "Failed to load current settings : " + pException);
            mValues = new ParseEntity();
        }
        catch(IOException pException)
        {
            Log.w(logTag, "Failed to read current settings : " + pException);
            mValues = new ParseEntity();
        }
    }

    private synchronized void write()
    {
        if(mDirectory == null || mValues == null)
            return;

        try
        {
            mValues.writeToFile(mDirectory, "current.settings");
        }
        catch(IOException pException)
        {
            Log.e(logTag, "Failed to write current settings : " + pException);
            mValues = new ParseEntity();
        }
    }

    public boolean containsValue(String pName)
    {
        return mValues != null && mValues.containsKey(pName);
    }

    public String value(String pName)
    {
        try
        {
            if(mValues != null && mValues.containsKey(pName))
                return mValues.getString(pName);
        }
        catch(ParseEntity.NameException|ParseEntity.TypeException pException)
        {
            Log.e(logTag, "Invalid settings value : " + pException.toString());
        }

        return "";
    }

    public void setValue(String pName, String pValue)
    {
        if(mValues == null)
            return;

        try
        {
            if(mValues.containsKey(pName))
                mValues.getParseEntity(pName).setValue(pValue);
            else
                mValues.putString(pName, pValue);
        }
        catch(ParseEntity.NameException pException)
        {
            Log.e(logTag, "Name not found to update : " + pException.toString());
            mValues.putString(pName, pValue);
        }

        write();
    }

    public int intValue(String pName)
    {
        try
        {
            if(mValues != null && mValues.containsKey(pName))
                return mValues.getInt(pName);
        }
        catch(ParseEntity.NameException|ParseEntity.TypeException pException)
        {
            Log.e(logTag, "Invalid settings value : " + pException.toString());
        }

        return 0;
    }

    public void setIntValue(String pName, int pValue)
    {
        if(mValues == null)
            return;

        try
        {
            if(mValues.containsKey(pName))
                mValues.getParseEntity(pName).setValue(pValue);
            else
                mValues.putInt(pName, pValue);
        }
        catch(ParseEntity.NameException pException)
        {
            Log.e(logTag, "Name not found to update : " + pException.toString());
            mValues.putInt(pName, pValue);
        }

        write();
    }

    public double doubleValue(String pName)
    {
        try
        {
            if(mValues != null && mValues.containsKey(pName))
                return mValues.getDouble(pName);
        }
        catch(ParseEntity.NameException|ParseEntity.TypeException pException)
        {
            Log.e(logTag, "Invalid settings value : " + pException.toString());
        }

        return 0.0;
    }

    public void setDoubleValue(String pName, double pValue)
    {
        if(mValues == null)
            return;

        try
        {
            if(mValues.containsKey(pName))
                mValues.getParseEntity(pName).setValue(pValue);
            else
                mValues.putDouble(pName, pValue);
        }
        catch(ParseEntity.NameException pException)
        {
            Log.e(logTag, "Name not found to update : " + pException.toString());
            mValues.putDouble(pName, pValue);
        }

        write();
    }

    public boolean boolValue(String pName)
    {
        try
        {
            if(mValues != null && mValues.containsKey(pName))
                return mValues.getBoolean(pName);
        }
        catch(ParseEntity.NameException|ParseEntity.TypeException pException)
        {
            Log.e(logTag, "Invalid settings value : " + pException.toString());
        }

        return false;
    }

    public void setBoolValue(String pName, boolean pValue)
    {
        if(mValues == null)
            return;

        try
        {
            if(mValues.containsKey(pName))
                mValues.getParseEntity(pName).setValue(pValue);
            else
                mValues.putBoolean(pName, pValue);
        }
        catch(ParseEntity.NameException pException)
        {
            Log.e(logTag, "Name not found to update : " + pException.toString());
            mValues.putBoolean(pName, pValue);
        }

        write();
    }

    public void removeValue(String pName)
    {
        if(mValues == null)
            return;

        try
        {
            if(mValues.containsKey(pName))
                mValues.remove(mValues.getParseEntity(pName));
        }
        catch(ParseEntity.NameException pException)
        {
            Log.i(logTag, "Name not found to remove : " + pException.toString());
        }

        write();
    }
}

