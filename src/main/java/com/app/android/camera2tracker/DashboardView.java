package com.app.android.camera2tracker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

// Display calculation result on preview session
public class DashboardView extends SurfaceView {

    private Paint mPaint;
    private SurfaceHolder mHolder;
    private Context context;
    private Canvas mCanvas;
    private final int STROKEWIDTH;
    private int mTopX, mTopY, mBottomX, mBottomY;
    private int mTotalWidth, mTotalHeight;
    private Rect mTrackingBox, mBatteryBar;
    private double mBattery = 3;
    public boolean mTouchFocus = false;
    public int mTouchX, mTouchY;
    private String mStat = "Standing By...";

    public DashboardView(PreviewFragment context) {
        super(context.getActivity().getBaseContext());
        STROKEWIDTH = 5;
        mTotalWidth = getMeasuredWidth();
        mTotalHeight = getMeasuredHeight();
        mTopX = 0;
        mTopY = 0;
        mBottomX = mTotalHeight;
        mBottomY = mTotalWidth;
        mTrackingBox = new Rect(mTopX, mTopY, mBottomX, mBottomY);
        mBatteryBar = new Rect(1300, 50, 1400, 100);
        mHolder = getHolder();
        mHolder.setFormat(PixelFormat.TRANSPARENT);
        this.context = context.getActivity().getBaseContext();
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(Color.YELLOW);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(STROKEWIDTH);
        mPaint.setTextSize(70);
    }

//    public BoxView(Context context, AttributeSet attrs) {
//        super(context, attrs);
//    }

    public void setScreenSize(int width, int height) {
        mTotalHeight = height;
        mTotalWidth = width;
    }

    public void setStat(String stat) {
        mStat = stat;
    }

    public void drawStat() {

        if (mCanvas != null) {
//            Log.d("track", "drawing system stat");
//            mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
//                canvas.drawColor(Color.TRANSPARENT);
            mPaint.setStyle(Paint.Style.FILL);

            mCanvas.drawText(mStat, 50, 100, mPaint);
            mPaint.setStyle(Paint.Style.STROKE);
        }
    }

    public void setBatteryStat(double volt) {
        mBattery = volt;
    }

    // Display battery stat of the board
    public void drawBatteryStat() {
        double voltage = mBattery;
        double percentage = voltage/3.8;
        Rect mBatteryStat = new Rect(1300, 50, (int)(percentage*100 + 1300), 100);
        String mHints = String.format("%d%%", (int)(percentage*100));

        if (mCanvas != null) {
//            Log.d("track", "drawing battery stat");
            mCanvas.drawRect(mBatteryBar, mPaint);
            mPaint.setStyle(Paint.Style.FILL);
            mCanvas.drawRect(mBatteryStat, mPaint);
            mCanvas.drawText(mHints, 1150, 100, mPaint);
            mPaint.setStyle(Paint.Style.STROKE);
        }
    }

    public void drawCenterMark() {

        if (mCanvas != null) {
//            Log.d("track", "drawing center mark");
            mCanvas.drawCircle(mTotalWidth/2, mTotalHeight/2, 100, mPaint);
        }
    }

    public void setTrackingBox(int left, int top, int right, int bottom) {
        mTopX = left;
        mTopY = top;
        mBottomX = right;
        mBottomY = bottom;
        mTrackingBox.left = left;
        mTrackingBox.top = top;
        mTrackingBox.right = right;
        mTrackingBox.bottom = bottom;
    }

    public void drawTrackingBox() {

        if (mCanvas != null) {
//            Log.d("track", "drawing bounding box");
            mCanvas.drawRect(mTrackingBox, mPaint);
        }
    }

    public void editCanvas() {
        if (mHolder.getSurface().isValid()) {
            mCanvas = mHolder.lockCanvas();
        }
    }

    public void clearCanvas() {
        mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }

    public void updateCanvas() {
        if (mHolder.getSurface().isValid()) {
            mHolder.unlockCanvasAndPost(mCanvas);
        }
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                editCanvas();
                clearCanvas();
//                setStat("Manual Mode!");
                mCanvas.drawCircle(event.getX(), event.getY(), 150, mPaint);
                mTouchFocus = true;
                mTouchX = (int) event.getX();
                mTouchY = (int) event.getY();
                drawBatteryStat();
                drawStat();
                drawCenterMark();
                updateCanvas();
                break;

            case MotionEvent.ACTION_MOVE:
                editCanvas();
                clearCanvas();
                mCanvas.drawCircle(event.getX(), event.getY(), 100, mPaint);
                drawBatteryStat();
                drawStat();
                drawCenterMark();
                updateCanvas();
                break;

            case MotionEvent.ACTION_UP:
//                editCanvas();
//                clearCanvas();
//    //            setStat("Standing By...");
//                drawBatteryStat();
//                drawStat();
//                drawCenterMark();
//                updateCanvas();
                break;

            default:
                break;
        }

        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mTotalWidth = w;
        mTotalHeight = h;
    }
}
