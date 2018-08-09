package eu.irnas.piraapp;

import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.UUID;

import static android.R.attr.format;

public class PiraService extends Service {

    private final static String TAG = PiraService.class.getSimpleName();

    public final static String ACTION_BLESCAN_CALLBACK = "eu.irnas.piraapp.ACTION_BLESCAN_CALLBACK";
    public final static String ACTION_CONNECTED = "eu.irnas.piraapp.ACTION_CONNECTED";
    public final static String ACTION_DISCONNECTED = "eu.irnas.piraapp.ACTION_DISCONNECTED";
    public final static String ACTION_SERVICES_DISCOVERED = "eu.irnas.piraapp.ACTION_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_RECEIVED = "eu.irnas.piraapp.ACTION_DATA_RECEIVED";
    public final static String CHR_NAME = "eu.irnas.piraapp.CHR_NAME";
    public final static String EXTRA_DATA = "eu.irnas.piraapp.EXTRA_DATA";

    public static BluetoothGattService piraGattService;
    public static BluetoothGattCharacteristic setTimeChr;
    public static BluetoothGattCharacteristic getTimeChr;
    public static BluetoothGattCharacteristic getStatusChr;
    public static BluetoothGattCharacteristic onPeriodChr;
    public static BluetoothGattCharacteristic offPeriodChr;
    public static BluetoothGattCharacteristic getBatteryLevel;

    private static BluetoothGatt bluetoothGatt;
    private static BluetoothDevice bluetoothDevice;

    public PiraService() {}

    private final IBinder localBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return localBinder;
    }

    @Override
    public boolean onUnbind (Intent intent) {
        Close();
        return super.onUnbind(intent);
    }

    public class LocalBinder extends Binder {
        PiraService getService() {
            return PiraService.this;
        }
    }

    public boolean Connect(BluetoothDevice device) {
        bluetoothDevice = device;
        if (bluetoothGatt != null) {
            return bluetoothGatt.connect();
        }
        bluetoothGatt = bluetoothDevice.connectGatt(this, false, gattCallback);
        return true;
    }

    public void Disconnect() {
        if (bluetoothGatt == null) {
            return;
        }
        bluetoothGatt.disconnect();
    }

    public void Close() {
        if (bluetoothGatt == null) {
            return;
        }
        bluetoothGatt.close();
        bluetoothGatt = null;
    }

    public void DiscoverServices() {
        if (bluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt not initialized");
            return;
        }
        bluetoothGatt.discoverServices();
    }

    public List<BluetoothGattService> getGattServices() {
        if (bluetoothGatt == null) {
            return null;
        }
        return bluetoothGatt.getServices();
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt == null) {
            Log.e(TAG, "Bluetooth Gatt not initialized.");
            return;
        }
        bluetoothGatt.readCharacteristic(characteristic);
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, int value) {
        int format = BluetoothGattCharacteristic.FORMAT_UINT32;
        characteristic.setValue(value, format, 0);
        Log.d(TAG, "Writing " + characteristic.getIntValue(format,0));
        bluetoothGatt.writeCharacteristic(characteristic);
    }

    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        bluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        byte[] value = new byte[1];
        if (enabled) {
            value[0] = 1;
        }
        else {
            value[0] = 0;
        }
        characteristic.setValue(value);
        //characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        bluetoothGatt.writeCharacteristic(characteristic);

        /*
        UUID uuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(uuid);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(descriptor);
        */
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate (final String action, final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        int format = 0;
        if (PiraGattAttributes.PIRA_GET_TIME_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
            Log.d(TAG, "found Time chr");
            intent.putExtra(CHR_NAME, "Time");
            intent.putExtra(EXTRA_DATA, characteristic.getStringValue(0));
        }
        else if (PiraGattAttributes.PIRA_STATUS_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
            Log.d(TAG, "found Status chr");
            intent.putExtra(CHR_NAME, "Status");
            format = BluetoothGattCharacteristic.FORMAT_UINT32;
            intent.putExtra(EXTRA_DATA, characteristic.getIntValue(format, 0));
        }
        else if (PiraGattAttributes.PIRA_BATTERY_LEVEL_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {
            Log.d(TAG, "found Battery chr");
            intent.putExtra(CHR_NAME, "Battery");
            format = BluetoothGattCharacteristic.FORMAT_UINT8;
            intent.putExtra(EXTRA_DATA, characteristic.getIntValue(format, 0));
        }
        else {

        }
        sendBroadcast(intent);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                broadcastUpdate(ACTION_CONNECTED);
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                broadcastUpdate(ACTION_DISCONNECTED);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (piraGattService == null || getBatteryLevel == null) {
                    piraGattService = gatt.getService(PiraGattAttributes.PIRA_SERVICE_UUID);
                    setTimeChr = piraGattService.getCharacteristic(PiraGattAttributes.PIRA_SET_TIME_CHARACTERISTIC_UUID);
                    getTimeChr = piraGattService.getCharacteristic(PiraGattAttributes.PIRA_GET_TIME_CHARACTERISTIC_UUID);
                    getStatusChr = piraGattService.getCharacteristic(PiraGattAttributes.PIRA_STATUS_CHARACTERISTIC_UUID);
                    onPeriodChr = piraGattService.getCharacteristic(PiraGattAttributes.PIRA_ON_PERIOD_CHARACTERISTIC_UUID);
                    offPeriodChr = piraGattService.getCharacteristic(PiraGattAttributes.PIRA_OFF_PERIOD_CHARACTERISTIC_UUID);
                    getBatteryLevel = piraGattService.getCharacteristic(PiraGattAttributes.PIRA_BATTERY_LEVEL_CHARACTERISTIC_UUID);
                }
                broadcastUpdate(ACTION_SERVICES_DISCOVERED);
            }
            else {
                Log.e(TAG, "Services not ok: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_RECEIVED, characteristic);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d(TAG, "Status: " + status);
            broadcastUpdate(ACTION_DATA_RECEIVED);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.d(TAG, "Chr changed");
            broadcastUpdate(ACTION_DATA_RECEIVED, characteristic);
        }
    };
}
