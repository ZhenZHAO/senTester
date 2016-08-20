package com.appzhen.sntester;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import java.util.ArrayList;
import java.util.Date;

/**
 * old
 * edit two functions:  1) updateReaderValue, get new reader from ble service
 *                      2) getBluetoothIsConnectState, get new ble state
 * Note: both of them should be included inside the thread cycle.
 */

/**
 * Edit:    1) configure setBleCorrectConnect() when define the BroadcastReceiver rfduinoReceiver
 *          2) configure setNewDetectedValue(addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA))); when rfduinoReceiver
 * Trick:   previousValue = getNewDetectedValue()-1;
 *          However, if we let ble keep sending the data, then this trick is nonneccessary.
 */

public class DataView extends AppCompatActivity {

    // sampling rate
    private final static int SAMPLYING_DURATION = 100;
    // returning flag
    public final static String EXTRA_RETRURN_TO_MAIN_STATE = "com.appzhen.sntester.RETURN_STATE";
    // Ble state
    private boolean isBleCorrectConnect;

    // readers
    private static  float newDetectedValue = 0f;
    private float previousValue;
    private ArrayList<Float> readerBuffer;

    // graph
    private float maximumSensedValue;
    private float defaultMaxValue;
    private String receiverEmail;
    private LineChart mChart;
    // show visible count on graph
    private  static  final float visibleCount = 10f;
    private Thread threadDraw;
    private boolean isThreadInterrupt = false;

    // file recording
    private  final  static int STATE_RECORDING_NONE = 0;
    private  final  static int STATE_RECORDING_START = 1;
    private  final  static int STATE_RECORDING_STOP= 2;
    private  final  static int STATE_RECORDING_EXPORT = 3;
    private Button startButton;
    private Button stopButton;
    private Button exportButton;
    private int recordingState;
    private int recordingStartIndex;
    private int recordingStopIndex;
    private boolean isRecordFromExport = false;

    // max value setting
    private boolean isNewMaxSetting = false;
    private Button maxValueClrBtn;
    private Button maxValueSetBtn;
    private EditText maxValueInput;
    private static int reNum = 0;

    //time axis switch
    private boolean isTimeAxisOn = false;
    private Switch timeAxisSwt;

    //Ui elements
    TextView resistorReader;
    TextView voltageReader;

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    private GoogleApiClient client;


