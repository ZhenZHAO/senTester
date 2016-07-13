package com.appzhen.sntester;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.UUID;

public class MainActivity extends AppCompatActivity implements BluetoothAdapter.LeScanCallback {

    // view setting -related
    public  final  static  String EXTRA_RECEIVER_EMAIL = "com.appzhen.sntester.RE_EMAIL";
    public  final  static  String EXTRA_MAX_SENSED_VALUE = "com.appzhen.sntester.MAX_VALUE";
    private String receiverEmail = "WTF@163.ca";
    private float maximumSensedValue = 24f;

    // extra control
    private final static byte WAKE_UP_FLAG = (byte) 127;
    private boolean isEverScaning = false;
    private boolean isShowPageJump = false;
    private boolean isLoseBLeFromOtherActivity = false;

    // data storage
    private static float newSensedData;
    private static boolean isBleConnected;
    // State machine
    final private static int STATE_BLUETOOTH_OFF = 1;
    final private static int STATE_DISCONNECTED = 2;
    final private static int STATE_CONNECTING = 3;
    final private static int STATE_CONNECTED = 4;
    private int state;

    private boolean scanStarted;
    private boolean scanning;

    // Ble
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private RFduinoService rfduinoService;

    // Ui element
    private TextView    phoneBluetoothState;
    private Button      enableBtn;
    private TextView    scanBluetoothState;
    private Button      scanBtn;
    private TextView    connectBluetoothState;
    private Button      connectBtn;
    private TextView    deviceInfoText;

    // services
    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
            if (state == BluetoothAdapter.STATE_ON) {
                upgradeState(STATE_DISCONNECTED);
            } else if (state == BluetoothAdapter.STATE_OFF) {
                downgradeState(STATE_BLUETOOTH_OFF);
            }
        }
    }; // bleStateReceiver
    private final BroadcastReceiver scanModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            scanning = (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_NONE);
