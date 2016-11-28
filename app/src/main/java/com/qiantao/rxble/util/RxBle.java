package com.qiantao.rxble.util;

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
 * 低功耗蓝牙使用RxJava
 */

public class RxBle {

    private static final long SCAN_PERIOD = 10000;
    private static final String TAG = RxBle.class.getSimpleName();
    private static final String UUID = "0000ffe1-0000-1000-8000-00805f9b34fb";

    private BluetoothAdapter mBleAdapter;
    private boolean mIsScanning;
    private String mTargetDeviceName;
    private Context mContext;
    private BluetoothGatt mBleGatt;
    private BluetoothGattCharacteristic mBleGattChar;
//    private BleDataListener mBleDataListener;

    private Subject<String, String> mBus;

//    public void setBleDataListener(BleDataListener bleDataListener) {
//        mBleDataListener = bleDataListener;
//    }

    private RxBle() {
        mTargetDeviceName = "Test";
        mBus = new SerializedSubject<>(PublishSubject.<String>create());
    }

    public static RxBle getInstance() {
        return Singleton.INSTANCE;
    }

    private static class Singleton {
        private static final RxBle INSTANCE = new RxBle();
    }

    /**
     * 初始化手机蓝牙设备
     *
     * @param context context
     */
    public void initBle(Context context) {
        this.mContext = context.getApplicationContext();
        BluetoothManager bluetoothManager =
                (BluetoothManager) this.mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBleAdapter = bluetoothManager.getAdapter();
        if (mBleAdapter != null) {
            mBleAdapter.enable();
        }
    }

    /**
     * 扫描蓝牙设备
     *
     * @param enable 开启或关闭扫描
     */
    public void scanBleDevices(boolean enable) {
        if (enable) {
            Log.d(TAG, "scanBleDevices: 扫描蓝牙");
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
     * 搜索到蓝牙设备后的回调
     */
    private BluetoothAdapter.LeScanCallback mBleScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice bleDevice, int rssi, byte[] scanRecord) {
            if (mIsScanning) {
                if (bleDevice.getName() != null) {
                    Log.d(TAG, "onLeScan：找到设备" + bleDevice.getName());
                    if (mTargetDeviceName.equals(bleDevice.getName())) {
                        connectDevice(bleDevice);
                    }
                }
            } else {
                Log.d(TAG, "onLeScan: 停止扫描");
            }
        }
    };

    /**
     * 连接搜索到的蓝牙设备
     *
     * @param bleDevice 目标蓝牙设备
     */
    private void connectDevice(BluetoothDevice bleDevice) {
        scanBleDevices(false);
        mBleGatt = bleDevice.connectGatt(mContext, true, new BleGattCallback());//true代表自动连接，能断开重连
        mBleGatt.connect();
        Log.d(TAG, "开始连接设备：" + mBleGatt.getDevice().getName());
    }

    /**
     * 蓝牙连接后的回调方法，包括连接状态、发现服务、接收数据
     */
    private class BleGattCallback extends BluetoothGattCallback {

        @Override
        public void onConnectionStateChange(BluetoothGatt bleGatt, int status, int newState) {
            super.onConnectionStateChange(bleGatt, status, newState);
            Log.d(TAG, "onConnectionStateChange: 连接状态: " + newState);
            if (newState == BluetoothGatt.STATE_CONNECTED) {//连接成功
                Log.d(TAG, "onConnectionStateChange: 设备连接");
                bleGatt.discoverServices();//搜索服务
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {//断开连接
                Log.d(TAG, "onConnectionStateChange: 设备断开");
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt bleGatt, int status) {
            Log.d(TAG, "onServicesDiscovered: 查找服务: " + bleGatt.getServices().size());
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
                            bleGatt.setCharacteristicNotification(bleGattChar, true);//设置开启接受蓝牙数据
                            mBleGattChar = bleGattChar;
                        }
                    });
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged");
            String receiveData = new String(characteristic.getValue());
            Log.d(TAG, "收到蓝牙发来数据：" + receiveData);
//            if (mBleDataListener != null) {
            Observable.just(receiveData)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Action1<String>() {
                        @Override
                        public void call(String receiveData) {
//                                mBleDataListener.onDataReceive(receiveData);
                            mBus.onNext(receiveData);
                        }
                    });
//            }
        }
    }


    /**
     * 发送蓝牙数据（延时）
     *
     * @param data 要发送的数据
     * @param time 延迟时间（毫秒）
     */
    public void sendData(final String data, long time) {
        if (!mBleAdapter.isEnabled() || mBleAdapter == null || !mBleGatt.connect()) {
            Log.d(TAG, "sendData: 蓝牙未连接");
            return;
        }
        Observable.timer(time, TimeUnit.MILLISECONDS)
                .subscribe(new Action1<Long>() {
                    @Override
                    public void call(Long l) {
                        if (mBleGatt != null && mBleGattChar != null) {
                            mBleGattChar.setValue(data);
                            boolean isSend = mBleGatt.writeCharacteristic(mBleGattChar);
                            Log.d(TAG, "发送：" + (isSend ? "成功" : "失败"));
                        }
                    }
                });
    }

    public Observable<String> receiveData() {
        return mBus;
    }

    /**
     * 关闭蓝牙
     */
    public void closeBle() {
        Log.d(TAG, "关闭所有蓝牙模块");
        if (mBleGatt != null) {
            mBleGatt.close();
            mBleGatt.disconnect();
        }
        if (mBleAdapter != null) {
            mBleAdapter.cancelDiscovery();
        }
    }
}
