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
import android.widget.Toast;

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
                    Log.d(TAG, "Permission granted for: " + device.getDeviceName());
                    connectedController = device;
                    openUsbDevice(device);
                } else {
                    Log.d(TAG, "Permission denied.");
                    Toast.makeText(context, "USB Permission Denied.", Toast.LENGTH_SHORT).show();
                }

            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    Log.d(TAG, "USB device attached: " + device.getDeviceName());
                    connectedController = device;
                    requestUsbPermission(device);
                }

            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && device.equals(connectedController)) {
                    Log.d(TAG, "Device detached: " + device.getDeviceName());
                    connectedController = null;
                    statusTextView.setText("USB Status: Not Connected");
                    if (usbConnection != null) {
                        usbConnection.close();
                        usbConnection = null;
                    }
                    Toast.makeText(context, "USB Device Detached", Toast.LENGTH_SHORT).show();
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
            Log.e(TAG, "USB Manager not available");
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

        // Find IN endpoint
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            for (int j = 0; j < intf.getEndpointCount(); j++) {
                UsbEndpoint endpoint = intf.getEndpoint(j);
                if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK &&
                        endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                    inEndpoint = endpoint;
                    usbInterface = intf;
                    break;
                }
            }
            if (inEndpoint != null) break;
        }

        if (usbInterface == null || inEndpoint == null) {
            Toast.makeText(this, "No readable endpoint found.", Toast.LENGTH_SHORT).show();
            return;
        }

        usbConnection = usbManager.openDevice(device);
        if (usbConnection == null) {
            Toast.makeText(this, "Failed to open connection.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (usbConnection.claimInterface(usbInterface, true)) {
            statusTextView.setText("USB Status: Connected (" +
                    device.getVendorId() + ":" + device.getProductId() + ")");
            Log.d(TAG, "USB Device ready. Starting read thread.");
            startReadingThread();
        } else {
            Toast.makeText(this, "Failed to claim interface.", Toast.LENGTH_SHORT).show();
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
                    // Convert bytes read to string and append to dataBuffer
                    String receivedChunk = new String(buffer, 0, bytesRead);
                    dataBuffer.append(receivedChunk);

                    int newlineIndex;
                    // Extract lines separated by \n
                    while ((newlineIndex = dataBuffer.indexOf("\n")) != -1) {
                        // Get a full line (trim removes \r if present)
                        String line = dataBuffer.substring(0, newlineIndex).trim();
                        // Remove processed line including \n from buffer
                        dataBuffer.delete(0, newlineIndex + 1);

                        // Display the line in UI
                        runOnUiThread(() -> {
                            statusTextView.append("\n" + line);
                            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
                        });
                    }
                }
            }
        }).start();
    }
}








