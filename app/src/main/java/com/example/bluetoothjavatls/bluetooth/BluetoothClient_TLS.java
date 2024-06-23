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

import com.example.bluetoothjavatls.tls.TLS;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLContext;

public class BluetoothClient_TLS implements BluetoothClientInterface {
    private static final String TAG = "BluetoothClient_TLS";
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket = null;
    private InputStream inputStream = null;
    private OutputStream outputStream = null;
    private final Context context;
    private SSLEngine sslEngine = null;

    public BluetoothClient_TLS(Context context, BluetoothAdapter bluetoothAdapter) {
        this.context = context;
        this.bluetoothAdapter = bluetoothAdapter;
    }

    @Override
    public void connect(String mac_address, int channel_number) throws IOException {
        disconnect();

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 1);
            return;
        }

        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(mac_address);
        try {
            Method m = device.getClass().getMethod("createRfcommSocket", int.class);
            bluetoothSocket = (BluetoothSocket) m.invoke(device, channel_number);
            bluetoothSocket.connect();

            inputStream = bluetoothSocket.getInputStream();
            outputStream = bluetoothSocket.getOutputStream();

            // Initialize SSLEngine
            TLS tls = new TLS(context);
            SSLContext sslContext = tls.getSSLContext();
            sslEngine = sslContext.createSSLEngine();
            sslEngine.setUseClientMode(true);
            sslEngine.setNeedClientAuth(true);  // Ensure client authentication is required
            sslEngine.beginHandshake();

            // Perform SSL handshake
            performHandshake();

        } catch (Exception e) {
            Log.e(TAG, "Could not connect to the device", e);
            try {
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }
            } catch (IOException closeException) {
                Log.e(TAG, "Could not close socket", closeException);
            }
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
    private void performHandshake() throws IOException {
        SSLEngineResult.HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
        ByteBuffer inAppData = ByteBuffer.allocate(1024);
        ByteBuffer outAppData = ByteBuffer.allocate(1024);
        ByteBuffer peerAppData = ByteBuffer.allocate(1024);
        ByteBuffer peerNetData = ByteBuffer.allocate(1024);

        Log.d(TAG, "Application Buffer Size " + sslEngine.getSession().getApplicationBufferSize());
        Log.d(TAG, "Packet Buffer Size " + sslEngine.getSession().getPacketBufferSize());

        Log.d(TAG, "Starting SSL handshake");

        while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED &&
                handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            Log.d(TAG, "Handshake status: " + handshakeStatus);

            switch (handshakeStatus) {
                case NEED_UNWRAP:
                    Log.d(TAG, "NEED_UNWRAP: Available input stream bytes: " + inputStream.available());
                    if (inputStream.available() > 0) {
                        int bytesRead = inputStream.read(peerNetData.array(), peerNetData.position(), peerNetData.remaining());
                        Log.d(TAG, "Bytes read: " + bytesRead);
                        if (bytesRead == -1) {
                            throw new IOException("Handshake failed: end of stream reached");
                        }
                        byte[] packetData = new byte[bytesRead];
                        System.arraycopy(peerNetData.array(), peerNetData.position(), packetData, 0, bytesRead);
                        Log.d(TAG, "Received packet data: " + bytesToHex(packetData));
                        peerNetData.position(peerNetData.position() + bytesRead);
                    } else {
                        try {
                            Log.d(TAG, "NEED_UNWRAP: Waiting for data...");
                            Thread.sleep(50); // Add small delay to wait for data
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    peerNetData.flip();
                    SSLEngineResult result = sslEngine.unwrap(peerNetData, peerAppData);
                    Log.d(TAG, "Unwrapped: " + result);
                    Log.d(TAG, "peerNetData position: " + peerNetData.position() + ", limit: " + peerNetData.limit());
                    Log.d(TAG, "peerAppData position: " + peerAppData.position() + ", limit: " + peerAppData.limit());
                    peerNetData.compact();
                    handshakeStatus = result.getHandshakeStatus();
                    if (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
                        Log.d(TAG, "BUFFER_UNDERFLOW: Need more data to continue.");
                        continue; // wait for the buffer to get data.
                    }
                    break;

                case NEED_WRAP:
                    outAppData.clear();
                    try{
                        SSLEngineResult wrapResult = sslEngine.wrap(inAppData, outAppData);
                        inAppData.compact();
                        Log.d(TAG, "Wrapped: " + wrapResult);
                        handshakeStatus = sslEngine.getHandshakeStatus();

                        switch (wrapResult.getStatus()) {
                            case OK :
                                outAppData.flip();
                                while (outAppData.hasRemaining()) {
                                    int remaining = outAppData.remaining();
                                    byte[] chunk = new byte[remaining];
                                    outAppData.get(chunk);
                                    outputStream.write(chunk);
                                }
                                break;
                            case BUFFER_OVERFLOW:
                                outAppData = enlargePacketBuffer(outAppData);
                                Log.d(TAG, "Buffer Overflow in wrap");
                                break;
                            case BUFFER_UNDERFLOW:
                                throw new IOException("Buffer underflow occured after a wrap. I don't think we should ever get here.");
                            case CLOSED:
                                try {
                                    outAppData.flip();
                                    while (outAppData.hasRemaining()) {
                                        int remaining = outAppData.remaining();
                                        byte[] chunk = new byte[remaining];
                                        outAppData.get(chunk);
                                        outputStream.write(chunk);
                                    }
                                    // At this point the handshake status will probably be NEED_UNWRAP so we make sure that peerNetData is clear to read.
                                    peerNetData.clear();
                                } catch (Exception e) {
                                    System.out.println("Failed to send server's CLOSE message due to socket channel's failure.");
                                    handshakeStatus = sslEngine.getHandshakeStatus();
                                }
                                break;
                            default:
                                throw new IllegalStateException("Invalid SSL status: " + wrapResult.getStatus());
                        }
                    }
                    catch (IOException sslException) {
                        System.out.println("A problem was encountered while processing the data that caused the SSLEngine to abort. Will try to properly close connection...");
                        sslEngine.closeOutbound();
                        handshakeStatus = sslEngine.getHandshakeStatus();
                        break;
                    }

                case NEED_TASK:
                    Log.d(TAG, "NEED_TASK: Running delegated tasks...");
                    Runnable task;
                    while ((task = sslEngine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    handshakeStatus = sslEngine.getHandshakeStatus();
                    break;

                default:
                    throw new IllegalStateException("Invalid SSL handshake status: " + handshakeStatus);
            }
        }
        Log.d(TAG, "Handshake finished");
    }

    @Override
    public void write(byte[] data) throws IOException {
        ByteBuffer appData = ByteBuffer.wrap(data);
        ByteBuffer netData = ByteBuffer.allocate(1024);

        try {
            while (appData.hasRemaining()) {
                netData.clear();
                SSLEngineResult result = sslEngine.wrap(appData, netData);
                Log.d(TAG, "Write Wrapped: " + result);
                if (result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                    netData = enlargePacketBuffer(netData);
                    continue;
                }
                if (result.getStatus() != SSLEngineResult.Status.OK) {
                    throw new IOException("SSL Engine error during wrap: " + result.getStatus());
                }
                netData.flip();
                while (netData.hasRemaining()) {
                    int length = netData.remaining();
                    byte[] chunk = new byte[length];
                    netData.get(chunk);
                    outputStream.write(chunk);
                    outputStream.flush();
                    Log.d(TAG, "Sent chunk of size: " + length);
                    Log.d(TAG, "Sent chunk data: " + bytesToHex(chunk));
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException during write", e);
            throw e; // rethrow the exception after logging
        }
    }

    private ByteBuffer enlargePacketBuffer(ByteBuffer buffer) {
        ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
        buffer.flip();
        newBuffer.put(buffer);
        return newBuffer;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        ByteBuffer netData = ByteBuffer.allocate(1024);
        ByteBuffer appData = ByteBuffer.wrap(buffer);
        int totalBytesRead = 0;

        while (totalBytesRead == 0) {
            // Read data from the input stream into netData buffer
            int bytesRead = inputStream.read(netData.array(), netData.position(), netData.remaining());
            if (bytesRead == -1) {
                throw new IOException("End of stream reached");
            }
            netData.position(netData.position() + bytesRead);
            netData.flip();

            // Unwrap data from netData buffer into appData buffer
            SSLEngineResult result;
            do {
                result = sslEngine.unwrap(netData, appData);
                Log.d(TAG, "Read Unwrapped: " + result);
                switch (result.getStatus()) {
                    case BUFFER_OVERFLOW:
                        // Enlarge appData buffer if needed
                        appData = enlargePacketBuffer(appData);
                        break;
                    case BUFFER_UNDERFLOW:
                        // Read more data into netData buffer
                        netData.compact();
                        bytesRead = inputStream.read(netData.array(), netData.position(), netData.remaining());
                        if (bytesRead == -1) {
                            throw new IOException("End of stream reached");
                        }
                        netData.position(netData.position() + bytesRead);
                        netData.flip();
                        break;
                    case CLOSED:
                        throw new IOException("SSLEngine closed during unwrap");
                    default:
                        break;
                }
            } while (result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW ||
                    result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW);

            totalBytesRead = appData.position();
        }
        return totalBytesRead;
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
        if (sslEngine != null) {
            sslEngine.closeOutbound();
            sslEngine = null;
        }
    }
}