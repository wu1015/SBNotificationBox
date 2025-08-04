package com.wu1015.sbnotificationbox.bluetoothsend;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

public class DeviceListAdapter extends ArrayAdapter<BluetoothDevice> {
    public DeviceListAdapter(Context context, List<BluetoothDevice> devices) {
        super(context, android.R.layout.simple_list_item_1, devices);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        BluetoothDevice device = getItem(position);
        if (convertView == null)
            convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        ((TextView) convertView.findViewById(android.R.id.text1)).setText(device.getName() + "\n" + device.getAddress());
        return convertView;
    }
}
