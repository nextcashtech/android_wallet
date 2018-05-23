package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;


public class Transaction
{
    public String hash; // Transaction ID
    public String block; // Block Hash in which transaction was confirmed
    public long date; // Date/Time in seconds since epoch of transaction
    public long amount; // Amount of transaction in satoshis. Negative for send.
    public int count; // Pending = Number of validating nodes. Confirmed = Number of confirmations.

    public Transaction()
    {
        date = 0;
        amount = 0;
        count = 0;
    }

    public String description(Context pContext)
    {
        int startString, endString;

        if(amount > 0)
            startString = R.string.receive_description_start;
        else
            startString = R.string.send_description_start;

        if(block == null)
            endString = R.string.pending_notification_description_end;
        else
            endString = R.string.confirmed_notification_description_end;

        double fiatRate = Settings.getInstance(pContext.getFilesDir()).doubleValue("usd_rate");

        return String.format(Locale.getDefault(), "%s %s %s",
          pContext.getString(startString), Bitcoin.amountText(amount, fiatRate), pContext.getString(endString));
    }

    public void updateView(Context pContext, View pView, double pFiatRate)
    {
        TextView amountText = pView.findViewById(R.id.amount);
        amountText.setText(Bitcoin.amountText(amount, pFiatRate));
        if(amount > 0)
            amountText.setTextColor(pContext.getResources().getColor(R.color.colorPositive));
        else
            amountText.setTextColor(pContext.getResources().getColor(R.color.colorNegative));

        long diff = (System.currentTimeMillis() / 1000) - date;
        if(diff < 60)
            ((TextView)pView.findViewById(R.id.time)).setText(String.format(Locale.getDefault(),
              "%d secs", diff));
        else if(diff < 3600)
            ((TextView)pView.findViewById(R.id.time)).setText(String.format(Locale.getDefault(),
              "%d mins", diff / 60));
        else if(diff < 86400)
            ((TextView)pView.findViewById(R.id.time)).setText(String.format(Locale.getDefault(),
              "%d hrs", diff / 3600));
        else
            ((TextView)pView.findViewById(R.id.time)).setText(String.format(Locale.getDefault(),
              "%1$tY-%1$tm-%1$td", date * 1000));

        if(block == null)
            ((TextView)pView.findViewById(R.id.count)).setText(String.format(Locale.getDefault(), "%d", count));
        else if(count > 9)
            ((TextView)pView.findViewById(R.id.count)).setText(pContext.getString(R.string.nine_plus));
        else
            ((TextView)pView.findViewById(R.id.count)).setText(String.format(Locale.getDefault(), "%d", count));
    }

    private static native void setupJNI();

    static
    {
        setupJNI();
    }
}
