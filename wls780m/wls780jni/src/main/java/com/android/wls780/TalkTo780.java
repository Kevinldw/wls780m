package com.android.wls780;

import android.util.Log;

//import org.greenrobot.eventbus.EventBus;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by ldw on 2018/2/2.
 */

public class TalkTo780 {
    private SerialPort mSerialPort = null;
    protected OutputStream mOutputStream;
    private InputStream mInputStream;
    private ReadThread mReadThread;
    private Timer mTimer = null;
    private TimerTask mTimerTask = null;
    private byte[] mBuffer = new byte[131072];
    private byte[] mRsp = new byte[1024];
    private int mBytesInBuffer = 0;
    private Lock glock = new ReentrantLock();
    private Condition gnoRsp = glock.newCondition();
    private Lock slock= new ReentrantLock();
    private Condition snoRsp = slock.newCondition();
    private final Object objBuf = new Object();
    private DigRawData digRawData;
    private int sample_type;
    private int sample_rate;
    private int sample_nums;

    public TalkTo780() {

    }

    public String GetLibVersion(){
        return "1.0.0.20180530";
    }

    //private byte len = 0;
    //private byte type = 0;
    /*private void watch(){
        if(mTimer != null){
            mTimer.cancel();
            mTimer = null;
        }
        if(mTimerTask != null){
            mTimerTask.cancel();
            mTimerTask = null;
        }
        mTimer = new Timer();
        mTimerTask = new TimerTask() {
            @Override
            public void run() {
                //Log.i("wls", "TO: " + mBytesInBuffer);
                //Log.i("wls", ":: " + ByteUtils.byteToString(mBuffer, 48));

                while (mBytesInBuffer >= 16) {
                    int head0 = mBuffer[0];
                    int head1 = mBuffer[1];
                    int head2 = mBuffer[2];
                    int head3 = mBuffer[3];
                    if((head0 != 0x55) && (head0 != 0xaa) && (head0 != 0x99) && (head0 != 0x66)) {
                        Log.i("wls", "erh");
                        synchronized (objBuf) {
                            mBytesInBuffer  -= 1;
                            ByteUtils.copy(mBuffer, mBuffer, 0, 1, mBytesInBuffer);
                        }
                    }else{
                        byte type = (byte)(mBuffer[12] & 0xFF);
                        byte len = (byte)(mBuffer[8] & 0xFF);
                        mRsp[0] = len;
                        mRsp[1] = type;
                        //Log.i("wls", "type:" + ByteUtils.byteToString(type) + "  len:" + len);
                        if((type== (byte)0xAC) || //// default command respond
                           (type == 0x2A) ||  /// set gate rf info
                           (type == 0x30) ||  /// gate timestamp require
                           (type == 0x27) ||  /// set timestamp resopnd
                           (type == 0x29) ||  /// get gate info resopnd
                           (type == 0x2E)) {  /// test resopnd
                            Log.i("wls", "Gate respond:" + ByteUtils.byteToString(type));
                            mRsp[2] = 10;

                            if(len > 0) {
                                ByteUtils.copy(mRsp, mBuffer, mRsp[2], 16, len);
                            }
                            glock.lock();
                            try {
                                gnoRsp.signalAll();
                            }finally {
                                glock.unlock();
                            }
                            synchronized (objBuf){
                                //Log.i("wls", "del: " + len);
                                mBytesInBuffer -= 16 + len;
                                if(mBytesInBuffer > 0)
                                    ByteUtils.copy(mBuffer, mBuffer, 0, 16+len, mBytesInBuffer);
                            }
                            if(type == 0x30){
                                setGateTime();
                            }
                        }
                        else if((type == 0x28) ||  // require info
                                 (type == 0x26) ||  /// inquire unknow sensor respond
                                 (type == 0x22) ||  // set args group
                                 (type == 0x2C) ||  // require temporary sample
                                 (type == (byte)0xEE) ||  // Test
                                 (type == 0x2B)){   // sensor info respond
                            Log.i("wls", "Sensor respond:" + ByteUtils.byteToString(type));
                            // 55AA99668000000002580100220039001FC3
                            mRsp[2] = 10;
                            mRsp[3] = mBuffer[4]; // sensor id l m lh h
                            mRsp[4] = mBuffer[5];
                            mRsp[5] = mBuffer[6];
                            mRsp[6] = mBuffer[7];
                            mRsp[7] = mBuffer[9]; // battery
                            mRsp[8] = mBuffer[14];// s-signal
                            mRsp[9] = mBuffer[15];// g-signal

                            if(len > 0) {
                                if(type == (byte)0xEE){
                                    PacketLostRateMsg plr = new PacketLostRateMsg(
                                            ByteUtils.byteToInt(mBuffer, 4),
                                            ByteUtils.byteToInt(mBuffer, 16),
                                            ByteUtils.byteToInt(mBuffer, 20),
                                            mBuffer[24], mBuffer[25]);
                                    //EventBus.getDefault().post(plr);
                                }else {
                                    ByteUtils.copy(mRsp, mBuffer, mRsp[2], 16, len);
                                }
                            }
                            slock.lock();
                            try {
                                snoRsp.signalAll();
                            }finally {
                                slock.unlock();
                            }
                            synchronized (objBuf){
                                Log.i("wls", "to " + mBytesInBuffer + " del: " + len);
                                mBytesInBuffer -= 16 + len;
                                if(mBytesInBuffer > 0) {
                                    ByteUtils.copy(mBuffer, mBuffer, 0, 16 + len, mBytesInBuffer);
                                    //System.arraycopy(mBuffer, 16+len, mBuffer, 0, mBytesInBuffer);
                                }
                            }
                        }
                        else if(type == 0x33){
                            int no = (mBuffer[10] & 0xFF) | ((mBuffer[11] & 0xFF) << 8);
                            int end = mBuffer[13] & 0xFF;
                            int sensorid = ByteUtils.byteToInt(mBuffer);
                            Log.i("wls", "" + no);
                            if(no == 1){
                                int sampleTime = ByteUtils.byteToInt(mBuffer, 16);
                                int sampleType = mBuffer[24];
                                int sampleGroup = mBuffer[25]; // should 5 but 0
                                int sampleCoeff = ByteUtils.byteToInt(mBuffer, 26);
                                int sampleVersion = ByteUtils.byteToInt(mBuffer, 30);
                                int sampleCrc = ByteUtils.byteToInt(mBuffer, 34);
                                Log.i("wls", "Tim:"+sampleTime);
                                Log.i("wls", "Typ:"+sampleType);
                                Log.i("wls", "Gro:"+sampleGroup);
                                Log.i("wls", "Coe:"+sampleCoeff);
                                Log.i("wls", "Ver:"+sampleVersion);
                                Log.i("wls", "Crc:"+sampleCrc);
                                if(sampleGroup == 5)
                                    digRawData = new DigRawData(sensorid, sampleType, sample_rate, sample_nums, sampleCoeff);
                                else
                                    digRawData = null;
                            }else {
                                if (digRawData != null) {
                                    if(digRawData.putRawData(mBuffer)){
                                        // send message to ui
                                        //EventBus.getDefault().post(digRawData);
                                    }
                                }
                            }
                            synchronized (objBuf) {
                                mBytesInBuffer -= 16 + len;
                                ByteUtils.copy(mBuffer, mBuffer, 0, 16 + len, mBytesInBuffer);
                            }
                        }
                        else if((type == 0x31) || (type == 0x32)){

                        }
                        else {
                            Log.i("wls", "unknow type: " + type);
                            synchronized (objBuf) {
                                mBytesInBuffer -= 16 + len;
                                ByteUtils.copy(mBuffer, mBuffer, 0, 16 + len, mBytesInBuffer);
                            }
                        }

                        Log.i("wls", "left: " + mBytesInBuffer);
                    }
                }
            }
        };
        mTimer.schedule(mTimerTask, 100);
    }*/
    private void watch() {
        if(this.mTimer != null) {
            this.mTimer.cancel();
            this.mTimer = null;
        }

        if(this.mTimerTask != null) {
            this.mTimerTask.cancel();
            this.mTimerTask = null;
        }

        this.mTimer = new Timer();
        this.mTimerTask = new TimerTask() {
            public void run() {
                while(mBytesInBuffer >= 16) {
                    int head0 = mBuffer[0];
                    int head1 = mBuffer[1];
                    int head2 = mBuffer[2];
                    int head3 = mBuffer[3];
                    if(head0 != 85 && head0 != 170 && head0 != 153 && head0 != 102) {
                        Log.i("wls", "erh");
                        synchronized(objBuf) {
                            mBytesInBuffer = mBytesInBuffer - 1;
                            ByteUtils.copy(mBuffer, mBuffer, 0, 1, mBytesInBuffer);
                        }
                    } else {
                        byte type = (byte)(mBuffer[12] & 0xFF);
                        byte len = (byte)(mBuffer[8] & 0xFF);
                        mRsp[0] = len;
                        mRsp[1] = type;
                        if(type != (byte)0xAC && type != 0x2A && type != 0x30 && type != 0x27 && type != 0x29 && type != 0x2E) {
                            if(type != 0x28 && type != 0x26 && type != 0x22 && type != 0x2C && type != (byte)0xEE && type != 0x2B) {
                                int sampleTime;
                                int sampleVpp;
                                int sampleRms;
                                int no;
                                int sampleTmp;
                                int sampleVersion;
                                if(type == 0x33) {
                                    no = mBuffer[10] & 0xFF | (mBuffer[11] & 0xFF) << 8;
                                    sampleTime = mBuffer[13] & 0xFF;
                                    sampleTmp = ByteUtils.byteToInt(mBuffer);
                                    if(no == 1) {
                                        sampleVersion = ByteUtils.byteToInt(mBuffer, 16);
                                        int sampleType = mBuffer[24];
                                        int sampleGroupx = mBuffer[25];
                                        sampleVpp = ByteUtils.byteToInt(mBuffer, 26);
                                        sampleRms = ByteUtils.byteToInt(mBuffer, 30);
                                        int sampleCrc = ByteUtils.byteToInt(mBuffer, 34);
                                        Log.i("wls", "Tim:" + sampleVersion);
                                        Log.i("wls", "Typ:" + sampleType);
                                        Log.i("wls", "Gro:" + sampleGroupx);
                                        Log.i("wls", "Coe:" + sampleVpp);
                                        Log.i("wls", "Ver:" + sampleRms);
                                        Log.i("wls", "Crc:" + sampleCrc);
                                        if(sampleGroupx == 5) {
                                            digRawData = new DigRawData(sampleTmp, sampleType, sample_rate, sample_nums, sampleVpp);
                                        } else {
                                            digRawData = null;
                                        }
                                    } else if(digRawData != null && digRawData.putRawData(mBuffer)) {
                                        EventBus.getDefault().post(digRawData);
                                    }

                                    synchronized(objBuf) {
                                        mBytesInBuffer = mBytesInBuffer - (16 + len);
                                        ByteUtils.copy(mBuffer, mBuffer, 0, 16 + len, mBytesInBuffer);
                                    }
                                } else {
                                    int sampleCoeff;
                                    if(type == 0x31) {
                                        no = mBuffer[10] & 0xFF | (mBuffer[11] & 0xFF) << 8;
                                        if(no == 1) {
                                            sampleTime = ByteUtils.byteToInt(mBuffer, 16);
                                            sampleTmp = mBuffer[24] << 24 >> 24;
                                            sampleVersion = ByteUtils.byteToInt(mBuffer, 25);
                                            Log.i("wls", "Tim:" + sampleTime);
                                            Log.i("wls", "Tmp:" + sampleTmp);
                                            Log.i("wls", "Ver:" + Integer.toHexString(sampleVersion));
                                            sampleCoeff = ByteUtils.byteToInt(mBuffer, 4);
                                            DigValueData digValueData = new DigValueData(sampleCoeff, 49, (float)sampleTmp);
                                            EventBus.getDefault().post(digValueData);
                                        }

                                        synchronized(objBuf) {
                                            mBytesInBuffer = mBytesInBuffer - (16 + len);
                                            ByteUtils.copy(mBuffer, mBuffer, 0, 16 + len, mBytesInBuffer);
                                        }
                                    } else if(type == 0x32) {
                                        no = mBuffer[10] & 255 | (mBuffer[11] & 255) << 8;
                                        if(no == 1) {
                                            sampleTime = ByteUtils.byteToInt(mBuffer, 16);
                                            int sampleTypex = mBuffer[24];
                                            int sampleGroup = mBuffer[25];
                                            sampleCoeff = ByteUtils.byteToInt(mBuffer, 26);
                                            int sampleVp = ByteUtils.byteToInt(mBuffer, 30);
                                            sampleVpp = ByteUtils.byteToInt(mBuffer, 34);
                                            sampleRms = ByteUtils.byteToInt(mBuffer, 38);
                                            float sampleCamber = (float)ByteUtils.byteToInt(mBuffer, 42);
                                            int sampleVersionx = ByteUtils.byteToInt(mBuffer, 46);
                                            Log.i("wls", "Tim:" + sampleTime);
                                            Log.i("wls", "Typ:" + sampleTypex);
                                            Log.i("wls", "Grp:" + sampleGroup);
                                            Log.i("wls", "Coe:" + Float.intBitsToFloat(sampleCoeff));
                                            Log.i("wls", " Vp:" + Float.intBitsToFloat(sampleVp));
                                            Log.i("wls", "Vpp:" + sampleVpp);
                                            Log.i("wls", "Rms:" + sampleRms);
                                            Log.i("wls", "Cam:" + sampleCamber);
                                            Log.i("wls", "Ver:" + Integer.toHexString(sampleVersionx));
                                            int sensorid = ByteUtils.byteToInt(mBuffer, 4);
                                            DigValueData digValueDatax = null;
                                            if(sampleTypex == 102) {
                                                digValueDatax = new DigValueData(sensorid, 50, Float.intBitsToFloat(sampleCoeff) * (float)sampleVp);
                                            } else if(sampleTypex == 101) {
                                                digValueDatax = new DigValueData(sensorid, 50, Float.intBitsToFloat(sampleCoeff) * (float)sampleRms);
                                            } else if(sampleTypex == 103) {
                                                digValueDatax = new DigValueData(sensorid, 50, Float.intBitsToFloat(sampleCoeff) * (float)sampleVpp);
                                            }

                                            EventBus.getDefault().post(digValueDatax);
                                        }

                                        synchronized(objBuf) {
                                            mBytesInBuffer = mBytesInBuffer - (16 + len);
                                            ByteUtils.copy(mBuffer, mBuffer, 0, 16 + len, mBytesInBuffer);
                                        }
                                    } else {
                                        Log.i("wls", "unknow type: " + type);
                                        synchronized(objBuf) {
                                            mBytesInBuffer = mBytesInBuffer - (16 + len);
                                            ByteUtils.copy(mBuffer, mBuffer, 0, 16 + len, mBytesInBuffer);
                                        }
                                    }
                                }
                            } else {
                                Log.i("wls", "Sensor respond:" + ByteUtils.byteToString(type));
                                mRsp[2] = 10;
                                mRsp[3] = mBuffer[4];
                                mRsp[4] = mBuffer[5];
                                mRsp[5] = mBuffer[6];
                                mRsp[6] = mBuffer[7];
                                mRsp[7] = mBuffer[9];
                                mRsp[8] = mBuffer[14];
                                mRsp[9] = mBuffer[15];
                                if(len > 0) {
                                    if(type == (byte)0xEE) {
                                        PacketLostRateMsg plr = new PacketLostRateMsg(ByteUtils.byteToInt(mBuffer, 4), ByteUtils.byteToInt(mBuffer, 16), ByteUtils.byteToInt(mBuffer, 20), mBuffer[24], mBuffer[25]);
                                        EventBus.getDefault().post(plr);
                                    } else {
                                        ByteUtils.copy(mRsp, mBuffer, mRsp[2], 16, len);
                                    }
                                }

                                slock.lock();

                                try {
                                    snoRsp.signalAll();
                                } finally {
                                    slock.unlock();
                                }

                                synchronized(objBuf) {
                                    Log.i("wls", "to " + mBytesInBuffer + " del: " + len);
                                    mBytesInBuffer = mBytesInBuffer - (16 + len);
                                    if(mBytesInBuffer > 0) {
                                        ByteUtils.copy(mBuffer, mBuffer, 0, 16 + len, mBytesInBuffer);
                                    }
                                }
                            }
                        } else {
                            Log.i("wls", "Gate respond:" + ByteUtils.byteToString(type));
                            mRsp[2] = 10;
                            if(len > 0) {
                                ByteUtils.copy(mRsp, mBuffer, mRsp[2], 16, len);
                            }

                            glock.lock();

                            try {
                                gnoRsp.signalAll();
                            } finally {
                                glock.unlock();
                            }

                            synchronized(objBuf) {
                                mBytesInBuffer = mBytesInBuffer - (16 + len);
                                if(mBytesInBuffer > 0) {
                                    ByteUtils.copy(mBuffer, mBuffer, 0, 16 + len, mBytesInBuffer);
                                }
                            }

                            if(type == 0x30) {
                                setGateTime();
                            }
                        }
                    }
                }

            }
        };
        mTimer.schedule(mTimerTask, 1000L);
    }

