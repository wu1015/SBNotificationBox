package com.wu1015.sbnotificationbox.bluetoothsend;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class BluetoothClientThread extends Thread {
    private final BluetoothDevice device;
    private final String message;
    private static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    public BluetoothClientThread(BluetoothDevice device, String message) {
        this.device = device;
        this.message = message;
    }

    public void run() {
        try (BluetoothSocket socket = device.createRfcommSocketToServiceRecord(MY_UUID)) {
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
            socket.connect();

            OutputStream out = socket.getOutputStream();
            out.write((message + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}


