package com.app.android.camera2tracker;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;

import static com.app.android.camera2tracker.PreviewFragment.EXTRA_COORDINATES;
import static com.app.android.camera2tracker.PreviewFragment.EXTRA_TARGET;

/**
 * Created by Liam Wu on 17/10/17.
 */

public class TargetSelectorView extends View {
    private static final int UP = 0;
    private static final int DOWN = 1;

    private Rect mRectangle;
    private Paint mPaint;
    private final int StrokeWidth;
    private String mCoordinates;
    private String mHints;
    private String mButtonHints;
    private Bitmap mBitmap, mTarget;
    private Rect mButtonRect;
    private Rect mSrcRect, mDestRect;
    private int mTotalWidth, mTotalHeight;
    private int mTopX, mTopY, mBottomX, mBottomY;
    private int mHintsX, mHintsY;
    private int mCoordinatesX, mCoordinatesY;
    private double[] mBox;
    private float mAspectRatio, mFitRatio;

    public TargetSelectorView(Context context, Bitmap bitmap) {
        super(context);
        int x = 50;
        int y = 50;
        int sideLength = 200;
        StrokeWidth = 5;
        mHintsX = 830;
        mHintsY = 2000;
        mTopX = 0;
        mTopY = 0;
        mBottomX = 0;
        mBottomY = 0;
        mCoordinatesX = 0;
        mCoordinatesY = -20;
        mBox = new double[4];
        mCoordinates = "Drag to target the object!";
        mHints = "Drag to target the object!";
        mButtonHints = "CONFIRM";

        mTotalWidth = getMeasuredWidth();
        mTotalHeight = getMeasuredHeight();

        int mBitWidth = bitmap.getWidth();
        int mBitHeight = bitmap.getHeight();

        Matrix matrix = new Matrix();
        matrix.setRotate(90);
        mBitmap = Bitmap.createBitmap(bitmap, 0, 0, mBitWidth, mBitHeight, matrix, false);
        mBitHeight = mBitmap.getHeight();
        mBitWidth = mBitmap.getWidth();
        mAspectRatio = (float)mBitHeight / (float)mBitWidth;
        mFitRatio = 0;
//        bitmap.recycle();
        mSrcRect = new Rect(0, 0, mBitWidth, mBitHeight);

        // create bounding box
        mRectangle = new Rect(x, y, sideLength, sideLength);

        // Rectangle of the button
        mButtonRect = new Rect(600, 2000, 900, 2200);

        // create the Paint and set its color
        mPaint = new Paint();
        mPaint.setColor(Color.YELLOW);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(StrokeWidth);
        mPaint.setTextSize(50);
    }

    // Set reference screen size
    public void setScreenSize(int Width, int Height) {
        mTotalHeight = Height;
        mTotalWidth = Width;
        mFitRatio = (float)(mBitmap.getWidth()) / mTotalWidth;   //Fitting ratio
        mDestRect = new Rect(0, 0, mTotalWidth, (int)(mTotalWidth * mAspectRatio));
    }

    // Display bounding box parameters
    public void setBoundingBox() {
        mCoordinates = String.format("From (%d, %d) To (%d, %d)", mTopX, mTopY, mBottomX, mBottomY);
    }

    public void drawButton(Canvas canvas, int x, int y, int state, Paint paint) {
        mButtonRect.left = x-200;
        mButtonRect.right = x+200;
        mButtonRect.top = y-100;
        mButtonRect.bottom = y+100;
        if (state == UP) {
            canvas.drawRect(mButtonRect, paint);
            canvas.drawText(mButtonHints, x-100, y+20, mPaint);
        }
        if (state == DOWN) {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawRect(mButtonRect, paint);
            paint.setColor(Color.BLACK);
            canvas.drawText(mButtonHints, x-100, y+20, mPaint);
            paint.setColor(Color.YELLOW);
            paint.setStyle(Paint.Style.STROKE);
        }
    }

    public boolean isClick(int x, int y, Rect button) {
        boolean isClick = false;
        if (x >= button.left && x <= button.right && y >= button.top
                && y <= button.bottom) {
            isClick = true;
        }
        return isClick;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mPaint.setAntiAlias(true);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        canvas.drawBitmap(mBitmap, mSrcRect, mDestRect, mPaint);
        canvas.drawRect(mRectangle, mPaint);
        setBoundingBox();
        canvas.drawText(mCoordinates, mCoordinatesX, mCoordinatesY, mPaint);
        canvas.drawText(mHints, mHintsX, mHintsY, mPaint);
        drawButton(canvas, mTotalWidth/2, mTotalHeight-220, UP, mPaint);
    }

