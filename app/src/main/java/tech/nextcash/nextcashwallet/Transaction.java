package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

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

        return String.format(Locale.getDefault(), "%s %,.5f %s",
          pContext.getString(startString), Bitcoin.bitcoins(amount), pContext.getString(endString));
    }

    public void updateView(Context pContext, View pView)
    {
        TextView amountText = pView.findViewById(R.id.amount);
        amountText.setText(String.format(Locale.getDefault(), "%+,.5f",
          Bitcoin.bitcoins(amount)));
        if(amount > 0)
            amountText.setTextColor(pContext.getResources().getColor(R.color.colorPositive));
        else
            amountText.setTextColor(pContext.getResources().getColor(R.color.colorNegative));

        ((TextView)pView.findViewById(R.id.date)).setText(String.format(Locale.getDefault(),
          "%1$tD %1$tr", date * 1000));

        if(block == null)
        {
            if(count == 1)
                ((TextView)pView.findViewById(R.id.count)).setText(String.format(Locale.getDefault(),
                  "%d %s", count, pContext.getString(R.string.peer)));
            else
                ((TextView)pView.findViewById(R.id.count)).setText(String.format(Locale.getDefault(),
                  "%d %s", count, pContext.getString(R.string.peers)));
        }
        else if(count > 9)
            ((TextView)pView.findViewById(R.id.count)).setText(String.format(Locale.getDefault(),
              "9+ %s", pContext.getString(R.string.confirms)));
        else if(count == 1)
            ((TextView)pView.findViewById(R.id.count)).setText(String.format(Locale.getDefault(), "%d %s",
              count, pContext.getString(R.string.confirm)));
        else
            ((TextView)pView.findViewById(R.id.count)).setText(String.format(Locale.getDefault(), "%d %s",
              count, pContext.getString(R.string.confirms)));

        ((TextView)pView.findViewById(R.id.hash)).setText(String.format(Locale.getDefault(),
          "%s...", hash.substring(0, 8)));
    }

    private static native void setupJNI();

    static
    {
        setupJNI();
    }
}
