/**************************************************************************
 * Copyright 2017-2019 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;


public class GainLossAdapter extends RecyclerView.Adapter
{
    public static final int columnCount = 4;

    private static String logTag = "GainLossAdapter";

    private class Item
    {
        long amount;
        long receiveDate, sendDate;
        String receiveTransaction, sendTransaction;
        double receiveRate, sendRate;

        Item()
        {
            sendDate = 0L;
            receiveDate = 0L;
            amount = 0L;
            sendTransaction = null;
            receiveTransaction = null;
            receiveRate = 0.0;
            sendRate = 0.0;
        }

        double receiveValue() { return (double)amount * receiveRate; }
        double sendValue() { return (double)amount * sendRate; }
        double gainLoss() { return sendValue() - receiveValue(); }
    }

    private class TaxLot
    {
        long date;
        long amount;
        double costRate;
        String transaction;

        TaxLot(String pTransaction, long pDate, long pAmount, double pCostRate)
        {
            transaction = pTransaction;
            date = pDate;
            amount = pAmount;
            costRate = pCostRate;
        }
    }

    private Bitcoin mBitcoin;
    private long mStartDate, mEndDate;
    private String mExchangeType;
    private int mWalletIndex;
    private ArrayList<Item> mItems;
    private double mTotalGainLoss;
    private int mShadeColor, mNonShadeColor, mHeaderColor, mTotalColor, mDefaultTextColor, mPositiveColor,
      mNegativeColor;
    private String mTotalText, mDateText, mCostDateText, mCostText, mAmountText, mGainLossText;
    public enum Status { VALID, MISMATCHED_EXCHANGE_TYPES, INVALID_TRANSACTIONS }
    private Status mStatus;

    private static final int mDateColumn = 0;
    private static final int mCostDateColumn = 1;
//    private static final int mCostColumn = 2;
    private static final int mAmountColumn = 2;
    private static final int mGainLossColumn = 3;

    public class GainLossItemViewHolder extends RecyclerView.ViewHolder
    {
        GainLossItemViewHolder(@NonNull View pItemView)
        {
            super(pItemView);
        }
    }

    public GainLossAdapter(Context pContext, Bitcoin pBitcoin, int pWalletIndex, long pStartDate, long pEndDate)
    {
        mBitcoin = pBitcoin;
        mWalletIndex = pWalletIndex;
        mStartDate = pStartDate;
        mEndDate = pEndDate;
        mExchangeType = mBitcoin.exchangeType();
        mTotalGainLoss = 0.0;

        mShadeColor = pContext.getResources().getColor(R.color.rowShade);
        mNonShadeColor = pContext.getResources().getColor(R.color.rowNotShade);
        mHeaderColor = pContext.getResources().getColor(R.color.colorHeader);
        mTotalColor = pContext.getResources().getColor(R.color.colorTotals);
        mDefaultTextColor = pContext.getResources().getColor(R.color.messageText);
        mPositiveColor = pContext.getResources().getColor(R.color.colorPositive);
        mNegativeColor = pContext.getResources().getColor(R.color.colorNegative);

        mTotalText = pContext.getString(R.string.total);
        mTotalText = pContext.getString(R.string.total);
        mDateText = pContext.getString(R.string.date);
        mCostDateText = pContext.getString(R.string.cost_date);
        mGainLossText = pContext.getString(R.string.gain_loss);
        mAmountText = pContext.getString(R.string.amount_title);
        mCostText = pContext.getString(R.string.cost);

        mItems = new ArrayList<>();

        calculate();
    }

    public Status status() { return mStatus; }
    public boolean isValid() { return mStatus == Status.VALID; }

    private void calculate()
    {
        mStatus = Status.VALID;

        Transaction transactions[] = mBitcoin.wallet(mWalletIndex).transactions;
        ArrayList<TaxLot> taxLots = new ArrayList<>();
        Transaction transaction;
        Item newItem;

        for(int i = transactions.length - 1; i >= 0; i--)
        {
            transaction = transactions[i];

            if(!transaction.data.effectiveType().equals(mExchangeType))
            {
                mStatus = Status.MISMATCHED_EXCHANGE_TYPES; // Mismatched exchange types
                return;
            }

            if(transaction.amount > 0)
            {
                // Receive
                taxLots.add(new TaxLot(transaction.hash, transaction.data.effectiveDate(), transaction.amount,
                  transaction.data.effectiveCost() / (double)transaction.amount));
            }
            else if(transaction.amount < 0)
            {
                // Send
                long sendAmount = Math.abs(transaction.amount);
                double sendRate = transaction.data.effectiveCost() / sendAmount;
                boolean found;

                while(sendAmount > 0)
                {
                    found = false;
                    for(TaxLot taxLot : taxLots)
                        if(taxLot.amount > 0)
                        {
                            found = true;
                            if(taxLot.amount > sendAmount)
                            {
                                // Split tax lot
                                newItem = new Item();

                                newItem.amount = sendAmount;

                                newItem.sendDate = transaction.data.effectiveDate();
                                newItem.sendTransaction = transaction.hash;
                                newItem.sendRate = sendRate;

                                newItem.receiveDate = taxLot.date;
                                newItem.receiveTransaction = taxLot.transaction;
                                newItem.receiveRate = taxLot.costRate;

                                if(newItem.sendDate >= mStartDate && newItem.sendDate <= mEndDate)
                                    mItems.add(newItem);

                                taxLot.amount -= sendAmount;
                                sendAmount = 0L;
                                break;
                            }
                            else if(taxLot.amount < sendAmount)
                            {
                                // Split sell
                                newItem = new Item();

                                newItem.amount = taxLot.amount;

                                newItem.sendDate = transaction.data.effectiveDate();
                                newItem.sendTransaction = transaction.hash;
                                newItem.sendRate = sendRate;

                                newItem.receiveDate = taxLot.date;
                                newItem.receiveTransaction = taxLot.transaction;
                                newItem.receiveRate = taxLot.costRate;

                                if(newItem.sendDate >= mStartDate && newItem.sendDate <= mEndDate)
                                    mItems.add(newItem);

                                sendAmount -= taxLot.amount;
                                taxLot.amount = 0L;
                            }
                            else
                            {
                                // Even match
                                newItem = new Item();

                                newItem.amount = taxLot.amount;
                                newItem.sendDate = transaction.data.effectiveDate();
                                newItem.sendTransaction = transaction.hash;
                                newItem.sendRate = sendRate;

                                newItem.receiveDate = taxLot.date;
                                newItem.receiveTransaction = taxLot.transaction;
                                newItem.receiveRate = taxLot.costRate;

                                if(newItem.sendDate >= mStartDate && newItem.sendDate <= mEndDate)
                                    mItems.add(newItem);

                                taxLot.amount = 0L;
                                sendAmount = 0L;
                                break;
                            }
                        }

                    if(!found)
                    {
                        mStatus = Status.INVALID_TRANSACTIONS; // Ran out of tax lots
                        return;
                    }
                }
            }
        }

        mTotalGainLoss = 0.0;
        for(Item item : mItems)
            mTotalGainLoss += item.gainLoss();
    }

    public int column(int pPosition)
    {
        return pPosition % columnCount;
    }

    public int row(int pPosition)
    {
        return (pPosition / columnCount) - 1;
    }

    @Override
    public int getItemViewType(int pPosition)
    {
        if(pPosition < columnCount)
            return column(pPosition);
        else if(pPosition >= (mItems.size() + 1) * columnCount)
            return (columnCount * 2) + column(pPosition);
        else
            return columnCount + column(pPosition);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup pViewGroup, int pViewType)
    {
        LayoutInflater inflater = LayoutInflater.from(pViewGroup.getContext());
        TextView result = (TextView)inflater.inflate(R.layout.report_cell, pViewGroup, false);

        switch(pViewType % columnCount)
        {
        case mDateColumn:
        case mCostDateColumn:
            result.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
            break;
//        case mCostColumn:
        case mAmountColumn:
        case mGainLossColumn:
            result.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
            break;
        default:
            break;
        }

        if(pViewType < columnCount) // Header
        {
            result.setTypeface(null, Typeface.BOLD);
            result.setBackgroundColor(mHeaderColor);
        }
        else if(pViewType >= (columnCount * 2)) // Totals
        {
            result.setTypeface(null, Typeface.BOLD);
            result.setBackgroundColor(mTotalColor);
        }

        result.setTextColor(mDefaultTextColor);
        return new GainLossItemViewHolder(result);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder pViewHolder, int pPosition)
    {
        if(getItemViewType(pPosition) < columnCount) // Header
        {
            switch(column(pPosition))
            {
            case mDateColumn:
                ((TextView)pViewHolder.itemView).setText(mDateText);
                break;
            case mCostDateColumn:
                ((TextView)pViewHolder.itemView).setText(mCostDateText);
                break;
//            case mCostColumn:
//                ((TextView)pViewHolder.itemView).setText(mCostText);
//                break;
            case mAmountColumn:
                ((TextView)pViewHolder.itemView).setText(mAmountText);
                break;
            case mGainLossColumn:
                ((TextView)pViewHolder.itemView).setText(mGainLossText);
                break;
            default:
                break;
            }
        }
        else if(getItemViewType(pPosition) < (columnCount * 2)) // Regular
        {
            Item item = mItems.get(row(pPosition));

            switch(column(pPosition))
            {
            case mDateColumn:
                ((TextView)pViewHolder.itemView).setText(String.format(Locale.getDefault(),
                  "%1$tY-%1$tm-%1$td", item.sendDate * 1000L));
                ((TextView)pViewHolder.itemView).setTextColor(mDefaultTextColor);
                break;
            case mCostDateColumn:
                ((TextView)pViewHolder.itemView).setText(String.format(Locale.getDefault(),
                  "%1$tY-%1$tm-%1$td", item.receiveDate * 1000L));
                ((TextView)pViewHolder.itemView).setTextColor(mDefaultTextColor);
                break;
//            case mCostColumn:
//                ((TextView)pViewHolder.itemView).setText(Bitcoin.formatAmount(Math.abs(item.receiveValue()),
//                  mExchangeType));
//                ((TextView)pViewHolder.itemView).setTextColor(mDefaultTextColor);
//                break;
            case mAmountColumn:
                ((TextView)pViewHolder.itemView).setText(Bitcoin.formatAmount(Math.abs(item.sendValue()),
                  mExchangeType));
                ((TextView)pViewHolder.itemView).setTextColor(mDefaultTextColor);
                break;
            case mGainLossColumn:
                ((TextView)pViewHolder.itemView).setText(Bitcoin.formatAmount(Math.abs(item.gainLoss()),
                  mExchangeType));
                if(item.gainLoss() > 0.0)
                    ((TextView)pViewHolder.itemView).setTextColor(mPositiveColor);
                else
                    ((TextView)pViewHolder.itemView).setTextColor(mNegativeColor);
                break;
            default:
                break;
            }

            if(row(pPosition) % 2 == 0)
                pViewHolder.itemView.setBackgroundColor(mNonShadeColor);
            else
                pViewHolder.itemView.setBackgroundColor(mShadeColor);
        }
        else // Total
        {
            switch(column(pPosition))
            {
            case mDateColumn:
                ((TextView)pViewHolder.itemView).setText(mTotalText);
                break;
            case mCostDateColumn:
                ((TextView)pViewHolder.itemView).setText("");
                break;
//            case mCostColumn:
//                ((TextView)pViewHolder.itemView).setText("");
//                break;
            case mAmountColumn:
                ((TextView)pViewHolder.itemView).setText("");
                break;
            case mGainLossColumn:
                ((TextView)pViewHolder.itemView).setText(Bitcoin.formatAmount(Math.abs(mTotalGainLoss), mExchangeType));
                if(mTotalGainLoss > 0.0)
                    ((TextView)pViewHolder.itemView).setTextColor(mPositiveColor);
                else
                    ((TextView)pViewHolder.itemView).setTextColor(mNegativeColor);
                break;
            default:
                break;
            }
        }
    }

    @Override
    public int getItemCount()
    {
        return (mItems.size() + 2) * columnCount;
    }

    public boolean writeData(Context pContext, OutputStream pOutput)
    {
        try
        {
            // Header line
            pOutput.write(pContext.getString(R.string.date).getBytes());
            pOutput.write(',');
            pOutput.write(pContext.getString(R.string.send_transaction).getBytes());
            pOutput.write(',');
            pOutput.write(pContext.getString(R.string.bitcoins).getBytes());
            pOutput.write(',');
            pOutput.write(pContext.getString(R.string.exchange_currency).getBytes());
            pOutput.write(',');
            pOutput.write(pContext.getString(R.string.cost_date).getBytes());
            pOutput.write(',');
            pOutput.write(pContext.getString(R.string.cost_transaction).getBytes());
            pOutput.write(',');
            pOutput.write(pContext.getString(R.string.cost_exchange_rate).getBytes());
            pOutput.write(',');
            pOutput.write(pContext.getString(R.string.cost_basis).getBytes());
            pOutput.write(',');
            pOutput.write(pContext.getString(R.string.send_exchange_rate).getBytes());
            pOutput.write(',');
            pOutput.write(pContext.getString(R.string.send_value).getBytes());
            pOutput.write(',');
            pOutput.write(pContext.getString(R.string.gain_loss).getBytes());
            pOutput.write('\n');

            for(Item item : mItems)
            {
                // Date
                pOutput.write(
                  String.format(Locale.getDefault(), "%1$tY-%1$tm-%1$td", item.sendDate * 1000L).getBytes());
                pOutput.write(',');

                // Send Transaction
                pOutput.write(item.sendTransaction.getBytes());
                pOutput.write(',');

                // Bitcoins
                pOutput.write(String.format(Locale.getDefault(), "%.08f",
                  Bitcoin.bitcoinsFromSatoshis(item.amount)).getBytes());
                pOutput.write(',');

                // Exchange Currency
                pOutput.write(mExchangeType.getBytes());
                pOutput.write(',');

                // Cost Date
                pOutput.write(String.format(Locale.getDefault(), "%1$tY-%1$tm-%1$td",
                  item.receiveDate * 1000L).getBytes());
                pOutput.write(',');

                // Cost Transaction
                pOutput.write(item.receiveTransaction.getBytes());
                pOutput.write(',');

                // Cost Exchange Rate
                pOutput.write(String.format(Locale.getDefault(), "%.02f",
                  item.receiveRate * Bitcoin.SATOSHIS_PER_BITCOIN).getBytes());
                pOutput.write(',');

                // Cost Basis
                pOutput.write(Bitcoin.formatAmountCSV(item.receiveValue(), mExchangeType).getBytes());
                pOutput.write(',');

                // Send Exchange Rate
                pOutput.write(String.format(Locale.getDefault(), "%.02f",
                  item.sendRate * Bitcoin.SATOSHIS_PER_BITCOIN).getBytes());
                pOutput.write(',');

                // Send Value
                pOutput.write(Bitcoin.formatAmountCSV(item.sendValue(), mExchangeType).getBytes());
                pOutput.write(',');

                // Gain Loss
                pOutput.write(Bitcoin.formatAmountCSV(item.gainLoss(), mExchangeType).getBytes());
                pOutput.write('\n');
            }
        }
        catch(IOException pException)
        {
            Log.e(logTag, String.format(Locale.US, "Write data exception : %s", pException.toString()));
            return false;
        }

        return true;
    }
}
