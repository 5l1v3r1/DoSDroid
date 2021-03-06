package gr.kalymnos.sk3m3l10.ddosdroid.mvc_controllers.connectivity.client.wifi.wifi_p2p;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.support.annotation.NonNull;
import android.util.Log;

import java.net.InetAddress;

import gr.kalymnos.sk3m3l10.ddosdroid.mvc_controllers.connectivity.client.wifi.SocketConnectionThread;
import gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Attacks;

import static android.net.wifi.p2p.WifiP2pManager.EXTRA_WIFI_STATE;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION;
import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_STATE_ENABLED;
import static gr.kalymnos.sk3m3l10.ddosdroid.utils.connectivity.WifiP2pUtil.failureTextFrom;

public class WifiDirectReceiver extends BroadcastReceiver implements SocketConnectionThread.OnServerResponseListener {
    private static final String TAG = "WifiDirectReceiver";

    private WifiP2PServerConnection serverConnection;
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;

    public WifiDirectReceiver(WifiP2PServerConnection serverConnection, WifiP2pManager manager, WifiP2pManager.Channel channel) {
        this.serverConnection = serverConnection;
        this.manager = manager;
        this.channel = channel;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case WIFI_P2P_STATE_CHANGED_ACTION:
                Log.d(TAG, "WIFI_P2P_STATE_CHANGED_ACTION");
                int state = intent.getIntExtra(EXTRA_WIFI_STATE, -1);
                handleWifiState(state);
                break;
            case WIFI_P2P_PEERS_CHANGED_ACTION:
                Log.d(TAG, "WIFI_P2P_PEERS_CHANGED_ACTION");
                manager.requestPeers(channel, getPeerListListener());
                break;
            case WIFI_P2P_CONNECTION_CHANGED_ACTION:
                Log.d(TAG, "WIFI_P2P_CONNECTION_CHANGED_ACTION");
                handleConnectionChange(intent);
                break;
            default:
                throw new IllegalArgumentException(TAG + ": Unknown action");
        }
    }

    private void handleWifiState(int state) {
        if (state == WIFI_P2P_STATE_ENABLED) {
            manager.discoverPeers(channel, getPeerDiscoveryActionListener());
        } else {
            serverConnection.connectionListener.onServerConnectionError();
        }
    }

    @NonNull
    private WifiP2pManager.ActionListener getPeerDiscoveryActionListener() {
        return new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Initiating discovering peers process...");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "Initiating discovering peers process: " + failureTextFrom(reason));
                serverConnection.connectionListener.onServerConnectionError();
            }
        };
    }

    @NonNull
    private WifiP2pManager.PeerListListener getPeerListListener() {
        return wifiP2pDeviceList -> {
            for (WifiP2pDevice device : wifiP2pDeviceList.getDeviceList()) {
                if (isServer(device)) {
                    Log.d(TAG, "Found server peer with name: " + device.deviceName + " and address: " + device.deviceAddress);
                    connectTo(device);
                    return;
                }
            }
            if (serverConnection.connectionListener != null)
                serverConnection.connectionListener.onServerConnectionError();
        };
    }

    private boolean isServer(WifiP2pDevice device) {
        String address = device.deviceAddress;
        String serverAddress = Attacks.getHostMacAddress(serverConnection.attack);
        return address.equals(serverAddress) && device.isGroupOwner();

    }

    private void connectTo(WifiP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        manager.connect(channel, config, getDeviceConnectionActionListener());
    }

    @NonNull
    private WifiP2pManager.ActionListener getDeviceConnectionActionListener() {
        return new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Initiated connection with server device...");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "Connection initiation with server device failed: " + failureTextFrom(reason));
                //  Don't broadcast a server connection error here, onReceive() may called again to establish a connection.
            }
        };
    }

    private void handleConnectionChange(Intent intent) {
        NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
        if (networkInfo.isConnected()) {
            Log.d(TAG, "Local device is connected with server device, requesting connection info");
            manager.requestConnectionInfo(channel, getConnectionInfoListener());
        } else {
            Log.d(TAG, "NetworkInfo.isConnected() returned false.");
        }
    }

    @NonNull
    private WifiP2pManager.ConnectionInfoListener getConnectionInfoListener() {
        return wifiP2pInfo -> {
            if (wifiP2pInfo.groupFormed) {
                Log.d(TAG, "Starting a connection thread");
                SocketConnectionThread thread = createConnectionThread(wifiP2pInfo);
                thread.start();
            }
        };
    }

    @NonNull
    private SocketConnectionThread createConnectionThread(WifiP2pInfo wifiP2pInfo) {
        InetAddress groupOwnerAddress = wifiP2pInfo.groupOwnerAddress;
        int hostLocalPort = Attacks.getHostLocalPort(serverConnection.attack);
        SocketConnectionThread thread = new SocketConnectionThread(groupOwnerAddress, hostLocalPort);
        thread.setServerResponseListener(this);
        return thread;
    }

    @Override
    public void onValidServerResponse() {
        Log.d(TAG, "Received server response");
        serverConnection.connectionListener.onServerConnection();
    }

    @Override
    public void onErrorServerResponse() {
        Log.d(TAG, "Did not receive response from server");
        serverConnection.connectionListener.onServerConnectionError();
    }

    public void releaseWifiP2pResources() {
        manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Initiated group removal");
            }

            @Override
            public void onFailure(int reason) {
                Log.d(TAG, "Failed to initiate group removal: " + failureTextFrom(reason));
            }
        });
    }

    @NonNull
    public static IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WIFI_P2P_CONNECTION_CHANGED_ACTION);
        return filter;
    }
}
