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
            result -= input.amount;

        for(Output output : outputs)
            if(output.related)
                result += output.amount;

        return result;
    }

    public String lockTimeString(Context pContext)
    {
        if(version < 2)
            return String.format(Locale.getDefault(), "0x%08x", lockTime);

        if(lockTime == 0xffffffff)
            return pContext.getString(R.string.none);

        if(lockTime > 500000000)
            return String.format(Locale.getDefault(), "%1$tY-%1$tm-%1$td", lockTime * 1000);
        else
            return String.format(Locale.getDefault(), "%s %d", pContext.getString(R.string.block), lockTime);
    }
}
