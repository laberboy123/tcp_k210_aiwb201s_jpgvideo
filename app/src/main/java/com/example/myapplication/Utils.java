package com.example.myapplication;
import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import android.Manifest;
import android.content.pm.PackageManager;
public class Utils {
    // 获取设备的 IP 地址
    @SuppressLint("DefaultLocale")
    public static String getLocalIpAddress(Context context) {
        // 检查权限
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
            // 如果没有权限，显示提示并返回 "Unknown"
            Toast.makeText(context, "Permission Denied: ACCESS_WIFI_STATE", Toast.LENGTH_SHORT).show();
            return "Unknown";
        }

        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);

        // 获取连接的 Wi-Fi 信息
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();

        // 将 int 类型的 IP 地址转换为点分十进制字符串
        return String.format(
                "%d.%d.%d.%d",
                (ipAddress & 0xff),
                (ipAddress >> 8 & 0xff),
                (ipAddress >> 16 & 0xff),
                (ipAddress >> 24 & 0xff)
        );
    }
}
