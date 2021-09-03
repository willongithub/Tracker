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

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static android.graphics.Bitmap.createScaledBitmap;

// Main fragment of the app
// Tracking algorithm and other major procedures are in this class
public class PreviewFragment extends Fragment
        implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback {

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    public static final String EXTRA_PICTURE = "com.app.camera2tracker.PICTURE";
    public static final String EXTRA_COORDINATES = "com.app.camera2tracker.COORDINATES";
    public static final String EXTRA_TARGET = "com.app.camera2tracker.TARGET";

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "PreviewFragment";

    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    public long timeStamp = System.currentTimeMillis();
    public long timer = System.currentTimeMillis();

    // Incoming frame
    public Bitmap currentFrame;

    // Landscape mode flag
    private final boolean LANDSCAPE = true;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
            mTrackingThread = new HandlerThread("TrackingBackground");
            mTrackingThread.start();
            mTrackingHandler = new Handler(mTrackingThread.getLooper());
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {

            // Update preview window
            if (coordinates == null) {
                mDashboardView.editCanvas();
                mDashboardView.clearCanvas();
                mDashboardView.drawStat();
                mDashboardView.drawBatteryStat();
                mDashboardView.drawCenterMark();
                mDashboardView.updateCanvas();
            }

            // Calculate new box template
            if (coordinates != null & mFlag) {
                currentFrame = mTextureView.getBitmap();
                int width = (int) ((coordinates[2] - coordinates[0]) * currentFrame.getWidth());
                int height = (int) ((coordinates[3] - coordinates[1]) * currentFrame.getHeight());
                int scaled_width = (int) (width / (double) Scalar);
                int scaled_height = (int) (height / (double) Scalar);
                mTargetBox = createScaledBitmap(mTargetBox, width, height, true);
                int[] box_pixels = getScaledPixels(mTargetBox, 1 / (double) Scalar);
                float[][] box_hsv = getHSV(box_pixels);
                getTemplate(box_hsv, scaled_width, scaled_height);
                ((CameraActivity) getActivity()).backUp();
                mFlag = false;
                mPosition[0] = 0;
                mPosition[1] = 0;
                mDegLog[0] = 0;
                mDegLog[1] = 0;
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
            }

            // Draw manual control view
            if (((CameraActivity) getActivity()).getBlueStat()) {
                if (mDashboardView.mTouchFocus) {
                    currentFrame = mTextureView.getBitmap();
                    mPosition[0] = getAngle(mDashboardView.mTouchX - currentFrame.getWidth() / 2, VERTICAL) + mPosition[0];
                    mPosition[1] = getAngle(mDashboardView.mTouchY - currentFrame.getHeight() / 2, HORIZONTAL) + mPosition[1];
                    mPosition[0] = boundLimit(mPosition[0], VERTICAL);
                    mPosition[1] = boundLimit(mPosition[1], HORIZONTAL);
                    ((CameraActivity) getActivity()).sendCommand(mPosition, DeadZone, Damping);
//                    Log.e(TAG, "Result: Degree (V)" + mPosition[0] + " (H)" + mPosition[1]);
                    mDashboardView.mTouchFocus = false;
                }
            }

            // Lock Camera Exposure
            if (((CameraActivity) getActivity()).getBlueStat() & coordinates != null & !AE_Lock) {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
                AE_Lock = true;
            }

            
            /**
             *
             * Tracking will start when Bluetooth connection exist
             */
            // Frame processing block
            if (((CameraActivity) getActivity()).getBlueStat() & coordinates != null & mFrameComplete) {
//            if (coordinates != null & mFrameComplete) {
                if (System.currentTimeMillis() - timer >= process_period) {

                    // Fetch incoming frame
                    currentFrame = mTextureView.getBitmap();

                    // Lock processing state
                    mFrameComplete = false;

                    // get current system time
                    timer = System.currentTimeMillis();

                    // Send reposition command from last period
                    ((CameraActivity) getActivity()).sendCommand(mPosition, DeadZone, Damping);

                    Log.d(TAG, "Frame start!");

                    // Get frame pixel array
                    int x = (int) (currentFrame.getWidth() * (1 - Area) / 2);
                    int y = (int) (currentFrame.getHeight() * (1 - Area) / 2);
                    int w = (int) (currentFrame.getWidth() * Area);
                    int h = (int) (currentFrame.getHeight() * Area);
                    Bitmap mSearchArea = Bitmap.createBitmap(currentFrame, x, y, w, h);
                    int frame_width = (int) (mSearchArea.getWidth() / (float) Scalar);
                    int frame_height = (int) (mSearchArea.getHeight() / (float) Scalar);
                    mFrameWidth = frame_width;
                    mFrameHeight = frame_height;
                    mPixels = getScaledPixels(mSearchArea, 1 / (double) Scalar);
                    mHSV = new float[3][mPixels.length];
                    mPortionLength = mPixels.length/Step;

                    // Color space Converting thread start
                    startConverting();

                }
            }

            if (!((CameraActivity) getActivity()).getBlueStat() & coordinates != null & mFrameComplete) {
                showToast("No Bluetooth Connection!");
            }
        }
    };

    /**
     * Parameters for App Settings
     */

    // General parameters
