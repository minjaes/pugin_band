package com.aware.plugin.plugin_band;

import com.microsoft.band.BandClient;

import android.content.ContentValues;
import android.content.Intent;

import android.os.AsyncTask;

import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Aware_Plugin;
import com.microsoft.band.BandClientManager;
import com.microsoft.band.BandException;
import com.microsoft.band.BandIOException;
import com.microsoft.band.BandInfo;
import com.microsoft.band.ConnectionState;
import com.microsoft.band.UserConsent;
import com.microsoft.band.sensors.BandAccelerometerEvent;
import com.microsoft.band.sensors.BandAccelerometerEventListener;
import com.microsoft.band.sensors.BandCaloriesEvent;
import com.microsoft.band.sensors.BandCaloriesEventListener;
import com.microsoft.band.sensors.BandContactEvent;
import com.microsoft.band.sensors.BandContactEventListener;
import com.microsoft.band.sensors.BandContactState;
import com.microsoft.band.sensors.BandDistanceEvent;
import com.microsoft.band.sensors.BandDistanceEventListener;
import com.microsoft.band.sensors.BandHeartRateEvent;
import com.microsoft.band.sensors.BandHeartRateEventListener;
import com.microsoft.band.sensors.BandPedometerEvent;
import com.microsoft.band.sensors.BandPedometerEventListener;
import com.microsoft.band.sensors.BandSkinTemperatureEvent;
import com.microsoft.band.sensors.BandSkinTemperatureEventListener;
import com.microsoft.band.sensors.BandUVEvent;
import com.microsoft.band.sensors.BandUVEventListener;
import com.microsoft.band.sensors.HeartRateConsentListener;
import com.microsoft.band.sensors.SampleRate;
import com.microsoft.band.sensors.UVIndexLevel;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;




import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.CalendarList;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;

public class Plugin extends Aware_Plugin {

    private BandClient client = null;

    private int heartRate;
    private float x;
    private float y;
    private float z;
    private long steps;
    private float skinTemp;
    private long calories;
    private float distance;
    private BandContactState contact;
    private UVIndexLevel UV;
    private int gyroscope;

    private String heartRateL = "Unix Timestamp,Local Timestamp,Heart Rate\n";
    private String accelerometerL = "Unix Timestamp,Local Timestamp,x,y,z\n";
    private String stepsL = "Unix Timestamp,Local Timestamp,steps\n";
    private String skinTempL  ="Unix Timestamp,Local Timestamp,Skin Temperature\n";
    private String caloriesL = "Unix Timestamp,Local Timestamp,Calories\n";
    private String distanceL = "Unix Timestamp,Local Timestamp,Distance\n";
    private String UVL = "Unix Timestamp,Local Timestamp,UV\n";


