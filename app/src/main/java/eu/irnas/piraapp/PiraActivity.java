/*
 * pira-smart-app
 *
 * Copyright (C) 2018 vid553, IRNAS <www.irnas.eu>
 */
package eu.irnas.piraapp;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import static eu.irnas.piraapp.PiraService.ACTION_BLESCAN_CALLBACK;

// TODO properly handle orientation change
// TODO test CharacteristicNotification

public class PiraActivity extends AppCompatActivity {

    private final static String TAG = PiraActivity.class.getSimpleName();

    TextView titleText;
    TextView timeData;
    TextView statusData;
    TextView batteryData;
    EditText timeInput;
    EditText onPeriodInput;
    EditText offPeriodInput;
    Button disconnectBtn;
    Button sendBtn;
    ToggleButton toggleAutoUpdate;

    private static boolean piraServiceConnected;
    private static PiraService piraService;
    private boolean read_need = false;
    Handler timeHandler;

    // Managing Pira Service lifecycle
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            piraService = ((PiraService.LocalBinder) service).getService();
            piraServiceConnected = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            piraServiceConnected = false;
            piraService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        timeData = (TextView) findViewById(R.id.timeView);
        statusData = (TextView) findViewById(R.id.statusView);
        batteryData = (TextView) findViewById(R.id.batteryView);
        timeInput = (EditText) findViewById(R.id.timeText);
        onPeriodInput = (EditText) findViewById(R.id.onPeriodText);
        offPeriodInput = (EditText) findViewById(R.id.offPeriodText);

