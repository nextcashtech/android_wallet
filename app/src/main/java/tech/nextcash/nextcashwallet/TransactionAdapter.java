/**************************************************************************
 * Copyright 2017-2019 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
package tech.nextcash.nextcashwallet;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;


public class TransactionAdapter extends RecyclerView.Adapter
{
    private Context mContext;
    private Bitcoin mBitcoin;
    private int mWalletOffset;
    private LinearLayoutManager mLayoutManager;
    private ArrayList<Transaction> mItems;
    private int mShadeColor, mNonShadeColor;
    private int mCountWidth;

    public class TransactionViewHolder extends RecyclerView.ViewHolder
    {
        public TransactionViewHolder(@NonNull View pItemView)
        {
            super(pItemView);
        }
    }

    public TransactionAdapter(Context pContext, Bitcoin pBitcoin, int pWalletOffset, LinearLayoutManager pLayoutManager,
      int pShadeColor, int pNonShadeColor, int pCountWidth)
    {
        mContext = pContext;
        mBitcoin = pBitcoin;
        mWalletOffset = pWalletOffset;
        mLayoutManager = pLayoutManager;
        Transaction transactions[] = mBitcoin.wallet(mWalletOffset).transactions;
        mItems = new ArrayList<>(transactions.length);
        mItems.addAll(Arrays.asList(transactions));
        mShadeColor = pShadeColor;
        mNonShadeColor = pNonShadeColor;
        mCountWidth = pCountWidth;
    }

    public void updateTransactions()
    {
        Transaction transactions[] = mBitcoin.wallet(mWalletOffset).transactions;
        ArrayList<Transaction> newItems = new ArrayList<>(transactions.length);
        newItems.addAll(Arrays.asList(transactions));
        mItems = newItems;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int pPosition)
    {
        return 0;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup pViewGroup, int pViewType)
    {
        LayoutInflater inflater = LayoutInflater.from(pViewGroup.getContext());
        LinearLayout itemView;

        switch(pViewType)
        {
        default:
        case 0: // Transaction Item
            itemView = (LinearLayout)inflater.inflate(R.layout.wallet_transaction, pViewGroup,
              false);
            itemView.findViewById(R.id.count).setMinimumWidth(mCountWidth);
            break;
        }

        return new TransactionViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder pViewHolder, int pPosition)
    {
        if(getItemViewType(pPosition) == 0)
        {
            mItems.get(pPosition).updateView(mContext, mBitcoin, (ViewGroup)pViewHolder.itemView, true);
            if(pPosition % 2 == 0)
                pViewHolder.itemView.setBackgroundColor(mNonShadeColor);
            else
                pViewHolder.itemView.setBackgroundColor(mShadeColor);
        }
    }

    @Override
    public int getItemCount()
    {
        return mItems.size();
    }


}
