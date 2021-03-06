package ru.valle.ankidrive;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.util.Log;

import java.util.Arrays;
import java.util.UUID;

/**
 * Created by Valentin on 6/26/2014.
 */
public final class AnkiCarInfo extends BluetoothGattCallback {
    public static final UUID ANKI_CAR_SERVICE_UUID = UUID.fromString("be15beef-6186-407e-8381-0bd89c4d8df4");
    private static final UUID UUID_ANKIO_WRITE = UUID.fromString("BE15BEE1-6186-407E-8381-0BD89C4D8DF4");
    private static final UUID UUID_ANKIO_READ = UUID.fromString("BE15BEE0-6186-407E-8381-0BD89C4D8DF4");
    private static final byte[] CMD_PING = new byte[]{0x1, 0x16};
    private static final byte[] CMD_VERSION_REQ = new byte[]{0x1, 0x18};
    private static final byte[] CMD_BATTERY_LEVEL_REQ = new byte[]{0x1, 0x1a};
    private static final byte[] CMD_SET_LIGHTS = new byte[]{0x2, 0x1d, 0};
    private static final byte[] CMD_SET_SDK_MODE = new byte[]{0x2, (byte) 0x90, 1};
    private static final byte[] CMD_SET_SPEED = new byte[]{0x6, 0x24, 0, 0, 0, 0, 0};
    private static final byte[] CMD_CHANGE_LINE = new byte[]{0x9, 0x25, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] CMD_SET_OFFSET = new byte[]{0x5, 0x2c, 0, 0, 0, 0};
    private final byte model;
    private final int carId;
    private final int productId;
    boolean fullBattery;
    private static final String TAG = "AnkiCarInfo";
    boolean lowBattery;
    boolean charging;
    final int version;
    final String carName;
    private BluetoothDevice bluetoothDevice;
    private int bluetoothState = BluetoothGatt.STATE_DISCONNECTED;
    private BluetoothGatt gatt;
    private boolean readyToAcceptCommands;

    public AnkiCarInfo(byte modelId, int carId, int productId, byte[] carInfoBytes) {
        byte state = carInfoBytes[0];
        fullBattery = (state & (1 << 4)) == (1 << 4);
        lowBattery = (state & (1 << 5)) == (1 << 5);
        charging = (state & (1 << 6)) == (1 << 6);
        version = ((carInfoBytes[2] & 0xff) << 8) | (carInfoBytes[1] & 0xff);
        if (carInfoBytes.length > 8) {
            carName = new String(Arrays.copyOfRange(carInfoBytes, 8, carInfoBytes.length - 1));
        } else {
            carName = "AnkioCar";
        }
        this.model = modelId;
        this.carId = carId;
        this.productId = productId;
    }

    @Override
    public String toString() {
        return "AnkiCarInfo{" +
                "model=" + getModelDescription(model) +
                ", carId=" + Integer.toHexString(carId) +
                ", productId=" + Integer.toHexString(productId) +
                ", fullBattery=" + fullBattery +
                ", lowBattery=" + lowBattery +
                ", charging=" + charging +
                ", version=" + version +
                ", carName='" + carName + '\'' +
                '}';
    }

    private String getModelDescription(byte model) {
        switch(model) {
            case 1:
                return "Kourai (Yellow)";
            case 2:
                return "Boson (Grey)";
            case 3:
                return "Rho (Red)";
            case 4:
                return "Katal (Blue)";
            default:
                return "" + model;
        }
    }

    public void setBluetoothDevice(BluetoothDevice bluetoothDevice) {
        this.bluetoothDevice = bluetoothDevice;
    }

