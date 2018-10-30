package tech.nextcash.nextcashwallet;

public class SendResult
{
    public int result;
    public Transaction transaction;
    public byte rawTransaction[];

    SendResult()
    {
        result = 1;
        transaction = null;
        rawTransaction = null;
    }

    SendResult(int pResult)
    {
        result = pResult;
        transaction = null;
        rawTransaction = null;
    }
}
