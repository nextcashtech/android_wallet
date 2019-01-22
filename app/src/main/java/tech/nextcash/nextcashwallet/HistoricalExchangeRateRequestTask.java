/**************************************************************************
 * Copyright 2017-2019 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.os.AsyncTask;

public class HistoricalExchangeRateRequestTask extends AsyncTask<String, Integer, Double>
{
    public static final String logTag = "HistExchRateRequestTask";

    private Context mContext;
    private String mExchangeType;
    private Bitcoin mBitcoin;
    private TransactionData.Item mItem;

    public HistoricalExchangeRateRequestTask(Context pContext, Bitcoin pBitcoin, TransactionData.Item pItem,
      String pExchangeType)
    {
        mContext = pContext;
        mBitcoin = pBitcoin;
        mItem = pItem;
        mExchangeType = pExchangeType;
    }

//    private double getCoinLib()
//    {
//        URL url;
//        try
//        {
//            Settings settings = Settings.getInstance(mContext.getFilesDir());
//            if(settings.intValue(Bitcoin.CHAIN_ID_NAME) == Bitcoin.CHAIN_ABC)
//                url = new URL(String.format("https://coinlib.io/api/v1/coin?key=a4c3d52c60dc7856&pref=%s&symbol=BCH",
//                  mExchangeType));
//            else
//                url = new URL(String.format("https://coinlib.io/api/v1/coin?key=a4c3d52c60dc7856&pref=%s&symbol=BSV",
//                  mExchangeType));
//        }
//        catch(MalformedURLException pException)
//        {
//            Log.w(logTag, String.format("Exception on http request task : %s", pException.toString()));
//            return 0.0;
//        }
//
//        double result = 0.0;
//        HttpsURLConnection connection = null;
//
//        try
//        {
//            connection = (HttpsURLConnection)url.openConnection();
//            if(connection != null)
//            {
//                int responseCode = connection.getResponseCode();
//                if(responseCode < 300)
//                {
//                    BufferedReader response = new BufferedReader(new InputStreamReader(
//                      connection.getInputStream()));
//                    String inputLine, text = "";
//                    while ((inputLine = response.readLine()) != null)
//                        text += inputLine + '\n';
//
//                    JSONObject json = new JSONObject(text);
//                    result = json.getDouble("price");
//                    Log.i(logTag, String.format(Locale.getDefault(), "CoinLib %s rate found : %,f",
//                      mExchangeType, result));
//                }
//            }
//        }
//        catch(IOException|JSONException|NullPointerException pException)
//        {
//            Log.w(logTag, String.format("Exception on CoinLib http request task : %s", pException.toString()));
//        }
//        finally
//        {
//            if(connection != null)
//                connection.disconnect();
//        }
//
//        return result;
//    }

    @Override
    protected Double doInBackground(String... pValues)
    {
        // TODO Request historical rate from source.
        return 0.0;
    }

    @Override
    protected void onPostExecute(Double pRate)
    {
        if(pRate != null && pRate != 0.0)
        {
            mItem.exchangeRate = pRate;
            mItem.exchangeType = mExchangeType;
            mBitcoin.saveTransactionData();
        }
        super.onPostExecute(pRate);
    }
}