    @Override
    public void onCreate() {
        super.onCreate();

        TAG = "AWARE::"+getResources().getString(R.string.app_name);
        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");


        if(DEBUG) Log.d(TAG, "plugin_band running");

        //Activate programmatically any sensors/plugins you need here
        //e.g., Aware.setSetting(this, Aware_Preferences.STATUS_ACCELEROMETER,true);

        //Any active plugin/sensor shares its overall context using broadcasts
        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                //Broadcast your context here
            }
        };

        //To sync data to the server, you'll need to set this variables from your ContentProvider
        //DATABASE_TABLES = Provider.DATABASE_TABLES
        //TABLES_FIELDS = Provider.TABLES_FIELDS
        //CONTEXT_URIS = new Uri[]{ Provider.Table_Data.CONTENT_URI }

        //Activate plugin
        Aware.startPlugin(this, getPackageName());

        new appTask().execute(); //runs receivers on background thread
    }

    //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent,flags,startId);
        //Check if the user has toggled the debug messages
        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

        new appTask().execute(); //runs receivers on background thread

        return 1;
    }

    @     Override
    public void onDestroy() {
        super.onDestroy();

        Aware.setSetting(this, Settings.STATUS_PLUGIN_TEMPLATE, false);

        //Deactivate any sensors/plugins you activated here
        //e.g., Aware.setSetting(this, Aware_Preferences.STATUS_ACCELEROMETER, false);



        /**
        try {
            client.getSensorManager().unregisterAccelerometerEventListeners();
        } catch (BandIOException e) {
            appendToUI(e.getMessage());
        }
         **/
        //Stop plugin
        Aware.stopPlugin(this, getPackageName());

    }


    private class appTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            try {
                if (getConnectedBandClient()) {
                    //register receivers
                    client.getSensorManager().registerAccelerometerEventListener(mAccelerometerEventListener, SampleRate.MS16);
                    client.getSensorManager().registerPedometerEventListener(mPedometerEventListener);
                    client.getSensorManager().registerSkinTemperatureEventListener(mSkinTemperatureEventListener);
                    client.getSensorManager().registerCaloriesEventListener(mCaloriesEventListener);
                    client.getSensorManager().registerUVEventListener(mUVEventListener);
                    client.getSensorManager().registerDistanceEventListener(mDistanceEventListener);
                    if(client.getSensorManager().getCurrentHeartRateConsent() == UserConsent.GRANTED) {
                        client.getSensorManager().registerHeartRateEventListener(mHeartRateEventListener);
                    } else {
                        //TODO: fill in ???
                        //client.getSensorManager().requestHeartRateConsent(this, mHeartRateConsentListener);
                    }
                } else {
                }
            } catch (BandException e) {
                String exceptionMessage="";
                switch (e.getErrorType()) {
                    case UNSUPPORTED_SDK_VERSION_ERROR:
                        exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.";
                        break;
                    case SERVICE_ERROR:
                        exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.";
                        break;
                    default:
                        exceptionMessage = "Unknown error occured: " + e.getMessage();
                        break;
                }
            } catch (Exception e) {
            }
            return null;
        }
    }

    private BandAccelerometerEventListener mAccelerometerEventListener = new BandAccelerometerEventListener() {
        @Override
        public void onBandAccelerometerChanged(final BandAccelerometerEvent event) {
            if (event != null) {
                long time = event.getTimestamp();
                x = event.getAccelerationX();
                y = event.getAccelerationY();
                z = event.getAccelerationZ();
                //new writeOnFile().execute(params);
                float magnitude = (float)Math.sqrt((x*x)+(y*y)+(z*z));
                //writeOnDB(time, magnitude, "accelerometer");

            }
        }
    };


    private BandHeartRateEventListener mHeartRateEventListener = new BandHeartRateEventListener() {
        @Override
        public void onBandHeartRateChanged(final BandHeartRateEvent event) {
            if (event != null) {
                long time = event.getTimestamp();
                heartRate = event.getHeartRate();
                writeOnDB(time,heartRate, "heartRate");
            }
        }
    };


    private HeartRateConsentListener mHeartRateConsentListener = new HeartRateConsentListener() {
        @Override
        public void userAccepted(boolean b) {
            // handle user's heart rate consent decision
            if(b){
                startHRListener();
            }
            else{
                // Consent hasn't been given
            }
        }
    };
    public void startHRListener() {
        try {
            // register HR sensor event listener
            client.getSensorManager().registerHeartRateEventListener(mHeartRateEventListener);
        } catch (BandIOException ex) {
        } catch (BandException e) {
            String exceptionMessage="";
            switch (e.getErrorType()) {
                case UNSUPPORTED_SDK_VERSION_ERROR:
                    exceptionMessage = "Microsoft Health BandService doesn't support your SDK Version. Please update to latest SDK.";
                    break;
                case SERVICE_ERROR:
                    exceptionMessage = "Microsoft Health BandService is not available. Please make sure Microsoft Health is installed and that you have the correct permissions.";
                    break;
                default:
                    exceptionMessage = "Unknown error occurred: " + e.getMessage();
                    break;
            }
        } catch (Exception e) {
        }
    }

    private BandSkinTemperatureEventListener mSkinTemperatureEventListener = new BandSkinTemperatureEventListener() {
        @Override
        public void onBandSkinTemperatureChanged(BandSkinTemperatureEvent event) {
            if (event != null) {
                long time = event.getTimestamp();
                skinTemp = event.getTemperature();

                writeOnDB(time, skinTemp, "skinTemp");
            }
        }
    };

    private BandPedometerEventListener mPedometerEventListener = new BandPedometerEventListener() {
        @Override
        public void onBandPedometerChanged(BandPedometerEvent event) {
            if (event != null) {
                long time = event.getTimestamp();

                steps = event.getTotalSteps();

                writeOnDB(time, steps, "pedometer");
            }
        }
    };

    private BandCaloriesEventListener mCaloriesEventListener = new BandCaloriesEventListener() {
        @Override
        public void onBandCaloriesChanged(BandCaloriesEvent event) {
            if (event != null) {
                long time = event.getTimestamp();
                calories = event.getCalories();

                writeOnDB(time, calories, "calories");
            }
        }
    };

    private BandDistanceEventListener mDistanceEventListener = new BandDistanceEventListener() {
        @Override
        public void onBandDistanceChanged(BandDistanceEvent event) {
            if (event != null) {
                long time = event.getTimestamp();
                distance = event.getTotalDistance();

                writeOnDB(time, distance, "distance");

            }
        }
    };

    private BandUVEventListener mUVEventListener = new BandUVEventListener() {
        @Override
        public void onBandUVChanged(BandUVEvent event) {
            if (event != null){
                long time = event.getTimestamp();
                UV = event.getUVIndexLevel();

                //writeOnDB(time, UV, "UV");
            }
        }
    };

    private BandContactEventListener mContactEventListener = new BandContactEventListener() {
        @Override
        public void onBandContactChanged(BandContactEvent event) {
            if (event != null) {
                long time = event.getTimestamp();
                contact = event.getContactState();
            }
        }
    };


    private boolean getConnectedBandClient() throws InterruptedException, BandException {
        if (client == null) {
            BandInfo[] devices = BandClientManager.getInstance().getPairedBands();
            if (devices.length == 0) {
                //appendToUI("Band isn't paired with your phone.\n");
                return false;
            }
            client = BandClientManager.getInstance().create(getBaseContext(), devices[0]);
        } else if (ConnectionState.CONNECTED == client.getConnectionState()) {
            return true;
        }
        //appendToUI("Band is connecting...\n");
        return ConnectionState.CONNECTED == client.connect().await();
    }

    private void writeOnDB (long timeStamp, float value, String type){
        ContentValues new_data = new ContentValues();
        new_data.put(bandProvider.Band_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        new_data.put(bandProvider.Band_Data.TIMESTAMP, timeStamp);
        new_data.put(bandProvider.Band_Data._Value, value);
        new_data.put(bandProvider.Band_Data.TYPE, type);
        getContentResolver().insert(bandProvider.Band_Data.CONTENT_URI, new_data);
    }
}
