package com.qiantao.rxble.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.PublishSubject;
import rx.subjects.SerializedSubject;
import rx.subjects.Subject;

/**
 * Created by qiantao on 2016/11/24.
 * Use RxJava help Bluetooth Low Energy device to communicate with your Android phone
 */

public class RxBle {

    private static final long SCAN_PERIOD = 10000;
    private static final String TAG = RxBle.class.getSimpleName();
    private static final String UUID = "0000ffe1-0000-1000-8000-00805f9b34fb";
    private Context mContext;

    private BluetoothAdapter mBleAdapter;
    private boolean mIsScanning;
    private static String sTargetDeviceName;
    private BluetoothGatt mBleGatt;
    private BluetoothGattCharacteristic mBleGattChar;

    private Subject<String, String> mBus;

    public interface BleScanListener {
        /**
         * Callback in BLE scanning,then you should use the method {@link #connectDevice} to connect target device
         * @param bleDevice Identifies the remote device
         * @param rssi The RSSI value for the remote device as reported by the Bluetooth hardware. 0 if no RSSI value is available
         * @param scanRecord The content of the advertisement record offered by the remote device.
         */
        void onBleScan(BluetoothDevice bleDevice, int rssi, byte[] scanRecord);
    }

    private BleScanListener mScanListener;

    /**
     * Set listener on device scanning
     * @param scanListener Listener of scaning
     */
    public void setScanListener(BleScanListener scanListener) {
        mScanListener = scanListener;
    }

    private RxBle() {
        mBus = new SerializedSubject<>(PublishSubject.<String>create());
    }

    public static RxBle getInstance() {
        return Singleton.INSTANCE;
    }

    public RxBle setTargetDevice(String deviceName) {
        sTargetDeviceName = deviceName;
        return Singleton.INSTANCE;
    }

    private static class Singleton {
        private static final RxBle INSTANCE = new RxBle();
    }

    public void openBle(Context context) {
        mContext = context.getApplicationContext();
        BluetoothManager bluetoothManager =
                (BluetoothManager) this.mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBleAdapter = bluetoothManager.getAdapter();
        if (mBleAdapter != null) {
            mBleAdapter.enable();
        }
    }

    public void scanBleDevices(boolean enable) {
        if (enable) {
            Log.d(TAG, "scanBleDevices");
            mIsScanning = true;
            mBleAdapter.startLeScan(mBleScanCallback);
            Observable.timer(SCAN_PERIOD, TimeUnit.MILLISECONDS).subscribe(new Action1<Long>() {
                @Override
                public void call(Long aLong) {
                    mIsScanning = false;
                    mBleAdapter.stopLeScan(mBleScanCallback);
                }
            });
        } else {
            mIsScanning = false;
            mBleAdapter.stopLeScan(mBleScanCallback);
        }
    }

    /**
     * Callback when scanning BLE devices
     */
    private BluetoothAdapter.LeScanCallback mBleScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice bleDevice, int rssi, byte[] scanRecord) {
            if (mIsScanning) {
                if (bleDevice.getName() != null) {
                    Log.d(TAG, "onLeScan: find device: " + bleDevice.getName());
                    if (sTargetDeviceName != null && sTargetDeviceName.equals(bleDevice.getName())) {
                        connectDevice(bleDevice);
                    } else if (mScanListener != null) {
                        mScanListener.onBleScan(bleDevice, rssi, scanRecord);
                    }
                }
            } else {
                Log.d(TAG, "onLeScan: stop scan");
            }
        }
    };


    /**
     * Connect BLE devices
     *
     * @param bleDevice Target device you want to connect
     */
    public void connectDevice(BluetoothDevice bleDevice) {
        scanBleDevices(false);
        mBleGatt = bleDevice.connectGatt(mContext,
                true,//true mean that can auto reconnect after disconnect
                new BleGattCallback());
        mBleGatt.connect();
        Log.d(TAG, "connectDevice: start to connect " + mBleGatt.getDevice().getName());
    }

    /**
     * Callback after device has been connected
     */
    private class BleGattCallback extends BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt bleGatt, int status, int newState) {
            super.onConnectionStateChange(bleGatt, status, newState);
            Log.d(TAG, "onConnectionStateChange: " + newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                Log.d(TAG, "onConnectionStateChange: device connected");
                //Discover services will call the next override method: onServicesDiscovered
                bleGatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.d(TAG, "onConnectionStateChange: device disconnected");
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt bleGatt, int status) {
            Log.d(TAG, "onServicesDiscovered: services size: " + bleGatt.getServices().size());
            List<BluetoothGattService> serviceList = bleGatt.getServices();
            Observable.from(serviceList)
                    .flatMap(new Func1<BluetoothGattService, Observable<BluetoothGattCharacteristic>>() {
                        @Override
                        public Observable<BluetoothGattCharacteristic> call(BluetoothGattService bleGattService) {
                            return Observable.from(bleGattService.getCharacteristics());
                        }
                    })
                    .filter(new Func1<BluetoothGattCharacteristic, Boolean>() {
                        @Override
                        public Boolean call(BluetoothGattCharacteristic bleGattChar) {
                            return bleGattChar.getUuid().toString().equals(UUID);
                        }
                    })
                    .subscribe(new Action1<BluetoothGattCharacteristic>() {
                        @Override
                        public void call(BluetoothGattCharacteristic bleGattChar) {
                            //
                            bleGatt.setCharacteristicNotification(bleGattChar, true);
                            mBleGattChar = bleGattChar;
                        }
                    });
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged");
            String receiveData = new String(characteristic.getValue());
            Log.d(TAG, "receive BLE 's data :" + receiveData);
            Observable.just(receiveData)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Action1<String>() {
                        @Override
                        public void call(String receiveData) {
                            mBus.onNext(receiveData);
                        }
                    });
        }
    }

    /**
     * Send data to BLE device
     *
     * @param data the data will send to BLE device
     */
    public void sendData(String data) {
        sendData(data, 0);
    }

    /**
     * Send data to BLE device with delay
     *
     * @param data String will be send to BLE device
     * @param time Delay time , milliseconds
     */
    public void sendData(final String data, long time) {
        if (!mBleAdapter.isEnabled() || mBleAdapter == null || !mBleGatt.connect()) {
            Log.d(TAG, "sendData: BLE is disconnected");
            return;
        }
        Observable.timer(time, TimeUnit.MILLISECONDS)
                .subscribe(new Action1<Long>() {
                    @Override
                    public void call(Long l) {
                        if (mBleGatt != null && mBleGattChar != null) {
                            mBleGattChar.setValue(data);
                            boolean isSend = mBleGatt.writeCharacteristic(mBleGattChar);
                            Log.d(TAG, "send " + (isSend ? "success" : "fail"));
                        }
                    }
                });
    }

    /**
     * Receive the data from BLE device
     *
     * @return Subject you should subscribed
     */
    public Observable<String> receiveData() {
        return mBus;
    }

    public void closeBle() {
        Log.d(TAG, "close BLE");
        if (mBleGatt != null) {
            mBleGatt.close();
            mBleGatt.disconnect();
        }
        if (mBleAdapter != null) {
            mBleAdapter.cancelDiscovery();
        }
        mBus.onCompleted();
    }
}
