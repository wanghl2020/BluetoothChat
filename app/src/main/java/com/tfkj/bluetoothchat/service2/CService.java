/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tfkj.bluetoothchat.service2;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.tfkj.bluetoothchat.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread that listens for
 * incoming connections, a thread for connecting with a device, and a
 * thread for performing data transmissions when connected.
 */
public class CService {
    // Debugging
    private static final String TAG = "BluetoothChatService";

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ConnectThread mConnectThread;
//    private ConnectedThread mConnectedThread;
    private Context mContext;
    private int mState;
    private int mNewState;

    //所有接入的服务端连接线程
    private HashMap<String, ConnectedThread> clientConnectedThread = new HashMap<>();


    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    public CService(Context context, Handler handler) {
        mContext = context;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = Constants.STATE_NONE;
        mNewState = mState;
        mHandler = handler;
    }

    /**
     * Update UI title according to the current state of the chat connection
     */
    private synchronized void updateUserInterfaceTitle() {
        mState = getState();
        Log.d(TAG, "updateUserInterfaceTitle() " + mNewState + " -> " + mState);
        mNewState = mState;

        // Give the new state to the Handler so the UI Activity can update
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, mNewState, -1).sendToTarget();
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return mState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
//        if (mConnectedThread != null) {
//            mConnectedThread.cancel();
//            mConnectedThread = null;
//        }

        // Update UI title
        updateUserInterfaceTitle();
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (mState == Constants.STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
//        if (mConnectedThread != null) {
//            mConnectedThread.cancel();
//            mConnectedThread = null;
//        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();
        // Update UI title
        updateUserInterfaceTitle();
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
//        if (mConnectedThread != null) {
//            mConnectedThread.cancel();
//            mConnectedThread = null;
//        }

        // Start the thread to manage the connection and perform transmissions
        ConnectedThread mConnectedThread = new ConnectedThread(socket, socketType, device);
        mConnectedThread.start();

        clientConnectedThread.put(device.getAddress(), mConnectedThread);

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        // Update UI title
        updateUserInterfaceTitle();
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");

        //关闭所有客户端线程
        for (ConnectedThread connectedThread : clientConnectedThread.values()) {
            connectedThread.cancel();
        }
        clientConnectedThread.clear();

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

//        if (mConnectedThread != null) {
//            mConnectedThread.cancel();
//            mConnectedThread = null;
//        }

        mState = Constants.STATE_NONE;
        // Update UI title
        updateUserInterfaceTitle();
    }

//    /**
//     * Write to the ConnectedThread in an unsynchronized manner
//     *
//     * @param out The bytes to write
//     * @see ConnectedThread#write(byte[])
//     */
//    public void write(byte[] out) {
//        // Create temporary object
//        ConnectedThread r;
//        // Synchronize a copy of the ConnectedThread
//        synchronized (this) {
//            if (mState != Constants.STATE_CONNECTED) return;
//            r = mConnectedThread;
//        }
//        // Perform the write unsynchronized
//        r.write(out);
//    }


    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[], boolean)
     */
    public void write(String clientDeviceAddress, byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != Constants.STATE_CONNECTED) return;
            r = clientConnectedThread.get(clientDeviceAddress);
        }
        // Perform the write unsynchronized
        if (r != null) {
            r.write(out, true);
            mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, out)
                    .sendToTarget();
        } else {
            Toast.makeText(mContext, "当前客户端已断开", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 服务端发布公告
     *
     * @param out
     */
    public void writeToAllClient(byte[] out, boolean isUpdateUI) {
        for (Map.Entry<String, ConnectedThread> item : clientConnectedThread.entrySet()) {
            item.getValue().write(out, true);
        }

        if (isUpdateUI) {
            mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, out)
                    .sendToTarget();
        }
    }


    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = Constants.STATE_NONE;
        // Update UI title
        updateUserInterfaceTitle();

        // Start the service over to restart listening mode
        CService.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        mState = Constants.STATE_NONE;
        // Update UI title
        updateUserInterfaceTitle();

        // Start the service over to restart listening mode
        CService.this.start();
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (secure) {
                    tmp = device.createRfcommSocketToServiceRecord(
                            Constants.MY_UUID_SECURE);
                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(
                            Constants.MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);
            }
            mmSocket = tmp;
            mState = Constants.STATE_CONNECTING;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);

            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + mSocketType +
                            " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (CService.this) {
                mConnectThread = null;
            }

            // Start the connected thread


            connected(mmSocket, mmDevice, mSocketType);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final BluetoothDevice mmBluetoothDevice;

        public ConnectedThread(BluetoothSocket socket, String socketType, BluetoothDevice bluetoothDevice) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmBluetoothDevice = bluetoothDevice;
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            mState = Constants.STATE_CONNECTED;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (mState == Constants.STATE_CONNECTED) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    for (Map.Entry<String, ConnectedThread> item : clientConnectedThread.entrySet()) {
                        if (!item.getKey().equals(mmBluetoothDevice.getAddress())) {
                            item.getValue().write(buffer, true);
                        }
                    }

                    // Send the obtained bytes to the UI Activity
                    mHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

//        /**
//         * Write to the connected OutStream.
//         *
//         * @param buffer The bytes to write
//         */
//        public void write(byte[] buffer) {
//            try {
//                mmOutStream.write(buffer);
//
//                // Share the sent message back to the UI Activity
//                mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer)
//                        .sendToTarget();
//            } catch (IOException e) {
//                Log.e(TAG, "Exception during write", e);
//            }
//        }
//
//        public void cancel() {
//            try {
//                mmSocket.close();
//            } catch (IOException e) {
//                Log.e(TAG, "close() of connect socket failed", e);
//            }
//        }
//    }
//}


        /**
         * Write to the connected OutStream.
         *
         * @param buffer     The bytes to write
         * @param isUpdateUI 控制是否作为输出方打印到UI界面，转发消息给其他客户端时不需要
         */
        public void write(byte[] buffer, boolean isUpdateUI) {
            try {
                mmOutStream.write(buffer);

                if (isUpdateUI) {
                    // Share the sent message back to the UI Activity
//                    mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer)
//                            .sendToTarget();
                }
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