        disconnectBtn = (Button) findViewById(R.id.disconnectBtn);
        disconnectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Disconnect();
            }
        });
        sendBtn = (Button) findViewById(R.id.sendBtn);
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SendData();
            }
        });
        toggleAutoUpdate = (ToggleButton) findViewById(R.id.toggleAutoUpdate);
        toggleAutoUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChangeUpdateState();
            }
        });

        piraServiceConnected = false;
        ShowConnectActivity();
    }

    /*
    // Scan callback for older APIs
    private BluetoothAdapter.LeScanCallback oldScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            discoveredDevices.add(device);
        }
    };
    */

    @Override
    protected void onResume() {
        Intent piraServiceIntent = new Intent(this, PiraService.class);
        bindService(piraServiceIntent, serviceConnection, BIND_AUTO_CREATE);

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_BLESCAN_CALLBACK);
        intentFilter.addAction(PiraService.ACTION_CONNECTED);
        intentFilter.addAction(PiraService.ACTION_DISCONNECTED);
        intentFilter.addAction(PiraService.ACTION_SERVICES_DISCOVERED);
        intentFilter.addAction(PiraService.ACTION_DATA_RECEIVED);
        registerReceiver(piraUpdateReceiver, intentFilter);
        super.onResume();

        timeHandler = new Handler();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(piraUpdateReceiver);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        piraService.Close();
        unbindService(serviceConnection);
        piraService = null;
        piraServiceConnected = false;
        super.onDestroy();
    }

    // Start BLE service and connect with device
    public void ConnectWithService(BluetoothDevice device) {
        setTitle("Connected with " + device.getName());
        if (piraServiceConnected) {
            Log.d(TAG, "Pira service started OK.");
            piraService.Connect(device);
        }
    }

    // Disconnect from device
    public void Disconnect () {
        piraService.Disconnect();
        read_need = true;
        timeData.setText("-");
        statusData.setText("-");
        batteryData.setText("-");
        timeInput.setText("");
        onPeriodInput.setText("");
        offPeriodInput.setText("");
        ShowConnectActivity();
    }

    // Write data to device
    public void SendData() {
        if (!timeInput.getText().toString().equals("")) {
            int timeSet = Integer.parseInt(timeInput.getText().toString());
            piraService.writeCharacteristic(PiraService.setTimeChr, timeSet);
        }
        if (!onPeriodInput.getText().toString().equals("")) {
            int onPeriodSet = Integer.parseInt(onPeriodInput.getText().toString());
            piraService.writeCharacteristic(PiraService.onPeriodChr, onPeriodSet);
        }
        if (!offPeriodInput.getText().toString().equals("")) {
            int offPeriodSet = Integer.parseInt(offPeriodInput.getText().toString());
            piraService.writeCharacteristic(PiraService.offPeriodChr, offPeriodSet);
        }
    }

    public void ChangeUpdateState() {
        boolean state = toggleAutoUpdate.isChecked();
        if (state) {    // on
            piraService.setCharacteristicNotification(PiraService.getTimeChr, true);

            // temporary fix
            final int delay = 5000;   // 5 secs
            timeHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    timeData.setText("-");
                    statusData.setText("-");
                    batteryData.setText("-");
                    ReadCharacteristics();
                    read_need = true;
                    timeHandler.postDelayed(this, delay);
                }
            }, delay);
        }
        else {          // off
            piraService.setCharacteristicNotification(PiraService.getTimeChr, false);
            timeHandler.removeCallbacksAndMessages(null);
        }
    }

    public void ShowConnectActivity() {
        Intent intent = new Intent(this, PiraConnect.class);
        startActivityForResult(intent, 1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1 && resultCode == RESULT_OK) {
            BluetoothDevice device = PiraConnect.deviceSelected;
            ConnectWithService(device);
            Log.d(TAG, "Activity result ok");
        }
        else {
            Log.e(TAG, "Error with getting the selected device...");
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /*
    private void DisplayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;

        adapter.clear();
        adapter.notifyDataSetChanged();
        // Loops through available GATT Services
        for (BluetoothGattService gattService : gattServices) {

            final String uuid = gattService.getUuid().toString();
            Log.i(TAG, "Service discovered: " + uuid);
            PiraActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    adapter.add("Service: "+uuid+" ");
                }
            });
            new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();

            // Loops through available Characteristics
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                final String charUuid = gattCharacteristic.getUuid().toString();
                Log.i(TAG, "Characteristic discovered for service: " + charUuid);
                PiraActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        adapter.add(" - Characteristic: "+charUuid+" ");
                    }
                });
            }
        }
    }
    */

    private final BroadcastReceiver piraUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String receivedAction = intent.getAction();
            switch(receivedAction) {
                case ACTION_BLESCAN_CALLBACK:
                    Log.d(TAG, "Bluetooth device found.");
                    break;
                case PiraService.ACTION_CONNECTED:
                    Log.d(TAG, "Device connected.");
                    piraService.DiscoverServices();
                    read_need = true;
                    break;
                case PiraService.ACTION_DISCONNECTED:
                    Log.d(TAG, "Device disconnected.");
                    Disconnect();
                    break;
                case PiraService.ACTION_SERVICES_DISCOVERED:
                    Log.d(TAG, "Services discovered.");
                    ReadCharacteristics();
                    //DisplayGattServices(piraService.getGattServices());
                    break;
                case PiraService.ACTION_DATA_RECEIVED:
                    Log.d(TAG, "Data received.");
                    if (intent.hasExtra(PiraService.CHR_NAME)) {
                        if (intent.getStringExtra(PiraService.CHR_NAME).equals("Time")) {
                            String time = intent.getStringExtra(PiraService.EXTRA_DATA);
                            int new_line = time.lastIndexOf("\n");
                            timeData.setText(time.substring(0, new_line));
                        }
                        else if (intent.getStringExtra(PiraService.CHR_NAME).equals("Status")) {
                            int status = intent.getIntExtra(PiraService.EXTRA_DATA, 0);
                            statusData.setText(status + "");
                        }
                        else if (intent.getStringExtra(PiraService.CHR_NAME).equals("Battery")) {
                            int batteryLevel = intent.getIntExtra(PiraService.EXTRA_DATA, 0);
                            float batteryVoltage = batteryLevel * 0.0164f;
                            batteryData.setText(batteryVoltage + " V");
                        }
                    }
                    else {
                        Toast.makeText(PiraActivity.this, "Data send OK", Toast.LENGTH_SHORT).show();
                    }

                    if (read_need) {
                        piraService.DiscoverServices();
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private void ReadCharacteristics() {
        if (timeData.getText().equals("-")) {
            piraService.readCharacteristic(PiraService.getTimeChr);
        }
        else if(statusData.getText().equals("-")) {
            piraService.readCharacteristic(PiraService.getStatusChr);
        }
        else if (batteryData.getText().equals("-")) {
            piraService.readCharacteristic(PiraService.getBatteryLevel);
        }
        else {
            read_need = false;
        }
    }
}
