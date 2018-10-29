/**************************************************************************
 * Copyright 2017-2018 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
package tech.nextcash.nextcashwallet;

import android.app.Application;
import android.content.res.Configuration;
import android.os.AsyncTask;
import android.util.Log;

import java.util.Locale;


public class MainApp extends Application
{
    public static final String logTag = "MainApp";

    public Bitcoin bitcoin;

    public MainApp()
    {
        super();
    }

    @Override
    public void onCreate()
    {
        bitcoin = new Bitcoin();

        AsyncTask initializeTask = new AsyncTask()
        {
            @Override
            protected Object doInBackground(Object[] objects)
            {
                bitcoin.initialize(getApplicationContext());
                return null;
            }
        };
        initializeTask.execute();

        super.onCreate();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onLowMemory()
    {
        Log.w(logTag, "Low Memory");
        super.onLowMemory();
    }

    @Override
    public void onTrimMemory(int pLevel)
    {
        Log.w(logTag, String.format(Locale.getDefault(), "Trim Memory : %d", pLevel));
        super.onTrimMemory(pLevel);
    }
}
