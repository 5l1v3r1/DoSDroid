package gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.attack.connectivity.server;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.util.HashSet;
import java.util.Set;

import gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Attack;

import static gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Constants.Extra.EXTRA_ATTACK;

public class ServersHost extends Service {
    private static final String TAG = "ServersHost";
    public static final String ACTION_START_SERVER = TAG + "start server action";
    public static final String ACTION_STOP_SERVER = TAG + "stop server action";

    private Set<Server> servers;

    public ServersHost() {
        servers = new HashSet<>();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        switch (intent.getAction()) {
            case ACTION_START_SERVER:
                handleStartServerAction(intent);
                return START_STICKY;
            case ACTION_STOP_SERVER:
                handleStopServerAction(intent);
            default:
                return super.onStartCommand(intent, flags, startId);
        }
    }

    private void handleStartServerAction(Intent intent) {
        Server server = createServerFrom(intent);
        boolean serverAdded = servers.add(server);
        if (serverAdded) {
            server.start();
        }
    }

    private Server createServerFrom(Intent intent) {
        Attack attack = intent.getParcelableExtra(EXTRA_ATTACK);
        return new Server.BuilderImp().build(attack);
    }

    private void handleStopServerAction(Intent intent) {
        String serverId = intent.getStringExtra(Server.EXTRA_ID);
        stopAndRemoveServerFromCollection(serverId);
    }

    private void stopAndRemoveServerFromCollection(String serverId) {
        for (Server server : servers) {
            if (server.getId().equals(serverId)) {
                server.stop();
                servers.remove(server);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }


}
