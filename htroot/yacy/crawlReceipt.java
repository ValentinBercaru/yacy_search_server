// crawlReceipt.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Using this software in any meaning (reading, learning, copying, compiling,
// running) means that you agree that the Author(s) is (are) not responsible
// for cost, loss of data or any harm that may be caused directly or indirectly
// by usage of this softare or this documentation. The usage of this software
// is on your own risk. The installation and usage (starting/running) of this
// software may allow other people or application to access your computer and
// any attached devices and is highly dependent on the configuration of the
// software which must be done by the user of the software; the author(s) is
// (are) also not responsible for proper configuration and usage of the
// software, even if provoked by documentation provided together with
// the software.
//
// Any changes to this file according to the GPL as documented in the file
// gpl.txt aside this file in the shipment you received can be done to the
// lines that follows this copyright notice here, but changes must not be
// done inside the copyright notive above. A re-distribution must contain
// the intact and unchanged copyright notice.
// Contributions and changes to the program code must be marked as such.

// You must compile this file with
// javac -classpath .:../classes crawlOrder.java


import java.io.IOException;

import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaURL;
import de.anomic.index.indexURLEntry;
import de.anomic.kelondro.kelondroBitfield;
import de.anomic.plasma.plasmaCrawlEURL;
import de.anomic.plasma.plasmaCrawlNURL;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.server.logging.serverLog;
import de.anomic.tools.crypt;
import de.anomic.yacy.yacyCore;
import de.anomic.yacy.yacySeed;

public final class crawlReceipt {

    
    /*
     * this is used to respond on a remote crawling request
     */

    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch env) {
        // return variable that accumulates replacements
        plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        serverLog log = switchboard.getLog();
        
        if ((post == null) || (env == null)) return prop;

        //int proxyPrefetchDepth = Integer.parseInt(env.getConfig("proxyPrefetchDepth", "0"));
        //int crawlingDepth = Integer.parseInt(env.getConfig("crawlingDepth", "0"));

        // request values
        String iam        = post.get("iam", "");      // seed hash of requester
        String youare     = post.get("youare", "");    // seed hash of the target peer, needed for network stability
        //String process    = post.get("process", "");  // process type
        String key        = post.get("key", "");      // transmission key
        String receivedUrlhash    = post.get("urlhash", "");  // the url hash that has been crawled
        String result     = post.get("result", "");   // the result; either "ok" or "fail"
        String reason     = post.get("reason", "");   // the reason for that result
        //String words      = post.get("wordh", "");    // priority word hashes
        String propStr    = crypt.simpleDecode(post.get("lurlEntry", ""), key);
        
        /*
         the result can have one of the following values:
         negative cases, no retry
           unavailable - the resource is not available (a broken link); not found or interrupted
           exception   - an exception occurred
           robot       - a robot-file has denied to crawl that resource

         negative cases, retry possible
           rejected    - the peer has rejected to load the resource
           dequeue     - peer too busy - rejected to crawl
         
         positive cases with crawling
           fill        - the resource was loaded and processed
           update      - the resource was already in database but re-loaded and processed
	 
         positive cases without crawling	 
           known       - the resource is already in database, believed to be fresh and not reloaded
           stale       - the resource was reloaded but not processed because source had no changes

        */
        
        final yacySeed otherPeer = yacyCore.seedDB.get(iam);
        final String otherPeerName = iam + ":" + ((otherPeer == null) ? "NULL" : (otherPeer.getName() + "/" + otherPeer.getVersion()));        
                
        
        if ((yacyCore.seedDB.mySeed == null) || (!(yacyCore.seedDB.mySeed.hash.equals(youare)))) {
            // no yacy connection / unknown peers
            prop.put("delay", "3600");
        } else if (propStr == null) {
            // error with url / wrong key
            prop.put("delay", "3600");
        } else if (result.equals("fill")) {
            // generating a new loaded URL entry
            indexURLEntry entry = switchboard.wordIndex.loadedURL.newEntry(propStr);
            if (entry == null) {
                log.logWarning("crawlReceipt: RECEIVED wrong RECEIPT (entry null) for hash " + receivedUrlhash + " from peer " + iam +
                              "\n\tURL properties: "+ propStr);
            } else {
                indexURLEntry.Components comp = entry.comp();
            if (comp.url() == null) {
                log.logWarning("crawlReceipt: RECEIVED wrong RECEIPT (url null) for hash " + receivedUrlhash + " from peer " + iam +
                              "\n\tURL properties: "+ propStr);
            } else try {
                // put new entry into database
                switchboard.wordIndex.loadedURL.store(entry);
                switchboard.wordIndex.loadedURL.stack(entry, youare, iam, 1);
                
                // generating url hash
                String newUrlHash = plasmaURL.urlHash(comp.url());
                String oldUrlHash = plasmaURL.oldurlHash(comp.url());
                
                // removing URL from notice URL                
                switchboard.noticeURL.remove(newUrlHash);
                switchboard.noticeURL.remove(oldUrlHash); 
                
                log.logInfo("crawlReceipt: RECEIVED RECEIPT from " + otherPeerName + " for URL " + receivedUrlhash + ":" + comp.url().toNormalform());
            } catch (IOException e) {
                e.printStackTrace();
            }
            }            
            // ready for more
            prop.put("delay", "10");
        } else {
            try {
                plasmaCrawlNURL.Entry en = switchboard.noticeURL.getEntry(receivedUrlhash);
                plasmaCrawlEURL.Entry ee = switchboard.errorURL.newEntry(en.url(), en.referrerHash(), en.initiator(), iam, en.name(), result + ":" + reason, new kelondroBitfield());
                ee.store();
                switchboard.errorURL.stackPushEntry(ee);
                switchboard.noticeURL.remove(receivedUrlhash);
            } catch (IOException e) {

            }
            prop.put("delay", "100"); // what shall we do with that???
        }
	
	// return rewrite properties
	return prop;
    }

}