    // Come back to main activity to start calculation
    public void startTrack() {
        mBox[0] = (double) mTopX / mTotalWidth;
        mBox[1] = (double) mTopY / (mTotalWidth * mAspectRatio);
        mBox[2] = (double) mBottomX / mTotalWidth;
        mBox[3] = (double) mBottomY / (mTotalWidth * mAspectRatio);
        Intent intent = new Intent(getContext(), CameraActivity.class);
        intent.putExtra(EXTRA_COORDINATES, mBox);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        mTarget.compress(Bitmap.CompressFormat.JPEG, 50, out);
        byte[] bytes = out.toByteArray();
        intent.putExtra(EXTRA_TARGET, bytes);
        getContext().startActivity(intent);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int x = (int)event.getX();
        int y = (int)event.getY();

        if (y >= (int)(mTotalWidth*mAspectRatio)) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (isClick(x, y, mButtonRect)) {
                        if (mTopX >= mBottomX | mTopY >= mBottomY) {
                            Toast.makeText(getContext(), "Box Invalid!", Toast.LENGTH_LONG).show();
                            break;
                        }
                        invalidate(mButtonRect);

                        // clip out content in the box
                        mSrcRect.set(mRectangle);
                        mSrcRect.left *= mFitRatio;
                        mSrcRect.right *= mFitRatio;
                        mSrcRect.top *= mFitRatio;
                        mSrcRect.bottom *= mFitRatio;
                        mDestRect.set(mRectangle);
                        mTarget = Bitmap.createBitmap(mBitmap, mSrcRect.left, mSrcRect.top, mSrcRect.right - mSrcRect.left, mSrcRect.bottom - mSrcRect.top);

                        // show animate button pushed down
                        mPaint.setStyle(Paint.Style.FILL);
                    }
                    else
                        break;

                case MotionEvent.ACTION_MOVE:
                    break;

                case MotionEvent.ACTION_UP:
                    if (isClick(x, y, mButtonRect)) {
                        invalidate(mButtonRect);
                        int clearance = mTotalWidth/4;

                        // show animate button pop back up
                        mPaint.setStyle(Paint.Style.STROKE);

                        // show enlarged target selected
                        mSrcRect.set(mRectangle);
                        mSrcRect.left *= mFitRatio;
                        mSrcRect.right *= mFitRatio;
                        mSrcRect.top *= mFitRatio;
                        mSrcRect.bottom *= mFitRatio;
                        mRectangle.setEmpty();

                        mDestRect.left = clearance;
                        mDestRect.top = clearance;
                        mDestRect.right = mTotalWidth - clearance;
                        mDestRect.bottom = clearance + 2*clearance*mSrcRect.height()/mSrcRect.width();
                        mCoordinatesX = clearance;
                        mCoordinatesY = clearance - 20;
                        mHints = "Target selected!";

                        // start tracking!
//                        Toast.makeText(getContext(), "Tracking Start!", Toast.LENGTH_LONG).show();
                        startTrack();
                    }
                    else
                        break;

                default:
                    break;
            }
        }

        else {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mTopX = x;
                    mTopY = y;
                    mCoordinatesX = mTopX;
                    mCoordinatesY = mTopY - 20;
                    invalidate(mRectangle);
                    mRectangle.left = x;
                    mRectangle.top = y;
                    mRectangle.right = mRectangle.left;
                    mRectangle.bottom = mRectangle.top;

                case MotionEvent.ACTION_MOVE:
                    mBottomX = x;
                    mBottomY = y;
                    Rect old =
                            new Rect(mRectangle.left, mRectangle.top, mRectangle.right
                                    + StrokeWidth, mRectangle.bottom + StrokeWidth);
                    mRectangle.right = x;
                    mRectangle.bottom = y;
                    old.union(x, y);
                    invalidate(old);
                    break;

                case MotionEvent.ACTION_UP:
                    break;

                default:
                    break;
            }
        }

        return  true;
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
