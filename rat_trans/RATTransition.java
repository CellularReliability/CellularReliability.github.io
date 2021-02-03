/*
 * Copyright (C) 2020 The Cellular Reliability Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.phone;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

import org.json.JSONObject;

public class RATTransition implements Runnable {
    public static final int INVALID_RSRP= -140;
    public static final int MAX_RSRP= -44;
    public static final int INVALID_SIGNAL_LEVEL = -1;
    private volatile boolean SCTransitionRunning = false;

    // control variable. if true, call startSCTransition().
    private volatile boolean SCTransitionOpen;

    private int phoneId;
    public RATTransition(int phoneId) {
        this.phoneId = phoneId;
    }

    public int get4GSignalLevel() {
        int rsrp = INVALID_RSRP;
        Phone phone = PhoneFactory.getPhone(this.phoneId);
        SignalStrength signalStrength = phone.getSignalStrength();
        if (signalStrength != null)
            rsrp = signalStrength.getLteRsrp();
        if (rsrp < INVALID_RSRP || rsrp > MAX_RSRP) {
            rsrp = INVALID_RSRP;
        }
        if (rsrp == INVALID_RSRP)
            return INVALID_SIGNAL_LEVEL;
        // get current signal level
        return getSignalLevelByLteRsrp(rsrp);
    }

    public int get5GSignalLevel() {
    	Phone phone = PhoneFactory.getPhone(this.phoneId);
    	// get 5G RSRP
        int rsrp = phone.get5GSignalStrength();
        if (rsrp < INVALID_RSRP || rsrp > MAX_RSRP) {
            rsrp = INVALID_RSRP;
        }
        if (rsrp == INVALID_RSRP)
            return INVALID_SIGNAL_LEVEL;
        // get current signal level
        return getSignalLevelByNrRsrp(rsrp);
    }

    public String getNetworkType() {
        String netClassStr = "UNKNOWN";

        Phone phone = PhoneFactory.getPhone(this.phoneId);
        
        // get 5G configurations
        String config5G = phone.get5GInfo(this.phoneId).getConfigType();

        if ((config5G == "NSA") || (config5G == "SA")){
            netClassStr = "5G";
        } else {
            if (phone != null) {
                int lteNetClass = android.telephony.TelephonyManager.getNetworkClass(
                    phone.getServiceState().getDataNetworkType());
                netClassStr = getClassName(lteNetClass);
            }
        }
        return netClassStr;
    }

    public static String getClassName(int networkClass) {
        String nwClassName = "UNKNOWN";
        switch (networkClass) {
            case android.telephony.TelephonyManager.NETWORK_CLASS_2_G:
                nwClassName = "2G";
                break;
            case android.telephony.TelephonyManager.NETWORK_CLASS_3_G:
                nwClassName = "3G";
                break;
            case android.telephony.TelephonyManager.NETWORK_CLASS_4_G:
                nwClassName = "4G";
                break;
            case 4:
                nwClassName = "5G";
                break;
            default:
                nwClassName = "UNKNOWN";
        }
        return nwClassName;
    }

    public synchronized boolean startSCTransition() {
    	if (!SCTransitionRunning){
            SCTransitionRunning = true;
            Thread thread = new Thread(this);
            thread.start();
            return true;
        }
        return false;
    }

    public synchronized void endSCTransition() {
        SCTransitionOpen = false;
    }

    @Override
    public void run() {
        if (!SCTransitionOpen){
            SCTransitionRunning = false;
        }
        else {
            if (getNetworkType() == "4G"){
                if (get5GSignalLevel() != INVALID_SIGNAL_LEVEL && !(get5GSignalLevel() == 0 && 
                       (get4GSignalLevel() == 1 || get4GSignalLevel() == 2 || 
                        get4GSignalLevel() == 3 || get4GSignalLevel() == 4))){
            	    // switch to 5G
            	    switchToNr(this.phoneId);
                }
            }
            else if (getNetworkType() == "5G") {
                if (get4GSignalLevel() != INVALID_SIGNAL_LEVEL && (get5GSignalLevel() == 0 && 
            	       (get4GSignalLevel() == 1 || get4GSignalLevel() == 2 || 
                        get4GSignalLevel() == 3 || get4GSignalLevel() == 4))){
                    // switch to 4G
                    switchToLte(this.phoneId);
                }
            }
        }
    }
}
