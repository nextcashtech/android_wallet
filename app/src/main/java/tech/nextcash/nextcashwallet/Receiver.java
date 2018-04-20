package tech.nextcash.nextcashwallet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;


public class Receiver extends BroadcastReceiver
{
    private static final String logTag = "Receiver";

    interface CallBacks
    {
        void onStop();
    }

    private CallBacks mCallBack;

    public Receiver(Context pContext, CallBacks pCallBack)
    {
        mCallBack = pCallBack;

        IntentFilter filter = new IntentFilter(pContext.getString(R.string.stop));
        LocalBroadcastManager.getInstance(pContext).registerReceiver(this, filter);
    }

    @Override
    public void onReceive(Context pContext, Intent pIntent)
    {
        if(pIntent.getAction() != null && pIntent.getAction().equals(pContext.getString(R.string.stop)))
        {
            Log.i(logTag, "Stop action received");
            mCallBack.onStop();
        }
    }
}
