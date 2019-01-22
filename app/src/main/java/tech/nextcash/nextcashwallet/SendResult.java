/**************************************************************************
 * Copyright 2017-2019 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
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