    private void checkBleState(){
        if ( isBleCorrectConnect() ){
            return;
        }else{
            // firstly, interrupt the thread
            isThreadInterrupt = true;
            // create a dialog by builder
            //create a dialog by alertDialog
            AlertDialog alert = new AlertDialog.Builder(DataView.this).create();
            alert.setIcon(android.R.drawable.ic_dialog_alert);
            alert.setTitle("Bluetooth Link Failed");
            alert.setMessage("Current BLE service is down, Pleas Reconnect!");
            alert.setButton(DialogInterface.BUTTON_POSITIVE, "OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Intent intent = new Intent();
                    intent.setClass(DataView.this,MainActivity.class);
                    intent.putExtra(EXTRA_RETRURN_TO_MAIN_STATE,true);
                    startActivity(intent);
                    DataView.this.finish();
//                    finish();
                }
            });
//            alert.setButton(DialogInterface.BUTTON_NEGATIVE, "", new DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialogInterface, int i) {
//                    finish();
//                }
//            });
            alert.setCancelable(false);
            alert.show();

        }
    }
    /*
    * Blue tooth state
     */
    public boolean isBleCorrectConnect() {
        return isBleCorrectConnect;
    }

    public void setBleCorrectConnect(boolean bleCorrectConnect) {
        isBleCorrectConnect = bleCorrectConnect;
    }

    private boolean getCurrentNewDataState(){
        if (previousValue == newDetectedValue){
            return  false;
        }else{
            return  true;
        }

    }

    public void setNewDetectedValue(float newDetectedValue) {
        this.newDetectedValue = newDetectedValue;
    }
    public static float getNewDetectedValue() {
        return newDetectedValue;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("提示：","画图页Create");
        super.onCreate(savedInstanceState);
        // set layout
        setContentView(R.layout.activity_data_view);
        // get passed view setting
        this.maximumSensedValue = getIntent().getFloatExtra(MainActivity.EXTRA_MAX_SENSED_VALUE,24f);
        defaultMaxValue = maximumSensedValue;
        this.receiverEmail = getIntent().getStringExtra(MainActivity.EXTRA_RECEIVER_EMAIL);
        // state value
        isBleCorrectConnect = true;
        // instantiate ui element -- readers
        resistorReader = (TextView)findViewById(R.id.resistorValue);
        voltageReader = (TextView)findViewById(R.id.voltageValue);
        readerBuffer = new ArrayList<>();

        // instantiate ui element -- file recording
        recordingState = STATE_RECORDING_NONE;
        recordingStartIndex = 0;
        recordingStopIndex = 0;

        // file recording
        startButton = (Button)findViewById(R.id.startBtn);
        stopButton = (Button)findViewById(R.id.stopBtn);
        exportButton = (Button)findViewById(R.id.exportBtn);

        startButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                recordingState = STATE_RECORDING_START;
                recordingStartIndex = readerBuffer.size()>0 ? readerBuffer.size()-1 : 0;
                updateRecordingButton();
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                recordingState = STATE_RECORDING_STOP;
                recordingStopIndex = readerBuffer.size()>0 ? readerBuffer.size()-1 : 0;
                updateRecordingButton();
            }
        });

        exportButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                recordingState = STATE_RECORDING_EXPORT;
                updateRecordingButton();
                // generate data string
                String dataString = generateStrDataFromReaderBuffer(recordingStartIndex,recordingStopIndex);
                // clear the index
                recordingStartIndex = 0; recordingStopIndex = 0;
                // new intent to open the email apps
               // Log.i("Data: ",dataString);
                sendDataThroughEmail(receiverEmail,dataString);

            }
        });

        // max value setting
        maxValueInput = (EditText)findViewById(R.id.sensorMax);
        maxValueInput.setHint(Float.toString(defaultMaxValue));
        maxValueClrBtn = (Button)findViewById(R.id.BtnUsrClr);
        maxValueSetBtn = (Button)findViewById(R.id.BtnUsrSet);

        maxValueClrBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                String inputMaxValue = maxValueInput.getText().toString();
                if (!inputMaxValue.isEmpty() && !inputMaxValue.equals("")){
                        maximumSensedValue = defaultMaxValue;
                        maxValueInput.setText("");
                        customizeLineChart();
                        previousValue = getNewDetectedValue() - 0.1f;

                    isNewMaxSetting = true;
                    maxValueClrBtn.setEnabled(false);
                }
            }
        });

        maxValueSetBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                String inputMaxValue = maxValueInput.getText().toString();
                //Log.i("zzzzz=",inputMaxValue);
                if (!inputMaxValue.isEmpty() && !inputMaxValue.equals("")){
                    float newValue = Float.parseFloat(inputMaxValue);
                    if (newValue != maximumSensedValue){
                        maximumSensedValue = newValue;
                        customizeLineChart();
                        isNewMaxSetting = true;
                        maxValueClrBtn.setEnabled(true);
                        reNum++;
                        maxValueSetBtn.setText("RESET "+reNum);
                        previousValue = getNewDetectedValue()- 0.1f;
                    }
                }else
                {
                    isNewMaxSetting = false;
                    maxValueClrBtn.setEnabled(false);
                }
            }
        });

        //time axis switch
        timeAxisSwt = (Switch)findViewById(R.id.showModeSwt);
        timeAxisSwt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            String tmpStr;
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b){
                    isTimeAxisOn = true;
                    tmpStr = "Time Axis ON: 10Hz";
                    Toast.makeText(getApplication().getBaseContext(),tmpStr,Toast.LENGTH_SHORT).show();
                }else{
                    isTimeAxisOn = false;
                    tmpStr = "Time Axis OFF";
                    Toast.makeText(getApplication().getBaseContext(),tmpStr,Toast.LENGTH_SHORT).show();
                }
            }
        });

        // set new detected value
        setNewDetectedValue(0f);
        previousValue = getNewDetectedValue()-10;
