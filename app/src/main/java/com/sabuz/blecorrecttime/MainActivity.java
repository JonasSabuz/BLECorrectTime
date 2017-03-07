package com.sabuz.blecorrecttime;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
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
import android.content.pm.PackageManager;
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

public class MainActivity extends Activity {
    private static final String TAG="BLE Correct Time";
    private static BluetoothAdapter mAdapter = null;
    private TextView tv;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv=(TextView)findViewById(R.id.tv);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check 

            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED /*||
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
        } else {

        }
    }

    public void connect(View view)
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

    public void disconnect(View view)
    {

    }

    public void correctTime(View view)
    {

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

    private void startScan()
    {
        Dialog dialog=ProgressDialog.show(this,"Connect","scan devices",true);
        new Thread(){
            public void run()
            {
                    if (android.os.Build.VERSION.SDK_INT >= 21) {
                        final ScanSettings.Builder lScanSettingsBuilder = new ScanSettings.Builder();
                        lScanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);

                        if (Build.VERSION.SDK_INT >= 23) {
                            lScanSettingsBuilder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);
                        }

                        ScanCallback lCallback = new ScanCallback() {
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

                        mAdapter.getBluetoothLeScanner().startScan(null, lScanSettingsBuilder.build(), lCallback);
                    } else {
                        //Old version compatibility
                        final BluetoothAdapter.LeScanCallback lCallback = new BluetoothAdapter.LeScanCallback() {
                            @Override
                            public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
                                OnDeviceFound(device, rssi, scanRecord);
                            }
                        };

                        mAdapter.startLeScan(lCallback);
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
                }

            }.start();

    }
}
