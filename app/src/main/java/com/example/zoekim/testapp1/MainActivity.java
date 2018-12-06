package com.example.zoekim.testapp1;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TextInputLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity
{
    //Constants
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_CONNECTED = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MAC_LEN = 17;

    private TextView connStatus;
    private ListView listView;
    private Dialog dialog;
    private View btnSend;
    private TextInputLayout textInputLayout;
    private ArrayAdapter<String> chatAdapter;
    private ArrayList<String> chatLog;
    private BluetoothAdapter btAdapter;
    private BluetoothDevice connectedDevice;
    private SharedPreferences prefs;

    private ConnectionManager connectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        prefs = getPreferences(MODE_PRIVATE);
        setContentView(R.layout.activity_main);
        connStatus = findViewById(R.id.status);
        listView = findViewById(R.id.list);
        textInputLayout = findViewById(R.id.input_layout);
        btnSend = findViewById(R.id.btn_send);

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null)
        {
            Toast.makeText(this, "This device does not support Bluetooth", Toast.LENGTH_SHORT).show();
            finish();
        }

        btnSend.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                if (textInputLayout.getEditText().getText().toString().equals(""))
                {
                    Toast.makeText(MainActivity.this, "Error: No text sent", Toast.LENGTH_SHORT).show();
                }
                else
                {
                    //TODO: here
                    sendMessage(textInputLayout.getEditText().getText().toString());
                    textInputLayout.getEditText().setText("");
                }
            }
        });

        chatLog = new ArrayList<>();
        chatAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, chatLog);
        listView.setAdapter(chatAdapter);

        FloatingActionButton btn_connect = findViewById(R.id.btnConn);
        btn_connect.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                ShowDialog();
            }
        });
    }

    private Handler handler = new Handler(new Handler.Callback()
    {

        @Override
        public boolean handleMessage(Message msg)
        {
            Gson gson = new Gson();
            SharedPreferences.Editor prefsEditor = prefs.edit();
            switch (msg.what)
            {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1)
                    {
                        case ConnectionManager.BT_CONNECTED:
                            connStatus.setText("Connected to: " + connectedDevice.getName());
                            break;
                        case ConnectionManager.BT_CONNECTING:
                            connStatus.setText("Connecting...");
                            break;
                        case ConnectionManager.BT_LISTEN:
                        case ConnectionManager.BT_NONE:
                            connStatus.setText("Disconnected");
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    String writeMessage = new String(writeBuf);
                    String history = prefs.getString(connectedDevice.getName(), "");
                    chatLog.clear();
                    if(history != "")
                    {
                        chatLog.addAll(gson.fromJson(history,ArrayList.class));
                    }
                    chatLog.add("Me: " + writeMessage);
                    String json = gson.toJson(chatLog);
                    prefsEditor.clear();
                    prefsEditor.putString(connectedDevice.getName(), json);
                    prefsEditor.commit();
                    chatAdapter.notifyDataSetChanged();
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    String retrieve = prefs.getString(connectedDevice.getName(), "");
                    chatLog.clear();
                    if(retrieve != "")
                    {
                        chatLog.addAll(gson.fromJson(retrieve, ArrayList.class));
                    }
                    chatLog.add(connectedDevice.getName() + ":  " + readMessage);
                    String json2 = gson.toJson(chatLog);
                    prefsEditor.clear();
                    prefsEditor.putString(connectedDevice.getName(), json2);
                    prefsEditor.commit();
                    chatAdapter.notifyDataSetChanged();
                    break;
                case MESSAGE_CONNECTED:
                    connectedDevice = msg.getData().getParcelable("device_name");
                    Toast.makeText(getApplicationContext(), "Connected to " + connectedDevice.getName(), Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString("toast"), Toast.LENGTH_SHORT).show();
                    break;
            }
            return false;
        }
    });

    private void ShowDialog()
    {
        dialog = new Dialog(this);
        dialog.setContentView(R.layout.layout_bluetooth);
        dialog.setTitle("Bluetooth Devices");

        ArrayAdapter<String> pairedDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        ListView listView = dialog.findViewById(R.id.pairedDeviceList);
        listView.setAdapter(pairedDevicesAdapter);

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();

        if (pairedDevices.size() > 0)
        {
            for (BluetoothDevice device : pairedDevices) {
                pairedDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        }


        listView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String contents = ((TextView) view).getText().toString();
                String devName = contents.substring(contents.length() - MAC_LEN);

                /* Should load chat history, doesn't appear to work, so commented out
                Gson gson = new Gson();
                String history = prefs.getString(devName, "");
                chatLog.clear();
                if(history != "")
                {
                    chatLog.addAll(gson.fromJson(history, ArrayList.class));
                }
                chatAdapter.notifyDataSetChanged();
                */

                BluetoothDevice device = btAdapter.getRemoteDevice(devName);
                connectionManager.connect(device);

                dialog.dismiss();
            }

        });

        dialog.findViewById(R.id.cancelButton).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                dialog.dismiss();
            }
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    //Associates Handler to connectionManager
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_ENABLE_BLUETOOTH)
        {
            if (resultCode == Activity.RESULT_OK) {
                connectionManager = new ConnectionManager(handler);
            } else {
                Toast.makeText(this, "Bluetooth Disabled", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    //Send data using connectionManager
    private void sendMessage(String message)
    {
        if (connectionManager.getState() != ConnectionManager.BT_CONNECTED)
        {
            Toast.makeText(this, "Connection Lost", Toast.LENGTH_SHORT).show();
            return;
        }

        if (message.length() > 0)
        {
            byte[] send = message.getBytes();
            connectionManager.write(send);
        }
    }

    //Check for BT enabled on app start
    @Override
    public void onStart()
    {
        super.onStart();
        if (!btAdapter.isEnabled())
        {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
        }
        else
        {
            connectionManager = new ConnectionManager(handler);
        }
    }

    //Start connection if connection state changed while out of app
    @Override
    public void onResume()
    {
        super.onResume();

        if ((connectionManager != null) && (connectionManager.getState() == ConnectionManager.BT_NONE))
            connectionManager.start();
    }

    //Stop connection if app closed
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (connectionManager != null)
            connectionManager.stop();
    }
}