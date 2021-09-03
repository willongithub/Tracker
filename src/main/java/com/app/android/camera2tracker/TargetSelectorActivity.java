package com.app.android.camera2tracker;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;

import static com.app.android.camera2tracker.PreviewFragment.EXTRA_PICTURE;

// Target selecting session
public class TargetSelectorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //show picture captured in ImageView
        Intent intent = getIntent();
        Bitmap bitmapPreview = BitmapFactory.decodeByteArray(intent.getByteArrayExtra(EXTRA_PICTURE),
                0, intent.getByteArrayExtra(EXTRA_PICTURE).length);

        TargetSelectorView mTargetSelectorView = new TargetSelectorView(this, bitmapPreview);
        DisplayMetrics displayMetrics = this.getResources().getDisplayMetrics();
        mTargetSelectorView.setScreenSize(displayMetrics.widthPixels, displayMetrics.heightPixels);
        setContentView(mTargetSelectorView);
    }
}
