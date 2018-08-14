/*
 * pira-smart-app
 *
 * Copyright (C) 2018 vid553, IRNAS <www.irnas.eu>
 */
package eu.irnas.piraapp;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import static android.R.attr.data;

public class PiraConnect extends AppCompatActivity {
    private final static String TAG = PiraConnect.class.getSimpleName();
    private final static int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private final static int REQUEST_ENABLE_BT = 1;
    private final static int SCANNING_TIME = 2000; // in ms

    TextView titleText;
    ListView devicesListView;
    ArrayList<String> devicesListItems = new ArrayList<String>();

    public static BluetoothDevice deviceSelected;

    private boolean scanRunning = false;
    private BluetoothAdapter bluetoothAdapter;
    private static BluetoothLeScanner bluetoothLeScanner;
    private Handler scanHandler = new Handler();
    private ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<BluetoothDevice>();
    private ArrayAdapter<String> adapter;
    private int deviceIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pira_connect);

        titleText = (TextView) findViewById(R.id.mainTitle);
        devicesListView = (ListView) findViewById(R.id.devicesListView);

        adapter = new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, devicesListItems);
        devicesListView.setAdapter(adapter);
        devicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ConnectWithDevice(position);
            }
        });

        // Get bluetooth service from device
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        // If bluetooth is not enabled, ask for permission
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        // We also need location permission on higher APIs
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        }
        else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
        }

        ScanForDevices();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        if (scanRunning) {
            StopScan();
        }
        bluetoothAdapter = null;
        bluetoothLeScanner = null;
        super.onDestroy();
    }

    // If user doesn't enable bluetooth
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            Log.w(TAG, "User didn't enable bluetooth");
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);//Menu Resource, Menu
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.scan:
                if (scanRunning) {
                    StopScan();
                }
                ScanForDevices();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Scan callback for newer Androids
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (!discoveredDevices.contains(result.getDevice())) {
                adapter.add((deviceIndex+1) + ". " + result.getDevice().getName() + " RSSI:" + result.getRssi() + " " +
                        "Adr: " + result.getDevice().getAddress());
                discoveredDevices.add(result.getDevice());
                deviceIndex++;
                if (titleText.getText().equals("")) {
                    titleText.setText("Connect with device by selecting it from the list:");
                }
            }
        }
    };

    private void ScanForDevices() {
        Toast.makeText(this, "Scanning for devices...", Toast.LENGTH_SHORT).show();
        scanRunning = true;
        titleText.setText("");
        discoveredDevices.clear();
        adapter.clear();
        adapter.notifyDataSetChanged();
        deviceIndex = 0;
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                /*
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    bluetoothAdapter.startLeScan(oldScanCallback);
                }
                else {
                */
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                bluetoothLeScanner.startScan(scanCallback);
                //}
            }
        });
        // Stop scanning after some time to avoid wasting battery
        scanHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (scanRunning) {
                    StopScan();
                    if (discoveredDevices.size() == 0) {
                        ScanForDevices();
                    }
                }
            }
        }, SCANNING_TIME);
    }

    private void StopScan() {
        scanRunning = false;
        Toast.makeText(this, "Scanning finished!", Toast.LENGTH_SHORT).show();
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                /*
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    bluetoothAdapter.stopLeScan(oldScanCallback);
                }
                else {
                */
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                bluetoothLeScanner.stopScan(scanCallback);
                //}
                Log.d(TAG, "Number of devices found: " + discoveredDevices.size());
            }
        });
    }

    private void ConnectWithDevice(int selected) {
        if (scanRunning) {
            StopScan();
        }
        deviceSelected = discoveredDevices.get(selected);
        if (deviceSelected != null) {
            setResult(RESULT_OK);
            finish();
        }
        else {
            Log.e(TAG, "Error getting desired device");
        }
    }
}
