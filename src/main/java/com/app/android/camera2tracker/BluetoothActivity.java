package com.app.android.camera2tracker;

import android.support.v4.app.FragmentTransaction;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.app.android.common.activities.SampleActivityBase;

// This is a primary testing interface for bluetooth connection
// Not function in working app
public class BluetoothActivity extends SampleActivityBase {

    public static final String TAG = "BluetoothActivity";

    // Whether the Log Fragment is currently shown
//    private boolean mLogShown;

    private BluetoothFragment mFragment;

    // Motor control testing command
    private final String RESET = "0";
    private final String MOVE_RIGHT = "1";
    private final String MOVE_LEFT = "2";
    private final String MOVE_UP = "3";
    private final String MOVE_DOWN = "4";
    private final String MOVE_CLEAR = "5";

    private final int UP = 0;
    private final int DOWN = 1;
    private final int LEFT = 2;
    private final int RIGHT = 3;
    private final int CLEAR = 4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);

        if (savedInstanceState == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
//            BluetoothFragment fragment = new BluetoothFragment();
            mFragment = new BluetoothFragment();
//            transaction.replace(R.id.sample_content_fragment, fragment);
            transaction.replace(R.id.sample_content_fragment, mFragment);
            transaction.commit();
        }
    }

    public void moveCenter(View view) {
        Toast.makeText(this, "Back to Center!", Toast.LENGTH_SHORT).show();
        // bluetooth command move back to center position
//        mFragment.sendMessage(RESET);
//        mFragment.sendCmd(350);
//        mFragment.sendMessage("R");
//        int cmd = degToCmd(15);
//        mFragment.sendCmd(cmd);
        reset();
    }

    public void moveRight(View view) {
        Toast.makeText(this, "Move Right!", Toast.LENGTH_SHORT).show();
        // bluetooth command move right by one unit length
        mFragment.sendMessage(MOVE_RIGHT);
    }

    public void moveLeft(View view) {
        Toast.makeText(this, "Move Left!", Toast.LENGTH_SHORT).show();
        // bluetooth command move left by one unit length
        mFragment.sendMessage(MOVE_LEFT);
    }

    public void moveUp(View view) {
        Toast.makeText(this, "Move Up!", Toast.LENGTH_SHORT).show();
        // bluetooth command move up by one unit length
        mFragment.sendMessage(MOVE_UP);
    }

    public void moveDown(View view) {
        Toast.makeText(this, "Move Down!", Toast.LENGTH_SHORT).show();
        // bluetooth command move down by one unit length
        mFragment.sendMessage(MOVE_DOWN);
    }

    public void rotateAngle(int direction, int angle) {
        String command;
        switch (direction) {
            case UP:
                command = String.format("a%d" + (angle+90)/180*1024);
                break;
            case DOWN:
                command = String.format("a%d" + (angle+90)/180*1024);
                break;
            case LEFT:
                command = String.format("" + angle);
                break;
            case RIGHT:
                command = String.format("" + angle);
                break;
            default:
                command = String.format("");
        }
        mFragment.sendMessage(command);
    }

    public void reset() {
        rotateAngle(UP, 15);
        rotateAngle(DOWN, 15);
        rotateAngle(LEFT, 15);
        rotateAngle(RIGHT, 15);
        rotateAngle(CLEAR, 0);
    }

    public String getBatteryStat() {
        mFragment.sendMessage("");
        String volt = mFragment.mMessage;
        return volt;
    }

    public int degToCmd(int deg) {
        int cmd;
        if (deg >= 0) {
            cmd = (int)((double)deg/90*512 + 512);
        }
        else {
            cmd = (int)((double)-deg/90*512);
        }
        return cmd;
    }



//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        getMenuInflater().inflate(R.menu.main, menu);
//        return true;
//    }
//
//    @Override
//    public boolean onPrepareOptionsMenu(Menu menu) {
//        MenuItem logToggle = menu.findItem(R.id.menu_toggle_log);
//        logToggle.setVisible(findViewById(R.id.sample_output) instanceof ViewAnimator);
//        logToggle.setTitle(mLogShown ? R.string.sample_hide_log : R.string.sample_show_log);
//
//        return super.onPrepareOptionsMenu(menu);
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        switch(item.getItemId()) {
//            case R.id.menu_toggle_log:
//                mLogShown = !mLogShown;
//                ViewAnimator output = (ViewAnimator) findViewById(R.id.sample_output);
//                if (mLogShown) {
//                    output.setDisplayedChild(1);
//                } else {
//                    output.setDisplayedChild(0);
//                }
//                supportInvalidateOptionsMenu();
//                return true;
//        }
//        return super.onOptionsItemSelected(item);
//    }
//
//    /** Create a chain of targets that will receive log data */
//    @Override
//    public void initializeLogging() {
//        // Wraps Android's native log framework.
//        LogWrapper logWrapper = new LogWrapper();
//        // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
//        Log.setLogNode(logWrapper);
//
//        // Filter strips out everything except the message text.
//        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
//        logWrapper.setNext(msgFilter);
//
//        // On screen logging via a fragment with a TextView.
//        LogFragment logFragment = (LogFragment) getSupportFragmentManager()
//                .findFragmentById(R.id.log_fragment);
//        msgFilter.setNext(logFragment.getLogView());
//
//        Log.i(TAG, "Ready");
//    }
}
