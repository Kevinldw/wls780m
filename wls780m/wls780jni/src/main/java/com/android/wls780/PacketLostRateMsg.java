package com.android.wls780;

/**
 * Created by ldw on 2018/2/28.
 */

public class PacketLostRateMsg {
    int sensorid;
    int sendTotal;
    int recvTotal;
    int sendSignal;
    int recvSignal;
    public PacketLostRateMsg(){}
    public PacketLostRateMsg(int sensorid, int sendTotal, int recvTotal, int sendSignal, int recvSignal){
        this.sensorid = sensorid;
        this.sendTotal = sendTotal;
        this.recvTotal = recvTotal;
        this.sendSignal = sendSignal;
        this.recvSignal = recvSignal;
    }
    public float getPacketLostRate(){
        float rate = 100.0f;
        if(this.sendTotal != 0) {
            rate = 100.0F - (float)this.recvTotal * 100.0F / (float)this.sendTotal;
		}
        return rate;
    }
    public float getSendSignalStrength(){
        return (float)this.sendSignal;
    }
    public float getRecvSignalStrength(){
        return (float)this.recvSignal;
    }
}
