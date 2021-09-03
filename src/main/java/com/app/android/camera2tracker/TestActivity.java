package com.app.android.camera2tracker;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

// Testing session for debug
public class TestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

//        setContentView(new CustomView(this));
    }

    public void moveCenter(View view) {
        Toast.makeText(this, "Center!", Toast.LENGTH_SHORT).show();
        // bluetooth signal move back to center position
    }

    public void moveRight(View view) {
        Toast.makeText(this, "Right!", Toast.LENGTH_SHORT).show();
        // bluetooth signal move right by one unit length
    }

    public void moveLeft(View view) {
        Toast.makeText(this, "Left!", Toast.LENGTH_SHORT).show();
        // bluetooth signal move left by one unit length
    }

    public void moveUp(View view) {
        Toast.makeText(this, "Up!", Toast.LENGTH_SHORT).show();
        // bluetooth signal move up by one unit length
    }

    public void moveDown(View view) {
        Toast.makeText(this, "Down!", Toast.LENGTH_SHORT).show();
        // bluetooth signal move down by one unit length
    }

    public void openSetting(View view) {
        Toast.makeText(this, "Setting!", Toast.LENGTH_SHORT).show();
        // open setting view

        Intent intent = new Intent(this, BluetoothActivity.class);
        startActivity(intent);
    }
}
