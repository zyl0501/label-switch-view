package com.ray.labelswitch.sample;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.ray.labelswitch.R;
import com.ray.labelswitch.library.LabelSwitchView;


public class MainActivity extends ActionBarActivity {

    Toast toast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LabelSwitchView labelSwitchView = (LabelSwitchView) findViewById(R.id.label_view);
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


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
