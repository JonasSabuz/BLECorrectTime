package com.sabuz.blecorrecttime;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;

import com.orhanobut.logger.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    public static final UUID PRI_SERVICE_UUID = UUID.fromString("00000210-0000-1000-8000-00805F9B34FB");
    public static final UUID STATE_UUID = UUID.fromString("00006046-0000-1000-8000-00805F9B34FB");
    private static final String TAG="BLE Correct Time";
    private static BluetoothAdapter mAdapter = null;
    private TextView tv;
    private EditText et;
    private BluetoothDevice mDevice;
    private BluetoothGatt mBluetoothGatt;

    private ArrayList<String> address=new ArrayList<String>();
    String target="";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv=(TextView)findViewById(R.id.tv);
        et=(EditText)findViewById(R.id.et);
        et.setText("AC:10");

        findViewById(R.id.btn_scan).setEnabled(true);
        findViewById(R.id.btn_correct).setEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED /*||
                    this.checkSelfPermission(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) != PackageManager.PERMISSION_GRANTED*/) {

                final android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect Switchmate devices.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION},
                                1);
                    }
                });
                builder.show();
            } else {

            }
        }
    }

    public void startScan(View view)
    {
        mAdapter=GetBluetoothAdapter();
        if (mAdapter != null && mAdapter.isEnabled()) {
            target="";
            findViewById(R.id.btn_scan).setEnabled(false);
            startScan();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(false);
            builder.setMessage("Check Bluetooth has turn on");
            builder.setNegativeButton("setting", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    startSettings();
                }
            });
            builder.setPositiveButton("ok", null);

            AlertDialog dialog = builder.create();
            Window window = dialog.getWindow();
            window.setDimAmount(0.3f);
            dialog.show();
        }
    }

    public void startCorrect(View view)
    {
        progressDialog=ProgressDialog.show(this,"Connect","connecting...",true);
        target=et.getText().toString().trim();
    }

    private BluetoothAdapter GetBluetoothAdapter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            BluetoothManager lManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            return lManager.getAdapter();
        }

        return BluetoothAdapter.getDefaultAdapter();
    }

    private void startSettings() {
        Intent intentOpenBluetoothSettings = new Intent();
        intentOpenBluetoothSettings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intentOpenBluetoothSettings.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        startActivity(intentOpenBluetoothSettings);
    }

    Dialog progressDialog;
    private void startScan()
    {
        progressDialog=ProgressDialog.show(this,"Connect","scan devices",true);
        new Thread(){
            Object lCallback;
            public void run()
            {
                if (Build.VERSION.SDK_INT >= 21) {
                    final ScanSettings.Builder lScanSettingsBuilder = new ScanSettings.Builder();
                    lScanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);

                    if (Build.VERSION.SDK_INT >= 23) {
                        lScanSettingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);
                    }

                    lCallback = new ScanCallback() {
                        @Override
                        public void onScanResult(int callbackType, ScanResult result) {


                            switch (callbackType) {
                                case ScanSettings.CALLBACK_TYPE_ALL_MATCHES:
                                    Log.d(TAG, "Scanner: ALL_MATCHES");
                                    OnDeviceFound(result);
                                    break;

                                case ScanSettings.CALLBACK_TYPE_FIRST_MATCH:
                                    Log.d(TAG, "Scanner: FIRST_MATCH:");
                                    OnDeviceFound(result);
                                    break;
                                case ScanSettings.CALLBACK_TYPE_MATCH_LOST:
                                    Log.d(TAG, "Scanner: MATCH_LOST :");
                                    break;

                                default:
                                    Log.e(TAG, "Scanner: Unknown callback type!");
                                    break;
                            }

                            super.onScanResult(callbackType, result);
                        }

                        @Override
                        public void onBatchScanResults(List<ScanResult> results) {
                            for (ScanResult lResult :
                                    results) {
                                OnDeviceFound(lResult);
                            }
                            super.onBatchScanResults(results);
                        }

                        @Override
                        public void onScanFailed(int errorCode) {
                            switch (errorCode) {
                                case SCAN_FAILED_ALREADY_STARTED:
                                    Log.e(TAG, "Scan failed - Scan is already in progress");
                                    break;
                                case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                                    Log.e(TAG, "Scan failed - Application registration failed");
                                    break;
                                case SCAN_FAILED_FEATURE_UNSUPPORTED:
                                    Log.e(TAG, "Scan failed - Unsupported feature");
                                    break;
                                case SCAN_FAILED_INTERNAL_ERROR:
                                    Log.e(TAG, "Scan failed - Internal error");
                                    break;
                                default:
                                    Log.e(TAG, "Scan failed - Unknown error");
                            }
                            super.onScanFailed(errorCode);
                        }
                    };

                    mAdapter.getBluetoothLeScanner().startScan(null, lScanSettingsBuilder.build(), (ScanCallback)lCallback);
                } else {
                    //Old version compatibility
                    lCallback = new BluetoothAdapter.LeScanCallback() {
                        @Override
                        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                            OnDeviceFound(device, rssi, scanRecord);
                        }
                    };

                    mAdapter.startLeScan((BluetoothAdapter.LeScanCallback)lCallback);
                }
            }
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            private void OnDeviceFound(ScanResult pScanResult) {
                ScanRecord lScanRecord = pScanResult.getScanRecord();
                if (lScanRecord == null) {
                    OnDeviceFound(pScanResult.getDevice(), pScanResult.getRssi(), null);
                } else {
                    OnDeviceFound(pScanResult.getDevice(), pScanResult.getRssi(), lScanRecord.getBytes());
                }
            }
            private void OnDeviceFound(BluetoothDevice pDevice, int pRSSI, byte[] pScanRecord)
            {
                if(!address.contains(pDevice.getAddress()))
                    address.add(pDevice.getAddress());
                Logger.d("Device :"+pDevice.getAddress());

                if(!target.isEmpty() && pDevice.getAddress().endsWith(target))
                {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        BluetoothLeScanner mBLEScanner = mAdapter.getBluetoothLeScanner();
                        if (mBLEScanner != null) {
                            mBLEScanner.stopScan((ScanCallback) lCallback);
                        }
                    } else {
                        mAdapter.stopLeScan((BluetoothAdapter.LeScanCallback) lCallback);
                    }

                    connectDevice(pDevice);
                }

                UIHandler.sendEmptyMessage(0);
            }

            private void connectDevice(BluetoothDevice pDevice)
            {
                mDevice=pDevice;

                final BluetoothGattCallback callback = new BluetoothGattCallback() {
                    public byte[] key;
                    Handler mHandler = new Handler(Looper.getMainLooper());
                    private boolean mSuccess = false;

                    @Override
                    public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            if (newState == BluetoothGatt.STATE_DISCONNECTING || newState == BluetoothGatt.STATE_DISCONNECTED) {
                                Log.i(TAG, "onConnectionStateChange, newState == BluetoothGatt.STATE_DISCONNECTING || newState == BluetoothGatt.STATE_DISCONNECTED");
                                try {
                                    Thread.sleep(250);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                                gatt.close();
                                Logger.d("disconnected");
                            }
                            return;
                        } else {
                            if (newState == BluetoothGatt.STATE_CONNECTED) {
                                Logger.d("onConnectionStateChange, newState == BluetoothGatt.STATE_CONNECTED");

                                mHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        gatt.discoverServices();
                                    }
                                }, 600);
                            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                                Logger.d("onConnectionStateChange, newState == BluetoothGatt.STATE_DISCONNECTED");
                                mHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {

                                    }
                                }, 500);

                                gatt.close();
                            }
                        }

                        super.onConnectionStateChange(gatt, status, newState);
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        super.onServicesDiscovered(gatt, status);
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Logger.d("onServicesDiscovered, status == BluetoothGatt.GATT_SUCCESS");
                            BluetoothGattService lFoundService = null;
                            for (BluetoothGattService lService :
                                    gatt.getServices()) {
                                if (lService.getUuid().compareTo(PRI_SERVICE_UUID) == 0) {
                                    lFoundService = lService;
                                    break;
                                }
                            }

                            if (lFoundService == null) {
                                Logger.d("Failed to find primary service");
                                mSuccess = false;
                                gatt.disconnect();
                                return;
                            }

                            BluetoothGattCharacteristic lFoundCharacteristic = null;

                            for (BluetoothGattCharacteristic lChar :
                                    lFoundService.getCharacteristics()) {
                                if (lChar.getUuid().compareTo(STATE_UUID) == 0) {
                                    lFoundCharacteristic = lChar;
                                    break;
                                }
                            }

                            if (lFoundCharacteristic == null) {
                                Logger.d("Failed to find state characteristic");
                                mSuccess = false;
                                gatt.disconnect();
                                return;
                            }

                            try {
                                SubscribeForNotifications(gatt, lFoundService.getUuid(), lFoundCharacteristic.getUuid(), true);

                            } catch (IOException e) {

                                mSuccess = false;
                                gatt.disconnect();
                            }
                            return;
                        } else {
                            Logger.d("onServicesDiscovered, status != BluetoothGatt.GATT_SUCCESS");
                            gatt.disconnect();
                            mSuccess = false;

                            return;
                        }
                    }

                    @Override
                    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            Logger.d("onCharacteristicWrite, status != BluetoothGatt.GATT_SUCCESS");
                            mSuccess = false;
                            gatt.disconnect();
                        }
                        super.onCharacteristicWrite(gatt, characteristic, status);
                    }

                    @Override
                    public void onCharacteristicChanged(final BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                        byte[] lValue = characteristic.getValue();

                        Logger.e("onCharacteristicChanged"+byteArrayToHexString(lValue));
                        /*

                        if (!AuthSuccess(lValue)) {
                            Logger.d("onCharacteristicChanged, !AuthSuccess(lValue)");
                            gatt.disconnect();
//                            callbackcalled = true;
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    listener.onAuthFailed();
                                }
                            });
                        }

                        if (lValue != null && lValue.length >= 3 &&
                                lValue[0] == BLECoordinator.RESPONSE_CODE &&
                                lValue[2] == BLECoordinator.SUCCESS_CODE) {
                            mSuccess = true;

                            Logger.d("onCharacteristicChanged, mSuccess = true");

                        } else {
                            mSuccess = false;

                            Logger.d("onCharacteristicChanged, mSuccess = false");
                        }
*/
                        gatt.disconnect();
                        super.onCharacteristicChanged(gatt, characteristic);
                    }


                    @Override
                    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Logger.d("onDescriptorWrite, status == BluetoothGatt.GATT_SUCCESS");
                            try {

                                BluetoothGattService lService = gatt.getService(PRI_SERVICE_UUID);
                                BluetoothGattCharacteristic lCharacteristic = lService.getCharacteristic(STATE_UUID);
                                Calendar c=Calendar.getInstance();
                                Logger.d("write correct time");
                                byte[] value=datetimeToByteArray(
                                        c.get(Calendar.YEAR),
                                        c.get(Calendar.MONTH)+1,
                                        c.get(Calendar.DAY_OF_MONTH),
                                        c.get(Calendar.HOUR_OF_DAY),
                                        c.get(Calendar.MINUTE),
                                        c.get(Calendar.SECOND));
                                Logger.d("write value");
                                Logger.d(byteArrayToHexString(value));
                                if (lCharacteristic.setValue(value)) {
                                    int lRetry = 3;

                                    while (lRetry > 0) {
                                        if (gatt.writeCharacteristic(lCharacteristic)) {
                                            Logger.d("write success");
                                            gatt.disconnect();
                                            UIHandler.sendEmptyMessageDelayed(1,1000);
                                            return;
                                        }
                                        try {

                                            Thread.sleep(250);
                                        } catch (Exception ignored) {
                                            gatt.disconnect();
                                        }

                                        --lRetry;
                                    }
                                }
                            } catch (Exception ex) {
                                Logger.d("Failed to write characteristic value - " + ex.getLocalizedMessage());
                                ex.printStackTrace();
                            }
                        }

                        Logger.d("diconnect");
                        mSuccess = false;
                        gatt.disconnect();


                        super.onDescriptorWrite(gatt, descriptor, status);
                    }
                };

                mBluetoothGatt=pDevice.connectGatt(getApplicationContext(), false, callback);
            }
        }.start();

    }

    public static void SubscribeForNotifications(BluetoothGatt lGatt, UUID serviceUUID, UUID characteristicUUID, boolean value) throws IOException {

        BluetoothGattService lService = lGatt.getService(serviceUUID);
        if (lService == null) {
            lGatt.disconnect();
            throw new IOException("No Service found");
        }

        BluetoothGattCharacteristic lCharacteristic = lService.getCharacteristic(characteristicUUID);
        if (lCharacteristic != null) {
            lGatt.setCharacteristicNotification(lCharacteristic, value);
            BluetoothGattDescriptor lDescriptor = lCharacteristic.getDescriptor(lCharacteristic.getDescriptors().get(0).getUuid());
            if (value) {
                lDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            } else {
                lDescriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            }

            int lRetryCount = 3;
            while (lRetryCount > 0) {
                if (!lGatt.writeDescriptor(lDescriptor)) {
                    try {
                        Thread.sleep(100);
                    } catch (Exception ex) {

                    }
                    --lRetryCount;
                } else {
                    break;
                }
            }

            if (lRetryCount == 0) {
                lGatt.disconnect();
                try {
                    Thread.sleep(100);
                } catch (Exception ex) {
                }
                throw new IOException("Failed to initiate bluetooth command");
            }
        }
    }

    Handler UIHandler=new Handler(){
        @Override
        public void handleMessage(Message msg)
        {
            switch(msg.what) {
                case 0:
                    if (progressDialog != null)
                        progressDialog.dismiss();

                    StringBuilder sb = new StringBuilder();
                    for (String s : address) {
                        sb.append(s);
                        sb.append("\n");
                    }
                    tv.setText(sb.toString());
                    target = "";
                    findViewById(R.id.btn_correct).setEnabled(true);
                    break;
                case 1://connect success
                    if (progressDialog != null)
                        progressDialog.dismiss();
                    if (mBluetoothGatt != null){
                        mBluetoothGatt.disconnect();
                        mBluetoothGatt.close();
                    }
                    target="";
                    findViewById(R.id.btn_scan).setEnabled(true);
                    findViewById(R.id.btn_correct).setEnabled(false);
                    break;

            }
        }
    };

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len/2];

        for(int i = 0; i < len; i+=2){
            data[i/2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
        }

        return data;
    }

    final protected static char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
    public static String byteArrayToHexString(byte[] bytes) {
        char[] hexChars = new char[bytes.length*2];
        int v;

        for(int j=0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j*2] = hexArray[v>>>4];
            hexChars[j*2 + 1] = hexArray[v & 0x0F];
        }

        return new String(hexChars);
    }

    public static final byte[] uint16ToByteArray(int value) {
        return new byte[] {
                (byte)value,
                (byte)(value >>> 8)
        };
    }

    public static final byte[] uint8ToByteArray(int value) {

        return new byte[] {
                (byte)value
        };
    }

    public static final byte[] datetimeToByteArray(int year,int month,int day,int hour,int minute,int sec)
    {
        Logger.d(year+" "+month+" "+day+" "+hour+" "+minute+" "+sec);
        byte[] data=new byte[]{
                (byte) (year & 0xFF),
                (byte) ((year >> 8) & 0xFF),
                (byte) (month & 0xFF),
                (byte) (day & 0xFF),
                (byte) (hour & 0xFF),
                (byte) (minute & 0xFF),
                (byte) (sec & 0xFF)
        };

        return data;
    }
}
