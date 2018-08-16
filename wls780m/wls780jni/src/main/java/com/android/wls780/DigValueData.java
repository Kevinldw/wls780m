//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.android.wls780;

public class DigValueData {
    int sensorid;
    float valueV;
    float valueT;

    public DigValueData(int sensorid, int type, float value) {
        this.sensorid = sensorid;
        if (type == 31) {
            this.valueT = value;
        } else {
            this.valueV = value;
        }

    }

    public float getTemperatureValue() {
        return this.valueV;
    }

    public float getVibrateValue() {
        return this.valueV;
    }
}
