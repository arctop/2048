package io.neuos.a2048;

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

//import com.google.android.gms.auth.api.Auth;
//import com.google.android.gms.auth.api.signin.GoogleSignIn;
//import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
//import com.google.android.gms.auth.api.signin.GoogleSignInClient;
//import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
//import com.google.android.gms.auth.api.signin.GoogleSignInResult;
//import com.google.android.gms.drive.Drive;
//import com.google.android.gms.games.Games;
//import com.google.android.gms.games.GamesClient;
//import com.google.android.gms.tasks.OnCompleteListener;
//import com.google.android.gms.tasks.Task;

import java.util.List;

//import io.neuos.a2048.snapshot.SnapshotData;
//import io.neuos.a2048.snapshot.SnapshotManager;

import io.neuos.INeuosSdk;
import io.neuos.INeuosSdkListener;
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

    private static final String NO_LOGIN_PROMPT = "no_login_prompt";

    private static final int RC_SIGN_IN = 9001;

    private boolean firstLoginAttempt = false;

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
        deviceListReceiver = new MuseConnectionBroadcastReceiver();
        registerReceiver(deviceListReceiver,
                new IntentFilter(NeuosSDK.IO_NEUOS_DEVICE_PAIRING_ACTION));
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
//        signInToGoogle();
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
            mService.finishSession();
            unregisterReceiver(deviceListReceiver);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    /**
     * Signs into Google. Used for cloud saves.
     */
//    private void signInToGoogle() {
//        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
//        final boolean noLoginPrompt = settings.getBoolean(NO_LOGIN_PROMPT, false);
//        GoogleSignInOptions signInOptions =
//                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
//                        .requestScopes(Drive.SCOPE_APPFOLDER)
//                        .build();
//        final GoogleSignInClient signInClient = GoogleSignIn.getClient(this, signInOptions);
//        signInClient.silentSignIn().addOnCompleteListener(this, new OnCompleteListener<GoogleSignInAccount>() {
//            @Override
//            public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
//                if (!task.isSuccessful()) {
//                    if (!firstLoginAttempt && !noLoginPrompt) {
//                        firstLoginAttempt = true;
//                        startActivityForResult(signInClient.getSignInIntent(), RC_SIGN_IN);
//                    }
//                } else {
//                    System.out.println("Successfully logged into Google.");
//
//                    if (task.getResult() != null) {
//                        GamesClient client = Games.getGamesClient(MainActivity.this, task.getResult());
//                        client.setViewForPopups(view);
//                    }
//
//                    SnapshotManager.loadSnapshot(MainActivity.this, new SnapshotManager.Callback() {
//                        @Override
//                        public void run(@NonNull SnapshotData data) {
//                            view.game.handleSnapshot(data);
//                        }
//                    });
//                }
//            }
//        });
//    }

    /*@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
//        if (requestCode != RC_SIGN_IN) {
//            return;
//        }
//
//        GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
//        if (result == null) {
//            return;
//        }
//
//        if (!result.isSuccess()) {
//            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
//            SharedPreferences.Editor editor = settings.edit();
//            editor.putBoolean(NO_LOGIN_PROMPT, true);
//            editor.apply();
//            System.out.println(result.getStatus());
//        } else {
//            if (result.getSignInAccount() != null) {
//                GamesClient client = Games.getGamesClient(MainActivity.this, result.getSignInAccount());
//                client.setViewForPopups(view);
//            }
//            SnapshotManager.loadSnapshot(MainActivity.this, new SnapshotManager.Callback() {
//                @Override
//                public void run(@NonNull SnapshotData data) {
//                    view.game.handleSnapshot(data);
//                }
//            });
//        }
    }*/

    final String TAG = "Neuos SDK";
    final String API_KEY = "aaaa";
    public static final String PREDICTION_NAME = "zone";
    private INeuosSdk mService;
    private Runnable mPostConnection;
    private MuseConnectionBroadcastReceiver deviceListReceiver;
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
    private final ActivityResultLauncher<String> requestPermissionLauncher =
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
                if ( response != NeuosSDK.ErrorCodes.SUCCESS){
                    Log.i(TAG, "initialize: failed with code " + response);
                }
                else{
                    response = mService.registerSDKCallback(mCallback);
                    checkNeuosLoginStatus();
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
    public void checkCalibrationStatus(){
        try {
            int calibrationStatus = mService.checkUserCalibrationStatus();
            Log.i(TAG, "onUserCalibrationStatus: " + calibrationStatus);
            if (calibrationStatus == NeuosSDK.UserCalibrationStatus.NEEDS_CALIBRATION){
                connectToMuse();
                //TODO:
                //mPostConnection = () -> startCalibration(null);
            }
            else if (calibrationStatus == NeuosSDK.UserCalibrationStatus.MODELS_AVAILABLE){
                connectToMuse();
                //TODO: add QA here as well.
                mPostConnection = () -> startSession();
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }
    public void startSession() {
        try {
            //isSessionInProgress = true;
            int response = mService.startPredictionSession(PREDICTION_NAME);
            if ( response != NeuosSDK.ErrorCodes.SUCCESS){
                Log.i(TAG, "startSession: failed with code " + response);
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }
    public void connectToMuse(){
        Intent activityIntent = new Intent("io.neuos.PairDevice");
        List<ResolveInfo> matches=getPackageManager()
                .queryIntentActivities(activityIntent , PackageManager.MATCH_ALL);
        Intent explicit=new Intent(activityIntent);
        ActivityInfo info = matches.get(0).activityInfo;
        ComponentName cn = new ComponentName(info.applicationInfo.packageName,
                info.name);
        explicit.setComponent(cn);
        startActivity(explicit);
    }

    public void checkNeuosLoginStatus() {
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
                    launchHome();
                    break;
                }
            }
        } catch (RemoteException e) {
            Log.e(TAG, e.getLocalizedMessage());
        }
    }

    private void launchHome(){
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
        //TODO: When this returns, call check calibration
    }

    boolean doBindService() {
        try {
            // Create an intent based on the class name
            Intent serviceIntent = new Intent(INeuosSdk.class.getName());
            // Use package manager to find intent reciever
            List<ResolveInfo> matches=getPackageManager()
                    .queryIntentServices(serviceIntent, 0);
            if (matches.size() == 0) {
                Log.d(TAG, "Cannot find a matching service!");
                Toast.makeText(this, "Cannot find a matching service!",
                        Toast.LENGTH_LONG).show();
            }
            else if (matches.size() > 1) {
                // This is really just a sanity check
                // and should never occur in a real life scenario
                Log.d(TAG, "Found multiple matching services!");
                Toast.makeText(this, "Found multiple matching services!",
                        Toast.LENGTH_LONG).show();
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
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "can't bind to NeuosService, check permission in Manifest");
        }
        return false;
    }

}