//        previousValue = getNewDetectedValue();
        // create line chart
        LinearLayout graphLayout = (LinearLayout) findViewById(R.id.lineChartView);
        mChart = new LineChart(this);
        // add it to graph layout
        graphLayout.addView(mChart, new AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT,AbsListView.LayoutParams.MATCH_PARENT));
        // customize line chart
        customizeLineChart();

        // create drawing thread
        createDrawingThread();
        // start drawing thread
        threadDraw.start();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    // create drawing thread
    private void createDrawingThread() {
        threadDraw = new Thread(new Runnable() {
            @Override
            public void run() {
                while(!isThreadInterrupt){
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
//                            Log.i("Data Amount:", Integer.toString(readerBuffer.size()));
                            checkBleState(); // check the ble connection
                            addEntry(); // chart is notified of update in addEntry method
                        }
                    });
                    // pause between adds
                    try {
                        Thread.sleep(SAMPLYING_DURATION);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

        });
    }

    // design the line chart details
    private void customizeLineChart() {
        // customize line chart
        mChart.setDescription("Sensor Dynamics");
        mChart.setNoDataText("No data for the moment");

        //enable value highlighting and touch gesture
        mChart.setHighlightPerDragEnabled(true);
        mChart.setHighlightPerTapEnabled(true);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);
        mChart.setDrawGridBackground(false);

        // enable pinch zoom to avoid scaling x and y axis
        mChart.setPinchZoom(true);

        // alternative backgroud color
        mChart.setBackgroundColor(Color.LTGRAY);

        // create data and whether draw point value
        LineData data = new LineData();
        data.setValueTextColor(Color.WHITE);
