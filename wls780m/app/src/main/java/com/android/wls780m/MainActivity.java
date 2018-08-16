package com.android.wls780m;

import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.wls780.ByteUtils;
import com.android.wls780.DigRawData;
import com.android.wls780.DigValueData;
import com.android.wls780.PacketLostRateMsg;
import com.android.wls780.SerialPort;
import com.android.wls780.TalkTo780;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.linheimx.app.library.adapter.DefaultValueAdapter;
import com.linheimx.app.library.adapter.IValueAdapter;
import com.linheimx.app.library.charts.LineChart;
import com.linheimx.app.library.data.Entry;
import com.linheimx.app.library.data.Line;
import com.linheimx.app.library.data.Lines;
import com.linheimx.app.library.model.Axis;
import com.linheimx.app.library.model.HighLight;
import com.linheimx.app.library.model.XAxis;
import com.linheimx.app.library.model.YAxis;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

import static android.content.DialogInterface.BUTTON_POSITIVE;

public class MainActivity extends AppCompatActivity {
    private Button mSetArgBtn;
    private Button mSetRfBtn;
    private Button mGetArgBtn;
    private Button mReadBtn;
    private Button mTemporaryBtn;
    private Button mChangeBtn;
    private Button mReadRfBtn;
    private Button mPacketLostBtn;
    private Button mPacketLostStopBtn;
    private Button mReadTemp;
    private TalkTo780 talkTo780;
    LineChart _lineChart;
    Line _line;
    private EditText metSensor;
    private EditText metChannel;
    private EditText metPan;

    private Config mConfig;
    private View mViewUnkonw;
    private TextView mtvSensor;
    private TextView mtvChannel;
    private TextView mtvPan;
    private Button mbtnOk;
    private MyHandler myHandler;

