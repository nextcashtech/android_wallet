package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;


public class GainLossAdapter extends RecyclerView.Adapter
{
    private class Item
    {
        long date;
        long costDate;
        long amount;
        double gainLoss;

        Item()
        {
            date = 0L;
            costDate = 0L;
            amount = 0L;
            gainLoss = 0.0;
        }

        Item(long pDate, long pCostDate, long pAmount, double pGainLoss)
        {
            date = pDate;
            costDate = pCostDate;
            amount = pAmount;
            gainLoss = pGainLoss;
        }
    }

    private class TaxLot
    {
        long date;
        long amount;
        double costRate;

        TaxLot(long pDate, long pAmount, double pCostRate)
        {
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
    private Item mTotalItem;
    private int mShadeColor, mNonShadeColor, mTotalColor, mPositiveColor, mNegativeColor;
    private String mTotalText;
    private boolean mValid;

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
        mShadeColor = pContext.getResources().getColor(R.color.rowShade);
        mNonShadeColor = pContext.getResources().getColor(R.color.rowNotShade);
        mTotalColor = pContext.getResources().getColor(R.color.colorTotals);
        mPositiveColor = pContext.getResources().getColor(R.color.colorPositive);
        mNegativeColor = pContext.getResources().getColor(R.color.colorNegative);
        mTotalText = pContext.getString(R.string.total);

        mItems = new ArrayList<>();
        mValid = calculate();
    }

    public boolean isValid() { return mValid; }

    private boolean calculate()
    {
        Transaction transactions[] = mBitcoin.wallet(mWalletIndex).transactions;
        ArrayList<TaxLot> taxLots = new ArrayList<>();
        Transaction transaction;
        Item newItem;

        for(int i = transactions.length - 1; i >= 0; i--)
        {
            transaction = transactions[i];

            if(!transaction.data.effectiveType().equals(mExchangeType))
                return false;

            if(transaction.amount > 0)
            {
                // Buy
                taxLots.add(new TaxLot(transaction.data.effectiveDate(), transaction.amount,
                  transaction.data.effectiveCost() / (double)transaction.amount));
            }
            else if(transaction.amount < 0)
            {
                // Sell
                long remainingAmount = -transaction.amount;
                double sellGain = transaction.data.effectiveCost();
                double gainLossRate;
                boolean found;

                while(remainingAmount > 0)
                {
                    found = false;
                    for(TaxLot taxLot : taxLots)
                        if(taxLot.amount > 0)
                        {
                            found = true;
                            if(taxLot.amount > remainingAmount)
                            {
                                // Split tax lot
                                gainLossRate = (sellGain / (double)-transaction.data.amount) - taxLot.costRate;

                                newItem = new Item(transaction.data.effectiveDate(), taxLot.date, remainingAmount,
                                  gainLossRate * remainingAmount);

                                if(newItem.date >= mStartDate && newItem.date <= mEndDate)
                                    mItems.add(newItem);

                                sellGain = 0.0;
                                taxLot.amount -= remainingAmount;
                                remainingAmount = 0L;
                            }
                            else if(taxLot.amount < remainingAmount)
                            {
                                // Split sell
                                gainLossRate = (sellGain / (double)-transaction.data.amount) - taxLot.costRate;

                                newItem = new Item(transaction.data.effectiveDate(), taxLot.date, taxLot.amount,
                                  gainLossRate * taxLot.amount);

                                if(newItem.date >= mStartDate && newItem.date <= mEndDate)
                                    mItems.add(newItem);

                                sellGain -= gainLossRate * taxLot.amount;
                                taxLot.amount = 0L;
                                remainingAmount -= taxLot.amount;
                                break;
                            }
                            else
                            {
                                // Even match
                                newItem = new Item(transaction.data.effectiveDate(), taxLot.date, remainingAmount,
                                  sellGain - (taxLot.costRate * remainingAmount));

                                if(newItem.date >= mStartDate && newItem.date <= mEndDate)
                                    mItems.add(newItem);

                                sellGain = 0.0;
                                taxLot.amount = 0L;
                                remainingAmount = 0L;
                            }
                        }

                    if(!found)
                        break; // Ran out of tax lots
                }
            }
        }

        mTotalItem = new Item();
        for(Item item : mItems)
            mTotalItem.gainLoss += item.gainLoss;

        return true;
    }

    @Override
    public int getItemViewType(int pPosition)
    {
        if(pPosition == mItems.size())
            return 1;
        else
            return 0;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup pViewGroup, int pViewType)
    {
        LayoutInflater inflater = LayoutInflater.from(pViewGroup.getContext());

        switch(pViewType)
        {
        default:
        case 0: // Regular Item
            return new GainLossItemViewHolder(inflater.inflate(R.layout.gain_loss_item, pViewGroup, false));
        case 1: // Total Item
            return new GainLossItemViewHolder(inflater.inflate(R.layout.gain_loss_item, pViewGroup, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder pViewHolder, int pPosition)
    {
        Item item;
        if(getItemViewType(pPosition) == 0)
        {
            item = mItems.get(pPosition);

            if(pPosition % 2 == 0)
                pViewHolder.itemView.setBackgroundColor(mNonShadeColor);
            else
                pViewHolder.itemView.setBackgroundColor(mShadeColor);
            ((TextView)pViewHolder.itemView.findViewById(R.id.date)).setText(
              String.format(Locale.getDefault(), "%1$tY-%1$tm-%1$td", item.date * 1000L));
            ((TextView)pViewHolder.itemView.findViewById(R.id.costDate)).setText(
              String.format(Locale.getDefault(), "%1$tY-%1$tm-%1$td", item.costDate * 1000L));
        }
        else // Totals
        {
            ((TextView)pViewHolder.itemView.findViewById(R.id.date)).setText(mTotalText);
            ((TextView)pViewHolder.itemView.findViewById(R.id.date)).setTypeface(null, Typeface.BOLD);
            ((TextView)pViewHolder.itemView.findViewById(R.id.costDate)).setText("");
            ((TextView)pViewHolder.itemView.findViewById(R.id.costDate)).setTypeface(null, Typeface.BOLD);
            item = mTotalItem;
            pViewHolder.itemView.setBackgroundColor(mTotalColor);
        }

        ((TextView)pViewHolder.itemView.findViewById(R.id.gainLoss)).setText(
          Bitcoin.formatAmount(item.gainLoss, mExchangeType));
        if(item.gainLoss > 0.0)
            ((TextView)pViewHolder.itemView.findViewById(R.id.gainLoss)).setTextColor(mPositiveColor);
        else
            ((TextView)pViewHolder.itemView.findViewById(R.id.gainLoss)).setTextColor(mNegativeColor);

    }

    @Override
    public int getItemCount()
    {
        return mItems.size() + 1;
    }
}
