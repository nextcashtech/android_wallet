package tech.nextcash.nextcashwallet;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;


public class AddressBookAdapter extends RecyclerView.Adapter
{
    private Bitcoin mBitcoin;
    private ArrayList<AddressBook.Item> mItems;
    private AddressBook.Item mItemToDelete;
    private int mPositionToDelete;
    private int mShadeColor, mNonShadeColor;

    public class AddressBookViewHolder extends RecyclerView.ViewHolder
    {
        public AddressBookViewHolder(@NonNull View pItemView)
        {
            super(pItemView);
        }
    }

    public AddressBookAdapter(Bitcoin pBitcoin, int pShadeColor, int pNonShadeColor)
    {
        mBitcoin = pBitcoin;
        mItems = pBitcoin.getAddresses();
        Collections.sort(mItems);
        mShadeColor = pShadeColor;
        mNonShadeColor = pNonShadeColor;

        mItemToDelete = null;
        mPositionToDelete = -1;
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

        switch(pViewType)
        {
        default:
        case 0: // Regular Item
            itemView = (LinearLayout)inflater.inflate(R.layout.address_book_item, pViewGroup,
              false);
            break;
        case 1: // Manual Add Item
            itemView = (LinearLayout)inflater.inflate(R.layout.address_book_manual_add, pViewGroup,
              false);
            break;
        }

        return new AddressBookViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder pViewHolder, int pPosition)
    {
        if(getItemViewType(pPosition) == 0)
        {
            AddressBook.Item item = mItems.get(pPosition);

            ((TextView)pViewHolder.itemView.findViewById(R.id.addressBookName)).setText(item.name);
            ((TextView)pViewHolder.itemView.findViewById(R.id.addressBookAddress)).setText(item.address);

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
            mBitcoin.removeAddress(mItemToDelete.address, false);

        mItemToDelete = mItems.get(pPosition);
        mPositionToDelete = pPosition;
        mItems.remove(pPosition);
        notifyItemRemoved(pPosition);
    }

    public void undoDelete()
    {
        mItems.add(mPositionToDelete, mItemToDelete);
        notifyItemInserted(mPositionToDelete);
        mPositionToDelete = -1;
        mItemToDelete = null;
    }

    public void confirmDelete()
    {
        mBitcoin.removeAddress(mItemToDelete.address, true);
        mPositionToDelete = -1;
        mItemToDelete = null;
    }
}
