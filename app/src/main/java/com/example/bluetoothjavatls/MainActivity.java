package com.example.bluetoothjavatls;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.provider.Settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.ArrayList;

import com.example.bluetoothjavatls.bluetooth.BluetoothClient;
import com.example.bluetoothjavatls.bluetooth.BluetoothClientInterface;
import com.example.bluetoothjavatls.bluetooth.BluetoothClient_TLS;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 0;
    private static final int REQUEST_DISABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;
    //    private static final int REQUEST_DISCOVER_BT = 3;
    private static final String TAG = "BluetoothMain";

    private static final int channel_number = 4;
    private static String CONNECTED_MAC_ADDRESS = "";

    private BluetoothClientInterface bluetoothClient;

    static {
        Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    TextView mStatusBluetooth, mReceivedMessage, mNoDevices;
    ImageView mBluetoothIcon;
    Button mOnBtn, mOffBtn, mDiscoverBtn, mPairedBtn, mConnectBtn_withoutTLS, mSendBtn, mConnectBtn_withTLS, mDisconnectBtn;
    ListView mDeviceListView, mPairedDevices;
    EditText mMessageBox;
    LinearLayout mMessageLayout;

    BluetoothAdapter mBlueAdapter;
    CustomAdapter mDeviceListAdapter, mPairedDeviceListAdapter;
    ArrayList<String> mDeviceList, mPairedDeviceList;
    ArrayList<BluetoothDevice> mDiscoveredBluetoothDevices;
    ArrayList<BluetoothDevice> mPairedBluetoothDevices;

    @SuppressLint({"MissingInflatedId", "SetTextI18n"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        mStatusBluetooth = findViewById(R.id.statusBluetooth);
        mBluetoothIcon = findViewById(R.id.bluetoothIcon);
        mOnBtn = findViewById(R.id.onBtn);
        mOffBtn = findViewById(R.id.offBtn);
        mDiscoverBtn = findViewById(R.id.discoverableBtn);
        mPairedBtn = findViewById(R.id.pairedBtn);
        mDeviceListView = findViewById(R.id.deviceListView);
        mNoDevices = findViewById(R.id.noDevices);
        mPairedDevices = findViewById(R.id.pairedDevices);

        mConnectBtn_withoutTLS = findViewById(R.id.mConnectBtn_withoutTLS);
        mConnectBtn_withTLS = findViewById(R.id.mConnectBtn_withTLS);
        mDisconnectBtn = findViewById(R.id.disconnectBtn);

        mMessageBox = findViewById(R.id.messageBox);
        mSendBtn = findViewById(R.id.sendBtn);
        mReceivedMessage = findViewById(R.id.receivedMessage);
        mMessageLayout = findViewById(R.id.messageLayout);

        mBlueAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothClient = null;

        mDeviceList = new ArrayList<>();
        mDiscoveredBluetoothDevices = new ArrayList<>();
        mPairedBluetoothDevices = new ArrayList<>();
        mDeviceListAdapter = new CustomAdapter(this, mDeviceList);
        mDeviceListView.setAdapter(mDeviceListAdapter);

        mPairedDeviceList = new ArrayList<>();
        mPairedDeviceListAdapter = new CustomAdapter(this, mPairedDeviceList);
        mPairedDevices.setAdapter(mPairedDeviceListAdapter);


        if (mBlueAdapter == null) {
            mStatusBluetooth.setText("Bluetooth is not available");
        } else {
            updateBluetoothStatus();
            fillPairedList();
        }

        mOnBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                                REQUEST_BLUETOOTH_PERMISSIONS);
                    } else {
                        enableBluetooth();
                    }
                } else {
                    enableBluetooth();
                }
            }
        });

        mOffBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                                REQUEST_BLUETOOTH_PERMISSIONS);
                    } else {
                        disableBluetooth();
                    }
                } else {
                    disableBluetooth();
                }
            }
        });

        mDiscoverBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPairedDevices.setVisibility(View.GONE);
                startDiscovery();
            }
        });

        mPairedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mNoDevices.setVisibility(View.GONE);
                mDeviceListView.setVisibility(View.GONE);
                showPairedDevices();
            }
        });

        mConnectBtn_withoutTLS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (CONNECTED_MAC_ADDRESS == null || CONNECTED_MAC_ADDRESS.isEmpty()) {
                        CONNECTED_MAC_ADDRESS = getConnectedDeviceMacAddress();
                        if (CONNECTED_MAC_ADDRESS != null) {
                            if (bluetoothClient != null) {
                                showToast("Disconnect from connection with TLS");
                            }
                            else {
                                bluetoothClient = new BluetoothClient(MainActivity.this, mBlueAdapter);
                                bluetoothClient.connect(CONNECTED_MAC_ADDRESS, channel_number);
                                mNoDevices.setVisibility(View.GONE);
                                mDeviceListView.setVisibility(View.GONE);
                                mPairedDevices.setVisibility(View.GONE);
                                mMessageLayout.setVisibility(View.VISIBLE);
                                mReceivedMessage.setVisibility(View.VISIBLE);
                                mReceivedMessage.setText(mReceivedMessage.getText() + "\nCommunication without TLS started");
                            }
                        } else {
                            showToast("No Bluetooth connection discovered");
                        }
                    } else {
                        if (bluetoothClient != null) {
                            showToast("Disconnect from connection with TLS");
                        }
                        else {
                            bluetoothClient = new BluetoothClient(MainActivity.this, mBlueAdapter);
                            bluetoothClient.connect(CONNECTED_MAC_ADDRESS, channel_number);
                            mNoDevices.setVisibility(View.GONE);
                            mDeviceListView.setVisibility(View.GONE);
                            mPairedDevices.setVisibility(View.GONE);
                            mMessageLayout.setVisibility(View.VISIBLE);
                            mReceivedMessage.setVisibility(View.VISIBLE);
                            mReceivedMessage.setText(mReceivedMessage.getText() + "\nCommunication without TLS started");
                        }
                    }
                } catch (IOException e) {
                    Log.d(TAG, e + "");
                    throw new RuntimeException(e);
                }
            }
        });

        mConnectBtn_withTLS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (CONNECTED_MAC_ADDRESS == null || CONNECTED_MAC_ADDRESS.isEmpty()) {
                        CONNECTED_MAC_ADDRESS = getConnectedDeviceMacAddress();
                        if (CONNECTED_MAC_ADDRESS != null) {
                            if (bluetoothClient != null) {
                                showToast("Disconnect from connection without TLS");
                            }
                            else {
                                bluetoothClient = new BluetoothClient_TLS(MainActivity.this, mBlueAdapter);
                                bluetoothClient.connect(CONNECTED_MAC_ADDRESS, channel_number);
                                mNoDevices.setVisibility(View.GONE);
                                mDeviceListView.setVisibility(View.GONE);
                                mPairedDevices.setVisibility(View.GONE);
                                mMessageLayout.setVisibility(View.VISIBLE);
                                mReceivedMessage.setVisibility(View.VISIBLE);
                                mReceivedMessage.setText(mReceivedMessage.getText() + "\nCommunication over TLS started");
                            }
                        } else {
                            showToast("No Bluetooth connection discovered");
                        }
                    } else {
                        if (bluetoothClient != null) {
                            showToast("Disconnect from connection without TLS");
                        }
                        else {
                            bluetoothClient = new BluetoothClient_TLS(MainActivity.this, mBlueAdapter);
                            bluetoothClient.connect(CONNECTED_MAC_ADDRESS, channel_number);
                            mNoDevices.setVisibility(View.GONE);
                            mDeviceListView.setVisibility(View.GONE);
                            mPairedDevices.setVisibility(View.GONE);
                            mMessageLayout.setVisibility(View.VISIBLE);
                            mReceivedMessage.setVisibility(View.VISIBLE);
                            mReceivedMessage.setText(mReceivedMessage.getText() + "\nCommunication over TLS started");
                        }
                    }
                } catch (IOException e) {
                    Log.d(TAG, e + "");
                    throw new RuntimeException(e);
                }
            }
        });

        mSendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String message = mMessageBox.getText().toString();
                if (!message.isEmpty() && !message.equals("quit")) {
                    try {
                        // Writting
                        bluetoothClient.write(message.getBytes());
                        showToast("Message sent: " + message);
                        String all_chats = (String) mReceivedMessage.getText() + "\n" + "Client: " + message;
//                        mReceivedMessage.setText(all_chats);
//                        mReceivedMessage.invalidate();
                        updateTextView(all_chats, mReceivedMessage);
                        // Reading
                        byte[] rvc_msg = new byte[1024];
                        bluetoothClient.read(rvc_msg);
                        String received_message = new String(rvc_msg, StandardCharsets.UTF_8).trim();
                        all_chats = (String) mReceivedMessage.getText() + "\n" + "Server: " + received_message;
//                        mReceivedMessage.setText(all_chats);
                        updateTextView(all_chats, mReceivedMessage);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if (message.equals("quit")) {
                    showToast("Can't send connection termination message, press the disconnect button");
                } else {
                    showToast("Please enter a message to send");
                }
            }
        });

        mDisconnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (bluetoothClient != null) {
                        bluetoothClient.write("quit".getBytes());
                        showToast("Message sent: quit");
                        String all_chats = (String) mReceivedMessage.getText() + "\n" + "Client: Client Ended Connection";
                        updateTextView(all_chats, mReceivedMessage);
                        // Reading
                        byte[] rvc_msg = new byte[1024];
                        bluetoothClient.read(rvc_msg);
                        String received_message = new String(rvc_msg, StandardCharsets.UTF_8).trim();
                        all_chats = (String) mReceivedMessage.getText() + "\n" + "Server: Server Ended Connection";
                        updateTextView(all_chats, mReceivedMessage);
                        bluetoothClient.disconnect();
                        bluetoothClient = null;

                        // disabling and enabling the bluetooth adapter
//                        resetBluetoothAdapter();
                        Log.d(TAG, "Bluetooth Adapter Enabled");
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        mDeviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice selectedDevice = mDiscoveredBluetoothDevices.get(position);
                pairDevice(selectedDevice, true);
            }
        });

        mPairedDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice selectedDevice = mPairedBluetoothDevices.get(position);
                pairDevice(selectedDevice, false);
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Register receiver for Bluetooth device discovery
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        IntentFilter discoveryFinishedFilter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(receiver, discoveryFinishedFilter);
    }

    private String getConnectedDeviceMacAddress() {
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            for (BluetoothDevice device : mPairedBluetoothDevices) {
                try {
                    Method isConnectedMethod = device.getClass().getMethod("isConnected");
                    boolean isConnected = (boolean) isConnectedMethod.invoke(device);
                    if (isConnected) {
                        return device.getAddress();
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Error checking if device is connected: " + e.getMessage());
                }
            }
        } else {
            showToast("Bluetooth permissions are required to check connected devices.");
        }
        return null;
    }

    private void fillPairedList() {
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            mPairedBluetoothDevices.clear();
            mPairedBluetoothDevices.addAll(mBlueAdapter.getBondedDevices());
        }
    }

    private void updateTextView(final String s, TextView textView) {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText(s);
                textView.invalidate();
            }
        });
    }

    private void openBluetoothSettings() {
        Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        startActivity(intent);
    }

    private void updateBluetoothStatus() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            try {
                if (mBlueAdapter.isEnabled()) {
                    mBluetoothIcon.setImageResource(R.drawable.ic_on);
                    mStatusBluetooth.setText("Bluetooth is enabled");
                } else {
                    mBluetoothIcon.setImageResource(R.drawable.ic_off);
                    mStatusBluetooth.setText("Bluetooth is disabled");
                }
            } catch (SecurityException e) {
                showToast("Bluetooth permission is required to get the status.");
            }
        } else {
            showToast("Bluetooth permissions are required to get the status.");
        }
    }

    private void enableBluetooth() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            try {
                if (!mBlueAdapter.isEnabled()) {
                    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(intent, REQUEST_ENABLE_BT);
                    String msg = "Turning on Bluetooth...";
                    showToast(msg);
                } else {
                    String msg = "Bluetooth is already on";
                    showToast(msg);
                }
            } catch (SecurityException e) {
                showToast("Bluetooth permission is required to enable Bluetooth.");
            }
        } else {
            showToast("Bluetooth permissions are required to enable Bluetooth.");
        }
    }

    private void disableBluetooth() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            try {
                if (mBlueAdapter.isEnabled()) {
                    Intent intent = new Intent("android.bluetooth.adapter.action.REQUEST_DISABLE");
                    startActivityForResult(intent, REQUEST_DISABLE_BT);
                    String msg = "Turning Bluetooth off...";
                    showToast(msg);
                } else {
                    String msg = "Bluetooth is already off";
                    showToast(msg);
                }
            } catch (SecurityException e) {
                showToast("Bluetooth permission is required to disable Bluetooth.");
            }
        } else {
            showToast("Bluetooth permissions are required to disable Bluetooth.");
        }
    }

    private void startDiscovery() {
        // Check if Bluetooth is enabled
        if (!mBlueAdapter.isEnabled()) {
            showToast("Bluetooth is off. Please enable Bluetooth first.");
            return;
        }

        // Check for permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    },
                    REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            startBluetoothDiscovery();
        }
    }

    private void startBluetoothDiscovery() {
        showToast("All necessary permissions accessed");

        try {
            if (mBlueAdapter.isDiscovering()) {
                mBlueAdapter.cancelDiscovery();
            }
            mDeviceList.clear();
            mDiscoveredBluetoothDevices.clear();
            mDeviceListAdapter.notifyDataSetChanged();
            mNoDevices.setVisibility(View.GONE);
            mDeviceListView.setVisibility(View.GONE);


            if (mBlueAdapter.startDiscovery()) {
                showToast("Discovery started...");
            } else {
                showToast("Discovery could not be started.");
            }
        } catch (SecurityException e) {
            showToast("An error occurred while starting discovery: " + e.getMessage());
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    String deviceName = device.getName();
                    String deviceAddress = device.getAddress();
                    if (deviceName != null && deviceAddress != null) {
                        String deviceInfo = deviceName + " (" + deviceAddress + ")";
                        fillPairedList();
                        if (!mPairedBluetoothDevices.contains(device) && !mDiscoveredBluetoothDevices.contains(device)) {
                            mDeviceList.add(deviceInfo);
                            mDiscoveredBluetoothDevices.add(device);
                            mDeviceListAdapter.notifyDataSetChanged();
                            mDeviceListView.setVisibility(View.VISIBLE);
                        }
                    }
                } else {
                    // Handle the case where permission is not granted
                    showToast("Bluetooth permission is required to get device name.");
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                showToast("Discovery finished");
                if (mDeviceList.isEmpty()) {
                    showToast("No devices found, check paired devices");
                    mNoDevices.setText("No new devices found, check paired devices");
                    mNoDevices.setVisibility(View.VISIBLE);
                } else {
                    mNoDevices.setVisibility(View.GONE);
                }
            }
        }
    };

    private void pairDevice(BluetoothDevice device, boolean discovered) {
        try {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                BluetoothSocket bluetoothSocket = null;
                if (discovered) {
                    showToast("Pairing with device: " + device.getName());
                    device.createBond();
                    mBlueAdapter.cancelDiscovery();
                    CONNECTED_MAC_ADDRESS = device.getAddress();
                    Log.d(TAG, "Connected with device with MAC " + CONNECTED_MAC_ADDRESS);
                } else {
                    openBluetoothSettings();
                }
            } else {
                showToast("Bluetooth permissions are required to pair with device.");
            }
        } catch (SecurityException e) {
            showToast("An error occurred while pairing: " + e.getMessage());
        }
    }
    private void showPairedDevices() {
        mPairedDevices.setVisibility(View.VISIBLE);
        if (mBlueAdapter != null && mBlueAdapter.isEnabled()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                fillPairedList();
                if (mPairedBluetoothDevices != null && !mPairedBluetoothDevices.isEmpty()) {
                    mPairedDeviceList.clear();
                    for (BluetoothDevice device : mPairedBluetoothDevices) {
                        String deviceInfo = device.getName() + " (" + device.getAddress() + ")";
                        mPairedDeviceList.add(deviceInfo);
                    }
                    mPairedDeviceListAdapter.notifyDataSetChanged();
                } else {
                    mPairedDeviceList.clear();
                    mPairedDeviceList.add("No paired devices found");
                    mPairedDeviceListAdapter.notifyDataSetChanged();
                }
            } else {
                showToast("Bluetooth permissions are required to show paired devices.");
            }
        } else {
            showToast("Bluetooth is not enabled");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0) {
                boolean allPermissionsGranted = true;
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allPermissionsGranted = false;
                        break;
                    }
                }
                if (allPermissionsGranted) {
                    startBluetoothDiscovery();
                } else {
                    showToast("Bluetooth and location permissions are required to start discovery.");
                }
            } else {
                showToast("Bluetooth and location permissions are required to start discovery.");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    mBluetoothIcon.setImageResource(R.drawable.ic_on);
                    String msg = "Bluetooth is on";
                    showToast(msg);
                } else {
                    String msg = "Couldn't turn Bluetooth on";
                    showToast(msg);
                }
                updateBluetoothStatus();
                break;
            case REQUEST_DISABLE_BT:
                if (resultCode == RESULT_OK) {
                    mBluetoothIcon.setImageResource(R.drawable.ic_off);
                    String msg = "Bluetooth is off";
                    showToast(msg);
                } else {
                    String msg = "Couldn't turn Bluetooth off";
                    showToast(msg);
                }
                updateBluetoothStatus();
                break;
        }
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
