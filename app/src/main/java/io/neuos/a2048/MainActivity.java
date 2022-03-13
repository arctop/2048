package io.neuos.a2048;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
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

public class MainActivity extends AppCompatActivity {

    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String SCORE = "score";
    private static final String HIGH_SCORE = "high score temp";
    private static final String UNDO_SCORE = "undo score";
    private static final String CAN_UNDO = "can undo";
    private static final String UNDO_GRID = "undo";
    private static final String GAME_STATE = "game state";
    private static final String UNDO_GAME_STATE = "undo game state";

    private MainView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new MainView(this);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        view.hasSaveState = settings.getBoolean("save_state", false);

        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean("hasState")) {
                load();
            }
        }
        // Register to receive device pairing callbacks
        deviceListReceiver = new DeviceConnectionReceiver();
        registerReceiver(deviceListReceiver,
                new IntentFilter(NeuosSDK.IO_NEUOS_DEVICE_PAIRING_ACTION));
        // Start the flow by verifying the permissions to use the SDK
        checkSDKPermissions();
        setContentView(view);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            //Do nothing
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            view.game.move(2);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            view.game.move(0);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            view.game.move(3);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            view.game.move(1);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean("hasState", true);
        save();
    }

    protected void onPause() {
        super.onPause();
        save();
    }

    private void save() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        Tile[][] field = view.game.grid.field;
        Tile[][] undoField = view.game.grid.undoField;
        editor.putInt(WIDTH, field.length);
        editor.putInt(HEIGHT, field.length);
        for (int xx = 0; xx < field.length; xx++) {
            for (int yy = 0; yy < field[0].length; yy++) {
                if (field[xx][yy] != null) {
                    editor.putInt(xx + " " + yy, field[xx][yy].getValue());
                } else {
                    editor.putInt(xx + " " + yy, 0);
                }

                if (undoField[xx][yy] != null) {
                    editor.putInt(UNDO_GRID + xx + " " + yy, undoField[xx][yy].getValue());
                } else {
                    editor.putInt(UNDO_GRID + xx + " " + yy, 0);
                }
            }
        }
        editor.putLong(SCORE, view.game.score);
        editor.putLong(HIGH_SCORE, view.game.highScore);
        editor.putLong(UNDO_SCORE, view.game.lastScore);
        editor.putBoolean(CAN_UNDO, view.game.canUndo);
        editor.putInt(GAME_STATE, view.game.gameState);
        editor.putInt(UNDO_GAME_STATE, view.game.lastGameState);
        editor.apply();
    }

    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        //Stopping all animations
        view.game.aGrid.cancelAnimations();

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        for (int xx = 0; xx < view.game.grid.field.length; xx++) {
            for (int yy = 0; yy < view.game.grid.field[0].length; yy++) {
                int value = settings.getInt(xx + " " + yy, -1);
                if (value > 0) {
                    view.game.grid.field[xx][yy] = new Tile(xx, yy, value);
                } else if (value == 0) {
                    view.game.grid.field[xx][yy] = null;
                }

                int undoValue = settings.getInt(UNDO_GRID + xx + " " + yy, -1);
                if (undoValue > 0) {
                    view.game.grid.undoField[xx][yy] = new Tile(xx, yy, undoValue);
                } else if (value == 0) {
                    view.game.grid.undoField[xx][yy] = null;
                }
            }
        }

        view.game.score = settings.getLong(SCORE, view.game.score);
        view.game.highScore = settings.getLong(HIGH_SCORE, view.game.highScore);
        view.game.lastScore = settings.getLong(UNDO_SCORE, view.game.lastScore);
        view.game.canUndo = settings.getBoolean(CAN_UNDO, view.game.canUndo);
        view.game.gameState = settings.getInt(GAME_STATE, view.game.gameState);
        view.game.lastGameState = settings.getInt(UNDO_GAME_STATE, view.game.lastGameState);
    }

    @Override
    protected void onDestroy() {
        try {
            // Make sure to close the session and unregister everything

            /**
             * NOTE - For this example we aren't waiting for the SDK to upload the files,
             * Normally, you would call finishSession(), and wait for the call back from
             * onSessionComplete() before shutting down and exiting.
            */
            mService.finishSession();
            mService.shutdownSdk();
            unregisterReceiver(deviceListReceiver);

        } catch (RemoteException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    /**
     * Neuos Section
     */
    final String TAG = "Neuos SDK";
    // TODO: This API key should be elsewhere
    final String API_KEY = "8AXRD2RBBCPjJYu3q";
    private INeuosSdk mService;
    private Runnable mPostConnection;
    private DeviceConnectionReceiver deviceListReceiver;
    // Callback class for device connections from Neuos
    private class DeviceConnectionReceiver extends BroadcastReceiver {
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
    // Neuos SDK Listener
    private final INeuosSdkListener mCallback = new INeuosSdkListener.Stub() {
        @Override
        public void onConnectionChanged(int previousConnection, int currentConnection) throws RemoteException {
            Log.i(TAG, "onConnectionChanged P: " + previousConnection + " C: " + currentConnection);
            if (currentConnection == NeuosSDK.ConnectionState.CONNECTED){
                if (mPostConnection != null){
                    mPostConnection.run();
                    mPostConnection = null;
                }
            }
        }
        @Override
        public void onValueChanged(String key, float value) throws RemoteException {
            Log.i(TAG, "onValueChanged K: " + key + " V: " + value);
            // update our view with proper values
            view.updateNeuosValue(key, value);
        }
        @Override
        public void onQAStatus(boolean passed , int type){
            Log.i(TAG, "on QA Passed: " + passed + " T: " + type);
        }

        @Override
        public void onSessionComplete() throws RemoteException {
            Log.i(TAG, "onSessionComplete");
        }


        @Override
        public void onError(int errorCode, String message) throws RemoteException {
            Log.i(TAG, "onError Code: " + errorCode + " " + message);
        }
    };

    // Activity launcher from login
    private final ActivityResultLauncher<Intent> loginResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    checkCalibrationStatus();
                }
            });
    // Activity launcher for QA result
    private final ActivityResultLauncher<Intent> qaResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != Activity.RESULT_OK) {
                    // Here you should work with your user to get his headband working
                    showError("QA Failed!");
                    // Or terminate the session
                    try {
                        mService.terminateSessionQaFailed();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            });
    // Activity launcher for permissions request
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your app
                    doBindService();
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // features requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                }
            });

    // Helper function that tests if we have permissions
    // and starts the binding process or requests the permissions
    private void checkSDKPermissions(){
        if (ContextCompat.checkSelfPermission(
                this, NeuosSDK.NEUOS_PERMISSION) ==
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
                    NeuosSDK.NEUOS_PERMISSION);
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
                int response = mService.initializeNeuos(API_KEY);
                if ( response != NeuosSDK.ResponseCodes.SUCCESS){
                    Log.i(TAG, "initialize: failed with code " + response);
                    showError("initialize: failed with code " + response);
                }
                else{
                    response = mService.registerSDKCallback(mCallback);
                    if ( response != NeuosSDK.ResponseCodes.SUCCESS){
                        Log.i(TAG, "registerSDKCallback: failed with code " + response);
                        showError("registerSDKCallback failed with code " + response);
                    }
                    else {
                        checkNeuosLoginStatus();
                    }
                    Log.i(TAG, "register callback: returned with code " + response);
                }
            } catch (RemoteException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "Detached.");
        }
    };

    // Check the user calibration status before starting a session
    private void checkCalibrationStatus(){
        try {
            int calibrationStatus = mService.checkUserCalibrationStatus();
            Log.i(TAG, "onUserCalibrationStatus: " + calibrationStatus);
            if (calibrationStatus == NeuosSDK.UserCalibrationStatus.NEEDS_CALIBRATION){
                // Here you can instead launch the calibration activity for the user.
                // We don't do this in this context, but you can do this using the code in startCalibration()
                showError("User is not calibrated. Cannot perform realtime stream");
            }
            else if (calibrationStatus == NeuosSDK.UserCalibrationStatus.MODELS_AVAILABLE){
                connectToDevice();
                mPostConnection = () -> {
                    // Start a session
                    if (startSession() == NeuosSDK.ResponseCodes.SUCCESS) {
                        // once started successfully, launch QA screen
                        launchQAScreen();
                    }
                };
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }
    // Start a session
    private int startSession() {
        int response = -1;
        try {
            response = mService.startPredictionSession(NeuosSDK.Predictions.ZONE);
            if ( response != NeuosSDK.ResponseCodes.SUCCESS){
                Log.i(TAG, "startSession: failed with code " + response);
                showError("startSession: failed with code " + response);
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
        return response;
    }

    // Starts the process of connecting to a device
    private void connectToDevice(){
        Intent explicit = getExplicitIntent(new Intent(NeuosSDK.NEUOS_PAIR_DEVICE));
        if (explicit == null){
            showError("Cannot find Neuos pair device activity");
            return;
        }
        startActivity(explicit);
    }

    // Check the login status of a use
    private void checkNeuosLoginStatus() {
        try {
            int status = mService.getUserLoginStatus();
            switch (status){
                case NeuosSDK.LoginStatus.LOGGED_IN:{
                    Log.i(TAG, "login: Logged In");
                    checkCalibrationStatus();
                    break;
                }
                case NeuosSDK.LoginStatus.NOT_LOGGED_IN:{
                    Log.i(TAG, "login: Not Logged In");
                    launchLogin();
                    break;
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }
    // Launches the Neuos Login activity
    private void launchLogin(){
        Intent explicit = getExplicitIntent(new Intent(NeuosSDK.NEUOS_LOGIN));
        if (explicit == null){
            showError("Cannot find Neuos Login activity");
            return;
        }
        loginResultLauncher.launch(explicit);
    }
    // Launches the QA activity
    private void launchQAScreen() {
        Intent explicit = getExplicitIntent(new Intent(NeuosSDK.NEUOS_QA_SCREEN));
        if (explicit == null){
            showError("Cannot find QA activity");
            return;
        }
        explicit.putExtra(NeuosQAProperties.STAND_ALONE , true);
        explicit.putExtra(NeuosQAProperties.TASK_PROPERTIES ,
                new NeuosQAProperties(NeuosQAProperties.Quality.Normal , NeuosQAProperties.INFINITE_TIMEOUT));
        qaResultLauncher.launch(explicit);
    }
    // Binds to Neuos Service
    private boolean doBindService() {
        try {
            // Create an intent based on the class name
            Intent serviceIntent = new Intent(INeuosSdk.class.getName());
            // Use package manager to find intent reciever
            List<ResolveInfo> matches=getPackageManager()
                    .queryIntentServices(serviceIntent, 0);
            if (matches.size() == 0) {
                Log.d(TAG, "Cannot find a matching service!");
                showError("Cannot find a matching service!");
            }
            else if (matches.size() > 1) {
                // This is really just a sanity check
                // and should never occur in a real life scenario
                showError("Found multiple matching services!");
            }
            else {
                // Create an explicit intent
                Intent explicit=new Intent(serviceIntent);
                ServiceInfo svcInfo=matches.get(0).serviceInfo;
                ComponentName cn=new ComponentName(svcInfo.applicationInfo.packageName,
                        svcInfo.name);
                explicit.setComponent(cn);
                // Bind using AUTO_CREATE
                if (bindService(explicit, mConnection,  BIND_AUTO_CREATE)){
                    Log.d(TAG, "Bound to Neuos Service");
                } else {
                    Log.d(TAG, "Failed to bind to Neuos Service");
                    showError("Failed to bind to Neuos Service");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "can't bind to NeuosService, check permission in Manifest");
            showError("can't bind to NeuosService, check permission in Manifest");
        }
        return false;
    }

    private void showError(String message){
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // Use this to launch a calibration session in case your user isn't calibrated
    private void startCalibration() {
        Intent explicit = getExplicitIntent(new Intent(NeuosSDK.NEUOS_CALIBRATION));
        if (explicit != null) {
            startActivity(explicit);
        }
    }

    private Intent getExplicitIntent(Intent activityIntent){
        List<ResolveInfo> matches=getPackageManager()
                .queryIntentActivities(activityIntent , PackageManager.MATCH_ALL);
        // if we couldn't find one, return null
        if (matches.isEmpty()) {
            return null;
        }
        // there really should only be 1 match, so we get the first one.
        Intent explicit=new Intent(activityIntent);
        ActivityInfo info = matches.get(0).activityInfo;
        ComponentName cn = new ComponentName(info.applicationInfo.packageName,
                info.name);
        explicit.setComponent(cn);
        return explicit;
    }

}
