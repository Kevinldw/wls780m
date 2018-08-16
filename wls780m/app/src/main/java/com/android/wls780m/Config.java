package com.android.wls780m;

public class Config {
    int cur_sensor;
    float cur_channel;
    int cur_pan;

    public Config(int sensor, float channel, int pan) {
        this.cur_sensor = sensor;
        this.cur_channel = channel;
        this.cur_pan = pan;
    }
    public int getSensor() {
        return this.cur_sensor;
    }
    public float getChannel() {
        return this.cur_channel;
    }
    public int getPan() {
        return this.cur_pan;
    }
    public void setSensor(int sensor) {
        this.cur_sensor = sensor;
    }
    public void setChannel(float channel) {
        this.cur_channel = channel;
    }
    public void setPan(int pan) {
        this.cur_pan = pan;
    }
}
