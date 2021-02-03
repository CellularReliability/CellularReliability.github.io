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

package com.android.phone.statistics;

import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import android.telephony.CellLocation;
import android.telephony.SignalStrength;
import android.telephony.ServiceState;
import android.telephony.data.ApnSetting;
import com.android.internal.telephony.dataconnection.ApnContext;

public class CellularStateProcessor {
    
    public interface ICallBack {
        public void onDataStall(int phoneId, Map<String, String> params);
        public void onDataSetupError(int phoneId, Map<String, String> params);
        public void onServiceStateChanged(int phoneId, ServiceState ss);
    }

    public static void notifyDataStall(int phoneId, Map<String, String> params) {
        synchronized (CellularStateProcessor.class) {
            for(ICallBack callback : sCallBack) {
                callback.onDataStall(phoneId, params);
            }
        }
    }

    public static void notifyDataSetupEvent(ApnContext apnContext, String cause, int phoneId){
    	HashMap<String, String> params = new HashMap<String, String>();
        ApnSetting apn = apnContext.getApnSetting();
        params.put('APN_NAME', apn != null ? apn.getApnName(): "unknown");
        params.put('APN_TYPE', apnContext.getApnType());
        params.put('REASON_CODE', apnContext.getReason());
        params.put('ERROR_CODE', cause);
        notifySetupError(phoneId, params);
    }

    public static void notifySetupError(int phoneId, Map<String, String> params) {
        synchronized (CellularStateProcessor.class) {
            for(ICallBack callback : sCallBack) {
                callback.onDataSetupError(phoneId, params);
            }
        }
    }

    public static void notifyServiceState(int phoneId, ServiceState ss) {
        synchronized (CellularStateProcessor.class) {
            for(ICallBack callback : sCallBack) {
                callback.onServiceStateChanged(phoneId, ss);
            }
        }
    }
}