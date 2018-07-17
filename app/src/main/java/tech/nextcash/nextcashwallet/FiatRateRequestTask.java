package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.content.Intent;
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
import java.util.ArrayList;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

public class FiatRateRequestTask extends AsyncTask<String, Integer, Double>
{
    public static final String logTag = "FiatRateRequestTask";

    private Context mContext;

    public FiatRateRequestTask(Context pContext)
    {
        mContext = pContext;
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

    private double getCoinLib()
    {
        URL url;
        try
        {
            url = new URL("https://coinlib.io/api/v1/coin?key=a4c3d52c60dc7856&pref=USD&symbol=BCH");
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

                    JSONObject json = new JSONObject(text);
                    result = json.getDouble("price");
                    Log.i(logTag, String.format(Locale.getDefault(), "CoinLib rate found : %,.2f",
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

    @Override
    protected Double doInBackground(String... pValues)
    {
        double coinMarketCap = getCoinMarketCap();
        double coinBase = getCoinBase();
        double coinLib = getCoinLib();

        ArrayList<Double> prices = new ArrayList<>();

        if(coinMarketCap != 0.0)
            prices.add(coinMarketCap);

        if(coinBase != 0.0)
            prices.add(coinBase);

        if(coinLib != 0.0)
            prices.add(coinLib);

        if(prices.size() == 0)
            return null;

        if(coinBase == 0.0)
            return coinMarketCap;

        if(coinMarketCap == 0.0)
            return coinBase;

        // TODO Add statistical check to exclude outliers

        double total = 0.0;
        for(Double price : prices)
            total += price;

        return total / (double)prices.size();
    }

    @Override
    protected void onPostExecute(Double pRate)
    {
        if(pRate != null && pRate != 0.0)
        {
            Settings.getInstance(mContext.getFilesDir()).setDoubleValue("usd_rate", pRate);

            Intent finishIntent = new Intent(MainActivity.ACTIVITY_ACTION);
            finishIntent.setAction(MainActivity.ACTION_EXCHANGE_RATE_UPDATED);
            finishIntent.putExtra(MainActivity.ACTION_EXCHANGE_RATE_FIELD, pRate);
            mContext.sendBroadcast(finishIntent);
        }
        super.onPostExecute(pRate);
    }
}
