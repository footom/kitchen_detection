package com.example.detection.logic;

/**
 * 本機端（App 端）的氣體警報門檻判斷。
 *
 * 這段邏輯原本直接寫死在 MainActivity.checkLocalAlert() 裡，跟
 * NotificationManager、Toast 等 Android 專屬類別混在一起，沒辦法用一般的
 * JUnit local test（跑在 JVM 上、不需要模擬器）測試。抽出來後，這個類別
 * 完全不 import 任何 android.* 套件，可以直接用純 Java 測試。
 *
 * 注意：這裡的門檻值目前是獨立寫死在 App 端的，跟後端 backend/lib/sensorLogic.js
 * 裡的 THRESHOLDS 是分開維護的兩份數字，並不是同一個設定來源同步過來的。
 */
public final class AlertThresholds {

    public static final double LPG_THRESHOLD_PPM = 180;
    public static final double CO_THRESHOLD_PPM = 150;

    private AlertThresholds() {
    }

    public static boolean isLpgHigh(double lpgPpm) {
        return lpgPpm > LPG_THRESHOLD_PPM;
    }

    public static boolean isCoHigh(double coPpm) {
        return coPpm > CO_THRESHOLD_PPM;
    }
}