//    int Scalar =  12;        // Scale down incoming frame to size of (1/scalar)
//    double Area = 1;        // Search area factor (0~1)
//    int Step = 10;          // Searching jump step (equals numbers of threads)
//    public long process_period = 100;   // Lower time(milliseconds) bound for each command sent

    // Motor control limit
    int DeadZone = 3;       // Deviation degree less than deadZone will be ignored
    int Bound = 30;         // Deviation degree larger than bound will reset the system
    double Damping = 1;   // Motion damping

    // Algorithm parameters
    int HueLevel = 32;
    int SaturationLevel = 16;
    double KernelHeight = 0.3;

    // Camera parameters
    double ViewAngleHorizontal = 40;
    double ViewAngleVertical = 22.5;
    double PreviewWidth = 1440;
    double PreviewHeight = 1920;
    int mAF = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
    int mAWB = CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT;

    int Scalar;
    double Area;
    int Step;
    int process_period;

    // Initialization
    boolean AE_Lock = false;
    double[] mPosition = new double[2];
    public int mFrameWidth, mFrameHeight;
    public float[][] mHSV;
    public int mQueue;
    public int mPieces;
    public boolean mFrameComplete = true;
    public boolean mInTracking = false;
    public int mLeft, mTop, mRight, mBottom;
    public int mThread;
    public int[] mPixels;
    public int mPortionLength;
    public int mMatch = Integer.MAX_VALUE;
    public boolean mFlag = true;
    public double[] mDegLog = new double[2];
    private final int VERTICAL = 0;
    private final int HORIZONTAL = 1;

    // Lock Exposure
    public void lockExposure() {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
        showToast("EXPOSURE LOCKED!");
    }

    // Trigger the convertor
    public void startConverting() {
        Log.d(TAG, "getHSV start!");
        for (mThread = 0 ; mThread < Step; mThread++) {
            mPieces -= 1;
            mConvertorTask = new HSVConvertingTask();
            mConvertorTask.executeOnExecutor(mExecutor, mThread);
//            mConvertorTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mThread);
//            mConvertorTask.execute(mThread);
        }
    }

    // Trigger the tracker
    public void startTracking() {
        Log.d(TAG, "getTarget start!");
        mInTracking = true;
        for (mThread = 0 ; mThread < Step; mThread++) {
            mQueue -= 1;
            mTrackerTask = new TrackingTask();
            mTrackerTask.executeOnExecutor(mExecutor, mThread);
//            mTrackerTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mThread);
//            mTrackerTask.execute(mThread);
        }
    }

    // Refresh the target box displayed
    public void updateTrackingBox() {
        Log.d(TAG, "Frame complete!");
        mInTracking = false;
        mDashboardView.setStat(String.format("%3dms", (int) (System.currentTimeMillis() - timeStamp)));
        mDashboardView.setTrackingBox(mLeft, mTop, mRight, mBottom);
        mDashboardView.editCanvas();
        mDashboardView.clearCanvas();
        mDashboardView.drawTrackingBox();
        mDashboardView.drawBatteryStat();
        mDashboardView.drawStat();
        mDashboardView.updateCanvas();

        double[] deg = getDeviation(mLeft, mTop, mRight, mBottom);

        // Jumping limit
        // System will be reset if the result box goes wild
        if (Math.abs(deg[0] - mDegLog[0]) > Bound | Math.abs(deg[1] - mDegLog[1]) > Bound) {
            reset();
            return;
        }

        mPosition[0] = deg[0] + mPosition[0];
        mPosition[1] = deg[1] + mPosition[1];
        mPosition[0] = boundLimit(mPosition[0], VERTICAL);
        mPosition[1] = boundLimit(mPosition[1], HORIZONTAL);
        mDegLog[0] = deg[0];
        mDegLog[1] = deg[1];
        mMatch = Integer.MAX_VALUE;
        mFrameComplete = true;
        timeStamp = System.currentTimeMillis();
    }

    // Initiate parallel processing module
    AsyncTask<Integer, Void, Boolean> mConvertorTask;
    AsyncTask<Integer, Void, String> mTrackerTask;

    private class HSVConvertingTask extends AsyncTask<Integer, Void, Boolean> {

        protected Boolean doInBackground(Integer... params) {
            try {
                int[] portion_pixel;
                int offset = params[0] * mPortionLength;
                portion_pixel = Arrays.copyOfRange(mPixels, offset, offset + mPortionLength);

//                Log.d(TAG, "Thread # " + params[0] + "getHSV start!");
                float[][] hsv = getHSV(portion_pixel);
//                Log.d(TAG, "Thread # " + params[0] + "getHSV complete!");

                System.arraycopy(hsv[0], 0, mHSV[0], offset, mPortionLength);
                System.arraycopy(hsv[1], 0, mHSV[1], offset, mPortionLength);
                System.arraycopy(hsv[2], 0, mHSV[2], offset, mPortionLength);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            mPieces += 1;

            if (mPieces == Step) {
                Log.d(TAG, "getHSV complete!");
                startTracking();
            }
        }
    }

    private class TrackingTask extends AsyncTask<Integer, Void, String> {

        protected String doInBackground(Integer... params) {
            String command = "";
            try {
                command = getTarget(mHSV, mFrameWidth, mFrameHeight, Scalar, params[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return command;
        }

//        @Override
//        protected void onProgressUpdate(Integer... progress) {
////                        setProgressPercent(progress[0]);
//        }

        @Override
        protected void onPostExecute(String result) {
            if (!result.equals("")) {
                String[] command;
                command = result.split(",");
                int match = Integer.parseInt(command[4]);
                if (match < mMatch) {
                    mMatch = match;
                    mLeft = Integer.valueOf(command[0]) + (int) (currentFrame.getWidth() * (1 - Area) / 2);
                    mTop = Integer.valueOf(command[1]) + (int) (currentFrame.getHeight() * (1 - Area) / 2);
                    mRight = Integer.parseInt(command[2]) + (int) (currentFrame.getWidth() * (1 - Area) / 2);
                    mBottom = Integer.parseInt(command[3]) + (int) (currentFrame.getHeight() * (1 - Area) / 2);
                }
                mQueue += 1;

                // When complete, update target box on screen
                if (mQueue == Step & !mFrameComplete & mInTracking) {
                    Log.d(TAG, "getTarget complete!");
                    updateTrackingBox();
                }
            }
        }
    }

    SynchronousQueue<Runnable> queue = new SynchronousQueue<Runnable>();
    ThreadPoolExecutor mExecutor = new ThreadPoolExecutor(Step, Integer.MAX_VALUE, 2, TimeUnit.MINUTES, queue);

    // Limit rotation angle for motor protection
    public double boundLimit(double deg, int dir) {
        switch (dir) {
            case VERTICAL:
                if (deg > 30) {
                    deg = 30;
                }
                if (deg < -10) {
                    deg = -10;
                }
                break;
            case HORIZONTAL:
                if (deg > 85) {
                    deg = 85;
                }
                if (deg < -85) {
                    deg = -85;
                }
                break;
        }
        return deg;
    }

    private static class GetHsvTask extends AsyncTask<String, Void, String> {

        private String mName = "AsyncTask";
        private int mParts = '0';
        private int[] mPixels;
        private float[][] mHsv;
        private long mTime;

        public GetHsvTask(String name) {
            super();
            mName = name;
        }

        public GetHsvTask(int parts, int[] pixels, long ini) {
            super();
            mParts = parts;
            mPixels = pixels;
            mTime = ini;
//            mHsv = new float[3][mPixels.length];
        }

        @Override
        protected String doInBackground(String... params) {

            float[][] mHsv = getHSV(mPixels);

            return mHsv[0].toString();
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            for (int i = 0; i < mPixels.length; i++){
//                fHue[i] = mHsv[0][i];
//                fSaturation[i] = tHSV[1][i];
//                fValue[i] = tHSV[2][i];
            }
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            Log.e(TAG, "#" + mParts + "execute finish at " + df.format(new Date()) + "after" + (int)(System.currentTimeMillis() - mTime) + "ms");
        }
    }

    // Calculate target's deviation from centre of frame
    public double[] getDeviation(int left, int top, int right, int bottom) {
        double[] deg = new double[2];
        int targetX = (right - left)/2 + left;
        int targetY = (bottom - top)/2 + top;
        int centerX = currentFrame.getWidth()/2;
        int centerY = currentFrame.getHeight()/2;
        double degX = getAngle(targetX - centerX, VERTICAL);
        double degY = getAngle(targetY - centerY, HORIZONTAL);

        deg[0] = degX;
        deg[1] = degY;
        return deg;
    }

    // Convert pixel deviation to angle deviation
    public double getAngle(double deviation, int dir) {
        double screenX = PreviewWidth;
        double screenY = PreviewHeight;
//        double ccdX = 7.09;
//        double ccdY = 4.68;
//        double focalLength = 4.30; // S6 Edge
//        double viewAngleHorizontal = 22.5;
//        double viewAngleVerticalUp = 12.2;
//        double viewAngleVerticalDown = 28.8;
        double angle = 0;
        switch (dir) {
            case VERTICAL:
                angle =  deviation / screenX * 2 * ViewAngleVertical / 2;
//                angle =  deviation / screenY * 2 * (viewAngleVerticalUp + viewAngleVerticalDown) * (1920/(double)2560) / 2;
//                angle = (int)((Math.atan(ccdX*deviation/screenX/focalLength) + Math.PI/2)/Math.PI*180);
//                if (deviation >= 0) {
//                    angle =  deviation / screenY / (30/(double)(76+30)) * viewAngleVerticalUp;
//
//                }
//                else
//                    angle = deviation / screenY / (76/(double)(76+30)) * viewAngleVerticalDown;
//                Log.e(TAG, "Horizontal Degree: " + angle);
                break;

            case HORIZONTAL:
                angle = deviation / screenY * 2 * ViewAngleHorizontal / 2;
//                angle = (int)((Math.atan(ccdY*deviation/screenY/focalLength) + Math.PI/2)/Math.PI*180);
//                angle = deviation / screenX * 2 * viewAngleHorizontal / 2;
                break;
        }

        return angle;
    }

    // Scale down the bitmap and convert into pixel array
    public int[] getScaledPixels(Bitmap frame, double scalar) {
        int dstWidth = (int)(frame.getWidth() * scalar);
        int dstHeight = (int)(frame.getHeight() * scalar);
        Bitmap scaledMap = createScaledBitmap(frame, dstWidth, dstHeight, true);
        int lengthMap = scaledMap.getByteCount()/4;
        int[] pixels = new int[lengthMap];
        scaledMap.getPixels(pixels, 0, dstWidth, 0, 0, dstWidth, dstHeight);
        return pixels;
    }

    public static float[][] getHSV(int[] pixels) {
//        float srcRatio = (float)frame.getHeight()/(float)frame.getWidth();
//        int dstHeight = (int)(dstWidth*srcRatio);
//        long ini = System.currentTimeMillis();
//        int dstWidth = (int)(frame.getWidth() * scalar);
//        int dstHeight = (int)(frame.getHeight() * scalar);
//        Bitmap scaledMap = createScaledBitmap(frame, dstWidth, dstHeight, false);
//        int lengthMap = scaledMap.getByteCount()/4;
//        int[] pixels = new int[lengthMap];
        int lengthMap = pixels.length;
//        hue = new float[lengthMap];
//        saturation = new float[lengthMap];
//        value = new float[lengthMap];
        float[] tempHsv = new float[3];
        float[][] hsv = new float[3][lengthMap];
//        scaledMap.getPixels(pixels, 0, dstWidth, 0, 0, dstWidth, dstHeight);
        for (int i = 0; i < lengthMap; i++) {
//            int pRed = Color.red(pixels[i]);
//            int pGreen = Color.green(pixels[i]);
//            int pBlue = Color.blue(pixels[i]);
//            Color.RGBToHSV(pRed, pGreen, pBlue, hsv);
            Color.colorToHSV(pixels[i], tempHsv);
//            int[] t = new int[3];
//            int[] t = toHSV(pixels[i]);
//            hue[i] = hsv[0];
//            saturation[i] = hsv[1];
//            value[i] = hsv[2];
            hsv[0][i] = tempHsv[0];
            hsv[1][i] = tempHsv[1];
            hsv[2][i] = tempHsv[2];
        }
//        for (int i = 0; i < lengthMap; i++) {
//            hue[i] += 100;
//        }
//        long elps = System.currentTimeMillis() - ini;
//        String hint = String.format("frame done after %d ms!", (int)elps);
//        showToast(hint);
        return hsv;
    }

    // Alternative HSV conversion method
    public static int[] toHSV(int color) {
        int H, S, V;
        H = 0;
        int R = (color >> 16) & 0xff;
        int G = (color >>  8) & 0xff;
        int B = (color      ) & 0xff;
        int max = Math.max(Math.max(R,G),B);
        int min = Math.min(Math.min(R,G),B);

        if (max == R) {
            H = (G - B) / (max - min);
        }
        if (max == G) {
            H = 2 + (B - R) / (max - min);
        }
        if (max == B) {
                H = 4 + (R - G)/(max - min);
        }
        H *= 60;
        if (H < 0) {
            H += 360;
        }
        S = (max - min)/max;
        V = max;

        int[] hsv = new int[3];
        hsv[0] = H;
        hsv[1] = S;
        hsv[2] = V;

        return hsv;
    }

    // overlay for drawing target box
    private DashboardView mDashboardView;
    private SurfaceHolder mBoxHolder;

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity) {
                activity.finish();
            }
        }

    };

    // Additional thread for tracking task
    private Handler mTrackingHandler;

    private HandlerThread mTrackingThread;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * This is the output file for our picture.
     */
    private File mFile;

    public Image mImage;

    public Bitmap mBitmap;

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {

            mImage = reader.acquireNextImage();

            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            mBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            mBitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);

//            Bitmap bm = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.toByteArray().length);

            bytes = out.toByteArray();

            Intent intent = new Intent(getActivity(), TargetSelectorActivity.class);
            intent.putExtra(EXTRA_PICTURE, bytes);

            closeCamera();

            startActivity(intent);

//            mBackgroundHandler.post(new ImageSaver(mImage, mFile));
        }


    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    private int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
            int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                    option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static PreviewFragment newInstance() {
        return new PreviewFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_preview, container, false);

        LinearLayout surface = (LinearLayout)view.findViewById(R.id.surface);
        mDashboardView = new DashboardView(this);
        mDashboardView.setZOrderOnTop(true);
        Resources resources = this.getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        mDashboardView.setScreenSize(dm.widthPixels, dm.heightPixels);
        surface.addView(mDashboardView);

        settings = ((CameraActivity) getActivity()).getSettings();

        mAnimationRight = AnimationUtils.loadAnimation(getActivity(), R.anim.rotate_right);
        mAnimationRight.setFillAfter(true);
        mAnimationLeft = AnimationUtils.loadAnimation(getActivity(), R.anim.rotate_left);
        mAnimationLeft.setFillAfter(true);

        return view;
    }

    public double[] coordinates = null;
    public Bitmap mTargetBox;
    SharedPreferences settings;
    private Animation mAnimationRight;
    private Animation mAnimationLeft;
    private Button mStart;
    private Button mReset;
    private Button mSetting;
    private OrientationEventListener mOrientationEventListener;

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(R.id.start).setOnClickListener(this);
        view.findViewById(R.id.info).setOnClickListener(this);
        view.findViewById(R.id.reset).setOnClickListener(this);
        view.findViewById(R.id.setting).setOnClickListener(this);

        mStart = (Button) view.findViewById(R.id.start);
        mReset = (Button) view.findViewById(R.id.reset);
        mSetting = (Button) view.findViewById(R.id.setting);

        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);

        Intent intent = getActivity().getIntent();
        coordinates = intent.getDoubleArrayExtra(EXTRA_COORDINATES);
        if (intent.getByteArrayExtra(EXTRA_TARGET) != null) {
            mTargetBox = BitmapFactory.decodeByteArray(intent.getByteArrayExtra(EXTRA_TARGET),
                    0, intent.getByteArrayExtra(EXTRA_TARGET).length);
        }

        Scalar = settings.getInt("scalar", 12);
        Area = settings.getInt("area", 100)/(double)100;
        Step = settings.getInt("step", 10);
        mPieces = Step;
        mQueue = Step;
        process_period = settings.getInt("period", 200);

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
//        mFile = new File(getActivity().getExternalFilesDir(null), "pic.jpg");
//        ((CameraActivity) getActivity()).backUp();
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void requestCameraPermission() {
        if (FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/2);
//                mImageReader = ImageReader.newInstance(240, 320,
//                        ImageFormat.JPEG, /*maxImages*/2);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    /**
     * Opens the camera specified by {@link PreviewFragment#mCameraId}.
     */
    private void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
            return;
        }

        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        mAF);
                                // Flash is automatically enabled when necessary.
                                setAutoFlash(mPreviewRequestBuilder);

                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE,
                                        mAWB);

