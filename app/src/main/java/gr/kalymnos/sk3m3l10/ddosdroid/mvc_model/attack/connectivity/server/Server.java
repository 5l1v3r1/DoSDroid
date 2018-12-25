package gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.attack.connectivity.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.attack.connectivity.server.internet.InternetServer;
import gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Attack;

import static gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Constants.NetworkType.BLUETOOTH;
import static gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Constants.NetworkType.INTERNET;
import static gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Constants.NetworkType.NSD;
import static gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Constants.NetworkType.WIFI_P2P;

public abstract class Server {
    protected static final String TAG = "Server";
    public static final String EXTRA_ID = TAG + "extra id";
    protected static final int THREAD_POOL_SIZE = 10;

    private Attack attack;
    private ExecutorService executor;

    public Server(Attack attack) {
        this.attack = attack;
        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    public final String getId() {
        return attack.getPushId();
    }

    public abstract void start();

    public void stop() {
        shutdownThreadPool();
    }

    private void shutdownThreadPool() {
        // https://www.baeldung.com/java-executor-service-tutorial
        executor.shutdown();
        try {
            if (executor.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;

        if (!(obj instanceof Server))
            return false;

        Server server = (Server) obj;
        return this.getId().equals(server.getId());
    }

    //  This technique is taken from the book Effective Java, Second Edition, Item 9
    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + getId().hashCode();
        return result;
    }

    public interface Builder {
        Server build(Attack attack);
    }

    public static class BuilderImp implements Server.Builder {

        @Override
        public Server build(Attack attack) {
            switch (attack.getNetworkType()) {
                case INTERNET:
                    return new InternetServer(attack);
                case BLUETOOTH:
                    return new BluetoothServer(attack);
                case WIFI_P2P:
                    return new WifiP2pServer(attack);
                case NSD:
                    return new NsdServer(attack);
                default:
                    throw new IllegalArgumentException(TAG + ": Unknown attack network type");
            }
        }
    }
}
