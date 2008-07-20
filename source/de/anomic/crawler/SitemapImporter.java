//SitemapImporter.java 
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@anomic.de
//first published on http://www.anomic.de
//Frankfurt, Germany, 2007
//
//this file was contributed by Martin Thelian
//last major change: $LastChangedDate$ by $LastChangedBy$
//Revision: $LastChangedRevision$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package de.anomic.crawler;

import de.anomic.data.SitemapParser;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.yacy.yacyURL;

public class SitemapImporter extends AbstractImporter implements Importer {

	private SitemapParser parser = null;
	private yacyURL sitemapURL = null;
	private ImporterManager superviser;
	
	public SitemapImporter(plasmaSwitchboard sb, ImporterManager importManager, yacyURL sitemapURL, CrawlProfile.entry profileEntry) throws ImporterException {
		super("sitemap");
		this.superviser = importManager;
        try {
            // getting the sitemap URL
            this.sitemapURL = sitemapURL;
            
            // creating the sitemap parser
            this.parser = new SitemapParser(sb, this.sitemapURL, profileEntry);
        } catch (Exception e) {
            throw new ImporterException("Unable to initialize Importer",e);
        }
    }
    
    
    
	public long getEstimatedTime() {
		long t = getElapsedTime();
		int p = getProcessingStatusPercent();
		return (p==0)?0:(t/p)*(100-p);
	}

	/**
	 * @see Importer#getJobName()
	 */
	public String getJobName() {
		return this.sitemapURL.toString();
	}

	/**
	 * @see Importer#getProcessingStatusPercent()
	 */
	public int getProcessingStatusPercent() {
		if (this.parser == null) return 0;
		
		long total = this.parser.getTotalLength();
		long processed = this.parser.getProcessedLength();
		
		if (total <= 1) return 0;		
		return (int) ((processed*100)/ total);
	}

	/**
	 * @see Importer#getStatus()
	 */
	public String getStatus() {
        StringBuffer theStatus = new StringBuffer();
        
        theStatus.append("#URLs=").append((this.parser==null)?0:this.parser.getUrlcount());
        
        return theStatus.toString();
	}
	
	public void run() {
		try {
			this.parser.parse();
		} finally {
			this.globalEnd = System.currentTimeMillis();
			this.superviser.finishedJobs.add(this);			
		}
	}
}
