package com.appzhen.sntester;

/**
 * Created by Zhen on 16-07-12.
 */
public class TransitData {

    private static float newSensedData;
    private static boolean isBleConnect;

    public TransitData() {
        newSensedData = 0f;
        isBleConnect = false;
    }

    public static void setNewSensedData(float newSensedData) {
        TransitData.newSensedData = newSensedData;
    }

    public static void setIsBleConnect(boolean isBleConnect) {
        TransitData.isBleConnect = isBleConnect;
    }


    public static float getNewSensedData() {
        return newSensedData;
    }

    public static boolean isBleConnect() {
        return isBleConnect;
    }


}
