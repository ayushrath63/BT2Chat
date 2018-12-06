package com.example.zoekim.testapp1;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/*
Class: ConnectionManager
Manages bluetooth connections and threads
 */
public class ConnectionManager
{
    private static final String appName = "BT2Chat";
    private static final UUID appUUID = UUID.fromString("85715ea5-ee6e-4086-a49c-b425d17192cd");

    private final BluetoothAdapter bluetoothAdapter;
    private final Handler handler;
    private AcceptThread acceptThread;
    private ConnectThread connectingThread;
    private ReadWriteThread rwThread;
    private int state;

    static final int BT_NONE = 0;
    static final int BT_LISTEN = 1;
    static final int BT_CONNECTING = 2;
    static final int BT_CONNECTED = 3;

    public ConnectionManager(Handler handler)
    {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        state = BT_NONE;
        this.handler = handler;
    }

    private synchronized void setState(int state)
    {
        this.state = state;
        handler.obtainMessage(MainActivity.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized int getState()
    {
        return state;
    }

    //Stops Connecting & R/W threads, Starts listening
    public synchronized void start()
    {
        if (connectingThread != null)
        {
            connectingThread.cancel();
            connectingThread = null;
        }

        if (rwThread != null)
        {
            rwThread.cancel();
            rwThread = null;
        }

        setState(BT_LISTEN);
        if (acceptThread == null)
        {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
    }

    //Stops Connecting Thread & R/W Thread, restarts connecting thread
    public synchronized void connect(BluetoothDevice device)
    {
        if (state == BT_CONNECTING)
        {
            if (connectingThread != null)
            {
                connectingThread.cancel();
                connectingThread = null;
            }
        }

        if (rwThread != null)
        {
            rwThread.cancel();
            rwThread = null;
        }

        connectingThread = new ConnectThread(device);
        connectingThread.start();
        setState(BT_CONNECTING);
    }

    //Starts R/W Thread, kills all other threads
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device)
    {
        if (connectingThread != null) {
            connectingThread.cancel();
            connectingThread = null;
        }

        if (rwThread != null)
        {
            rwThread.cancel();
            rwThread = null;
        }

        if (acceptThread != null)
        {
            acceptThread.cancel();
            acceptThread = null;
        }

        rwThread = new ReadWriteThread(socket);
        rwThread.start();

        Message msg = handler.obtainMessage(MainActivity.MESSAGE_CONNECTED);
        Bundle bundle = new Bundle();
        bundle.putParcelable("device_name", device);
        msg.setData(bundle);
        handler.sendMessage(msg);

        setState(BT_CONNECTED);
    }

    //Kills all threads
    public synchronized void stop()
    {
        if (connectingThread != null) {
            connectingThread.cancel();
            connectingThread = null;
        }

        if (rwThread != null)
        {
            rwThread.cancel();
            rwThread = null;
        }

        if (acceptThread != null)
        {
            acceptThread.cancel();
            acceptThread = null;
        }
        setState(BT_NONE);
    }

    public void write(byte[] out)
    {
        ReadWriteThread r;
        synchronized (this)
        {
            if (state != BT_CONNECTED)
                return;
            r = rwThread;
        }
        r.write(out);
    }

    private void handleFail()
    {
        Message msg = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Connection Failed");
        msg.setData(bundle);
        handler.sendMessage(msg);
        this.start();
    }

    private void handleConnectionLoss()
    {
        Message msg = handler.obtainMessage(MainActivity.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Device connection was lost");
        msg.setData(bundle);
        handler.sendMessage(msg);
        this.start();
    }

    //Accepts incoming connection
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread()
        {
            BluetoothServerSocket tmp = null;
            try
            {
                tmp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName, appUUID);
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
            serverSocket = tmp;
        }

        public void run()
        {
            setName("AcceptThread");
            BluetoothSocket socket;
            while (state != BT_CONNECTED)
            {
                try
                {
                    socket = serverSocket.accept();
                }
                catch (IOException e)
                {
                    break;
                }

                if (socket != null)
                {
                    synchronized (this)
                    {
                        switch (state)
                        {
                            case BT_LISTEN:
                            case BT_CONNECTING:
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case BT_NONE:
                            case BT_CONNECTED:
                                try
                                {
                                    socket.close();
                                }
                                catch (IOException e) {}
                                break;
                        }
                    }
                }
            }
        }

        public void cancel()
        {
            try
            {
                serverSocket.close();
            }
            catch (IOException e) {}
        }
    }

    //Handles Device Connection
    private class ConnectThread extends Thread
    {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        public ConnectThread(BluetoothDevice device)
        {
            this.device = device;
            BluetoothSocket tmp = null;
            try
            {
                tmp = device.createInsecureRfcommSocketToServiceRecord(appUUID);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            socket = tmp;
        }

        public void run()
        {
            setName("ConnectThread");
            bluetoothAdapter.cancelDiscovery();
            try
            {
                socket.connect();
            }
            catch (IOException e)
            {
                try
                {
                    socket.close();
                }
                catch (IOException e2) {}
                handleFail();
                return;
            }

            synchronized (this)
            {
                connectingThread = null;
            }

            connected(socket, device);
        }

        public void cancel()
        {
            try
            {
                socket.close();
            }
            catch (IOException e) {}
        }
    }

    //Manages BT Socket & I/O
    private class ReadWriteThread extends Thread
    {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ReadWriteThread(BluetoothSocket socket)
        {
            this.bluetoothSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try
            {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            }
            catch (IOException e) {}
            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run()
        {
            byte[] buffer = new byte[256];
            int bytes;

            while (true)
            {
                try
                {
                    bytes = inputStream.read(buffer);
                    handler.obtainMessage(MainActivity.MESSAGE_READ, bytes, -1,
                            buffer).sendToTarget();
                }
                catch (IOException e)
                {
                    handleConnectionLoss();
                    this.start();
                    break;
                }
            }
        }

        public void write(byte[] buffer)
        {
            try
            {
                outputStream.write(buffer);
                handler.obtainMessage(MainActivity.MESSAGE_WRITE, -1, -1,
                        buffer).sendToTarget();
            }
            catch (IOException e) {}
        }

        public void cancel()
        {
            try
            {
                bluetoothSocket.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }
}
