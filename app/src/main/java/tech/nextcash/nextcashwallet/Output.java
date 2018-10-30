/**************************************************************************
 * Copyright 2017-2018 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
package tech.nextcash.nextcashwallet;

public class Output
{
    public long amount;
    public String script;
    public byte scriptData[];

    public String address;
    public boolean related;

    Output()
    {
        amount = 0;
        script = null;
        scriptData = null;

        address = null;
        related = false;
    }
}
