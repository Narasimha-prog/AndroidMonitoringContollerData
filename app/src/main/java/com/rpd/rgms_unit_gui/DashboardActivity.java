package com.rpd.rgms_unit_gui;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;

public class DashboardActivity extends AppCompatActivity {

    private TextView statusTextView;
    private ScrollView scrollView;

    private static final String TAG = "DashboardActivity";
    private static final String ACTION_USB_PERMISSION = "com.rpd.rgms_unit_gui.USB_PERMISSION";

    private UsbManager usbManager;
    private UsbDevice connectedController;
    private UsbDeviceConnection usbConnection;
    private UsbEndpoint inEndpoint;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    logMessage("Permission granted for: " + device.getDeviceName());
                    connectedController = device;
                    openUsbDevice(device);
                } else {
                    logMessage("USB Permission Denied.");
                }

            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    logMessage("USB device attached: " + device.getDeviceName());
                    connectedController = device;
                    requestUsbPermission(device);
                }

            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && device.equals(connectedController)) {
                    logMessage("Device detached: " + device.getDeviceName());
                    connectedController = null;
                    statusTextView.setText("USB Status: Not Connected");
                    if (usbConnection != null) {
                        usbConnection.close();
                        usbConnection = null;
                    }
                }
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        statusTextView = findViewById(R.id.status_text);
        scrollView = findViewById(R.id.scroll_view);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(usbReceiver, filter);
        }

        findAndRequestFirstUsbDevice();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
        if (usbConnection != null) {
            usbConnection.close();
            usbConnection = null;
        }
    }

    private void findAndRequestFirstUsbDevice() {
        if (usbManager == null) {
            logMessage("USB Manager not available");
            return;
        }

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        if (deviceList.isEmpty()) {
            statusTextView.setText("USB Status: Not Connected");
            return;
        }

        UsbDevice device = deviceList.values().iterator().next();
        connectedController = device;
        requestUsbPermission(device);
    }

    private void requestUsbPermission(UsbDevice device) {
        if (!usbManager.hasPermission(device)) {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(device, permissionIntent);
        } else {
            connectedController = device;
            openUsbDevice(device);
        }
    }

    private void openUsbDevice(UsbDevice device) {
        UsbInterface usbInterface = null;
        inEndpoint = null;

        logMessage("Opening device: " + device.getDeviceName() +
                " VID=" + device.getVendorId() +
                " PID=" + device.getProductId());

        // List interfaces and endpoints
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            logMessage("Interface " + i + " with " + intf.getEndpointCount() + " endpoints");
            for (int j = 0; j < intf.getEndpointCount(); j++) {
                UsbEndpoint endpoint = intf.getEndpoint(j);
                logMessage("  Endpoint " + j +
                        " type=" + endpoint.getType() +
                        " dir=" + (endpoint.getDirection() == UsbConstants.USB_DIR_IN ? "IN" : "OUT"));
                if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                        endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    inEndpoint = endpoint;
                    usbInterface = intf;
                }
            }
        }

        if (usbInterface == null || inEndpoint == null) {
            logMessage("No readable (bulk IN) endpoint found.");
            return;
        }

        usbConnection = usbManager.openDevice(device);
        if (usbConnection == null) {
            logMessage("Failed to open connection.");
            return;
        }

        if (usbConnection.claimInterface(usbInterface, true)) {
            logMessage("USB Device ready. Interface claimed.");
            statusTextView.setText("USB Status: Connected (" +
                    device.getVendorId() + ":" + device.getProductId() + ")");
            startReadingThread();
        } else {
            logMessage("Failed to claim interface.");
            usbConnection.close();
        }
    }

    private void startReadingThread() {
        new Thread(() -> {
            byte[] buffer = new byte[inEndpoint.getMaxPacketSize()];
            StringBuilder dataBuffer = new StringBuilder();

            while (connectedController != null && usbConnection != null) {
                int bytesRead = usbConnection.bulkTransfer(inEndpoint, buffer, buffer.length, 1000);

                if (bytesRead > 0) {
                    String receivedChunk = new String(buffer, 0, bytesRead);
                    logMessage("[RAW] " + receivedChunk);

                    dataBuffer.append(receivedChunk);

                    int newlineIndex;
                    while ((newlineIndex = dataBuffer.indexOf("\n")) != -1) {
                        String line = dataBuffer.substring(0, newlineIndex).trim();
                        dataBuffer.delete(0, newlineIndex + 1);
                        logMessage("[LINE] " + line);
                    }
                } else {
                    logMessage("bulkTransfer timeout (no data this cycle).");
                }
            }
        }).start();
    }

    private void logMessage(String msg) {
        Log.d(TAG, msg); // Logcat
        runOnUiThread(() -> {
            statusTextView.append("\n" + msg);
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });
    }
}
