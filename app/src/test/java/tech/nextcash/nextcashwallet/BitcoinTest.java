/**************************************************************************
 * Copyright 2017-2018 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
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
