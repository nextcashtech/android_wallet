/**************************************************************************
 * Copyright 2017-2019 NextCash, LLC                                      *
 * Contributors :                                                         *
 *   Curtis Ellis <curtis@nextcash.tech>                                  *
 * Distributed under the MIT software license, see the accompanying       *
 * file license.txt or http://www.opensource.org/licenses/mit-license.php *
 **************************************************************************/
package tech.nextcash.nextcashwallet;

public class KeyData
{
    public int version;
    public int depth;
    public int index;

    public KeyData()
    {
        version = 0;
        depth = 0;
        index = 0;
    }
}
