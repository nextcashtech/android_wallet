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
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Locale;


public class Transaction
{
    public String hash; // Transaction ID
    public String block; // Block Hash in which transaction was confirmed. Null when unconfirmed.
    public long date; // Date/Time in seconds since epoch of transaction
    public long amount; // Amount of transaction in satoshis. Negative for send.
    public int count; // Unconfirmed = Number of validating nodes. Confirmed = Number of confirmations.
    public TransactionData.Item data;

    public Transaction()
    {
        date = 0;
        amount = 0;
        count = 0;
        data = null;
    }

    public String description(Context pContext, Bitcoin pBitcoin)
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

        return String.format(Locale.getDefault(), "%s %s %s", pContext.getString(startString),
          pBitcoin.amountText(amount, data), pContext.getString(endString));
    }

    public void updateView(Context pContext, Bitcoin pBitcoin, ViewGroup pView)
    {
        ViewGroup basicGroup = (ViewGroup)pView.getChildAt(0);
        TextView amountText = basicGroup.findViewById(R.id.amount);
        TextView bitcoinAmountText = basicGroup.findViewById(R.id.bitcoinAmount);
        amountText.setText(pBitcoin.amountText(amount, data));
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

        TextView timeText = basicGroup.findViewById(R.id.time);
        if(data == null && count == -1)
            timeText.setText(pContext.getString(R.string.not_available_abbreviation));
        else
        {
            long dateToUse;
            if(data != null)
                dateToUse = data.date;
            else
                dateToUse = date;
            long diff = (System.currentTimeMillis() / 1000L) - dateToUse;
            if(diff < 60)
                timeText.setText(pContext.getString(R.string.just_now));
            else if(diff < 3600L)
                timeText.setText(String.format(Locale.getDefault(), "%d %s", diff / 60L,
                  pContext.getString(R.string.minutes_abbreviation)));
            else if(diff < 86400L)
                timeText.setText(String.format(Locale.getDefault(), "%d %s", diff / 3600L,
                  pContext.getString(R.string.hours_abbreviation)));
            else
                timeText.setText(String.format(Locale.getDefault(), "%1$tY-%1$tm-%1$td",
                  dateToUse * 1000L));
        }

        TextView countText = basicGroup.findViewById(R.id.count);
        if(count == -1)
            countText.setText(pContext.getString(R.string.not_available_abbreviation));
        else if(block == null)
            countText.setText(String.format(Locale.getDefault(), "%d", count));
        else if(count > 9)
            countText.setText(pContext.getString(R.string.nine_plus));
        else
            countText.setText(String.format(Locale.getDefault(), "%d", count));

        TextView commentView = pView.findViewById(R.id.comment);
        if(data != null && data.comment != null)
        {
            commentView.setText(data.comment);
            commentView.setVisibility(View.VISIBLE);
        }
        else
            commentView.setVisibility(View.GONE);

        // Set tag with transaction ID
        pView.setTag(new TransactionData.ID(hash, amount));
    }

    private static native void setupJNI();

    static
    {
        setupJNI();
    }
}