    private Config loadConfig()
    {
        Config currentConfig = null;
        File file = new File("/data/data/com.android.wls780m/databases/currentconfig");
        if (!file.exists())
        {
            file.delete();
            currentConfig = new Config(1035, 780.5F, 780);
            return currentConfig;
        }

        String json = null;
        try {
            json = new RandomAccessFile(file, "rwd").readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return (Config)new Gson().fromJson(json, Config.class);
    }
    private void saveConfig()
    {
        String str = new GsonBuilder().registerTypeAdapter(Config.class, new ConfigTypeAdapter()).create().toJson(mConfig);
        Log.i("osc", "json:" + str);
        File file = new File("/data/data/com.android.wls780m/databases/currentconfig");
        if (file.exists()) {
            file.delete();
        }

        try
        {
            file.createNewFile();
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rwd");
            randomAccessFile.writeBytes(str);
            randomAccessFile.close();
        }
        catch (FileNotFoundException localFileNotFoundException)
        {
            localFileNotFoundException.printStackTrace();
            return;
        }
        catch (IOException localIOException)
        {
            localIOException.printStackTrace();
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        File file = new File("/data/data/com.android.wls780m/databases");
        if (!file.mkdirs()) {
            file.mkdirs();
        }

        metSensor = findViewById(R.id.et_sensor);
        metChannel = findViewById(R.id.et_channel);
        metPan = findViewById(R.id.et_pan);
        mConfig = loadConfig();

        metSensor.setText(String.valueOf(mConfig.getSensor()));
        metChannel.setText(String.valueOf(mConfig.getChannel()));
        metPan.setText(String.valueOf(mConfig.getPan()));

        talkTo780 = new TalkTo780();
        talkTo780.create();

        mSetArgBtn = findViewById(R.id.button);
        mSetArgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int[] info = new int[16];

                info[0] = 0;
                info[1] = 1;
                info[2] = 1000;
                info[3] = 512;
                talkTo780.setSensorParameter(mConfig.getSensor(), 5, info);
            }
        });
        mSetRfBtn = findViewById(R.id.button2);
        mSetRfBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //talkTo780.setGateRfInfo(788, 170, 0x05);
                mConfig.setChannel(Float.parseFloat(metChannel.getText().toString()));
                mConfig.setPan(Integer.parseInt(metPan.getText().toString()));
                talkTo780.setGateRfInfo(mConfig.getChannel(), mConfig.getPan(), 5);
            }
        });
        mGetArgBtn = findViewById(R.id.button3);
        mGetArgBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int[] info = new int[24];
                talkTo780.getSensorInfo(mConfig.getSensor(), info);
            }
        });
        mReadBtn = findViewById(R.id.button4);
        mReadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                talkTo780.requireSensorData(mConfig.getSensor());
            }
        });

        mTemporaryBtn = findViewById(R.id.button5);
        mTemporaryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int[] info = new int[4];
                info[0] = 102;
                info[1] = 1;
                info[2] = 1000;
                info[3] = 512;
                talkTo780.requireSensorTemporaryData(mConfig.getSensor(), info);
            }
        });

        mChangeBtn = findViewById(R.id.button6);
        mChangeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //talkTo780.setSensorRfInfo(mSensor, 788, 170, 5, 0);
                mConfig.setSensor(Integer.parseInt(metSensor.getText().toString()));
                mConfig.setChannel(Float.parseFloat(metChannel.getText().toString()));
                mConfig.setPan(Integer.parseInt(metPan.getText().toString()));
                talkTo780.setSensorRfInfo(mConfig.getSensor(), mConfig.getChannel(), mConfig.getPan(), 0, 0);
            }
        });

        mReadRfBtn = findViewById(R.id.button7);
        mReadRfBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int[] info = new int[8];
                //talkTo780.inquireUnknowSensorRfInfo(12000, info);
                if(talkTo780.inquireUnknowSensorRfInfo2(12000, info)){
                    metSensor.setText(String.valueOf(info[0]));
                    metChannel.setText(String.valueOf(Float.intBitsToFloat(info[1])));
                    metPan.setText(String.valueOf(info[2]));
                    mConfig.setSensor(info[0]);
                    mConfig.setChannel(Float.intBitsToFloat(info[1]));
                    mConfig.setPan(info[2]);
                    talkTo780.setGateRfInfo(mConfig.getChannel(), mConfig.getPan(), 0);
                    saveConfig();

                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    final AlertDialog dialog = builder.create();

                    View ViewUnkonw = LayoutInflater.from(MainActivity.this).inflate(R.layout.unknow_sensor, null);
                    final TextView tvSensor = ViewUnkonw.findViewById(R.id.tv_sensor);
                    final TextView tvChannel = ViewUnkonw.findViewById(R.id.tv_channel);
                    final TextView tvPan = ViewUnkonw.findViewById(R.id.tv_pan);
                    final Button btnOk = ViewUnkonw.findViewById(R.id.ok);
                    btnOk.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dialog.dismiss();
                        }
                    });
                    tvSensor.setText(String.valueOf(info[0]));
                    tvChannel.setText(String.valueOf(Float.intBitsToFloat(info[1])));
                    tvPan.setText(String.valueOf(info[2]));

                    final TextView tv = new TextView(MainActivity.this);
                    tv.setText(R.string.str_unknow);
                    tv.setGravity(Gravity.CENTER);

                    dialog.setView(ViewUnkonw);
                    dialog.setCustomTitle(tv);
                    dialog.setCanceledOnTouchOutside(true);
                    dialog.setCancelable(false);
                    dialog.show();
                }
            }
        });

        mPacketLostBtn = findViewById(R.id.button8);
        mPacketLostBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                talkTo780.packetLossRateTest(mConfig.getSensor(), 64, 10000);
            }
        });

        mPacketLostStopBtn = findViewById(R.id.button9);
        mPacketLostStopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int[] rf_info = new int[4];
                talkTo780.getGateInfo(rf_info);
            }
        });

        mReadTemp = findViewById(R.id.button11);
        mReadTemp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int[] info = new int[4];
                info[0] = 116;
                info[1] = 0;
                info[2] = 0;
                info[3] = 0;
                if(talkTo780.requireSensorTemporaryData(mConfig.getSensor(), info)) {
                    Toast.makeText(MainActivity.this,"require Sensor Temporary Data successful",Toast.LENGTH_SHORT).show();
                }
            }
        });
        myHandler = new MyHandler();

        _lineChart = (LineChart) findViewById(R.id.chart);
        _lineChart.set_paddingLeft(0);
        _lineChart.set_paddingRight(0);
        setChartData(_lineChart);

        talkTo780.setGateRfInfo(mConfig.getChannel(), mConfig.getPan(), 0);
    }

    @Override
    protected void onDestroy() {
        talkTo780.destroy();
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }
    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(DigRawData event) {
        Log.i("wls", "Got raw data event");
        float[] data = event.getRawShortData();

        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            double x = i;
            double y = data[i];
            //Log.i("wls", "x: " + i + " y: " + data[i]);
            list.add(new Entry(x, y));
        }

        Lines lines = _lineChart.getlines();
        Line line = lines.getLines().get(0);

        line.setEntries(list);
        //_lineChart.setYMax_Min(-1, 1);
        _lineChart.notifyDataChanged();
        _lineChart.invalidate();
    }

    class MyHandler extends Handler {
        public MyHandler() {
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    Display display = getWindowManager().getDefaultDisplay();
                    Toast toast = Toast.makeText(MainActivity.this, msg.obj+" ℃", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.TOP, 0, display.getHeight()/4);

                    toast.show();
                    break;
            }

        }
    }
    @Subscribe
    public void onMessageEvent(DigValueData event) {
        Log.i("wls", "Got value data event:" + event.getTemperatureValue());
        Message msg = myHandler.obtainMessage();
        msg.obj = Float.toString(event.getTemperatureValue());
        msg.what = 1;
        myHandler.sendMessage(msg);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(PacketLostRateMsg event){
        //Log.i("wls", "Got packet lost rate event");
        Log.i("wls", " rs:"+event.getPacketLostRate()+" ss:"+event.getSendSignalStrength() + " rs:"+ event.getRecvSignalStrength());
    }

    private void setChartData(LineChart lineChart) {

        // 高亮
        HighLight highLight = lineChart.get_HighLight1();
        highLight.setEnable(true);// 启用高亮显示  默认为启用状态
        highLight.setxValueAdapter(new IValueAdapter() {
            @Override
            public String value2String(double value) {
                return "X:" + value;
            }
        });
        highLight.setyValueAdapter(new IValueAdapter() {
            @Override
            public String value2String(double value) {
                return "Y:" + value;
            }
        });

        // x,y轴上的单位
        XAxis xAxis = lineChart.get_XAxis();
        xAxis.set_unit("ms");
        xAxis.set_ValueAdapter(new DefaultValueAdapter(0));

        YAxis yAxis = lineChart.get_YAxis();
        yAxis.set_unit("m/s^2");
        yAxis.set_ValueAdapter(new DefaultValueAdapter(2));// 默认精度到小数点后2位,现在修改为0位精度

        // 数据
        _line = new Line();
        _line.setDrawCircle(false);
        _line.setLineColor(Color.BLUE);
        Lines lines = new Lines();
        lines.addLine(_line);

        lineChart.setLines(lines);
    }
}
