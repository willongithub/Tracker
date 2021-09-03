/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.app.android.camera2tracker;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.widget.Toast;

import com.app.android.common.activities.SampleActivityBase;

import static com.app.android.camera2tracker.PreviewFragment.EXTRA_COORDINATES;
import static com.app.android.camera2tracker.PreviewFragment.EXTRA_TARGET;

// Main activity
public class CameraActivity extends SampleActivityBase {

    // Bounding coordinates of the target box
    private int[] coordinates = null;

    // Pixels array of target box
    private int[] mTargetBox;

    private final int VERTICAL = 0;
    private final int HORIZONTAL = 1;

    private BluetoothFragment mFragment;
    private SharedPreferences mSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        if (null == savedInstanceState) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            mFragment = new BluetoothFragment();
            transaction.replace(R.id.debugger, mFragment);
            transaction.commit();

            getFragmentManager().beginTransaction()
                    .replace(R.id.container, PreviewFragment.newInstance())
                    .commit();
        }

        // Retrieve target box information
        Intent intent = getIntent();
        coordinates = intent.getIntArrayExtra(EXTRA_COORDINATES);
        mTargetBox = intent.getIntArrayExtra(EXTRA_TARGET);

        mSettings = getSharedPreferences("pref_general", 0);
    }

    public SharedPreferences getSettings() {
        return mSettings;
    }

    // get current bluetooth connection stat
    public boolean getBlueStat() {
        return mFragment.mStat;
    }

    public void disconnect() {
        mFragment.cut();
    }

    // Connect board if not connected
    public void startUp() {
        if (getBlueStat()) {
//            mFragment.connectBoard();
            reset();
//        getVolt();
        }
        else
            mFragment.connectBoard();
//            while (!getBlueStat()) {
//                Log.e(TAG, "Connecting...");
//            }
//            reset();
    }

    // Reconnect the board
    public void backUp() {
        mFragment.connectBoard();
    }

    // Reset the camera to initial position
    public void reset() {
//        SystemClock.sleep(1500);
        move(0, VERTICAL, 0);
        move(0, HORIZONTAL, 0);
        Toast.makeText(this, "Reset!", Toast.LENGTH_SHORT).show();
    }

    // Read battery stat from control board
    public double getVolt() {
        double volt;
        mFragment.sendMessage("v");
        Toast.makeText(this, mFragment.mMessage, Toast.LENGTH_SHORT).show();
        volt = Double.valueOf(mFragment.mMessage);
        return volt;
    }

    public void sendCommand(double[] deg, int deadZone, double damping) {

        if (deg[0] > deadZone | deg[0] < -deadZone) {
            // Vertical
            move(deg[0], VERTICAL, damping);
            Log.e(TAG, "Sent Vertical Degree: " + deg[0]);
        }

        if (deg[1] > deadZone | deg[1] < -deadZone) {
            // Horizontal
            move(deg[1], HORIZONTAL, damping);
            Log.e(TAG, "Sent Horizontal Degree: " + deg[1]);
        }
    }

    // Send commands for two axis
    public void move(double deg, int dir, double damping) {
        switch (dir) {
            case HORIZONTAL:
                mFragment.sendMessage("R");
                mFragment.sendCmd(degToCmd(deg*damping, HORIZONTAL));
                break;

            case VERTICAL:
                mFragment.sendMessage("T");
                mFragment.sendCmd(degToCmd(deg*damping, VERTICAL));
                break;
        }
    }

    // Convert degree deviations to motor commands
    public int degToCmd(double deg, int dir) {
        int cmd = 0;
        switch (dir) {
            case HORIZONTAL:
                cmd = (int) ((deg + 90)/180 * 1024);
//                Log.e(TAG, "Sent Horizontal Command: " + cmd);
                break;

            case VERTICAL:
                cmd = (int) ((deg + 12.5)/45 * (18-5)*256 + 5*256);
//                Log.e(TAG, "Sent Vertical Command: " + cmd);
                break;
        }
        return cmd;
    }

}
