package tech.nextcash.nextcashwallet;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class NextCashTest
{
    @Test
    public void runTests() throws Exception
    {
        assertEquals(NextCash.test(), true);
    }
}
