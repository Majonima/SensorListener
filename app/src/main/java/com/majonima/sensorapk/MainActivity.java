package com.majonima.sensorapk;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static android.os.BatteryManager.BATTERY_PLUGGED_USB;

public class MainActivity extends AppCompatActivity {
    private SensorManager msensorManager;
    private LocationManager locationManager;
    private AudioRecord audioRecord;

    private float[] accelData;
    private float[] accelOld;

    private double accelAlert;

    private int minSize;
    private double P0;

    private TextView xyView;
    private TextView xzView;
    private TextView zyView;

    private TextView gpsLong;
    private TextView gpsLat;

    private TextView noiseView;

    private Toast alertToast;

    private String locationText;
    private String accelerationText;

    private Date lastAlertDate;
    private Date alertDate;
    private SimpleDateFormat alertFormat;

    private Map<String, String> alertTable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        msensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            if(locationManager!=null){
                Log.v("location", String.valueOf(getApplicationContext().getSystemService(LOCATION_SERVICE)));
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 10, locationListener);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 10, locationListener);
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Log.v("location", String.valueOf(locationManager.getAllProviders()));

            } else{
                alertToast= Toast.makeText(getApplicationContext(), "GPS не обнаружено!", Toast.LENGTH_LONG);
                Log.v("location", String.valueOf(getApplicationContext().getSystemService(LOCATION_SERVICE)));
                alertToast.show();
            }

        registerReceiver(this.PowerConnectionReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        if (msensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).isEmpty()) {
            alertToast = Toast.makeText(getApplicationContext(), "Встроенного акселеолметра не обнаружено!", Toast.LENGTH_LONG);
            alertToast.show();
        } else {
            msensorManager.registerListener(sensorEventListener, msensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);
        }

        accelAlert=1.0;
        P0= 0.000002;
        accelData = new float[3];
        accelOld = new float[3];
        lastAlertDate = new Date();
        alertDate = new Date();
        alertFormat = new SimpleDateFormat("dd.MM.yyyy hh:mm:ss");
        alertTable = new HashMap<>();


        setContentView(R.layout.activity_main);

        xyView = (TextView) findViewById(R.id.xyValue);
        xzView = (TextView) findViewById(R.id.xzValue);
        zyView = (TextView) findViewById(R.id.zyValue);

        gpsLat = (TextView) findViewById(R.id.gpsValueLat);
        gpsLong= (TextView) findViewById(R.id.gpsValueLong);

        noiseView= (TextView) findViewById(R.id.noiseValue);


        Timer myTimer = new Timer();
        final Handler uiHandler = new Handler();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                final String result = NoiseListener();

                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        noiseView.setText(result.substring(0,6));
                    }
                });
            }
        }, 0L, 500);

    }
    protected void onResume() {
        super.onResume();
        registerReceiver(this.PowerConnectionReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

    }
    @Override
    protected void onPause() {
        super.onPause();
        msensorManager.unregisterListener(sensorEventListener);
        unregisterReceiver(this.PowerConnectionReceiver);
    }

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            locationText="Provider "+location.getProvider()+" Широта="+location.getLatitude()+" "+"Долгота="+location.getLongitude();

            gpsLat.setText(String.valueOf(location.getLatitude()).substring(0,6));
            gpsLong.setText(String.valueOf(location.getLongitude()).substring(0,6));

            MakeAlert("Location alert", locationText, "Обнаружены изменения в местоположении");

        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {

        }

        @Override
        public void onProviderEnabled(String s) {

        }

        @Override
        public void onProviderDisabled(String s) {

        }
    };

    private SensorEventListener sensorEventListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            loadNewSensorData(event);

            xyView.setText(String.valueOf(Math.abs(accelData[0])).substring(0,3));
            xzView.setText(String.valueOf(Math.abs(accelData[1])).substring(0,3));
            zyView.setText(String.valueOf(Math.abs(accelData[2])).substring(0,3));
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }

        private void loadNewSensorData(SensorEvent event) {

            final int type = event.sensor.getType();
            if (type == Sensor.TYPE_ACCELEROMETER) {
                accelOld = accelData;
                accelData = event.values.clone();
                for (int i = 0; i < accelData.length; i++) {
                    if ((accelOld[i] != 0.0) && ((Math.abs(Math.abs(accelOld[i]) - Math.abs(accelData[i])) > accelAlert))) {
                        accelerationText=String.valueOf(Math.abs(Math.abs(accelOld[i]) - Math.abs(accelData[i])));
                        MakeAlert("Acceleration alert", accelerationText,"Обнаружены чрезмерные колебания");
                    }
                }
            }
        }
    };

    private String NoiseListener() {
            minSize = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minSize);
            audioRecord.startRecording();
                            short[] buffer = new short[minSize];
                            audioRecord.read(buffer, 0, minSize);
                            double splValue = 0.0;
                            double rmsValue = 0.0;
                            for (short s : buffer) {
                                rmsValue += s * s;
                            }
                            rmsValue = rmsValue / minSize;
                            rmsValue = Math.sqrt(rmsValue);

                            splValue = 20 * Math.log10(rmsValue / P0);
                            splValue = splValue - 80;
                            if(splValue>80){
                                MakeAlert("Noise alert", "Noise lvl: "+String.valueOf(splValue).substring(0,6), "Слишком шумно");
                            }
                           return String.valueOf(splValue);
    }

    private void MakeAlert(String source, String value, final String toastMessage){

        alertDate.setTime(System.currentTimeMillis());
        alertTable.put(alertFormat.format(alertDate).toString(), source+" "+value);
        Log.v(source, source+" "+value+" "+alertFormat.format(alertDate).toString());
        if (alertDate.getTime() - lastAlertDate.getTime() > 10000) {
            runOnUiThread(new Runnable() {
                              public void run() {
                                  alertToast = Toast.makeText(getApplicationContext(), toastMessage, Toast.LENGTH_LONG);
                                  alertToast.show();
                              }
                          });


            lastAlertDate.setTime(System.currentTimeMillis());
        }

    }


    private BroadcastReceiver PowerConnectionReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            int chargePlug = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            boolean usbCharge = chargePlug == BATTERY_PLUGGED_USB;
            if(!usbCharge){
                MakeAlert("Battery alert","Not plugged with usb","Зарядное устройство отключено");
            }

        }
    };
}