    public void connect(Context context) {
        gatt = bluetoothDevice.connectGatt(context, false, this);
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        byte[] value = characteristic.getValue();
        if (value.length >= 2) {
            switch (value[1]) {
                case 0x17:
                    Log.v(TAG, "received pong!");
                    break;
                case 0x19:
                    Log.v(TAG, "received version info! " + toHex(value) + " " + readShort(value, 2));
                    break;
                case 0x1b:
                    Log.v(TAG, "received battery level! " + toHex(value) + " " + readShort(value, 2));
                    break;
                case 0x27:
                    Log.v(TAG, "received position! " + toHex(value) + " offs_center " + readFloat(value, 4) + " speed " + readShort(value, 8) + " clockwise " + value[9]);
                    break;
                case 0x29:
                    Log.v(TAG, "received transition! " + toHex(value) + " offs_center " + readFloat(value, 3) + " clockwise " + value[7]);
                    break;
                case 0x2b:
                    Log.v(TAG, "vehicle delocated! " + toHex(value));
                    break;
                case 0x36:
                    Log.v(TAG, "vehicle speed update! " + readFloat(value, 2));
                    break;
                case 0x3f:
                    Log.v(TAG, "vehicle status update! on charger " + value[3] + " battery low " + value[4] + " battery full " + value[5]);
                    break;
                case 0x41:
                    Log.v(TAG, "lane relevant stuff: " + toHex(value));
                    break;
                case 0x43:
                    Log.v(TAG, "iphone relevant stuff: vehicle strayed? " + toHex(value));
                    break;
                case (byte) 0x86:
                case (byte) 0x44:
                    Log.v(TAG, "iphone relevant stuff: unknown " + toHex(value));
                    break;


                default:
                    Log.w(TAG, "received unknown response, type " + Integer.toHexString(value[1] & 0xff) + " full " + toHex(value));
                    break;
            }
            if (!readyToAcceptCommands) {
                readyToAcceptCommands = true;
                onSessionStart();
            }
        } else {
            Log.d(TAG, "onCharacteristicChanged " + toHex(value) + " ch " + characteristic.getUuid() + " type " + Integer.toHexString(value[1] & 0xff));
        }
    }

    public static float readFloat(byte[] bytes, int offs) {
        return Float.intBitsToFloat(((bytes[offs + 3] & 0xff) << 24) | ((bytes[offs + 2] & 0xff) << 16) | ((bytes[offs + 1] & 0xff) << 8) | (bytes[offs] & 0xff));
//        return Float.intBitsToFloat(((bytes[offs + 0] & 0xff) << 24) | ((bytes[offs + 1] & 0xff) << 16) | ((bytes[offs + 2] & 0xff) << 8) | (bytes[offs+3] & 0xff));
//        return ByteBuffer.wrap(bytes, offs, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
//        return ByteBuffer.wrap(bytes, offs, 4).order(ByteOrder.BIG_ENDIAN).getFloat();
    }

    public static int readShort(byte[] bytes, int offs) {
        return ((bytes[offs + 1] & 0xff) << 8) | (bytes[offs] & 0xff);
    }


    //    private static final byte MASK_LIGHT_TYPE_HEADLIGHTS = 1;
//    private static final byte MASK_LIGHT_TYPE_BRAKELIGHS = 2;
    public static final byte MASK_LIGHT_TYPE_FRONTLIGHTS = 4;
    public static final byte MASK_LIGHT_TYPE_ENGINE = 8;

