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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Handler;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.List;

public class DataStallDiagnostics implements Runnable{
    private volatile boolean dsDiagStop;
    private volatile boolean dsDiagRunning;

	public synchronized boolean startDiag(){
        if (!dsDiagRunning) {
            dsDiagRunning = true;
            Thread thread = new Thread(this);
            thread.start();
            return true;
        }
        return false;
    }

    public void stopDiag() {
        dsDiagStop= true;
    }

    @Override
    public void run() {
        String result = startDiagThread();

        // record diagnostics results
        recordDiag(result);

        dsDiagRunning = false;
    }

    public String startDiagThread() {
        dsDiagStop = false;
        
        // non-blocking network diagnostics operations
        diagLocalHost();
        diagDns();
        diagDnsWithICMP();

        // block until all network diagnostics are finished and collect results
        String collectDiag = collectDiag();
        if (collectDiag == null || collectDiag.isEmpty()) {
            return null;
        }
        return collectDiag;
    }
}