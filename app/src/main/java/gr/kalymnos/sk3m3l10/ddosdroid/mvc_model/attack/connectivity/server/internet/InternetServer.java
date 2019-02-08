package gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.attack.connectivity.server.internet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.attack.connectivity.server.Server;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.attack.connectivity.server.ServerHost;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.attack.connectivity.server.status.ServerStatusBroadcaster;
import gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Attack;
import gr.kalymnos.sk3m3l10.ddosdroid.pojos.bot.Bots;

import static gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Constants.Extra.EXTRA_ATTACK_HOST_UUID;
import static gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Constants.Extra.EXTRA_ATTACK_STARTED;

public class InternetServer extends Server {
    private BroadcastReceiver connectivityReceiver;

    public InternetServer(Context context, Attack attack) {
        super(context, attack);
        initializeConnectivityReceiver();
    }

    private void initializeConnectivityReceiver() {
        //  Is registered only after a successful start, which happens in onConstraintsResolved()
        connectivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (disconnectionHappened(intent)) {
                    ServerHost.Action.stopServer(context, getAttackedWebsite());
                    context.unregisterReceiver(this);
                }
            }

            private boolean disconnectionHappened(Intent intent) {
                return intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)
                        && intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
            }
        };
    }

    @Override
    public void start() {
        super.start();
        constraintsResolver.resolveConstraints();
    }

    @Override
    public void stop() {
        context.unregisterReceiver(connectivityReceiver);
        super.stop();
    }

    @Override
    public void onConstraintsResolved() {
        ServerStatusBroadcaster.broadcastRunning(getAttackedWebsite(), LocalBroadcastManager.getInstance(context));
        uploadAttack();
        registerReceiver();
    }

    private void uploadAttack() {
        attack.addSingleHostInfo(EXTRA_ATTACK_HOST_UUID, Bots.getLocalUser().getId());
        attack.addSingleHostInfo(EXTRA_ATTACK_STARTED, Attack.STARTED_PASS);
        repository.upload(attack);
    }

    private void registerReceiver() {
        context.registerReceiver(connectivityReceiver, getIntentFilter());
    }

    @NonNull
    private IntentFilter getIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        return filter;
    }

    @Override
    public void onConstraintResolveFailure() {
        ServerStatusBroadcaster.broadcastError(getAttackedWebsite(), LocalBroadcastManager.getInstance(context));
    }
}
