package tech.nextcash.nextcashwallet;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

public class FiatRateRequestTask extends AsyncTask<String, Integer, Double>
{
    public static final String logTag = "FiatRateRequestTask";

    private File mDirectory;

    public FiatRateRequestTask(File pDirectory)
    {
        mDirectory = pDirectory;
    }

    private double getCoinMarketCap()
    {
        URL url;
        try
        {
            url = new URL("https://api.coinmarketcap.com/v1/ticker/bitcoin-cash/");
        }
        catch(MalformedURLException pException)
        {
            Log.w(logTag, String.format("Exception on http request task : %s", pException.toString()));
            return 0.0;
        }

        double result = 0.0;
        HttpsURLConnection connection = null;

        try
        {
            connection = (HttpsURLConnection)url.openConnection();
            if(connection != null)
            {
                int responseCode = connection.getResponseCode();
                if(responseCode < 300)
                {
                    BufferedReader response = new BufferedReader(new InputStreamReader(
                      connection.getInputStream()));
                    String inputLine, text = "";
                    while ((inputLine = response.readLine()) != null)
                        text += inputLine + '\n';

                    JSONArray json = new JSONArray(text);
                    result = ((JSONObject)json.get(0)).getDouble("price_usd");
                    Log.i(logTag, String.format(Locale.getDefault(), "CoinMarketCap rate found : %,.2f",
                      result));
                }
            }
        }
        catch(IOException|JSONException pException)
        {
            Log.w(logTag, String.format("Exception on http request task : %s", pException.toString()));
        }
        finally
        {
            if(connection != null)
                connection.disconnect();
        }

        return result;
    }

    private double getCoinBase()
    {
        URL url;
        try
        {
            url = new URL("https://api.coinbase.com/v2/exchange-rates?currency=BCH");
        }
        catch(MalformedURLException pException)
        {
            Log.w(logTag, String.format("Exception on http request task : %s", pException.toString()));
            return 0.0;
        }

        double result = 0.0;
        HttpsURLConnection connection = null;

        try
        {
            connection = (HttpsURLConnection)url.openConnection();
            if(connection != null)
            {
                int responseCode = connection.getResponseCode();
                if(responseCode < 300)
                {
                    BufferedReader response = new BufferedReader(new InputStreamReader(
                      connection.getInputStream()));
                    String inputLine, text = "";
                    while((inputLine = response.readLine()) != null)
                        text += inputLine + '\n';

                    JSONObject json = new JSONObject(text);
                    result = json.getJSONObject("data").getJSONObject("rates").getDouble("USD");
                    Log.i(logTag, String.format(Locale.getDefault(), "CoinBase rate found : %,.2f", result));
                }
            }
        }
        catch(IOException|JSONException pException)
        {
            Log.w(logTag, String.format("Exception on http request task : %s", pException.toString()));
        }
        finally
        {
            if(connection != null)
                connection.disconnect();
        }

        return result;
    }

    @Override
    protected Double doInBackground(String... pValues)
    {
        double coinMarketCap = getCoinMarketCap();
        double coinBase = getCoinBase();

        if(coinMarketCap == 0.0 && coinBase == 0.0)
            return null;

        if(coinBase == 0.0)
            return coinMarketCap;

        if(coinMarketCap == 0.0)
            return coinBase;

        if(Math.abs(coinMarketCap - coinBase) / coinBase > 0.1)
            return 0.0;

        return (coinBase + coinMarketCap) / 2.0;
    }

    @Override
    protected void onPostExecute(Double pRate)
    {
        if(pRate != 0.0)
            Settings.getInstance(mDirectory).setDoubleValue("usd_rate", pRate);
        super.onPostExecute(pRate);
    }
}
