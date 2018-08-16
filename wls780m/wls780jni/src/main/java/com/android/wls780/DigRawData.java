package com.android.wls780;

import android.util.Log;

/**
 * Created by ldw on 2018/2/6.
 */

public class DigRawData {
    int sensorid;
    byte[] raw_byte;
    int raw_l = 0;
    int sampleType;
    int sampleRate;
    int sampleNums;
    float sampelCoef;

    public DigRawData() {

    }
    public DigRawData(int sensorid) {
        this.sensorid = sensorid;
        raw_byte = new byte[20480];
        raw_l = 0;
    }
    public DigRawData(int sensorid, int sampleType, int sampleRate, int sampleNums, int sampelCoef) {
        this.sensorid = sensorid;
        this.sampleType = sampleType;
        this.sampleRate = sampleRate;
        this.sampleNums = sampleNums;
        this.sampelCoef = Float.intBitsToFloat(sampelCoef);
        raw_byte = new byte[20480];
        raw_l = 0;
    }

    public boolean putRawData(byte[] bytes){
        //counter is start from 1, 1 is for args, and 2 up to the end is for data
        int no = (((bytes[10] & 0xFF) | ((bytes[11] & 0xFF) << 8)) & 0xFFFF) - 2;
        int end = bytes[13] & 0xFF;
        int len = (bytes[8] & 0xFF) - 2;

        System.arraycopy(bytes, 16, raw_byte, no * len, len);
        raw_l += len;
        if(end == 0){
            Log.i("wls", "raw_l: " + raw_l);
        }
        return (end == 0);
    }

    public int getRawDataLength(){
        return raw_l;
    }

    //24位AD波形数据
    public float[] getRawIntData(){
        float[] raw_int = new float[raw_l/3];
        for(int i=0; i<raw_l/3; i++){
            int l = raw_byte[i * 3];
            int m = raw_byte[i * 3 + 1];
            int h = raw_byte[i * 3 + 2];
            int o = (l & 0xff) | ((m & 0xff)<<8) | ((h & 0xff)<<16);
            o = (o << 8) >> 8;
            raw_int[i] = this.sampelCoef * o;
        }
        return raw_int;
    }

    // 16 位AD波形数据
    public float[] getRawShortData(){
        float[] raw_int = new float[raw_l/2];
        for(int i=0; i<raw_l/2; i++){
            int l = raw_byte[i * 2];
            int m = raw_byte[i * 2 + 1];
            int o = (l & 0xff) | ((m & 0xff)<<8);
            o = (o << 16) >> 16;
            raw_int[i] = this.sampelCoef * o;
        }
        return raw_int;
    }
}
