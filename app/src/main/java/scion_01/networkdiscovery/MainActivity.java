package scion_01.networkdiscovery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, AdapterView.OnItemClickListener {

    private Button power, send, discover;
    private ListView listView;
    public TextView ReceivedMess, connectionStatus;
    private EditText SendMess;
    private WifiManager wifiManager;
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    private BroadcastReceiver broadcastReceiver;
    private IntentFilter intentFilter;
    private List<WifiP2pDevice> p2pDeviceList = new ArrayList<WifiP2pDevice>();
    private String [] deviceNameArray;
    private WifiP2pDevice [] deviceArray;
    static final int MESSAGE_READ=1;

    ClientClass clientClass;
    SendRecieve sendRecieve;
    ServerClass serverClass;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        //ignore these lines

        power = findViewById(R.id.onOff);
        send = findViewById(R.id.sendButton);
        discover = findViewById(R.id.discover);
        listView = findViewById(R.id.peerListView);
        ReceivedMess = findViewById(R.id.readMsg);
        connectionStatus = findViewById(R.id.connectionStatus);
        SendMess = findViewById(R.id.writeMsg);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = wifiP2pManager.initialize(getApplicationContext(),getMainLooper(),null);

        broadcastReceiver = new WiFiDirectBroadcastReceiver(wifiP2pManager,channel,this);
        intentFilter = new IntentFilter();

        intentFilter.addAction(wifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(wifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        intentFilter.addAction(wifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(wifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        if(wifiManager.isWifiEnabled())
            power.setText("OFF");
        else
            power.setText("ON");


        power.setOnClickListener(this);
        discover.setOnClickListener(this);
        listView.setOnItemClickListener(this);
        send.setOnClickListener(this);
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch(msg.what){
                case MESSAGE_READ:
                    byte [] readBuff = (byte[]) msg.obj;
                    String tempMsg= new String(readBuff,0,msg.arg1);
                    //msg.arg1 is length
                    ReceivedMess.setText(tempMsg);
                    break;
            }
            return true;
        }
    });

    @Override
    public void onClick(View v) {
        if(v == power){
            if(wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(false);
                //Toast.makeText(getApplicationContext(),"Wifi is now disabled",Toast.LENGTH_SHORT).show();
                power.setText("ON");
            }
            else {
                wifiManager.setWifiEnabled(true);
                //Toast.makeText(getApplicationContext(),"Wifi is now enabled",Toast.LENGTH_SHORT).show();
                power.setText("OFF");
            }
        }
        if(v== discover){
            wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    connectionStatus.setText("Discovery Started..");
                }

                @Override
                public void onFailure(int reason) {
                    connectionStatus.setText("Discovery Failed...");
                }
            });
        }
        if(v == send){
            String msg = SendMess.getText().toString();
            sendRecieve.write(msg.getBytes());
            SendMess.setText("");

            InputMethodManager imm = (InputMethodManager) this.getSystemService(MainActivity.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
            send.setText("Send");
        }
    }

    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peerList) {
            if(!peerList.getDeviceList().equals(p2pDeviceList)){
                p2pDeviceList.clear();
                p2pDeviceList.addAll(peerList.getDeviceList());
                deviceNameArray = new String[peerList.getDeviceList().size()];
                deviceArray = new WifiP2pDevice [peerList.getDeviceList().size()];
                int index =0;

                for(WifiP2pDevice device : peerList.getDeviceList()){
                    deviceNameArray[index] = device.deviceName;
                    deviceArray[index] = device;
                    index++;
                }
                ArrayAdapter<String > adapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_list_item_1,deviceNameArray);
                listView.setAdapter(adapter);
            }
            if (p2pDeviceList.size() ==0){
                Toast.makeText(getApplicationContext(),"No Devices Found",Toast.LENGTH_SHORT).show();
                return;
            }
        }
    };
    WifiP2pManager.ConnectionInfoListener connectionInfoListener = new WifiP2pManager.ConnectionInfoListener() {
        @Override
        public void onConnectionInfoAvailable(WifiP2pInfo info) {
            final InetAddress groupOwnerAddress  =info.groupOwnerAddress;

            if(info.groupFormed && info.isGroupOwner){
                connectionStatus.setText("Your are the host!");
                serverClass= new ServerClass();
                serverClass.start();
                //to start the thread
            }
            else if(info.groupFormed){
                connectionStatus.setText("Your are a client!");
                clientClass = new ClientClass(groupOwnerAddress);
                clientClass.start();
            }
        }
    };
    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(broadcastReceiver,intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(broadcastReceiver);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final WifiP2pDevice  device = deviceArray[position];
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;

        wifiP2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(getApplicationContext(),"Connected to "+device.deviceName.toString(),Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(getApplicationContext(),"Not able to connect...",Toast.LENGTH_SHORT).show();
            }
        });
    }
    public class ServerClass extends Thread{
        Socket socket;
        ServerSocket serverSocket;

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(8888);
                socket= serverSocket.accept();
                sendRecieve = new SendRecieve(socket);
                sendRecieve.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private class SendRecieve extends Thread{
        private Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public SendRecieve(Socket skt){
            socket = skt;
            try {
                inputStream =socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while(socket!=null){
                try {
                    bytes = inputStream.read(buffer);
                    if(bytes>0){
                        handler.obtainMessage(MESSAGE_READ,bytes,-1,buffer).sendToTarget();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        public void write(byte[] bytes){
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public class ClientClass extends Thread{
        Socket socket;
        String hostAdd;
        public ClientClass(InetAddress hostAddress){
            hostAdd = hostAddress.getHostAddress();
            socket = new Socket();
        }

        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostAdd,8888),2000);
                sendRecieve = new SendRecieve(socket);
                sendRecieve.start();

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}