//            scanStarted &= scanning;
            updateUi();
        }
    }; //scan mode receiver
    private final ServiceConnection rfduinoServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            rfduinoService = ((RFduinoService.LocalBinder) service).getService();
            if (rfduinoService.initialize()) {
                if (rfduinoService.connect(bluetoothDevice.getAddress())) {
                    upgradeState(STATE_CONNECTING);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            rfduinoService = null;
            downgradeState(STATE_DISCONNECTED);
        }
    }; // rfduino connect service

    // broadcast receiver
    private final BroadcastReceiver rfduinoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (RFduinoService.ACTION_CONNECTED.equals(action)) {
                upgradeState(STATE_CONNECTED);
            } else if (RFduinoService.ACTION_DISCONNECTED.equals(action)) {
                downgradeState(STATE_DISCONNECTED);
            } else if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
                upgradeState(STATE_CONNECTED);
                addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA));
            }
        }
    };

    /**
     * Begin with penalized functions
     */
    private void addData(byte[] data) {
        setNewSensedData(DataFormatHelper.bytesToSingleFloat(data));
        Log.i("Sensed Data:",Float.toString(getNewSensedData()));
    }
    private void upgradeState(int newState) {
        if (newState > state) {
            updateState(newState);
        }
    }

    private void downgradeState(int newState) {
        if (newState < state) {
            updateState(newState);
        }
    }

    private void updateState(int newState) {
        state = newState;
        updateUi();
    }

    private void updateUi() {

        //set state
        setIsBleConnected(state == STATE_CONNECTED);

        // Phone Bluetooth
        boolean isOn = state > STATE_BLUETOOTH_OFF;
        enableBtn.setEnabled(!isOn);
        phoneBluetoothState.setText(isOn?"Enabled":"Disabled");

        // BLE Scanning Devices
        scanBtn.setEnabled(isOn);
        if (state != STATE_CONNECTED){
            if (scanStarted && scanning){
                scanBluetoothState.setText("Scanning..");
                isEverScaning = true;
                scanBtn.setText("Stop Scan");
                scanBtn.setEnabled(true);
            }else if (scanStarted) {
                if (isEverScaning){
                    scanBluetoothState.setText("Scanned");
                    scanBtn.setText("ReScan");
                    scanBtn.setEnabled(true);

                }else{
                    scanBluetoothState.setText("Start..");
                    scanBtn.setText("Scan");
                    scanBtn.setEnabled(false);
                }

            } else {
                scanBluetoothState.setText("Wait...");
                scanBtn.setText("Scan");
//            scanBtn.setEnabled(true);
                scanBtn.setEnabled(isOn);
            }
        }else{
            scanBtn.setEnabled(false);
            scanBtn.setText("ReScan");
            scanBluetoothState.setText("Scanned");
        }


        // Connect state
        String connectionText = "Disconnected";
        isShowPageJump = false;
        if (state == STATE_CONNECTING) {
            connectionText = "Connecting...";
        } else if (state == STATE_CONNECTED) {
            isShowPageJump = true;
            connectionText = "Connected";
        }
        connectBluetoothState.setText(connectionText);
        if (isShowPageJump){
            connectBtn.setEnabled(true);
            connectBtn.setText("View Data");
        }else{
            connectBtn.setText("Connect");
            connectBtn.setEnabled(bluetoothDevice != null && state == STATE_DISCONNECTED);
        }


    }

    private void checkAndGoDataView() {
        if (state == STATE_CONNECTED){
            Intent intent = new Intent(getApplicationContext(),DataView.class);
            intent.putExtra(EXTRA_RECEIVER_EMAIL,this.receiverEmail);
            intent.putExtra(EXTRA_MAX_SENSED_VALUE,this.maximumSensedValue);
            startActivity(intent);
        }
    }

    /**
     * Begin with setters and getters
     */
    public static void setNewSensedData(float newSensedData) {
        MainActivity.newSensedData = newSensedData;
    }

    public static void setIsBleConnected(boolean isBleConnected) {
        MainActivity.isBleConnected = isBleConnected;
    }

    public static float getNewSensedData() {
        return newSensedData;
    }

    public static boolean isBleConnected() {
        return isBleConnected;
    }

    /**
     * Begin with normal activity functions
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // initialize variables
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        setNewSensedData(0f);
        setIsBleConnected(false);
        state = STATE_BLUETOOTH_OFF;
        scanStarted = false;
        scanning = false;

        // initialize Ui elements
            // there are 3 sections corresponding to the UI page.
        // UI - Phone Bluetooth
        phoneBluetoothState = (TextView)findViewById(R.id.phoneBleState);
        enableBtn = (Button) findViewById(R.id.enableBtn);
        enableBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                enableBtn.setEnabled(false);
                phoneBluetoothState.setText(bluetoothAdapter.enable()?"Enabling...":"Enable failed");
            }
        });


        // UI - BLE Devices - scanning
        scanBluetoothState = (TextView)findViewById(R.id.scanBleState);
        scanBtn = (Button)findViewById(R.id.scanBtn);
        scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                Log.i("scanning state:",Boolean.toString(scanning));
//                Log.i("scanStarted state:", Boolean.toString(scanStarted));
                if (scanning){
                    if (scanStarted){
                        bluetoothAdapter.stopLeScan(MainActivity.this);
                        scanning = false;
                        updateUi();
                    }else{
                        scanStarted = true;
                    }

                }else {
                    scanStarted = true;
                    deviceInfoText.setText("");
                    bluetoothAdapter.startLeScan(
                            new UUID[]{ RFduinoService.UUID_SERVICE },
                                MainActivity.this);
                    scanning = true;
                    updateUi();
//                    if (scanStarted == false){
//                        scanStarted = true;
//                        bluetoothAdapter.startLeScan(
//                                new UUID[]{ RFduinoService.UUID_SERVICE },
//                                MainActivity.this);
//                        scanning = true;
//                        updateUi();
//                    }else{
//                        scanning = true;
//                    }

                }

//                if (scanStarted == false){
//                    scanStarted = true;
//                    bluetoothAdapter.startLeScan(
//                            new UUID[]{ RFduinoService.UUID_SERVICE },
//                            MainActivity.this);
//                    scanning = true;
//                    updateUi();
//                }else{
//                    if (scanning){
//                        bluetoothAdapter.stopLeScan(MainActivity.this);
//                        scanning = false;
//                        updateUi();
//                    }
//                }


            }
        });

        // UI - Connect State -- // bind service
        connectBluetoothState = (TextView)findViewById(R.id.connectBleState);
        connectBtn = (Button)findViewById(R.id.connectBtn);
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i("State:",Integer.toString(state));
                if (isShowPageJump){
                    isShowPageJump = false;
                    // jump to dataView page after successful connection
                    checkAndGoDataView();

                }else {
                    connectBluetoothState.setText("Connecting...");
                    Intent rfduinoIntent = new Intent(MainActivity.this, RFduinoService.class);
                    bindService(rfduinoIntent, rfduinoServiceConnection, BIND_AUTO_CREATE);
                }

            }
        });

        // UI - Device Info
        deviceInfoText = (TextView)findViewById(R.id.deviceInfo);

        // for testing
//        Button changeBtn = (Button)findViewById(R.id.enableBtn);
//        changeBtn.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Intent intent = new Intent(getApplicationContext(),DataView.class);
//                startActivity(intent);
//            }
//        });


    }

    @Override
    protected void onRestart() {
        super.onRestart();
        scanning = false;
        scanStarted = false;
        isLoseBLeFromOtherActivity = getIntent().getBooleanExtra(DataView.EXTRA_RETRURN_TO_MAIN_STATE,false);
        wakeupBLE();
        // wake up 之后还是connect 还是false, 就必须false
        if (state!= STATE_CONNECTED || isLoseBLeFromOtherActivity == true){
            updateState(STATE_DISCONNECTED);
            scanBtn.callOnClick();
        }

    }

    @Override
    protected void onStart() {
        super.onStart();

        registerReceiver(scanModeReceiver, new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
        registerReceiver(bluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());

        updateState(bluetoothAdapter.isEnabled() ? STATE_DISCONNECTED : STATE_BLUETOOTH_OFF);

    }

    @Override
    protected void onStop() {
        super.onStop();

        bluetoothAdapter.stopLeScan(this);

        unregisterReceiver(scanModeReceiver);
        unregisterReceiver(bluetoothStateReceiver);
        unregisterReceiver(rfduinoReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();


    }

    @Override
    protected void onPause() {
        super.onPause();
        isEverScaning = false;
        isShowPageJump = false;
    }

    @Override
    public void onLeScan(BluetoothDevice device, final int rssi, final byte[] scanRecord) {
        bluetoothAdapter.stopLeScan(this);
        bluetoothDevice = device;

        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                deviceInfoText.setText(
                        BluetoothHelper.getDeviceInfoText(bluetoothDevice, rssi, scanRecord));
                updateUi();
            }
        });
    }

    // roatating the screen will invoke it.
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (newConfig.orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            // TODO:
        }
        super.onConfigurationChanged(newConfig);
    }
    /**
     * Begin with BLE operations
     */
    private void sendDataToBLE(byte[] data){
        rfduinoService.send(data);
    }

    private void wakeupBLE(){
        byte [] data = {WAKE_UP_FLAG};
        try {
            sendDataToBLE(data);
        }catch (Exception e){
            Log.i("SendError:",e.toString());
        }

    }
}

// 代码实现点击某个按钮 sendValueButton.callOnClick();
// 给蓝牙发数据  rfduinoService.send(valueEdit.getData());

