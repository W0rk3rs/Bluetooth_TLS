package com.example.bluetoothjavatls.bluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

public class BluetoothClient implements BluetoothClientInterface {
    //private static final int CHANNEL_NUMBER = 1; // specify the channel number
    //private static final String MAC_ADDRESS = "00:11:22:33:44:55"; // specify the MAC address

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private Context context;

    private String TAG = "BluetoothClient";

    public BluetoothClient(Context context, BluetoothAdapter bluetoothAdapter) {
        Log.d(TAG, "In Constructor");
        this.context = context;
        Log.d(TAG, "Context");
        this.bluetoothAdapter = bluetoothAdapter;
        Log.d(TAG, "Bluetooth Adapter");
    }

    @Override
    public void connect(String mac_address, int channel_number) throws IOException {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
            return;
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(mac_address);

        Log.d("TestBTConn", device.getAddress());
        try {
            Method m = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
            bluetoothSocket = (BluetoothSocket) m.invoke(device, channel_number);

            bluetoothSocket.connect();

            if(bluetoothSocket.isConnected())
                Log.d("TestBTConn", "Connected");

            inputStream = bluetoothSocket.getInputStream();
            outputStream = bluetoothSocket.getOutputStream();

        } catch (SecurityException se) {
            Log.e("BluetoothClient", "SecurityException: " + se.getMessage());
        } catch (IOException e) {
            Log.e("BluetoothClient", "Could not connect to the device", e);
            try {
                bluetoothSocket.close();
            } catch (IOException closeException) {
                Log.e("BluetoothClient", "Could not close socket", closeException);
            }
        } catch (Exception e) {
            Log.e("BluetoothClient", "Exception: " + e.getMessage());
        }

        inputStream = bluetoothSocket.getInputStream();
        outputStream = bluetoothSocket.getOutputStream();
    }

    @Override
    public void write(byte[] data) throws IOException {
        outputStream.write(data);
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        return inputStream.read(buffer);
    }
    @Override
    public void disconnect() throws IOException {
        if (inputStream != null) {
            inputStream.close();
            inputStream = null;
        }
        if (outputStream != null) {
            outputStream.close();
            outputStream = null;
        }
        if (bluetoothSocket != null) {
            bluetoothSocket.close();
            bluetoothSocket = null;
        }
    }
}
