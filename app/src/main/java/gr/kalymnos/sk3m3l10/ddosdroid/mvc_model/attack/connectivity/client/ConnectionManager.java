package gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.attack.connectivity.client;

import android.content.Context;

import gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Attack;

import static gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Constants.NetworkType.BLUETOOTH;
import static gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Constants.NetworkType.INTERNET;
import static gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Constants.NetworkType.NSD;
import static gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Constants.NetworkType.WIFI_P2P;

/*  This class offloads the connection implementations from Client class.*/

abstract class ConnectionManager {
    private static final String TAG = "ConnectionManager";

    protected Attack attack;
    protected Context context;
    protected ConnectionListener connectionListener;

    interface ConnectionListener {
        void onConnected();

        void onConnectionError();

        void onDisconnected();
    }

    ConnectionManager(Context context, Attack attack) {
        initializeFields(context, attack);
    }

    private void initializeFields(Context context, Attack attack) {
        this.context = context;
        this.attack = attack;
    }

    void setConnectionListener(ConnectionListener connectionListener) {
        this.connectionListener = connectionListener;
    }

    abstract void connect();

    abstract void disconnect();

    protected void releaseResources() {
        context = null;
        connectionListener = null;
    }

    interface Factory {
        ConnectionManager create(Context context, Attack attack);
    }

    static class FactoryImp implements Factory {

        @Override
        public ConnectionManager create(Context context, Attack attack) {
            switch (attack.getNetworkType()) {
                case INTERNET:
                    return new InternetConnectionManager(context, attack);
                case BLUETOOTH:
                    return new BluetoothConnectionManager(context, attack);
                case WIFI_P2P:
                    return new WifiP2PConnectionManager(context, attack);
                case NSD:
                    return new NsdConnectionManager(context, attack);
                default:
                    throw new IllegalArgumentException(TAG + "Unknown attack network type");
            }
        }
    }

}
