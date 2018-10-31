package tech.nextcash.nextcashwallet;

import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;


public class AddressLabelAdapter extends RecyclerView.Adapter
{
    private Bitcoin mBitcoin;
    private LinearLayoutManager mLayoutManager;
    private ArrayList<AddressLabel.Item> mItems;
    private AddressLabel.Item mItemToDelete;
    private int mPositionToDelete;
    private int mShadeColor, mNonShadeColor;
    private String mAnyAmountString;

    public class AddressLabelViewHolder extends RecyclerView.ViewHolder
    {
        public AddressLabelViewHolder(@NonNull View pItemView)
        {
            super(pItemView);
        }
    }

    public AddressLabelAdapter(Bitcoin pBitcoin, LinearLayoutManager pLayoutManager, int pWalletOffset,
      int pShadeColor, int pNonShadeColor)
    {
        mBitcoin = pBitcoin;
        mLayoutManager = pLayoutManager;
        mItems = pBitcoin.getAddressLabels(pWalletOffset);
        Collections.reverse(mItems);
        mShadeColor = pShadeColor;
        mNonShadeColor = pNonShadeColor;

        mItemToDelete = null;
        mPositionToDelete = -1;
        mAnyAmountString = null;
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
        LinearLayout itemView;

        if(mAnyAmountString == null)
            mAnyAmountString = pViewGroup.getContext().getString(R.string.any_amount);

        switch(pViewType)
        {
        default:
        case 0: // Regular Item
            itemView = (LinearLayout)inflater.inflate(R.layout.address_label_item, pViewGroup,
              false);
            break;
        case 1: // Manual Add Item
            itemView = (LinearLayout)inflater.inflate(R.layout.address_label_manual_add, pViewGroup,
              false);
            break;
        }

        return new AddressLabelViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder pViewHolder, int pPosition)
    {
        if(getItemViewType(pPosition) == 0)
        {
            AddressLabel.Item item = mItems.get(pPosition);

            ((TextView)pViewHolder.itemView.findViewById(R.id.addressLabelName)).setText(item.label);
            ((TextView)pViewHolder.itemView.findViewById(R.id.addressLabelAddress)).setText(item.address);
            if(item.amount == 0)
                ((TextView)pViewHolder.itemView.findViewById(R.id.addressLabelAmount)).setText(mAnyAmountString);
            else
                ((TextView)pViewHolder.itemView.findViewById(R.id.addressLabelAmount))
                  .setText(mBitcoin.amountText(item.amount));

            if(pPosition % 2 == 0)
                pViewHolder.itemView.setBackgroundColor(mNonShadeColor);
            else
                pViewHolder.itemView.setBackgroundColor(mShadeColor);
        }
    }

    @Override
    public int getItemCount()
    {
        return mItems.size() + 1;
    }

    public void remove(int pPosition)
    {
        if(pPosition >= mItems.size())
            return;

        if(mItemToDelete != null)
            mBitcoin.removeAddressLabel(mItemToDelete.address, false);

        mItemToDelete = mItems.get(pPosition);
        mPositionToDelete = pPosition;
        mItems.remove(pPosition);
        notifyItemRemoved(pPosition);
    }

    public void undoDelete()
    {
        if(mItemToDelete == null)
            return;
        mItems.add(mPositionToDelete, mItemToDelete);
        notifyItemInserted(mPositionToDelete);
        mLayoutManager.scrollToPosition(mPositionToDelete);
        mPositionToDelete = -1;
        mItemToDelete = null;
    }

    public void confirmDelete()
    {
        if(mItemToDelete == null)
            return;
        mBitcoin.removeAddressLabel(mItemToDelete.address, true);
        mPositionToDelete = -1;
        mItemToDelete = null;
    }
}
