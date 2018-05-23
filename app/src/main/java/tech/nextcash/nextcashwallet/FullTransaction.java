package tech.nextcash.nextcashwallet;

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
}
