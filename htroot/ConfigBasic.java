// ConfigBasic_p.java 
// -----------------------
// part of YaCy
// (C) by Michael Peter Christen; mc@anomic.de
// first published on http://www.anomic.de
// Frankfurt, Germany, 2006
// Created 28.02.2006
//
// $LastChangedDate: 2005-09-13 00:20:37 +0200 (Di, 13 Sep 2005) $
// $LastChangedRevision: 715 $
// $LastChangedBy: borg-0300 $
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

// You must compile this file with
// javac -classpath .:../classes ConfigBasic_p.java
// if the shell's current path is HTROOT

import java.util.regex.Pattern;

import de.anomic.data.translator;
import de.anomic.http.httpHeader;
import de.anomic.http.httpd;
import de.anomic.http.httpdFileHandler;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.server.serverCore;
import de.anomic.server.serverDomains;
import de.anomic.server.serverInstantBusyThread;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacySeed;

public class ConfigBasic {
    
    private static final int NEXTSTEP_FINISHED  = 0;
    private static final int NEXTSTEP_PWD       = 1;
    private static final int NEXTSTEP_PEERNAME  = 2;
    private static final int NEXTSTEP_PEERPORT  = 3;
    private static final int NEXTSTEP_RECONNECT = 4;
    
    public static serverObjects respond(httpHeader header, serverObjects post, serverSwitch<?> env) {
        
        // return variable that accumulates replacements
        plasmaSwitchboard sb = (plasmaSwitchboard) env;
        serverObjects prop = new serverObjects();
        String langPath = env.getConfigPath("locale.work", "DATA/LOCALE/locales").getAbsolutePath();
        String lang = env.getConfig("locale.language", "default");
        
        int authentication = sb.adminAuthenticated(header);
        if (authentication < 2) {
            // must authenticate
            prop.put("AUTHENTICATE", "admin log-in"); 
            return prop;
        }
        
        // starting a peer ping
        
        //boolean doPeerPing = false;
        if ((sb.webIndex.seedDB.mySeed().isVirgin()) || (sb.webIndex.seedDB.mySeed().isJunior())) {
            serverInstantBusyThread.oneTimeJob(sb.yc, "peerPing", null, 0);
            //doPeerPing = true;
        }
        
        // language settings
        if ((post != null) && (!(post.get("language", "default").equals(lang)))) {
            translator.changeLang(env, langPath, post.get("language", "default") + ".lng");
        }
        
        // peer name settings
        String peerName = (post == null) ? env.getConfig("peerName","") : (String) post.get("peername", "");
        
        // port settings
        String port = env.getConfig("port", "8080"); //this allows a low port, but it will only get one, if the user edits the config himself.
		if (post != null && Integer.parseInt(post.get("port")) > 1023) {
			port = post.get("port", "8080");
		}

        // check if peer name already exists
        yacySeed oldSeed = sb.webIndex.seedDB.lookupByName(peerName);
        if ((oldSeed == null) && (!(env.getConfig("peerName", "").equals(peerName)))) {
            // the name is new
        	boolean nameOK = Pattern.compile("[A-Za-z0-9\\-_]{3,80}").matcher(peerName).matches();
            if (nameOK) env.setConfig("peerName", peerName);
        }
 
        // check port
        boolean reconnect = false;
        if (!env.getConfig("port", port).equals(port)) {
            // validate port
            serverCore theServerCore = (serverCore) env.getThread("10_httpd");
            env.setConfig("port", port);
            
            // redirect the browser to the new port
            reconnect = true;
            
            String host = null;
            if (header.containsKey(httpHeader.HOST)) {
                host = header.get(httpHeader.HOST);
                int idx = host.indexOf(":");
                if (idx != -1) host = host.substring(0,idx);
            } else {
                host = serverDomains.myPublicLocalIP().getHostAddress();
            }
            
            prop.put("reconnect", "1");
            prop.put("reconnect_host", host);
            prop.put("nextStep_host", host);
            prop.put("reconnect_port", port);
            prop.put("nextStep_port", port);
            prop.put("reconnect_sslSupport", theServerCore.withSSL() ? "1" : "0");
            prop.put("nextStep_sslSupport", theServerCore.withSSL() ? "1" : "0");
            
            // generate new shortcut (used for Windows)
            //yacyAccessible.setNewPortBat(Integer.parseInt(port));
            //yacyAccessible.setNewPortLink(Integer.parseInt(port));
            
            // force reconnection in 7 seconds
            theServerCore.reconnect(7000);
        } else {
            prop.put("reconnect", "0");
        }

        // set a use case
        String networkName = sb.getConfig("network.unit.name", "");
        if (post != null && post.containsKey("usecase")) {
            if (post.get("usecase", "").equals("freeworld")) {
                if (!networkName.equals("freeworld")) {
                    // switch to freeworld network
                    sb.switchNetwork("defaults/yacy.network.freeworld.unit");
                }
                // switch to p2p mode
                sb.setConfig(plasmaSwitchboard.INDEX_DIST_ALLOW, true);
                sb.setConfig(plasmaSwitchboard.INDEX_RECEIVE_ALLOW, true);
            }
            if (post.get("usecase", "").equals("portal")) {
                if (!networkName.equals("webportal")) {
                    // switch to webportal network
                    sb.switchNetwork("defaults/yacy.network.webportal.unit");
                }
                // switch to robinson mode
                sb.setConfig(plasmaSwitchboard.INDEX_DIST_ALLOW, false);
                sb.setConfig(plasmaSwitchboard.INDEX_RECEIVE_ALLOW, false);
            }
            if (post.get("usecase", "").equals("intranet")) {
                if (!networkName.equals("intranet")) {
                    // switch to intranet network
                    sb.switchNetwork("defaults/yacy.network.intranet.unit");
                }
                // switch to p2p mode: enable ad-hoc networks between intranet users
                sb.setConfig(plasmaSwitchboard.INDEX_DIST_ALLOW, true);
                sb.setConfig(plasmaSwitchboard.INDEX_RECEIVE_ALLOW, true);
            }
        }
        
        networkName = sb.getConfig("network.unit.name", "");
        if (networkName.equals("freeworld")) {
            prop.put("setUseCase", 1);
            prop.put("setUseCase_freeworldChecked", 1);
        } else if (networkName.equals("webportal")) {
            prop.put("setUseCase", 1);
            prop.put("setUseCase_portalChecked", 1);
        } else if (networkName.equals("intranet")) {
            prop.put("setUseCase", 1);
            prop.put("setUseCase_intranetChecked", 1);
        } else {
            prop.put("setUseCase", 0);
        }
        prop.put("setUseCase_port", port);
        
        // check if values are proper
        boolean properPassword = (sb.getConfig(httpd.ADMIN_ACCOUNT_B64MD5, "").length() > 0) || sb.getConfigBool("adminAccountForLocalhost", false);
        boolean properName = (env.getConfig("peerName","").length() >= 3) && (!(yacySeed.isDefaultPeerName(env.getConfig("peerName",""))));
        boolean properPort = (sb.webIndex.seedDB.mySeed().isSenior()) || (sb.webIndex.seedDB.mySeed().isPrincipal());
        
        if ((env.getConfig("defaultFiles", "").startsWith("ConfigBasic.html,"))) {
        	env.setConfig("defaultFiles", env.getConfig("defaultFiles", "").substring(17));
        	env.setConfig("browserPopUpPage", "Status.html");
            httpdFileHandler.initDefaultPath();
        }
        
        prop.put("statusName", properName ? "1" : "0");
        prop.put("statusPort", properPort ? "1" : "0");
        if (reconnect) {
            prop.put("nextStep", NEXTSTEP_RECONNECT);
        } else if (!properName) {
            prop.put("nextStep", NEXTSTEP_PEERNAME);
        } else if (!properPassword) {
            prop.put("nextStep", NEXTSTEP_PWD);
        } else if (!properPort) {
            prop.put("nextStep", NEXTSTEP_PEERPORT);
        } else {
            prop.put("nextStep", NEXTSTEP_FINISHED);
        }
        
        
        // set default values       
        prop.put("defaultName", env.getConfig("peerName", ""));
        prop.put("defaultPort", env.getConfig("port", "8080"));
        lang = env.getConfig("locale.language", "default"); // re-assign lang, may have changed
        if (lang.equals("default")) {
            prop.put("langDeutsch", "0");
            prop.put("langEnglish", "1");
        } else if (lang.equals("de")) {
            prop.put("langDeutsch", "1");
            prop.put("langEnglish", "0");
        } else {
            prop.put("langDeutsch", "0");
            prop.put("langEnglish", "0");
        }
        return prop;
    }
}
