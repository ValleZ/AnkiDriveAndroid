package ru.valle.ankidrive;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

public final class GameSession {
    private static final String TAG = "AnkiGameSession";
    private static GameSession instance;
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final Handler handler;
    public boolean scanningForDevices;
    public final HashMap<String, AnkiCarInfo> scannedDevices = new HashMap<String, AnkiCarInfo>();
    public final ArrayList<AnkiCarInfo> activeCars = new ArrayList<AnkiCarInfo>();


    private GameSession(Context context) {
        this.context = context;
        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        handler = new Handler();

    }

    public static GameSession getInstance(Context context) {
        if (instance == null) {
            instance = new GameSession(context);
        }
        return instance;
    }

    public void startScanning() {
        scanningForDevices = true;
        bluetoothAdapter.startLeScan(mLeScanCallback);
    }

    public void stopScanning() {
        if (scanningForDevices) {
            scanningForDevices = false;
            bluetoothAdapter.stopLeScan(mLeScanCallback);
            scannedDevices.clear();
        }
    }

    private final BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (scanningForDevices) {
                        String address = device.getAddress();
                        if (!scannedDevices.containsKey(address)) {
                            AnkiCarInfo carInfo = parseScanData(scanRecord);
                            if (carInfo != null) {
                                Log.d(TAG, "ankio car found " + carInfo);
                                carInfo.setBluetoothDevice(device);
                                activeCars.add(carInfo);
                            }
                            scannedDevices.put(address, carInfo);
                        }
                    }
                }
            });
        }
    };

    public void closeAllConnections() {
        for (AnkiCarInfo car : activeCars) {
            car.close();
        }
        activeCars.clear();
    }

    private static AnkiCarInfo parseScanData(final byte[] advertisedData) {
        boolean isAnkiCar = false;
        byte[] localName = null;
        int offset = 0;
        while (offset < (advertisedData.length - 2)) {
            int len = advertisedData[offset++];
            if (len == 0) {
                break;
            }
            int type = advertisedData[offset++];
            switch (type & 0xff) {
                case 0x06:// Partial list of 128-bit UUIDs
                case 0x07:// Complete list of 128-bit UUIDs
                    // Loop through the advertised 128-bit UUID's.
                    while (len >= 16) {
                        try {
                            // Wrap the advertised bits and order them.
                            ByteBuffer buffer = ByteBuffer.wrap(advertisedData, offset++, 16).order(ByteOrder.LITTLE_ENDIAN);
                            long mostSignificantBit = buffer.getLong();
                            long leastSignificantBit = buffer.getLong();
                            isAnkiCar |= AnkiCarInfo.ANKI_CAR_SERVICE_UUID.equals(new UUID(leastSignificantBit, mostSignificantBit));
                        } catch (IndexOutOfBoundsException e) {
                            // Defensive programming.
                            Log.e(TAG, e.toString());
                            break;
                        } finally {
                            // Move the offset to read the next uuid.
                            offset += 15;
                            len -= 16;
                        }
                    }
                    break;
                case 0x09://localname
                    localName = Arrays.copyOfRange(advertisedData, offset, offset += len - 1);
                    break;
                default:
                    offset += (len - 1);
                    break;
            }
        }

        return isAnkiCar && localName != null && localName.length >= 3 ? new AnkiCarInfo(localName) : null;
    }


}
