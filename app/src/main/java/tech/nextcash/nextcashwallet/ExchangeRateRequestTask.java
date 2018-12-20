/**************************************************************************
 * Copyright 2017-2018 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

public class ExchangeRateRequestTask extends AsyncTask<String, Integer, Double>
{
    public static final String logTag = "FiatRateRequestTask";

    public static final String USE_COINBASE_RATE_NAME = "use_coinbase_rate";
    public static final String USE_COINMARKETCAP_RATE_NAME = "use_coinmarketcap_rate";
    public static final String USE_COINLIB_RATE_NAME = "use_coinlib_rate";

    private Context mContext;
    private Bitcoin mBitcoin;
    private String mExchangeType;

    public ExchangeRateRequestTask(Context pContext, Bitcoin pBitcoin)
    {
        mContext = pContext;
        mBitcoin = pBitcoin;
        mExchangeType = mBitcoin.exchangeType();
    }

    private double getCoinMarketCap()
    {
        URL url;
        try
        {
            Settings settings = Settings.getInstance(mContext.getFilesDir());
            if(settings.intValue(Bitcoin.CHAIN_ID_NAME) == Bitcoin.CHAIN_ABC)
                url = new URL(String.format("https://api.coinmarketcap.com/v1/ticker/bitcoin-cash/?convert=%s",
                  mExchangeType));
            else
                url = new URL(String.format("https://api.coinmarketcap.com/v1/ticker/bitcoin-sv/?convert=%s",
                  mExchangeType));
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
                    result = ((JSONObject)json.get(0)).getDouble("price_" + mExchangeType.toLowerCase());
                    Log.i(logTag, String.format(Locale.getDefault(), "CoinMarketCap %s rate found : %,f",
                      mExchangeType, result));
                }
            }
        }
        catch(IOException|JSONException|NullPointerException pException)
        {
            Log.w(logTag, String.format("Exception on CoinMarketCap http request task : %s", pException.toString()));
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
            Settings settings = Settings.getInstance(mContext.getFilesDir());
            if(settings.intValue(Bitcoin.CHAIN_ID_NAME) == Bitcoin.CHAIN_ABC)
                url = new URL("https://api.coinbase.com/v2/exchange-rates?currency=BCH");
            else
                url = new URL("https://api.coinbase.com/v2/exchange-rates?currency=BSV");
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
                    result = json.getJSONObject("data").getJSONObject("rates").getDouble(mExchangeType);
                    Log.i(logTag, String.format(Locale.getDefault(), "CoinBase %s rate found : %,f",
                      mExchangeType, result));
                }
            }
        }
        catch(IOException|JSONException|NullPointerException pException)
        {
            Log.w(logTag, String.format("Exception on CoinBase http request task : %s", pException.toString()));
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
            Settings settings = Settings.getInstance(mContext.getFilesDir());
            if(settings.intValue(Bitcoin.CHAIN_ID_NAME) == Bitcoin.CHAIN_ABC)
                url = new URL(String.format("https://coinlib.io/api/v1/coin?key=a4c3d52c60dc7856&pref=%s&symbol=BCH",
                  mExchangeType));
            else
                url = new URL(String.format("https://coinlib.io/api/v1/coin?key=a4c3d52c60dc7856&pref=%s&symbol=BSV",
                  mExchangeType));
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
                    Log.i(logTag, String.format(Locale.getDefault(), "CoinLib %s rate found : %,f",
                      mExchangeType, result));
                }
            }
        }
        catch(IOException|JSONException|NullPointerException pException)
        {
            Log.w(logTag, String.format("Exception on CoinLib http request task : %s", pException.toString()));
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
        Settings settings = Settings.getInstance(mContext.getFilesDir());
        double coinBase = 0.0;
        double coinMarketCap = 0.0;
        double coinLib = 0.0;
        boolean usingCoinMarketCap = false;
        boolean usingCoinLib = false;

        if(!settings.containsValue(USE_COINBASE_RATE_NAME) || settings.boolValue(USE_COINBASE_RATE_NAME))
            coinBase = getCoinBase();

        if(!settings.containsValue(USE_COINMARKETCAP_RATE_NAME) || settings.boolValue(USE_COINMARKETCAP_RATE_NAME))
        {
            usingCoinMarketCap = true;
            coinMarketCap = getCoinMarketCap();
        }

        if(!settings.containsValue(USE_COINLIB_RATE_NAME) || settings.boolValue(USE_COINLIB_RATE_NAME))
        {
            usingCoinLib = true;
            coinLib = getCoinLib();
        }

        if(!usingCoinMarketCap && !usingCoinLib && coinBase == 0.0)
            coinBase = getCoinBase();

        ArrayList<Double> prices = new ArrayList<>();

        if(coinBase != 0.0)
            prices.add(coinBase);

        if(coinMarketCap != 0.0)
            prices.add(coinMarketCap);

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
            mBitcoin.setExchangeRate(pRate, mExchangeType);

            Intent finishIntent = new Intent(MainActivity.ACTIVITY_ACTION);
            finishIntent.setAction(MainActivity.ACTION_EXCHANGE_RATE_UPDATED);
            finishIntent.putExtra(MainActivity.ACTION_EXCHANGE_RATE_FIELD, pRate);
            mContext.sendBroadcast(finishIntent);
        }
        super.onPostExecute(pRate);
    }
}
