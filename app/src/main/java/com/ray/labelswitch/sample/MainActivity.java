package com.ray.labelswitch.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.ray.labelswitch.R;
import com.ray.widget.lab.LabelSwitchView;


public class MainActivity extends AppCompatActivity {

    Toast toast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LabelSwitchView labelSwitchView = findViewById(R.id.label_view);
        labelSwitchView.setOnIndexChangeListener(new LabelSwitchView.OnIndexChangeListener() {
            @Override
            public void onIndexChange(int oldIndex, int newIndex) {
                if(toast == null) {
                    toast= Toast.makeText(MainActivity.this, oldIndex + " -> " + newIndex, Toast.LENGTH_SHORT);
                }else{
                    toast.setText(oldIndex + " -> " + newIndex);
                }
                toast.show();
            }
        });
    }

}
