package tech.nextcash.nextcashwallet;

import android.os.AsyncTask;

public class BitcoinLoader extends AsyncTask<String, Integer, Integer>
{
    public BitcoinLoader()
    {
        super();
    }

    @Override
    protected Integer doInBackground(String[] pStrings)
    {
        Bitcoin.setPath(pStrings[0]);
        Bitcoin.load();
        return null;
    }

    @Override
    protected void onPreExecute()
    {
        super.onPreExecute();
    }

    @Override
    protected void onPostExecute(Integer pObject)
    {
        super.onPostExecute(pObject);
    }
}
