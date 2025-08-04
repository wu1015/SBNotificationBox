package com.wu1015.sbnotificationbox.bluetoothsend;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;

public class BluetoothServerThread extends Thread {
    private final BluetoothServerSocket serverSocket;
    private static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");

    public BluetoothServerThread() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothServerSocket tmp = null;
        try {
            tmp = adapter.listenUsingRfcommWithServiceRecord("BTServer", MY_UUID);
        } catch (IOException e) {
            e.printStackTrace();
        }
        serverSocket = tmp;
    }

    public void run() {
        try (BluetoothSocket socket = serverSocket.accept()) {
            InputStream in = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String msg = reader.readLine();
            Log.d("BluetoothServer", "Received: " + msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void cancel() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

