package com.rpd.rgms_unit_gui;

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
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.rpd.rgms_unit_gui.R;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;


public class DashboardActivity extends AppCompatActivity {


    TextView statusTextView;
    Button connectButton;
    EditText inputDataEditText;
    Button sendDataButton;
    private static final String TAG = "DashboardActivity";
    private static final String ACTION_USB_PERMISSION = "com.rpd.rgms_unit_gui.USB_PERMISSION"; // Unique action string for your app

    // TODO: Replace with your actual USB device's Vendor ID and Product ID
    private static final int YOUR_VENDOR_ID = 0x1234; // Example Vendor ID
    private static final int YOUR_PRODUCT_ID = 0x5678; // Example Product ID

    private UsbManager usbManager;
    private UsbDevice connectedController; // Store the connected controller device

    // BroadcastReceiver to handle USB permission responses
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // Permission granted for the device
                            Log.d(TAG, "Permission granted for USB device: " + device.getDeviceName());
                            Toast.makeText(context, "USB Permission Granted!", Toast.LENGTH_SHORT).show();
                            connectedController = device;
                            // Now you can proceed to communicate with your controller
                            openUsbDevice(device);
                        }
                    } else {
                        // Permission denied
                        Log.d(TAG, "Permission denied for USB device: " + device.getDeviceName());
                        Toast.makeText(context, "USB Permission Denied.", Toast.LENGTH_SHORT).show();
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                // Handle device attached events (e.g., if the app wasn't running when device was plugged in)
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && device.getVendorId() == YOUR_VENDOR_ID && device.getProductId() == YOUR_PRODUCT_ID) {
                    Log.d(TAG, "Controller attached: " + device.getDeviceName());
                    requestUsbPermission(device); // Request permission immediately upon attachment
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                // Handle device detached events
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && device.equals(connectedController)) {
                    Log.d(TAG, "Controller detached: " + device.getDeviceName());
                    Toast.makeText(context, "USB Device Detached.", Toast.LENGTH_SHORT).show();
                    connectedController = null;
                    // Close communication if open
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Initialize your views by finding them by their IDs
        statusTextView = findViewById(R.id.status_text);
        connectButton = findViewById(R.id.connect_button);
        inputDataEditText = findViewById(R.id.input_data_edittext);
        sendDataButton = findViewById(R.id.send_data_button); // Initialize the button
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        // Register the BroadcastReceiver for USB events
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(usbReceiver, filter);

        // Find and request permission for the device when the activity starts
        findAndRequestControllerPermission();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the BroadcastReceiver to prevent memory leaks
        unregisterReceiver(usbReceiver);
    }

    private void findAndRequestControllerPermission() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Log.d(TAG, "Found " + deviceList.size() + " USB devices.");

        // Iterate through connected USB devices
        for (UsbDevice device : deviceList.values()) {
            Log.d(TAG, "Device: " + device.getDeviceName() + ", VID: " + String.format("0x%04X", device.getVendorId()) + ", PID: " + String.format("0x%04X", device.getProductId()));

            // Identify your controller by Vendor ID and Product ID
            if (device.getVendorId() == YOUR_VENDOR_ID && device.getProductId() == YOUR_PRODUCT_ID) {
                Log.d(TAG, "Controller found: " + device.getDeviceName());
                connectedController = device;
                requestUsbPermission(device);
                return; // Found the device, no need to continue searching
            }
        }
        if (connectedController == null) {
            Toast.makeText(this, "Controller not found. Please connect it.", Toast.LENGTH_LONG).show();
        }
    }

    private void requestUsbPermission(UsbDevice device) {
        if (!usbManager.hasPermission(device)) {
            Log.d(TAG, "Requesting permission for device: " + device.getDeviceName());
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            usbManager.requestPermission(device, permissionIntent);
        } else {
            Log.d(TAG, "Permission already granted for device: " + device.getDeviceName());
            Toast.makeText(this, "USB Permission already granted.", Toast.LENGTH_SHORT).show();
            connectedController = device;
            openUsbDevice(device);
        }
    }

    private void openUsbDevice(UsbDevice device) {
        // This is where you would typically open the connection and start communicating
        Log.d(TAG, "Attempting to open USB device: " + device.getDeviceName());

        UsbDeviceConnection connection = null;
        UsbInterface usbInterface = null;
        UsbEndpoint inEndpoint = null;
        UsbEndpoint outEndpoint = null;

        // Find the correct interface and endpoints for your device
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface currentInterface = device.getInterface(i);
            // You might need to check interface class, subclass, or protocol here
            // For example: if (currentInterface.getInterfaceClass() == UsbConstants.USB_CLASS_HID)
            usbInterface = currentInterface; // Assuming the first interface for simplicity

            for (int j = 0; j < usbInterface.getEndpointCount(); j++) {
                UsbEndpoint endpoint = usbInterface.getEndpoint(j);
                if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) { // Example: Bulk transfer
                    if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                        inEndpoint = endpoint;
                    } else {
                        outEndpoint = endpoint;
                    }
                }
            }
            if (inEndpoint != null && outEndpoint != null) {
                break; // Found both IN and OUT endpoints for this interface
            }
        }

        if (usbInterface == null) {
            Log.e(TAG, "No suitable USB interface found for the device.");
            Toast.makeText(this, "No suitable USB interface found.", Toast.LENGTH_SHORT).show();
            return;
        }

        connection = usbManager.openDevice(device);
        if (connection == null) {
            Log.e(TAG, "Failed to open USB device connection.");
            Toast.makeText(this, "Failed to open USB device connection.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (connection.claimInterface(usbInterface, true)) {
            Log.d(TAG, "Interface claimed successfully.");
            Toast.makeText(this, "Interface claimed!", Toast.LENGTH_SHORT).show();

            // Now you can use 'connection', 'inEndpoint', and 'outEndpoint'
            // to send and receive data from your USB controller.
            // Example: connection.bulkTransfer(outEndpoint, dataToSend, dataToSend.length, 0);
            // Example: connection.bulkTransfer(inEndpoint, dataToReceive, dataToReceive.length, 0);

            // Remember to close the connection when done or on device detach
            // connection.releaseInterface(usbInterface);
            // connection.close();

        } else {
            Log.e(TAG, "Failed to claim USB interface.");
            Toast.makeText(this, "Failed to claim USB interface.", Toast.LENGTH_SHORT).show();
            connection.close();
        }
    }
}
