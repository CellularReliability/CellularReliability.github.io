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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.telephony.CellLocation;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.DisconnectCause;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.telephony.cdma.CdmaCellLocation;

import com.android.phone.statistics.DataStallDiagnostics;
import com.android.phone.statistics.CellularStateProcessor;

import java.util.HashMap;
import java.util.Map;

public class CellularReliability {
    private CellularReliabilityHandler crHandler;
    private DataStallDiagnostics dsDiag;
    private static final int EVENT_DATA_STALL = 1;
    private static final int EVENT_SETUP_ERROR = 2;
    private static final int EVENT_SERVICE_STATE_CHANGED = 3;
    private static final int EVENT_PROBE = 4;
    private static final int MSG_START_PROBE = 5;
    private static final int MSG_STOP_PROBE = 6;
    private static final int DEFAULT_PROBE_TIMEOUT = 5*1000;
    private int probeTimeout = DEFAULT_PROBE_TIMEOUT;

    protected class CellularReliabilityHandler extends Handler {
        public CellularReliabilityHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_DATA_STALL:
                    processDataStallEvent(msg.arg1);
                    break;
                case EVENT_SETUP_ERROR:
                    processSetupErrorEvent(msg.arg1);
                    break;
                case EVENT_SERVICE_STATE_CHANGED:
                    processOutOfServiceEvent(msg.arg1, msg.arg2);
                    break;
                case MSG_START_PROBE:
                    dsDiag = new DataStallDiagnostics();
                    dsDiag.startDiag();
                    break;
                case MSG_STOP_PROBE:
                    if (dsDiag != null){
                        dsDiag.stopDiag();
                        dsDiag = null;
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private void processDataStallEvent(int phoneId) {
        // get radio information by phoneId (including signal level, RAT, cell info etc.)
        HashMap<String, String> data = getRadioInfo(phoneId);
        if (data == null) return;

        // record Data_Stall information
        recordEvent('DATA_STALL', data);

        startProbing();
    }

    private void processSetupErrorEvent(int phoneId) {
        // get radio information by phoneId (including duration, signal level, RAT, cell info etc.)
        HashMap<String, String> data = getRadioInfo(phoneId);

        data.put('APN_NAME', param.get('APN_NAME'));
        data.put('APN_TYPE', param.get('APN_TYPE'));
        data.put('REASON_CODE', param.get('REASON_CODE'));
        data.put('ERROR_CODE', param.get('ERROR_CODE'));

        // record SetupError information
        recordEvent('SETUP_ERROR', data);
    }

    private void processOutOfServiceEvent(int phoneId, ServiceState ss){
        if (ss == ServiceState.STATE_OUT_OF_SERVICE && !isNormal(phoneId)) {
            // get radio information by phoneId (including duration, signal level, RAT, cell info etc.)
            HashMap<String, String> data = getRadioInfo(phoneId);

            // record OutOfService
            recordEvent('OUT_OF_SERVICE', data);
        }
    }

    private boolean isNormal(int phoneId) {
        // check airplane mode and sim status
        return !isAirPlaneMode(phoneId) || !isSimReady(phoneId);
    }

    // start sending probe packets
    private void startProbing() {
        if (!mIsDcBlocked) return;
        // timeout increases when Data_Stall duration reaches 1200s.
        probeTimeout = getCurrentDuration() > 1200 * 1000 ? probeTimeout*2:DEFAULT_PROBE_TIMEOUT;
        crHandler.removeMessages(MSG_START_PROBE);
        crHandler.sendMessageDelayed(
            crHandler.obtainMessage(MSG_START_PROBE), probeTimeout);
    }

    // stop sending probe packets and reset timeout, used when Data_Stall no longer exists.
    private void stopProbing() {
        crHandler.removeMessages(MSG_START_PROBE);
        crHandler.sendMessages(MSG_STOP_PROBE);
        probeTimeout = DEFAULT_PROBE_TIMEOUT;
    }

    class CellularReliabilityCallBack implements CellularStateProcessor.ICallBack{
        @Override
        public void onDataStall(int phoneId, Map<String, String> params) {
            crHandler.obtainMessage(EVENT_DATA_STALL, phoneId, params).sendToTarget();
        }

        @Override
        public void onDataSetupError(int phoneId, Map<String, String> params) {
            crHandler.obtainMessage(EVENT_SETUP_ERROR, phoneId, params).sendToTarget();
        }

        @Override
        public void onServiceStateChanged(int phoneId, ServiceState ss) {
            crHandler.obtainMessage(EVENT_SERVICE_STATE_CHANGED, phoneId, ss).sendToTarget();
        }
    }
}