    private class ReadThread extends Thread {
        private ReadThread() {
        }

        public void run() {
            super.run();

            while(!isInterrupted()) {
                try {
                    byte[] buffer = new byte[131072];
                    int size;
                    if(mInputStream == null) {
                        Log.e("wls", "Input stream is null");
                        return;
                    }

                    size = mInputStream.read(buffer);
                    if(size > 0) {
                        synchronized(objBuf) {
                            ByteUtils.copy(mBuffer, buffer, mBytesInBuffer, 0, size);
                            mBytesInBuffer = mBytesInBuffer + size;
                        }
                    }

                    watch();
                } catch (IOException var6) {
                    var6.printStackTrace();
                    return;
                }
            }

        }
    }

    //
    // 建立资源
    //
    public void create(){
        if(mSerialPort == null) {
            try {
                mBytesInBuffer = 0;
                mSerialPort = new SerialPort(new File("/dev/ttymxc0"), 2000000, 0);
                mOutputStream = mSerialPort.getOutputStream();
                mInputStream = mSerialPort.getInputStream();
                //
                if(mInputStream.available() > 0){
                    mInputStream.read();
                }
                mReadThread = new ReadThread();
                mReadThread.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //
    // 摧毁资源
    //
    public void destroy(){
        if (mReadThread != null) {
            mReadThread.interrupt();
        }
        if(mSerialPort != null){
            mSerialPort.close();
            mSerialPort = null;
        }
    }

    private byte[] setOutputStreamInfo(byte[] msg, byte type) {
        byte length = 0;

        if(msg != null) {
            length = (byte)msg.length;
        }

        byte[] info = new byte[12 + length];
        info[0] = (byte)(0x55 & 0xFF);        // head
        info[1] = (byte)(0xAA & 0xFF);
        info[2] = (byte)(0x99 & 0xFF);
        info[3] = (byte)(0x66 & 0xFF);
        info[4] = (byte)(0x01 & 0xFF);        // no
        info[5] = (byte)(0x00);
        info[6] = (byte)(length & 0xFF);      // len
        info[7] = (byte)(type & 0xFF);        // type
        info[8] = (byte)(0x00);        // end
        info[9] = (byte)(0x00);        // res1
        info[10] = (byte)(0x00);       // res2
        info[11] = (byte)(0x00);
        for(int i=0; i<length; i++){
            info[12+i] = msg[i];
        }
        return info;
    }
    private boolean outputStreamInfo(byte[] info, int timeout) {
        try {
            mOutputStream.write(info);
        } catch (IOException e) {
            e.printStackTrace();
        }

        glock.lock();
        try {
            if(!gnoRsp.await(timeout, TimeUnit.MILLISECONDS)){
                Log.i("wls", "gate no respond");
                return false;
            } else {
                //Log.i("wls", "gate respond");
                return true;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            glock.unlock();
        }

        return false;
    }
    private boolean waitSensorResond(long timeout){
        slock.lock();
        try {
            if(!snoRsp.await(timeout, TimeUnit.MILLISECONDS)){
                Log.i("wls", "sensor no respond");
                return false;
            } else {
                return true;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            slock.unlock();
        }
        return false;
    }

    //
    // 设置网关时间
    // 网关在初始化时会主动请求时间设置，程序自动处理了该请求
    //
    public void setGateTime(){
        long timestamp = System.currentTimeMillis() / 1000;
        byte[] bytes = ByteUtils.fromLong(timestamp);

        try {
            mOutputStream.write(setOutputStreamInfo(bytes, (byte)0x27));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //
    // 获取网关信息，返回网关固件版本
    // rf_info[0] panid
    // rf_info[1] channel
    // rf_info[2] power
    //
    public String getGateInfo(int[] rf_info){
        String version = "";
        if( outputStreamInfo(setOutputStreamInfo(null, (byte)0x29), 3000) ) {
            version = Integer.toHexString(ByteUtils.byteToInt(mRsp, 10));
            rf_info[0] = ByteUtils.byteToInt(mRsp, 14) & 0xFFFF;
            rf_info[1] = ByteUtils.byteToInt(mRsp, 16) & 0xFFFF;
            rf_info[2] = mRsp[18] & 0xFF;
        }
        Log.i("wls", " "+version + " " + rf_info[0] + " " + rf_info[1] + " " + rf_info[2]);
        return version;
    }

    public String getGateInfo2(int[] rf_info) {
        String version = "";
        if(outputStreamInfo(setOutputStreamInfo(null, (byte)0x29), 3000)) {
            version = Integer.toHexString(ByteUtils.byteToInt(mRsp, 10));
            rf_info[0] = ByteUtils.byteToInt(mRsp, 14) & 0xFFFF;
            int channel = ByteUtils.byteToInt(mRsp, 16) & 0xFFFF;
            float f_channel = (float)channel / 10.0F;
            rf_info[1] = Float.floatToIntBits(f_channel);
            rf_info[2] = mRsp[18] & 0xFF;
        }

        Log.i("wls", " " + version + " " + rf_info[0] + " " + rf_info[1] + " " + rf_info[2]);
        return version;
    }
    //
    // 设置网关射频信息
    // rf_channel 信道，780，782
    // rf_panid Mac
    // rf_power PA功率
    //
    public boolean setGateRfInfo(int rf_channel, int rf_panid, int rf_power){
        byte[] msg = new byte[12];
        byte[] bytes;
        bytes = ByteUtils.fromInt(rf_channel);
        ByteUtils.copy(msg, bytes, 0, 0);
        bytes = ByteUtils.fromInt(rf_panid);
        ByteUtils.copy(msg, bytes, 4, 0);
        bytes = ByteUtils.fromInt(rf_power);
        ByteUtils.copy(msg, bytes, 8, 0);

        return outputStreamInfo(setOutputStreamInfo(msg, (byte)0x2A), 3000);
    }
    public boolean setGateRfInfo(float rf_channel, int rf_panid, int rf_power) {
        byte[] msg = new byte[12];
        byte[] bytes = ByteUtils.fromInt(Math.round(rf_channel * 10.0F));
        ByteUtils.copy(msg, bytes, 0, 0);
        bytes = ByteUtils.fromInt(rf_panid);
        ByteUtils.copy(msg, bytes, 4, 0);
        bytes = ByteUtils.fromInt(rf_power);
        ByteUtils.copy(msg, bytes, 8, 0);
        return outputStreamInfo(setOutputStreamInfo(msg, (byte)0x2A), 3000);
    }
    //
    // 修改传感器射频信息
    // sensorid 带操作的传感器
    // new_rf_channel 新的信道，范围769~794 857~882 903~928，若值不在此范围内，则忽略，传感器保持原有的信道
    // new_rf_panid   新的PAN号，范围1~65535，若值<0，则忽略，传感器保持原有的PAN号
    // new_rf_power   新的功率，范围-25~10,其它值，传感器保持原有的功率
    // newSensorid    新的ID，若值<=0,则忽略，传感器保持原有的ID号
    public boolean setSensorRfInfo(int sensorid, int new_rf_channel, int new_rf_panid, int new_rf_power, int newSensorid){
        boolean ret = false;
        byte[] info = new byte[28];
        //int channel = 0;
        byte map = 0x00;
        // 769~794 857~882 903~928
        if((new_rf_channel >= 769) && (new_rf_channel <= 794)) {
            //channel = (new_rf_channel - 769) * 10;
            map |= 0x40;
        }else if((new_rf_channel >= 857) && (new_rf_channel <= 882)) {
            //channel = (new_rf_channel - 857) * 10;
            map |= 0x40;
        }else if((new_rf_channel >= 903) && (new_rf_channel <= 928)) {
            //channel = (new_rf_channel - 903) * 10;
            map |= 0x40;
        }
        //pan 128 id 7685 channel 769
        if(new_rf_power >= -25 && new_rf_power <= 10) map |= 0x20; //-25~10
        if((new_rf_panid > 0) && (new_rf_panid < 65536)) map |= 0x10;
        if((newSensorid > 0) && (newSensorid < 65536))map |= 0x01;
        int index = 0;
        info[index++] = (byte)sensorid;
        info[index++] = (byte)(sensorid >>> 8);
        info[index++] = (byte)(sensorid >>> 16);
        info[index++] = (byte)(sensorid >>> 24);

        info[index++] = (byte)new_rf_channel;
        info[index++] = (byte)(new_rf_channel >>> 8);
        info[index++] = (byte)new_rf_power;
        info[index++] = (byte)(new_rf_power >>> 8);
        info[index++] = (byte)new_rf_panid;
        info[index++] = (byte)(new_rf_panid >>> 8);
        info[index++] = (byte) newSensorid;
        info[index++] = (byte)(newSensorid >>> 8);
        info[index++] = (byte)(newSensorid >>> 16);
        info[index++] = (byte)(newSensorid >>> 24);

        info[index++] = 0; //08
        info[index++] = 0;
        info[index++] = 0;
        info[index++] = 0;

        info[index++] = 0; //04
        info[index++] = 0;
        info[index++] = 0;
        info[index++] = 0;

        info[index++] = 0; //02
        info[index++] = 0;
        info[index++] = 0;
        info[index++] = 0;

        info[index++] = map;
        info[index] = (byte) 0xFF;
        //Log.i("wls", "index:" + index);
        for(int i=4; i<index; i++) {
            info[index] ^= (byte) info[i];
        }
        //Log.i("wls", ":: " + ByteUtils.byteToString(info));
        if(outputStreamInfo(setOutputStreamInfo(info, (byte)0x2B), 3000) &&
                waitSensorResond(3000)) {
            ret = true;
        }
        return ret;
    }
    public boolean setSensorRfInfo(int sensorid, float new_rf_channel, int new_rf_panid, int new_rf_power, int newSensorid) {
        boolean ret = false;
        byte[] info = new byte[28];
        byte map = 0;
        int rf_channel = Math.round(new_rf_channel * 10.0F);
        if(new_rf_channel >= 769.0F && new_rf_channel <= 794.0F) {
            map = (byte)(map | 0x40);
        } else if(new_rf_channel >= 857.0F && new_rf_channel <= 882.0F) {
            map = (byte)(map | 0x40);
        } else if(new_rf_channel >= 903.0F && new_rf_channel <= 928.0F) {
            map = (byte)(map | 0x40);
        }

        if(new_rf_power >= -25 && new_rf_power <= 10) {
            map = (byte)(map | 0x20);
        }

        if(new_rf_panid > 0 && new_rf_panid < 65536) {
            map = (byte)(map | 0x10);
        }

        if(newSensorid > 0 && newSensorid < 65536) {
            map = (byte)(map | 0x01);
        }

        int index = 0;
        info[index++] = (byte)(sensorid >>> 0 & 0xFF);
        info[index++] = (byte)(sensorid >>> 8 & 0xFF);
        info[index++] = (byte)(sensorid >>> 16 & 0xFF);
        info[index++] = (byte)(sensorid >>> 24 & 0xFF);
        info[index++] = (byte)rf_channel;
        info[index++] = (byte)(rf_channel >>> 8);
        info[index++] = (byte)new_rf_power;
        info[index++] = (byte)(new_rf_power >>> 8);
        info[index++] = (byte)new_rf_panid;
        info[index++] = (byte)(new_rf_panid >>> 8);
        info[index++] = (byte)newSensorid;
        info[index++] = (byte)(newSensorid >>> 8);
        info[index++] = (byte)(newSensorid >>> 16);
        info[index++] = (byte)(newSensorid >>> 24);
        info[index++] = 0;
        info[index++] = 0;
        info[index++] = 0;
        info[index++] = 0;
        info[index++] = 0;
        info[index++] = 0;
        info[index++] = 0;
        info[index++] = 0;
        info[index++] = 0;
        info[index++] = 0;
        info[index++] = 0;
        info[index++] = 0;
        info[index++] = map;
        info[index] = (byte) 0xFF;

        for(int i = 4; i < index; ++i) {
            info[index] ^= (byte) info[i];
        }

        if(outputStreamInfo(setOutputStreamInfo(info, (byte)0x2B), 3000) && waitSensorResond(6000L)) {
            ret = true;
        }

        return ret;
    }
    //
    // 获取未知传感器RF信息
    // timeout 等待超时，单位ms
    // rf_info 输出信息
    // rf_info[0] 传感器ID
    // rf_info[1] 信道号
    // rf_info[2] PAN id
    // rf_info[3] 功率
    // rf_info[4] 保留
    // rf_info[5] 保留
    // rf_info[6] 保留
    // rf_info[7] 保留
    public boolean inquireUnknowSensorRfInfo(int timeout, int[] rf_info){
        boolean ret = false;
        byte[] info = ByteUtils.fromInt(timeout);
        //55AA9966 E8030000 1C 63 0100 26 004B00 0000 E600 0005 0C03 17B7D138 17B7D138 17B7D138 07021820 0500 0000
        if(outputStreamInfo(setOutputStreamInfo(info, (byte)0x26), 3000) &&
                waitSensorResond(timeout)) {
            int panid = ByteUtils.byteToShort(mRsp, 12);
            int sensorid = ByteUtils.byteToInt(mRsp, 14);
            int channel = ByteUtils.byteToShort(mRsp, 18);//16
            int power = ByteUtils.byteToShort(mRsp, 20);//34
            int coef1 = ByteUtils.byteToInt(mRsp, 22);//18
            int coef2 = ByteUtils.byteToInt(mRsp, 26);//22
            int coef3 = ByteUtils.byteToInt(mRsp, 30);//26
            int coef4 = ByteUtils.byteToInt(mRsp, 34);//30

            Log.i("wls", " " + panid + " " + sensorid + " " + channel + " " + power);
            rf_info[0] = sensorid;
            rf_info[1] = channel;
            rf_info[2] = panid;
            rf_info[3] = power;
            rf_info[4] = coef1;
            rf_info[5] = coef2;
            rf_info[6] = coef3;
            rf_info[7] = coef4;
            ret = true;
        }
        return ret;
    }
    public boolean inquireUnknowSensorRfInfo2(int timeout, int[] rf_info) {
        boolean ret = false;
        byte[] info = ByteUtils.fromInt(timeout);
        if(outputStreamInfo(setOutputStreamInfo(info, (byte)0x26), 3000) && waitSensorResond((long)timeout)) {
            int panid = ByteUtils.byteToShort(mRsp, 12);
            int sensorid = ByteUtils.byteToInt(mRsp, 14);
            int channel = ByteUtils.byteToShort(mRsp, 18);
            float f_channel = (float)channel / 10.0F;
            int power = ByteUtils.byteToShort(mRsp, 20);
            int coef1 = ByteUtils.byteToInt(mRsp, 22);
            int coef2 = ByteUtils.byteToInt(mRsp, 26);
            int coef3 = ByteUtils.byteToInt(mRsp, 30);
            int coef4 = ByteUtils.byteToInt(mRsp, 34);
            Log.i("wls", " " + panid + " " + sensorid + " " + channel + " " + power);
            rf_info[0] = sensorid;
            rf_info[1] = Float.floatToIntBits(f_channel);
            rf_info[2] = panid;
            rf_info[3] = power;
            rf_info[4] = coef1;
            rf_info[5] = coef2;
            rf_info[6] = coef3;
            rf_info[7] = coef4;
            ret = true;
        }

        return ret;
    }
    //
    // 获取传感器信息,返回传感器固件版本
    // sensorid 待操作的传感器
    // info[0] battery vol
    // info[1] sensor signal power
    // info[2] gate signal power
    // info[3] start time
    // info[4] end time
    // info[5] period
    // info[6] args map, 1 for group1, 2 for group2, 4 for group3, 8 for group4
    // info[7]~info[10] group1 type iswave rate num
    // info[11]~info[14] group2 type iswave rate num
    // info[15]~info[18] group3 type iswave rate num
    // info[19]~info[22] group4 type iswave rate num
    //
    public String getSensorInfo(int sensorid, int[] info){
        byte[] bytes;
        String version = "";
        //55AA996680000000205B010028003D45010001000000A00502000100660009090000000000000000000000000595044E
        bytes = ByteUtils.fromShort((short)(sensorid & 0xFFFF));
        if(outputStreamInfo(setOutputStreamInfo(bytes, (byte)0x28), 3000) &&
            waitSensorResond(3000)){
            int offset = mRsp[2];
            info[0] = ByteUtils.ubyteToInt(mRsp[7]) + 250; // battery
            info[1] = ByteUtils.ubyteToInt(mRsp[8]);       // s-signal
            info[2] = ByteUtils.ubyteToInt(mRsp[9]);       // g-signal
            version = Integer.toHexString(ByteUtils.byteToInt(mRsp, offset));
            info[3] = ByteUtils.byteToInt(mRsp, offset + 4) & 0xFFFF;//info[2] 4; // start time 2 bytes
            info[4] = ByteUtils.byteToInt(mRsp, offset + 6) & 0xFFFF;//info[3] 6; // end time 2 bytes;
            info[5] = ByteUtils.byteToInt(mRsp, offset + 8) & 0xFFFF;//info[4] 8; // period 2 bytes
            info[6] = ByteUtils.byteToInt(mRsp, offset +10) & 0xFFFF;//info[5] 10; // parameter map 1,2,4,8
            for(int j=0; j<4; j++) {
                if ((info[6] & (0x01<<j)) != 0x00) {
                        info[7 + 4*j + 0] = ByteUtils.ubyteToInt(mRsp[offset + 12 + 4 * j + 0]);
                        info[7 + 4*j + 1] = ByteUtils.ubyteToInt(mRsp[offset + 12 + 4 * j + 1]);
                        info[7 + 4*j + 2] = ByteUtils.ubyteToInt(mRsp[offset + 12 + 4 * j + 2]);
                        int rate = info[7 + 4*j + 2] & 0xFF;
                        if(rate== 7)info[7 + 4*j] = 200;
                        else if(rate == 8)info[7 + 4*j + 2] = 500;
                        else if(rate == 9)info[7 + 4*j + 2] = 1000;
                        else if(rate ==10)info[7 + 4*j + 2] = 2000;
                        else if(rate ==11)info[7 + 4*j + 2] = 5000;
                        else if(rate ==12)info[7 + 4*j + 2] = 10000;
                        info[7 + 4*j + 3] = ByteUtils.ubyteToInt(mRsp[offset + 12 + 4 * j + 3]);
                        int num = info[7 + 4*j + 3] & 0xFF;
                        if(num == 8)info[7 + 4*j] = 256;
                        else if(num == 9)info[7 + 4*j + 3] = 512;
                        else if(num ==10)info[7 + 4*j + 3] = 1024;
                        else if(num ==11)info[7 + 4*j + 3] = 2048;
                        else if(num ==12)info[7 + 4*j + 3] = 4096;
                        else if(num ==13)info[7 + 4*j + 3] = 8192;

                }
            }
        }
        Log.i("wls", "version: " + version);
        return version;
    }

    //
    // 设置传感器采集参数
    // sensorid 待操作的传感器
    // period 采集周期
    // args[0]~info[3] group1 {type iswave rate num}
    // args[4]~info[7] group2 {type iswave rate num}
    // args[8]~info[11] group3 {type iswave rate num}
    // args[12]~info[16] group4 {type iswave rate num}
    // type:102 ACC 101 VEL DIS 103 TEM 116
    public boolean setSensorParameter(int sensorid, int period, int[] args) {
        byte[] bytes = new byte[28];
        boolean ret = false;
        int startTime = 0;
        int endTime = 1440;
        bytes[0] = (byte)(sensorid >>> 0 & (byte)0xFF);
        bytes[1] = (byte)(sensorid >>> 8 & (byte)0xFF);
        bytes[2] = (byte)(sensorid >>> 16 & (byte)0xFF);
        bytes[3] = (byte)(sensorid >>> 24 & (byte)0xFF);
        bytes[4] = (byte)startTime;
        bytes[5] = (byte)(startTime >>> 8);
        bytes[6] = (byte)endTime;
        bytes[7] = (byte)(endTime >>> 8);
        bytes[8] = (byte)period;
        bytes[9] = (byte)(period >>> 8);
        bytes[10] = 0;
        bytes[11] = 0;

        for(int i = 0; i < 4; ++i) {
            bytes[12 + 4 * i + 0] = (byte)args[4 * i + 0];
            if(bytes[12 + 4 * i + 0] != 0) {
                bytes[10] = (byte)(bytes[10] | 1 << i);
            }

            bytes[12 + 4 * i + 1] = (byte)args[4 * i + 1];
            int rate = args[4 * i + 2];
            if(rate == 200) {
                bytes[12 + 4 * i + 2] = 7;
            } else if(rate == 500) {
                bytes[12 + 4 * i + 2] = 8;
            } else if(rate == 1000) {
                bytes[12 + 4 * i + 2] = 9;
            } else if(rate == 2000) {
                bytes[12 + 4 * i + 2] = 10;
            } else if(rate == 5000) {
                bytes[12 + 4 * i + 2] = 11;
            } else if(rate == 10000) {
                bytes[12 + 4 * i + 2] = 12;
            } else if(rate == 0) {
                bytes[12 + 4 * i + 2] = 0;
            } else {
                bytes[12 + 4 * i + 2] = 8;
            }

            int num = args[4 * i + 3];
            if(num == 256) {
                bytes[12 + 4 * i + 3] = 8;
            } else if(num == 512) {
                bytes[12 + 4 * i + 3] = 9;
            } else if(num == 1024) {
                bytes[12 + 4 * i + 3] = 10;
            } else if(num == 2048) {
                bytes[12 + 4 * i + 3] = 11;
            } else if(num == 4096) {
                bytes[12 + 4 * i + 3] = 12;
            } else if(num == 8192) {
                bytes[12 + 4 * i + 3] = 13;
            } else if(num == 0) {
                bytes[12 + 4 * i + 3] = 0;
            } else {
                bytes[12 + 4 * i + 3] = 9;
            }
        }

        if(outputStreamInfo(setOutputStreamInfo(bytes, (byte)0x22), 3000) &&
                waitSensorResond(6000L)) {
            int offset = mRsp[2];
            ret = mRsp[offset] == 1;
        }

        Log.i("wls", "ret=" + ret);
        return ret;
    }
    //
    // 请求传感器数据
    // sensorid 待操作的传感器
    //
    public boolean requireSensorData(int sensorid) {
        byte[] bytes = new byte[4];
        boolean ret = false;
        bytes[0] = (byte)(sensorid >>> 0 & (byte)0xFF);
        bytes[1] = (byte)(sensorid >>> 8 & (byte)0xFF);
        bytes[2] = (byte)(sensorid >>> 16 & (byte)0xFF);
        bytes[3] = (byte)(sensorid >>> 24 & (byte)0xFF);
        if(outputStreamInfo(setOutputStreamInfo(bytes, (byte)0x21), 3000)) {
            ret = true;
        }

        return ret;
    }

    //
    // 请求临时数据采集
    // sensorid 待操作的传感器
    // args[0]~info[3] group {type iswave rate num}
    public boolean requireSensorTemporaryData(int sensorid, int[] info) {
        boolean ret = false;
        byte[] bytes = new byte[8];

        bytes[0] = (byte)sensorid;
        bytes[1] = (byte)(sensorid >>> 8);
        bytes[2] = (byte)(sensorid >>> 16);
        bytes[3] = (byte)(sensorid >>> 24);
        bytes[4] = (byte)info[0];
        bytes[5] = (byte)info[1];

        int rate = info[2];
        if(rate == 200) {
            bytes[6] = 7;
        } else if(rate == 500) {
            bytes[6] = 8;
        } else if(rate == 1000) {
            bytes[6] = 9;
        } else if(rate == 2000) {
            bytes[6] = 10;
        } else if(rate == 5000) {
            bytes[6] = 11;
        } else if(rate == 10000) {
            bytes[6] = 12;
        } else {
            bytes[6] = 8;
        }

        int num = info[3];
        if(num == 256) {
            bytes[7] = 8;
        } else if(num == 512) {
            bytes[7] = 9;
        } else if(num == 1024) {
            bytes[7] = 10;
        } else if(num == 2048) {
            bytes[7] = 11;
        } else if(num == 4096) {
            bytes[7] = 12;
        } else if(num == 8192) {
            bytes[7] = 13;
        } else {
            bytes[7] = 9;
        }

        this.sample_type = bytes[4];
        this.sample_rate = rate;
        this.sample_nums = num;
        Log.i("wls", ">> " + ByteUtils.byteToString(bytes));
        if(outputStreamInfo(setOutputStreamInfo(bytes, (byte)0x2C), 3000) &&
                waitSensorResond(6000L)) {
            int offset = mRsp[2];
            ret = mRsp[offset] == 1;
        }

        return ret;
    }
    //丢包率测试
    // sensorid 带操作传感器
    // packetSize 数据包长度，测试时包的大小，最大64，一般就设为64
    // duration 测试持续时间
    //
    public boolean packetLossRateTest(int sensorid, int packetSize, int duration){
        boolean ret = false;
        byte[] bytes = new byte[16];
        bytes[0] = (byte)(sensorid & 0xff);
        bytes[1] = (byte)((sensorid >>> 8) & 0xff);
        bytes[2] = (byte)((sensorid >>> 16) & 0xff);
        bytes[3] = (byte)((sensorid >>> 24) & 0xff);
        bytes[4] = 0x04;
        bytes[5] = 0x00;
        bytes[6] = 0x04;
        bytes[7] = 0x00;
        bytes[8] = 0x46;
        bytes[9] = 0x45;
        bytes[10]= 0x44;
        bytes[11]= 0x43;
        bytes[12]= (byte)(duration & 0xff);
        bytes[13]= (byte)((duration>>8) & 0xff);
        bytes[14]= (byte)(packetSize & 0xff);
        bytes[15]= (byte)((packetSize>>8) & 0xff);
        if(outputStreamInfo(setOutputStreamInfo(bytes, (byte)0x2E), 3000)){
            ret = true;
        }
        return ret;
    }
    public boolean packetLossRateTestStop(int sensorid){
        boolean ret = false;
        byte[] bytes = new byte[16];
        bytes[0] = (byte)(sensorid & 0xff);
        bytes[1] = (byte)((sensorid >>> 8) & 0xff);
        bytes[2] = (byte)((sensorid >>> 16) & 0xff);
        bytes[3] = (byte)((sensorid >>> 24) & 0xff);
        bytes[4] = 0x02;
        bytes[5] = 0x00;
        bytes[6] = 0x02;
        bytes[7] = 0x00;
        bytes[8] = 0x46;
        bytes[9] = 0x45;
        bytes[10]= 0x44;
        bytes[11]= 0x43;
        bytes[12]= 0x00;
        bytes[13]= 0x00;
        bytes[14]= 0x00;
        bytes[15]= 0x00;
        if(outputStreamInfo(setOutputStreamInfo(bytes, (byte)0x2E), 3000)){
            ret = true;
        }
        return ret;
    }
}
