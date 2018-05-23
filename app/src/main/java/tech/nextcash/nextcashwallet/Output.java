package tech.nextcash.nextcashwallet;

public class Output
{
    public long amount;
    public String script;

    public String address;
    public boolean related;

    Output()
    {
        amount = 0;
        related = false;
    }
}
