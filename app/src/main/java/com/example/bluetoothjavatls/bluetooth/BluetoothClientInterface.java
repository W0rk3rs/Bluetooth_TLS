package com.example.bluetoothjavatls.bluetooth;

import java.io.IOException;

public interface BluetoothClientInterface {
    void connect(String macAddress, int channelNumber) throws IOException;
    void disconnect() throws IOException;
    void write(byte[] data) throws IOException;
    int read(byte[] buffer) throws IOException;
}
