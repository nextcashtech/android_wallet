/**************************************************************************
 * Copyright 2017-2018 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
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
        TextView bitcoinAmountText = pView.findViewById(R.id.bitcoinAmount);
        amountText.setText(Bitcoin.amountText(amount, pFiatRate));
        bitcoinAmountText.setText(Bitcoin.satoshiText(amount));
        if(amount > 0)
        {
            amountText.setTextColor(pContext.getResources().getColor(R.color.colorPositive));
            bitcoinAmountText.setTextColor(pContext.getResources().getColor(R.color.colorPositive));
        }
        else
        {
            amountText.setTextColor(pContext.getResources().getColor(R.color.colorNegative));
            bitcoinAmountText.setTextColor(pContext.getResources().getColor(R.color.colorNegative));
        }

        TextView timeText = pView.findViewById(R.id.time);
        if(count == -1)
            timeText.setText(pContext.getString(R.string.not_available_abbreviation));
        else
        {
            long diff = (System.currentTimeMillis() / 1000L) - date;
            if(diff < 60)
                timeText.setText(pContext.getString(R.string.just_now));
            else if(diff < 3600)
                timeText.setText(String.format(Locale.getDefault(), "%d %s", diff / 60, pContext.getString(R.string.minutes_abbreviation)));
            else if(diff < 86400)
                timeText.setText(String.format(Locale.getDefault(), "%d %s", diff / 3600, pContext.getString(R.string.hours_abbreviation)));
            else
                timeText.setText(String.format(Locale.getDefault(), "%1$tY-%1$tm-%1$td", date * 1000L));
        }

        TextView countText = pView.findViewById(R.id.count);
        if(count == -1)
            countText.setText(pContext.getString(R.string.not_available_abbreviation));
        else if(block == null)
            countText.setText(String.format(Locale.getDefault(), "%d", count));
        else if(count > 9)
            countText.setText(pContext.getString(R.string.nine_plus));
        else
            countText.setText(String.format(Locale.getDefault(), "%d", count));
    }

    private static native void setupJNI();

    static
    {
        setupJNI();
    }
}