//                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
//                                        -3);

//                                // Lock Camera Exposure
//                                if (((CameraActivity) getActivity()).getBlueStat() & coordinates != null & !AE_Lock) {
//                                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
//                                    AE_Lock = true;
//                                }
//                                else
//                                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
//                                            -3);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture() {
        lockFocus();
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
//                    showToast("Saved: " + mFile);
//                    Log.d(TAG, mFile.toString());
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.start: {
                takePicture();
                ((CameraActivity) getActivity()).disconnect();
                mFlag = true;
                break;
            }
            case R.id.info: {
                Activity activity = getActivity();
//                send(0);
                if (null != activity) {
                    new AlertDialog.Builder(activity)
                            .setMessage(R.string.intro_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
                break;
            }
            case R.id.reset: {
                reset();
                break;
            }
            case R.id.setting: {
//                showSettings();
                lockExposure();
                break;
            }
        }
    }

    public void showSettings() {
        Intent intent = new Intent(getActivity(), SettingsActivity.class);
        startActivity(intent);
    }

    public void reset() {
        ((CameraActivity) getActivity()).startUp();
        mPosition[0] = 0;
        mPosition[1] = 0;
        mDegLog[0] = 0;
        mDegLog[1] = 0;
        mFrameComplete = true;
        mInTracking = false;
        coordinates = null;
//        mLeft, mTop, mRight, mBottom;
        mMatch = Integer.MAX_VALUE;
        AE_Lock = false;
//        showToast("Reset!");
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    // Convert color space using multi thread
    private static class HsvCovertor implements Runnable {

//        private final Bitmap mBitmap;
        private final Bitmap frame;
        private int mOffset;
        private int mLength;
        private double scalar;
        private float[] hue, saturation, value;

        public HsvCovertor(Bitmap bitmap) {
//            mBitmap = bitmap;
            frame = bitmap;
//            mOffset = offset;
//            mLength = length;
            scalar = 1;
        }

        @Override
        public void run() {
            int dstWidth = (int)(frame.getWidth() * scalar);
            int dstHeight = (int)(frame.getHeight() * scalar);
            Bitmap scaledMap = createScaledBitmap(frame, dstWidth, dstHeight, false);
            int lengthMap = scaledMap.getByteCount()/4;
            int[] pixels = new int[lengthMap];
            hue = new float[lengthMap];
            saturation = new float[lengthMap];
            value = new float[lengthMap];
            float[] hsv = new float[3];
            scaledMap.getPixels(pixels, 0, dstWidth, 0, 0, dstWidth, dstHeight);
            for (int i = 0; i < lengthMap; i++) {
                Color.colorToHSV(pixels[i], hsv);
                hue[i] = hsv[0];
                saturation[i] = hsv[1];
                value[i] = hsv[2];
            }
        }
    }

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        public ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }


        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }


    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent,
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_CAMERA_PERMISSION);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = parent.getActivity();
                                    if (activity != null) {
                                        activity.finish();
                                    }
                                }
                            })
                    .create();
        }
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mStart.setAnimation(mAnimationRight);
            mReset.setAnimation(mAnimationRight);
            mSetting.setAnimation(mAnimationRight);
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            mStart.setAnimation(mAnimationLeft);
            mReset.setAnimation(mAnimationLeft);
            mSetting.setAnimation(mAnimationLeft);
        }
    }

    // Core Tracking Algorithm

    final int H_level_max = 360;
    final int S_level_max = 256; //Originally 0~1 scaled to 0`255 in the code

    final int H_level = HueLevel;
    final int S_level = SaturationLevel;

    int[][] hist_sel = new int[H_level][S_level];
    int[][] gaussianKernel_opt;

    int sel_height_scaled;
    int sel_width_scaled;

    // Calculate target template
    public void getTemplate(float[][] box, int boxWidth, int boxHeight) {
        double ker_h = KernelHeight;
        sel_height_scaled = boxHeight;
        sel_width_scaled = boxWidth;
        double[][] gaussianKernel = new double[sel_height_scaled + 1][sel_width_scaled + 1];
        gaussianKernel_opt = new int[sel_height_scaled + 1][sel_width_scaled + 1];
        int deltaHeight = sel_height_scaled / 2;
        int deltaWidth = sel_width_scaled / 2;
        double H2W_ratio = sel_height_scaled / (double) sel_width_scaled;
        int[][] mHue_sel_arr = new int[sel_height_scaled][sel_width_scaled];
        int[][] mSat_sel_arr = new int[sel_height_scaled][sel_width_scaled];

        //Gaussian Kernel generation - Verified
        double inversed_denominator = 1 / (2 * Math.pow(ker_h * deltaHeight, 2));
        for (int x = -deltaWidth; x <= deltaWidth; x++) {
            for (int y = -deltaHeight; y <= deltaHeight; y++) {
                gaussianKernel[y + deltaHeight][x + deltaWidth] = Math.exp(-(Math.pow(x * H2W_ratio, 2) + Math.pow(y, 2)) * inversed_denominator);
            }
        }

        //Gaussian Kernel optimization
        for (int x = -deltaWidth; x <= deltaWidth; x++) {
            for (int y = -deltaHeight; y <= deltaHeight; y++) {
                gaussianKernel_opt[y + deltaHeight][x + deltaWidth] = (int) (10000 * gaussianKernel[y + deltaHeight][x + deltaWidth]);
            }
        }

        //Copy data to array and calculate histogram
        for (int col = 0; col <= sel_width_scaled - 1; col++) {
            for (int row = 0; row <= sel_height_scaled - 1; row++) {
                int x = (int) (box[0][col + sel_width_scaled * row] * H_level / H_level_max);
                int y = (int) (box[1][col + sel_width_scaled * row] * 255 * S_level / S_level_max);
                mHue_sel_arr[row][col] = ((x >= 0) ? x : -x);
                mSat_sel_arr[row][col] = ((y >= 0) ? y : -y);
                hist_sel[mHue_sel_arr[row][col]][mSat_sel_arr[row][col]] += gaussianKernel_opt[row][col];
            }
        }
    }

    // Find target in current frame
    public String getTarget(float[][] hsv, int preview_width_scaled, int preview_height_scaled, int scaling_factor, int thread) {

//        Log.d(TAG,  "Thread #" + thread + " getTarget Start!");

        int row_start_match = 0;
        int col_start_match = 0;
        int row_end_match;
        int col_end_match;

        int row_end_max = preview_height_scaled - 1;
        int col_end_max = preview_width_scaled - 1;

        int row_diff = sel_height_scaled;
        int col_diff = sel_width_scaled;
        int row_start = thread;
        int col_start;

        int col_total;

        int hist_diff_min = Integer.MAX_VALUE;

        col_total = preview_width_scaled;

        for(col_start = thread; (col_start + col_diff - 1) <= col_end_max; col_start += Step){
            int[][] mHue_arr = new int[sel_height_scaled][sel_width_scaled];
            int[][] mSat_arr = new int[sel_height_scaled][sel_width_scaled];
            int[][] hist = new int[H_level][S_level];
            int hist_diff;

//            Arrays.fill(hist, 0);

            for(int i=0;i<=H_level-1;i++){
                for(int j=0;j<=S_level-1;j++) {
                    hist[i][j] = 0;
                }
            }

            int row = 0;
            for(int arr_row = row_start; arr_row <= row_start + row_diff - 1; arr_row++) {
                int col = 0;
                for(int arr_col = arr_row * col_total + (col_start); arr_col <= arr_row * col_total + ((col_start - Step) + col_diff - 1); arr_col += 1){
                    mHue_arr[row][col] = Math.abs((int)(hsv[0][arr_col] * H_level / H_level_max));
                    mSat_arr[row][col] = Math.abs((int)(hsv[1][arr_col] * 255 * S_level / S_level_max));
                    hist[mHue_arr[row][col]][mSat_arr[row][col]] += gaussianKernel_opt[row][col];
                    col++;
                }
                row++;
            }

            hist_diff = 0;

            for(int i=0;i<=H_level-1;i++){
                for(int j=0;j<=S_level-1;j++) {
                    int x = hist[i][j] - hist_sel[i][j];
                    hist_diff += (x >= 0) ? x : -x;
                    hist[i][j] = 0;
                }
            }

            if(hist_diff<hist_diff_min){
                row_start_match = row_start;
                col_start_match = col_start;
                hist_diff_min = hist_diff;
            }

            if(((col_start + col_diff - 1 + Step) > col_end_max) && ((row_start + row_diff - 1 + Step) <= row_end_max)){
                col_start = thread;
                row_start += Step;
            }
        }

        row_start_match *= scaling_factor;
        col_start_match *= scaling_factor;
        row_end_match = row_start_match + (sel_height_scaled * scaling_factor);
        col_end_match = col_start_match + (sel_width_scaled * scaling_factor);

        String target = (col_start_match + "," + row_start_match + "," + col_end_match + "," + row_end_match + "," + hist_diff_min);

//        Log.d(TAG,  "Thread #" + thread + " getTarget Complete!");

        return target;
    }

}
