package tech.nextcash.nextcashwallet;

import android.content.Context;

import java.util.Locale;

public class FullTransaction
{
    public String hash; // Transaction ID
    public String block; // Block Hash in which transaction was confirmed
    public int count; // Pending = Number of validating nodes. Confirmed = Number of confirmations.

    public int version;
    public Input[] inputs;
    public Output[] outputs;
    public int lockTime;


    public FullTransaction()
    {
        version = 0;
        lockTime = 0xffffffff;
        count = 0xffffffff;
    }

    long amount()
    {
        if(inputs == null || outputs == null)
            return 0;

        long result = 0;

        for(Input input : inputs)
            if(input.amount != -1)
                result -= input.amount;

        for(Output output : outputs)
            if(output.related)
                result += output.amount;

        return result;
    }

    long fee()
    {
        if(inputs == null || outputs == null)
            return -1;

        long result = 0;

        for(Input input : inputs)
        {
            if(input.amount == -1)
                return -1;
            result += input.amount;
        }

        for(Output output : outputs)
            result -= output.amount;

        return result;
    }

    public String lockTimeString(Context pContext)
    {
        boolean sequenceFound = false;

        for(Input input : inputs)
            if(input.sequence != 0xffffffff)
                sequenceFound = true;

        if(sequenceFound)
        {
            if(lockTime > 500000000)
                return String.format(Locale.getDefault(), "%1$tY-%1$tm-%1$td", lockTime * 1000);
            else
                return String.format(Locale.getDefault(), "%s %d", pContext.getString(R.string.block), lockTime);
        }
        else
            return pContext.getString(R.string.none);
    }
}
