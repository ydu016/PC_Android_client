package com.example.csi3104.myapplication6181;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.apache.http.conn.util.InetAddressUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static String LOG_TAG = "WifiBroadcastActivity";
    private boolean start = true;
    private EditText IPAddress, reader, textResponse, msg;
    private String address;
    public static final int DEFAULT_PORT = 43708;
    private static final int MAX_DATA_PACKET_LENGTH = 40;
    private byte[] buffer = new byte[MAX_DATA_PACKET_LENGTH];
    private byte[] buffer2 = new byte[MAX_DATA_PACKET_LENGTH];
    Button startButton, stopButton, send, disconnect,sender;

    private Handler handler;
    private boolean akb = true;

    Socket socket = null;
    PrintWriter out = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        IPAddress = (EditText) this.findViewById(R.id.address);
        reader = (EditText) this.findViewById(R.id.editText2);
        startButton = (Button) this.findViewById(R.id.start);
        stopButton = (Button) this.findViewById(R.id.stop);
        send = (Button) this.findViewById(R.id.send);
        sender = (Button) this.findViewById(R.id.sendr);
        disconnect = (Button) this.findViewById(R.id.disconnect);
        textResponse = (EditText)findViewById(R.id.response);
        msg = (EditText)findViewById(R.id.toSend);

        startButton.setEnabled(true);
        stopButton.setEnabled(false);

        handler = new Handler() {

            public void handleMessage(Message msg) {

                String add = (String) msg.obj;

                if (add != address && validate(add)) {
                    reader.setText(add);
                    start = false;
                    startButton.setEnabled(true);
                    stopButton.setEnabled(false);
                }
            }
        };

        address = getLocalIPAddress();
        if (address != null) {
            IPAddress.setText(address);
        } else {
            IPAddress.setText("Can not get IP address");
            return;
        }

        startButton.setOnClickListener(listener);
        stopButton.setOnClickListener(listener);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MyClientTask myClientTask = new MyClientTask(
                        reader.getText().toString(),
                        6666);
                myClientTask.execute();
            }
        });
        disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    socket.close();
                }catch(Exception e){}
            }
        });

        sender.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    out.println(msg.getText());
                } catch (Exception e) {
                }
            }
        });

    }

    private static final String PATTERN =
            "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";

    public static boolean validate(final String ip) {

        Pattern pattern = Pattern.compile(PATTERN);
        Matcher matcher = pattern.matcher(ip);
        return matcher.matches();
    }


    private View.OnClickListener listener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (v == startButton) {
                reader.setText("");
                start = true;
                new BroadCastUdp(address + "akbbbbbbbbbbbbbbbbbbbb").start();
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
            } else if (v == stopButton) {
                reader.setText("");
                start = false;
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
            }
        }
    };

    private String getLocalIPAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress().toString();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e(LOG_TAG, ex.toString());
        }
        return null;
    }

    public class BroadCastUdp extends Thread {
        private String dataString;
        private DatagramSocket udpSocket;

        public BroadCastUdp(String dataString) {
            this.dataString = dataString;
        }

        int second = 0;

        public void run() {
            DatagramPacket dataPacket = null;
            DatagramPacket dataPacket2 = null;

            try {
                udpSocket = new DatagramSocket(DEFAULT_PORT);

                dataPacket = new DatagramPacket(buffer, MAX_DATA_PACKET_LENGTH);
                dataPacket2 = new DatagramPacket(buffer2, MAX_DATA_PACKET_LENGTH);

                byte[] data = dataString.getBytes();
                dataPacket.setData(data);
                dataPacket.setLength(data.length);
                dataPacket.setPort(DEFAULT_PORT);

                dataPacket2.setPort(DEFAULT_PORT);

                InetAddress broadcastAddr;

                broadcastAddr = InetAddress.getByName("255.255.255.255");
                dataPacket.setAddress(broadcastAddr);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.toString());
            }
            while (start) {
                try {
                    if (akb) {
                        udpSocket.send(dataPacket);
                        udpSocket.receive(dataPacket2);

                        String result = new String(dataPacket2.getData(), dataPacket2.getOffset(), dataPacket2.getLength());
                        Message msg = new Message();
                        msg.obj = result;
                        handler.sendMessage(msg);
                    }
                    sleep(500);

                } catch (Exception e) {
                    Log.e(LOG_TAG, e.toString());
                }
            }

            udpSocket.close();
        }
    }

    public class MyClientTask extends AsyncTask<Void, Void, Void> {

        String dstAddress;
        int dstPort;
        String response = "";
        InputStream is = null;

        MyClientTask(String addr, int port){
            dstAddress = addr;
            dstPort = port;
        }

        @Override
        protected Void doInBackground(Void... arg0) {

            //Socket socket = null;
            try {
                socket = new Socket(dstAddress, dstPort);
                out = new PrintWriter(socket.getOutputStream(), true);
                is = socket.getInputStream();
                ByteArrayOutputStream byteArrayOutputStream =
                        new ByteArrayOutputStream(1024);
                byte[] buffer = new byte[1024];

                int bytesRead;
                InputStream inputStream = socket.getInputStream();
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

    /*
     * notice:
     * inputStream.read() will block if no data return
     */
                /**
                while ((bytesRead = inputStream.read(buffer)) != -1){
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                    response += byteArrayOutputStream.toString("UTF-8");
                }
                 **/
                while(true){
                    int temp = is.read(buffer);
                    String str=new String(buffer).trim();
                    response+=str;
                    if(response.length() > 0){
                        break;
                    }
                }

            } catch (UnknownHostException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                response = "UnknownHostException: " + e.toString();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                response = "IOException: " + e.toString();
            }finally{
                if(socket != null){
                    try {
                        //socket.close();
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            textResponse.setText(response);
            super.onPostExecute(result);
        }

    }

}