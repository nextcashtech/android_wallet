package tech.nextcash.nextcashwallet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class BitcoinTest
{
    @Test
    public void runTests() throws Exception
    {
        Bitcoin bitcoin = new Bitcoin();
        assertEquals(bitcoin.test(), true);
    }
}
