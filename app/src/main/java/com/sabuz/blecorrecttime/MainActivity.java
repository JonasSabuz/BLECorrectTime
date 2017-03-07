package com.sabuz.blecorrecttime;

import android.annotation.TargetApi;
import android.app.Application;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.orhanobut.logger.Logger;

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
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv=(TextView)findViewById(R.id.tv);
        et=(EditText)findViewById(R.id.et);
        et.setText("AC:10");

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED /*||
                    this.checkSelfPermission(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) != PackageManager.PERMISSION_GRANTED*/ ) {

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

    public void startCorrect(View view)
    {
        mAdapter=GetBluetoothAdapter();
        if (mAdapter != null && mAdapter.isEnabled()) {
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
                Logger.d("Device :"+pDevice.getAddress());
                String address=et.getText().toString().trim();
                if(!address.isEmpty() && pDevice.getAddress().endsWith(address))
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
                        super.onConnectionStateChange(gatt, status, newState);
                        Log.i("DFU", "onConnectionStateChange");
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            Log.i("DFU", "onConnectionStateChange status != BluetoothGatt.GATT_SUCCESS");
                            if (newState == BluetoothGatt.STATE_DISCONNECTING || newState == BluetoothGatt.STATE_DISCONNECTED) {
                                gatt.close();
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                Log.i("DFU", "disconnected");
                            }
                        } else {
                            Log.i("DFU", "onConnectionStateChange status == BluetoothGatt.GATT_SUCCESS");
                            if (newState == BluetoothGatt.STATE_CONNECTED) {
                                mHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        boolean discoverState = gatt.discoverServices();
                                        Log.i("DFU", "discoverState = " + discoverState);
                                    }
                                }, 600);
                            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {

                                gatt.close();
                                Log.i("DFU", "disconnected");
                            }
                        }
                    }

                    @Override
                    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                        Log.i("DFU", "onServiceDiscovered");
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.i("DFU", "onServiceDiscovered status == BluetoothGatt.GATT_SUCCESS");

                            try {
                                //SubscribeForNotifications(gatt, DFU_SERVICE_UUID, DFU_UUID, true);
                            } catch (Exception ex) {
                                Log.i("DFU", "SubscribeForNotifications", ex);
                                mSuccess = false;
                                gatt.disconnect();
                            }

                            return;

                        } else {
                            Log.i("DFU", "onServiceDiscovered status != BluetoothGatt.GATT_SUCCESS");
                            mSuccess = false;
                            gatt.disconnect();
                        }
                    }

                    @Override
                    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.i(TAG, "onDescriptorWrite, status == BluetoothGatt.GATT_SUCCESS");
                            try {

                                BluetoothGattService lService = gatt.getService(PRI_SERVICE_UUID);
                                BluetoothGattCharacteristic lCharacteristic = lService.getCharacteristic(STATE_UUID);
                                if (lCharacteristic.setValue(new Time().format2445())) {
                                    int lRetry = 3;

                                    while (lRetry > 0) {
                                        if (gatt.writeCharacteristic(lCharacteristic)) {
                                            return;
                                        }
                                        try {

                                            Thread.sleep(250);
                                        } catch (Exception ignored) {
                                        }

                                        --lRetry;
                                    }
                                }
                            } catch (Exception ex) {
                                Log.e(TAG, "Failed to write characteristic value - " + ex.getLocalizedMessage());
                                ex.printStackTrace();
                            }
                        }

                        gatt.disconnect();


                        super.onDescriptorWrite(gatt, descriptor, status);
                    }

                    @Override
                    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            mSuccess = true;
                            Log.i("DFU", "onCharacteristicWrite status == BluetoothGatt.GATT_SUCCESS");
                        } else {
                            Log.i("DFU", "onCharacteristicWrite status != BluetoothGatt.GATT_SUCCESS");
                            mSuccess = false;
                        }

                        gatt.disconnect();
                    }
                };

                mBluetoothGatt=pDevice.connectGatt(getApplicationContext(), false, callback);
            }
        }.start();

    }

    Handler handler=new Handler(){
        @Override
        public void handleMessage(Message msg)
        {
            switch(msg.what)
            {
                case 1://connect success
                    if(progressDialog!=null)
                        progressDialog.dismiss();
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
        byte[] data=new byte[8];
        System.arraycopy(uint16ToByteArray(year), 0, data, 0, 2);
        System.arraycopy(uint8ToByteArray(month), 0, data, 2, 1);
        System.arraycopy(uint8ToByteArray(day), 0, data, 3, 1);
        System.arraycopy(uint8ToByteArray(hour), 0, data, 4, 1);
        System.arraycopy(uint8ToByteArray(minute), 0, data, 5, 1);
        System.arraycopy(uint8ToByteArray(sec), 0, data, 6, 1);
        return new byte[7];
    }
}
