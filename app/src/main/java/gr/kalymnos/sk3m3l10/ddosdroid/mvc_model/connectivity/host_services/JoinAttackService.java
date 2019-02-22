package gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.connectivity.host_services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import java.util.Map;

import gr.kalymnos.sk3m3l10.ddosdroid.R;
import gr.kalymnos.sk3m3l10.ddosdroid.constants.Extras;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_controllers.activities.AllAttackListsActivity;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.connectivity.client.Client;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.persistance.attack.AttackRepository;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.persistance.attack.FirebaseRepository;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.persistance.job.JobPersistance;
import gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.persistance.job.PrefsJobPersistance;
import gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Attack;
import gr.kalymnos.sk3m3l10.ddosdroid.pojos.attack.Attacks;
import gr.kalymnos.sk3m3l10.ddosdroid.pojos.bot.Bots;

import static gr.kalymnos.sk3m3l10.ddosdroid.constants.ContentTypes.FETCH_ONLY_USER_JOINED_ATTACKS;
import static gr.kalymnos.sk3m3l10.ddosdroid.mvc_model.connectivity.host_services.JoinAttackService.ForegroundNotification.NOTIFICATION_ID;

public class JoinAttackService extends Service implements Client.ClientConnectionListener,
        AttackRepository.OnRepositoryChangeListener, JobPersistance.OnJobPersistanceListener {
    private static final String TAG = "JoinAttackService";

    private AttackRepository attackRepo;
    private JobPersistance jobPersit;

    @Override
    public void onCreate() {
        super.onCreate();
        initRepos();
        attackRepo.startListenForChanges();
    }

    private void initRepos() {
        attackRepo = new FirebaseRepository();
        attackRepo.setOnRepositoryChangeListener(this);
        jobPersit = new PrefsJobPersistance(getSharedPreferences(JobPersistance.FILE_NAME, MODE_PRIVATE));
        jobPersit.setOnJobPersistanceListener(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Attack attack = intent.getParcelableExtra(Extras.EXTRA_ATTACK);
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
        if (jobPersit.has(attack.getWebsite())) {
            Toast.makeText(this, getString(R.string.already_attacking_label) + " " + attack.getWebsite(), Toast.LENGTH_SHORT).show();
        } else {
            Client client = new Client(this, attack);
            client.setClientConnectionListener(this);
            client.connect();
        }
    }

    private void handleStopAttackAction(Attack attack) {
        String key = attack.getWebsite();
        Client client = clients.get(key);
        clients.remove(key);
        client.disconnect();
    }

    @Override
    public void onClientConnected(String key) {
        Client client = clients.get(key);
        updateAttackWithCurrentUser(client.getAttack());
        //  Better to startForeground when update() returned
        startForeground(NOTIFICATION_ID, new ForegroundNotification().createNotification());
    }

    private void updateAttackWithCurrentUser(Attack attack) {
        Attacks.addBot(attack, Bots.local());
        attackRepo.update(attack);
    }

    @Override
    public void onClientConnectionError(String key) {
        clients.remove(key);
        displayErrorToastOnUIThread();
    }

    private void displayErrorToastOnUIThread() {
        Runnable displayToast = () -> Toast.makeText(this, R.string.client_connection_error_msg, Toast.LENGTH_SHORT).show();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(displayToast);
    }

    @Override
    public void onClientDisconnected(Attack attack) {
        //  Not called from main thread - not removing client here because of concurrent connection exception.
        updateAttackWithoutCurrentUser(attack);
        if (clients.size() == 0)
            stopSelf();
    }

    private void updateAttackWithoutCurrentUser(Attack attack) {
        attack.getBotIds().remove(Bots.localId());
        attackRepo.update(attack);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        attackRepo.stopListenForChanges();
        disconnectClients();
        clients.clear();
    }

    private void disconnectClients() {
        for (Map.Entry<String, Client> entry : clients.entrySet())
            entry.getValue().disconnect();
    }

    @Override
    public void onAttackDelete(Attack deletedAttack) {
        if (clients.containsKey(deletedAttack.getWebsite()))
            Action.leave(deletedAttack, this);
    }

    @Override
    public void onAttackUpload(Attack uploadedAttack) {
    }

    @Override
    public void onAttackUpdate(Attack changedAttack) {
    }

    @Override
    public void onJobSave(String jobTag) {

    }

    @Override
    public void onJobSaveError(String jobTag) {

    }

    @Override
    public void onJobDelete(String jobTag) {

    }

    public static class Action {
        private static final String ACTION_START_ATTACK = TAG + "start attack action";
        private static final String ACTION_STOP_ATTACK = TAG + "stop attack action";
        private static final String ACTION_STOP_SERVICE = TAG + "stop service action";

        public static void join(Attack attack, Context context) {
            context.startService(createIntentWithAttackExtra(context, attack, ACTION_START_ATTACK));
        }

        public static void leave(Attack attack, Context context) {
            context.startService(createIntentWithAttackExtra(context, attack, ACTION_STOP_ATTACK));
        }

        private static Intent createIntentWithAttackExtra(Context context, Attack attack, String action) {
            Intent intent = new Intent(context, JoinAttackService.class);
            intent.setAction(action);
            intent.putExtra(Extras.EXTRA_ATTACK, attack);
            return intent;
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
            return new NotificationCompat.Builder(JoinAttackService.this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_fist)
                    .setContentTitle(getString(R.string.client_notification_title))
                    .setContentText(getString(R.string.client_notification_small_text))
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.client_notification_big_text)))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(getContentPendingIntent())
                    .addAction(R.drawable.ic_stop, getString(R.string.shutdown_label), getStopServicePendingIntent());
        }

        PendingIntent getContentPendingIntent() {
            Intent intent = AllAttackListsActivity.Action.createIntent(JoinAttackService.this, FETCH_ONLY_USER_JOINED_ATTACKS, R.string.joined_attacks_label);
            return PendingIntent.getActivity(JoinAttackService.this, CONTENT_INTENT_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        PendingIntent getStopServicePendingIntent() {
            Intent intent = new Intent(JoinAttackService.this, JoinAttackService.class);
            intent.setAction(Action.ACTION_STOP_SERVICE);
            return PendingIntent.getService(JoinAttackService.this, STOP_INTENT_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }
}