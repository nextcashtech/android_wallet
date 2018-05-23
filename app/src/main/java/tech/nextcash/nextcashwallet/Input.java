package tech.nextcash.nextcashwallet;

public class Input
{
    public String outpointID;
    public int outpointIndex;
    public String script;
    public int sequence;

    public String address;
    public long amount; // Only set if related to key (spend)

    Input()
    {
        outpointIndex = 0xffffffff;
        sequence = 0xffffffff;
        amount = 0;
    }
}
