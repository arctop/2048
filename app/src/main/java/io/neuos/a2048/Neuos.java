package io.neuos.a2048;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.List;

import io.neuos.INeuosSdk;
import io.neuos.INeuosSdkListener;
import io.neuos.NeuosQAProperties;
import io.neuos.NeuosSDK;

public class Neuos extends AppCompatActivity {
    private static final String TAG = "Neuos-Service-Example";
    private static final String API_KEY = "aaaa";
    //private static final String USER_ID = "CsKjQDccitgHzgHy5";

    private static final String USER_ID = "pcnnkbB5vgCAeoTTq";
    public static final String PREDICTION_NAME = "flow";

    private MuseConnectionBroadcastReceiver museListBroadcastReceiver;
    private INeuosSdk mService;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        museListBroadcastReceiver = new MuseConnectionBroadcastReceiver();
        registerReceiver(museListBroadcastReceiver,
                new IntentFilter(io.neuos.NeuosSDK.IO_NEUOS_DEVICE_PAIRING_ACTION));
    }

    private Runnable mPostConnection;


    private class MuseConnectionBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String address = intent.getStringExtra(io.neuos.NeuosSDK.DEVICE_ADDRESS_EXTRA);
            Log.d(TAG, "Connection Intent : " + address);
            try {
                mService.connectSensorDevice(address);
            } catch (RemoteException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }
    }


    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.d(TAG, "Attached.");
            mService = INeuosSdk.Stub.asInterface(service);
            try {
                mService.registerSDKCallback(mCallback);
                initialize(null);
            } catch (Exception e) {

                Log.e(TAG, e.getLocalizedMessage());
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "Detached.");
        }
    };

    @Override
    protected void onDestroy() {
        try {
            mService.finishSession();
            mService.disconnectSensorDevice();
            unregisterReceiver(museListBroadcastReceiver);
        } catch (RemoteException e) {
            Log.e(TAG , e.getLocalizedMessage());
        }
        super.onDestroy();
    }

    void doBindService() {
        try {
            Intent serviceIntent = new Intent(INeuosSdk.class.getName());
            List<ResolveInfo> matches=getPackageManager()
                    .queryIntentServices(serviceIntent, 0);
            if (matches.size() == 0) {
                Log.d(TAG, "Cannot find a matching service!");
                Toast.makeText(this, "Cannot find a matching service!",
                        Toast.LENGTH_LONG).show();
            }
            else if (matches.size() > 1) {
                Log.d(TAG, "Found multiple matching services!");
                Toast.makeText(this, "Found multiple matching services!",
                        Toast.LENGTH_LONG).show();
            }
            else {
                Intent explicit=new Intent(serviceIntent);
                ServiceInfo svcInfo=matches.get(0).serviceInfo;
                ComponentName cn=new ComponentName(svcInfo.applicationInfo.packageName,
                        svcInfo.name);
                explicit.setComponent(cn);
                if (bindService(explicit, mConnection,  BIND_AUTO_CREATE)){
                    Log.d(TAG, "Bound to Neuos Service");
                } else {
                    Log.d(TAG, "Failed to bind to Neuos Service");
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "can't bind to NeuosService, check permission in Manifest");
        }

    }
    private boolean isSessionInProgress = false;
    private final INeuosSdkListener mCallback = new INeuosSdkListener.Stub() {
        @Override
        public void onConnectionChanged(int previousConnection, int currentConnection) throws RemoteException {
            Log.i(TAG, "onConnectionChanged P: " + previousConnection + " C: " + currentConnection);
            if (currentConnection == io.neuos.NeuosSDK.ConnectionState.CONNECTED){
                if (mPostConnection != null){
                    mPostConnection.run();
                    mPostConnection = null;
                }
            }
        }
        @Override
        public void onValueChanged(String key, float value) throws RemoteException {
            Log.i(TAG, "onValueChanged K: " + key + " V: " + value);
        }
        @Override
        public void onQAStatus(boolean passed , int type){
            Log.i(TAG, "on QA Passed: " + passed + " T: " + type);
        }

        @Override
        public void onUserCalibrationStatus(int calibrationStatus) throws RemoteException {
            Log.i(TAG, "onUserCalibrationStatus: " + calibrationStatus);
            if (calibrationStatus == io.neuos.NeuosSDK.UserCalibrationStatus.NEEDS_CALIBRATION){
                connectToMuse(null);
                mPostConnection = () -> startCalibration(null);
            }
            else if (calibrationStatus == io.neuos.NeuosSDK.UserCalibrationStatus.MODELS_AVAILABLE){
                connectToMuse(null);
                mPostConnection = () -> startSession(null);
            }
        }

        @Override
        public void onPredictionSessionStart() throws RemoteException {
            Log.i(TAG, "onPredictionSessionStart: ");
            isSessionInProgress = true;
        }

        @Override
        public void onExperimentSessionStart() throws RemoteException {
            Log.i(TAG, "onExperimentSessionStart: ");
        }

        @Override
        public void onSessionComplete() throws RemoteException {
            Log.i(TAG, "onSessionComplete");
        }

        /*@Override
        public void onUserLoaded() throws RemoteException {
            Log.i(TAG, "onUserLoaded: ");
        }

        @Override
        public void onUserUnloaded() throws RemoteException {
            Log.i(TAG, "onUserUnloaded");
        }

       @Override
        public void onUserCreated(String userId) throws RemoteException {
            Log.i(TAG, "onUserCreated: ");
        }*/

        @Override
        public void onInitialized() throws RemoteException {
            Log.i(TAG, "onInitialized");
//            login(null);
        }

        @Override
        public void onShutDown() throws RemoteException {
            Log.i(TAG, "onShutDown");
        }

        @Override
        public void onError(int errorCode, String message) throws RemoteException {
            Log.i(TAG, "onError Code: " + errorCode + " " + message);
        }
    };


    public void bindToService(View view) {
        //doBindService();
        checkSDKPermissions();
    }

    public void connectToMuse(View view){
        Intent activityIntent = new Intent("io.neuos.PairDevice");

        //activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //activityIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        List<ResolveInfo> matches=getPackageManager()
                .queryIntentActivities(activityIntent , PackageManager.MATCH_ALL);
        Intent explicit=new Intent(activityIntent);
        ActivityInfo info = matches.get(0).activityInfo;
        ComponentName cn = new ComponentName(info.applicationInfo.packageName,
                info.name);
        explicit.setComponent(cn);
        startActivity(explicit);
    }

    public void disconnect(View view) {
        try {
            mService.disconnectSensorDevice();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }

    // Register the permissions callback, which handles the user's response to the
// system permissions dialog. Save the return value, an instance of
// ActivityResultLauncher, as an instance variable.
    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                    //launchHome();
                    doBindService();
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // features requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                }
            });


    private void checkSDKPermissions(){
        if (ContextCompat.checkSelfPermission(
                this, io.neuos.NeuosSDK.NEUOS_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED) {
            // You can use the API that requires the permission.
            doBindService();
            //performAction(...);
        } /*else if (shouldShowRequestPermissionRationale(...)) {
            // In an educational UI, explain to the user why your app requires this
            // permission for a specific feature to behave as expected. In this UI,
            // include a "cancel" or "no thanks" button that allows the user to
            // continue using your app without granting the permission.
            //showInContextUI(...);
        }*/ else {
            // You can directly ask for the permission.
            // The registered ActivityResultCallback gets the result of this request.
            requestPermissionLauncher.launch(
                    io.neuos.NeuosSDK.NEUOS_PERMISSION);
        }
    }

    public void login(View view) {


        try {
            int status = mService.getUserLoginStatus();
            switch (status){
                case io.neuos.NeuosSDK.LoginStatus.LOGGED_IN:{
                    Log.i(TAG, "login: Logged In");
                    break;
                }
                case io.neuos.NeuosSDK.LoginStatus.NOT_LOGGED_IN:{
                    Log.i(TAG, "login: Not Logged In");
                    launchHome();
                    break;
                }
            }
            //loadUser(USER_ID);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }


    public void launchQAScreen(View view) {
        Intent activityIntent = new Intent("io.neuos.QAScreen");

        /*activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);*/
        List<ResolveInfo> matches=getPackageManager()
                .queryIntentActivities(activityIntent , PackageManager.MATCH_ALL);
        Intent explicit=new Intent(activityIntent);
        ActivityInfo info = matches.get(0).activityInfo;
        ComponentName cn = new ComponentName(info.applicationInfo.packageName,
                info.name);
        explicit.setComponent(cn);
        explicit.putExtra(NeuosQAProperties.STAND_ALONE , true);
        explicit.putExtra(NeuosQAProperties.TASK_PROPERTIES ,
                new NeuosQAProperties(NeuosQAProperties.Quality.Good , NeuosQAProperties.INFINITE_TIMEOUT));
        //TODO: Start activity for result
        startActivity(explicit);
    }

    private void launchHome() {
        Intent activityIntent = new Intent("io.neuos.NeuosLogin");

        /*activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);*/
        List<ResolveInfo> matches=getPackageManager()
                .queryIntentActivities(activityIntent , PackageManager.MATCH_ALL);
        Intent explicit=new Intent(activityIntent);
        ActivityInfo info = matches.get(0).activityInfo;
        ComponentName cn = new ComponentName(info.applicationInfo.packageName,
                info.name);
        explicit.setComponent(cn);
        //TODO: Start activity for result
        startActivity(explicit);
    }

    /*public void logout(View view) {
        try {
            mService.unloadUser();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }*/

    public void startSession(View view) {
        try {
            isSessionInProgress = true;
            mService.startPredictionSession(PREDICTION_NAME);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }

    public void stopSession(View view) {
        try {
            mService.finishSession();
            isSessionInProgress = false;
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }

    public void checkCalibrationStatus(View view){
        try {
            mService.checkUserCalibrationStatus();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }
    public void initialize(View view) {
        try {
            mService.initializeNeuos(API_KEY);
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }

    public void startCalibration(View view) {

        Intent activityIntent = new Intent("io.neuos.NeuosCalibration");
        //Intent activityIntent = new Intent("io.neuos.NeuosLogin");

        /*activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);*/
        List<ResolveInfo> matches=getPackageManager()
                .queryIntentActivities(activityIntent , PackageManager.MATCH_ALL);
        Log.i(TAG, "startGame: " + matches.size());
        Intent explicit=new Intent(activityIntent);
        ActivityInfo info = matches.get(0).activityInfo;
        ComponentName cn = new ComponentName(info.applicationInfo.packageName,
                info.name);
        explicit.setComponent(cn);
        startActivity(explicit);
        //
        /*try {
            mService.startCalibrationGame();
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }*/
    }

    public void neuosHome(View view) {
        launchHome();
    }


}