    private void onSessionStart() {
        sendCommand(CMD_SET_SDK_MODE);
//        sendCommand(CMD_PING);
        sendCommand(CMD_VERSION_REQ);
//        sendCommand(CMD_BATTERY_LEVEL_REQ);

//        CMD_SET_LIGHTS[2] = (byte) (0xf0 | MASK_LIGHT_TYPE_ENGINE | MASK_LIGHT_TYPE_FRONTLIGHTS);
//        sendCommand(CMD_SET_LIGHTS);
    }


    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        bluetoothState = newState;
        if (newState == BluetoothGatt.STATE_CONNECTED) {
            Log.d(TAG, "connected!");
            gatt.discoverServices();
        }
    }

    public void close() {
        if (bluetoothState != BluetoothGatt.STATE_DISCONNECTED) {
            gatt.close();
        }
    }

    public boolean sendCommand(byte[] cmd) {
        BluetoothGattCharacteristic writeChar = getWriteCharacteristic();
        boolean result = false;
        if (writeChar != null) {
            Log.d(TAG, "send " + toHex(cmd));
            writeChar.setValue(cmd);
            result = gatt.writeCharacteristic(writeChar);
        }
        return result;
    }

    private BluetoothGattCharacteristic getWriteCharacteristic() {
        BluetoothGattService service = gatt.getService(ANKI_CAR_SERVICE_UUID);
        return service == null ? null : service.getCharacteristic(UUID_ANKIO_WRITE);
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        Log.d(TAG, "services discovered " + status);
        BluetoothGattService service = gatt.getService(ANKI_CAR_SERVICE_UUID);
        BluetoothGattCharacteristic readChar = service == null ? null : service.getCharacteristic(UUID_ANKIO_READ);
        if (readChar != null) {
            boolean result = gatt.setCharacteristicNotification(readChar, true);
            Log.d(TAG, "not0 " + result);
            for (BluetoothGattDescriptor descriptor : readChar.getDescriptors()) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                result = gatt.writeDescriptor(descriptor);
                Log.d(TAG, "not1 " + result);
            }
        } else {
            Log.w(TAG, "readChar null on connect");
        }


    }

    public void lights(int mask, boolean turnOn) {
        CMD_SET_LIGHTS[2] = (byte) ((turnOn ? 0xf0 : 0) | mask);
        sendCommand(CMD_SET_LIGHTS);
    }

    /**
     * @param speed mm/sec from 0 to ~6000
     * @param accel mm/sec^2 25000 is okay
     */
    public void setSpeed(int speed, int accel) {
        CMD_SET_SPEED[2] = (byte) (speed & 0xff);
        CMD_SET_SPEED[3] = (byte) ((speed >> 8) & 0xff);
        CMD_SET_SPEED[4] = (byte) ((accel >> 8) & 0xff);
        CMD_SET_SPEED[5] = (byte) (accel & 0xff);
        sendCommand(CMD_SET_SPEED);
    }

    public void changeLane(int horizontalSpeed, float targetOffset, byte hopIntent, byte tag) {
        CMD_CHANGE_LINE[2] = (byte) (horizontalSpeed & 0xff);
        CMD_CHANGE_LINE[3] = (byte) ((horizontalSpeed >> 8) & 0xff);
        int offsInt = Float.floatToIntBits(targetOffset);
        CMD_CHANGE_LINE[4] = (byte) (offsInt & 0xff);
        CMD_CHANGE_LINE[5] = (byte) ((offsInt >> 8) & 0xff);
        CMD_CHANGE_LINE[6] = (byte) ((offsInt >> 16) & 0xff);
        CMD_CHANGE_LINE[7] = (byte) ((offsInt >> 24) & 0xff);
        CMD_CHANGE_LINE[8] = hopIntent;
        CMD_CHANGE_LINE[9] = tag;
        sendCommand(CMD_SET_OFFSET);//zero
        sendCommand(CMD_CHANGE_LINE);
    }

    public static String toHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        final char[] hexArray = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] fromHex(String s) {
        if (s != null) {
            try {
                StringBuilder sb = new StringBuilder(s.length());
                for (int i = 0; i < s.length(); i++) {
                    char ch = s.charAt(i);
                    if (!Character.isWhitespace(ch)) {
                        sb.append(ch);
                    }
                }
                s = sb.toString();
                int len = s.length();
                byte[] data = new byte[len / 2];
                for (int i = 0; i < len; i += 2) {
                    int hi = (Character.digit(s.charAt(i), 16) << 4);
                    int low = Character.digit(s.charAt(i + 1), 16);
                    if (hi >= 256 || low < 0 || low >= 16) {
                        return null;
                    }
                    data[i / 2] = (byte) (hi | low);
                }
                return data;
            } catch (Exception ignored) {
            }
        }
        return null;
    }


}