//        data.setDrawValues(true);

        // add data to line Chart
        mChart.setData(data);

        //get legend Object and customize legend
        Legend legend = mChart.getLegend();
        legend.setForm(Legend.LegendForm.LINE);
        legend.setTextColor(Color.WHITE);

        // get Axis object, and customize them.
        XAxis xl = mChart.getXAxis();
        xl.setTextColor(Color.WHITE);
        xl.setDrawGridLines(false);
        xl.setAvoidFirstLastClipping(true);
        YAxis yl = mChart.getAxisLeft();
        yl.setTextColor(Color.WHITE);
        yl.setAxisMaxValue(maximumSensedValue);
        yl.setAxisMinValue(0f);
        yl.setDrawGridLines(true);
        YAxis yl2 = mChart.getAxisRight();
        yl2.setEnabled(false);
    }

    // add new entry to line chart
    private void addEntry(){
        LineData data = mChart.getLineData();

        if (data!=null){
            LineDataSet lineDataSet =  (LineDataSet) data.getDataSetByIndex(0);
            if (lineDataSet == null){
                // create if null
                lineDataSet = createSet();
                data.addDataSet(lineDataSet);
                Log.i("提示：","重新获取数据");
            }

            //limit the number of visible
            mChart.setVisibleXRangeMaximum(visibleCount);
            // scroll to the last entry
            mChart.moveViewToX(data.getXValCount() - visibleCount -1);

            // add new value
//            updateReaderValue();

            //notify chart whether data have changed
            if (getCurrentNewDataState() || isTimeAxisOn){
                // update reader buffer
                updateReaderBuffer(getNewDetectedValue());
                // update line chart
                data.addXValue("");
                data.addEntry(new Entry(getNewDetectedValue(),lineDataSet.getEntryCount() ),0);
                mChart.notifyDataSetChanged();
                // update reader Ui
                updateReaderUi();
                previousValue = newDetectedValue;
                // test for generate data
//                if (readerBuffer.size() >3){
//
//                    Log.i("输出：",generateStrDataFromReaderBuffer(readerBuffer.size()-3, readerBuffer.size()-1));
//                }

            }

        }
    }

    // create data set
    private LineDataSet createSet(){
        LineDataSet set = new LineDataSet(null,"Detected Sensor Resistance");
        set.setDrawCubic(true);
        set.setCubicIntensity(0.2f);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        set.setColor(ColorTemplate.getHoloBlue());
        set.setLineWidth(2f);
//        set.setCircleSize(4f);
        set.setCircleRadius(6f);
        set.setFillAlpha(65);
        set.setFillColor(ColorTemplate.getHoloBlue());
        set.setHighLightColor(Color.rgb(244,117,117));
        set.setValueTextColor(Color.WHITE);
        set.setValueTextSize(10f);

        return  set;
    }

    // update reader value from ble service
    private void updateReaderValue() {
        //previousValue = getNewDetectedValue();
        //setNewDetectedValue(addData(Intent.getByteArrayExtra(RFduinoService.EXTRA_DATA)););
        //setNewDetectedValue((float) (Math.random() * 20) + 4f);
        //setNewDetectedValue(MainActivity.getNewSensedData());
    }

    private final BroadcastReceiver rfduinoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (RFduinoService.ACTION_CONNECTED.equals(action)) {
                setBleCorrectConnect(true);
            }
            if (RFduinoService.ACTION_DATA_AVAILABLE.equals(action)) {
                previousValue = getNewDetectedValue();
                setBleCorrectConnect(true);
                setNewDetectedValue(addData(intent.getByteArrayExtra(RFduinoService.EXTRA_DATA)));
            }
            if (RFduinoService.ACTION_DISCONNECTED.equals(action)){
                setBleCorrectConnect(false);
            }

        }
    };

    public  float addData(byte[] data) {
        return  DataFormatHelper.bytesToSingleFloat(data) ;
    }

    //update reader buffer --- invoked inside addEntry()
    private void updateReaderBuffer(float newValue){
        readerBuffer.add(newValue);
    }

    // update UI readers -- invoked inside the addEntry()
    private  void updateReaderUi(){
        resistorReader.setText(String.format("%.2f",newDetectedValue));
        ;
        float referenceResistor = 10.0f;
        float referenceVol = 3.3f;
        float volReader = referenceVol * newDetectedValue / (referenceResistor+newDetectedValue);
        voltageReader.setText(String.format("%.2f",volReader));

    }

    // generate export string data
    private String generateStrDataFromReaderBuffer(int beginIndex, int endIndex){

        if (beginIndex>=endIndex){
            Log.i("提示：","Bad String");
            return "";
        }

        StringBuilder generateStr = new StringBuilder();
        float reader;
        for (int i = beginIndex; i<=endIndex;i++){
            reader = readerBuffer.get(i);
            generateStr.append(String.format("%.2f \n",reader));
        }

        return generateStr.toString();
    }

    // update 3 recording-related buttons
    private  void updateRecordingButton(){
        switch (recordingState){
            case STATE_RECORDING_START:
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                exportButton.setEnabled(false);
                exportButton.setText("Export");
                break;
            case STATE_RECORDING_STOP:
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                exportButton.setText("Export");
                if (recordingStopIndex > recordingStartIndex){
                    exportButton.setEnabled(true);
                } else{
                    exportButton.setEnabled(false);
                }
                break;
            case STATE_RECORDING_EXPORT:
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                exportButton.setEnabled(false);
                exportButton.setText("!Exported");
                break;
            case STATE_RECORDING_NONE:
                default:
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    exportButton.setEnabled(false);
                    exportButton.setText("Export");
        }
    }

    private void sendDataThroughEmail(String toEmail, String generatedData){
//        Log.i("提示：","Send Email");
//        String[] TO = {"zhen.now@gmail.com"};
        String [] TO = {toEmail};
        String[] CC = {"zhen.zhao@outlook.com"};

        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setData(Uri.parse("mailto:"));
        emailIntent.setType("text/plain");
//        Log.i("提示！发送给：",TO);
        emailIntent.putExtra(Intent.EXTRA_EMAIL, TO);
        emailIntent.putExtra(Intent.EXTRA_CC,CC);
        String subject = "Record On "+String.format("%tc",new Date()) ;
        emailIntent.putExtra(Intent.EXTRA_SUBJECT,subject);
        StringBuilder emailMessage =  new StringBuilder();
        emailMessage.append("Hi,\n\n\tThis email is sent from App, Sensor Tester. Following is the Sensed Data ");
        emailMessage.append(subject);
        emailMessage.append(".\n\n\nData:\n");
        emailMessage.append(generatedData);
        emailMessage.append("\n\n\nBest regards,\n\nSensor Tester Development Team");
        emailIntent.putExtra(Intent.EXTRA_TEXT,emailMessage.toString());

//        if (emailIntent.resolveActivity(getPackageManager()) != null){
//            startActivity(emailIntent);
//        }

        try {
            startActivity(Intent.createChooser(emailIntent, "Send mail..."));
            isRecordFromExport = true;
            //finish();
            Log.i("提示：", "Finished send email...");

        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(getApplicationContext(),
                    "There is no email client installed.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.i("提示：","画图页 RE-START");
        if (threadDraw == null){
            Log.i("提示：","重新创建线程");
            isThreadInterrupt = false;
            createDrawingThread();
//            try {
//                Thread.sleep(200);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            threadDraw.start();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "DataView Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.appzhen.sntester/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);

        registerReceiver(rfduinoReceiver, RFduinoService.getIntentFilter());

        Log.i("提示：","画图页START");
    }

    @Override
    protected void onResume() {
        super.onResume();
        isThreadInterrupt = false;
        Log.i("提示：","中断继续");
        if (isRecordFromExport){
            this.onStop();
            this.onRestart();
            this.onStart();
            isRecordFromExport = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isThreadInterrupt = true;
        Log.i("提示：","中断暂停");

    }

    @Override
    protected void onStop() {
        super.onStop();

        unregisterReceiver(rfduinoReceiver);

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "DataView Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://com.appzhen.sntester/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.disconnect();
        Log.i("提示：","画图页STOP");
        if(threadDraw!=null){
            Log.i("提示：","删除线程");
            threadDraw.interrupt();
            threadDraw = null;
        }

    }

    @Override
    protected void onDestroy() {

        if (threadDraw != null){
            threadDraw.interrupt();
            threadDraw = null;
        }
        if (readerBuffer!=null){
            readerBuffer.clear();
            readerBuffer = null;
        }
        Log.i("提示：","画图页Destory");
        super.onDestroy();
    }

    // roatating the screen will invoke it.
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (newConfig.orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            // TODO:
        }
        super.onConfigurationChanged(newConfig);
    }

    //1. Activity创建时调用----protected void onCreate(Bundle savedInstanceState)
    //2. Activity创建或者从后台重新回到前台时被调用----protected void onStart()
    //3. Activity从后台重新回到前台时被调用-----protected void onRestart()
    //4. Activity创建或者从被覆盖、后台重新回到前台时被调用----protected void onResume()
    //5. Activity被覆盖到下面或者锁屏时被调用-----protected void onPause()
    //6. 退出当前Activity或者跳转到新Activity时被调用----protected void onStop()
    //7. 退出当前Activity时被调用,调用之后Activity就结束了----protected void onDestroy()
    //8. 当指定了android:configChanges="orientation"后,方向改变时onConfigurationChanged被调用,并且activity不再销毁重建----public void onConfigurationChanged(Configuration newConfig)

}
