package com.devs.vectorchildfinderdemo;

import static android.graphics.drawable.Drawable.createFromPath;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.PathParser;

import com.devs.vectorchildfinder.VectorChildFinder;
import com.devs.vectorchildfinder.VectorDrawableCompat;
//import android.support.graphics.drawable.VectorDrawableCompat;


public class MainActivity extends AppCompatActivity {

    VectorDrawableCompat.VFullPath dressPath;
    VectorDrawableCompat.VGroup mouthGroup, eyesGroup;
    ImageView image;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


         image = findViewById(R.id.image);

        VectorChildFinder vector = new VectorChildFinder(this,
                R.drawable.boy, image);
        dressPath = vector.findPathByName("dress_path");
        mouthGroup = vector.findGroupByName("mouth_group");
        eyesGroup = vector.findGroupByName("eyes_group");



        for (int i=0; i < dressPath.getPathData().length; i++){
            String path =dressPath.getPathData()[i].toString();
            Log.d("savedInstanceState", ""+path);

        }


        image.setOnTouchListener((v, event) -> {
            switch (event.getAction()){
                case MotionEvent.ACTION_UP:
                    // TODO
                    Toast.makeText(MainActivity.this,
                            mouthGroup.getScaleX() +"__"+
                                    mouthGroup.getLocalMatrix(),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
            return true;
        });

    }

    boolean e = true, m = true;
    public void onButtonClick(View view){
        switch (view.getId()) {
            case R.id.button1:
                dressPath.setFillColor(getResources().getColor(android.R.color.holo_red_light));
            break;

            case R.id.button2:
                dressPath.setFillColor(getResources().getColor(android.R.color.holo_green_dark));
                break;

            case R.id.button3:
                dressPath.setFillColor(getResources().getColor(android.R.color.holo_blue_dark));
                break;

            case R.id.button4:
                dressPath.setFillColor(getResources().getColor(android.R.color.holo_purple));
                break;

            case R.id.btn_eyes:
                if (e) {
                    e = false;
                    eyesGroup.setTranslateX(-10);
                }
                else {
                    e = true;
                    eyesGroup.setTranslateX(0);
                }

                break;

            case R.id.btn_mouth:
                if(m) {
                    m = false;
                    mouthGroup.setTranslateX(-10);
                }
                else {
                    m = true;
                    mouthGroup.setTranslateX(0);
                }
                break;
        }

        image.invalidate();
    }

}
