package app.grapheneos.gmscompat;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.app.Application;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.internal.gmscompat.GmsCompatApp;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class App extends Application {
    private static Context ctx;
    private static Context deviceProtectedStorageContext;
    private static NotificationManager notificationManager;
    private static SharedPreferences preferences;
    private static Thread mainThread;
    private static Executor bgExecutor;

    public void onCreate() {
        super.onCreate();
        maybeInit(this);
    }

    static void maybeInit(Context componentContext) {
        if (ctx != null) {
            return;
        }
        ctx = componentContext.getApplicationContext();
        mainThread = Thread.currentThread();
        bgExecutor = Executors.newSingleThreadExecutor();

        if (GmsCompatApp.PKG_NAME.equals(Application.getProcessName())) {
            // main process
            deviceProtectedStorageContext = ctx.createDeviceProtectedStorageContext();
            preferences = deviceProtectedStorageContext
                    .getSharedPreferences(MAIN_PROCESS_PREFS_FILE, MODE_PRIVATE);
            notificationManager = ctx.getSystemService(NotificationManager.class);
            Notifications.createNotificationChannels();

            new ConfigUpdateReceiver(ctx);

            // Enable contacts sync by default for Google accounts
            setupContactsSyncForGoogleAccounts(ctx);
        }
    }

    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";
    private static final String CONTACTS_AUTHORITY = "com.google.android.gms.people";

    private static void setupContactsSyncForGoogleAccounts(Context ctx) {
        AccountManager accountManager = AccountManager.get(ctx);

        // Enable sync for any existing Google accounts
        enableContactsSyncForGoogleAccounts(accountManager);

        // Listen for new accounts being added
        accountManager.addOnAccountsUpdatedListener(
            accounts -> enableContactsSyncForGoogleAccounts(accountManager),
            null, // handler (null = main thread)
            true  // updateImmediately
        );
    }

    private static void enableContactsSyncForGoogleAccounts(AccountManager accountManager) {
        Account[] googleAccounts = accountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE);
        for (Account account : googleAccounts) {
            // Only enable if not already configured (isSyncable == -1 means "unknown/not set")
            int syncable = ContentResolver.getIsSyncable(account, CONTACTS_AUTHORITY);
            if (syncable < 1) {
                ContentResolver.setIsSyncable(account, CONTACTS_AUTHORITY, 1);
                ContentResolver.setSyncAutomatically(account, CONTACTS_AUTHORITY, true);
            }
        }
    }

    public static Context ctx() {
        return ctx;
    }

    public static Context deviceProtectedStorageContext() {
        return deviceProtectedStorageContext;
    }

    public static NotificationManager notificationManager() {
        return notificationManager;
    }

    public static SharedPreferences preferences() {
        return preferences;
    }

    public static Thread mainThread() {
        return mainThread;
    }

    public static Executor bgExecutor() {
        return bgExecutor;
    }

    private static final String MAIN_PROCESS_PREFS_FILE = "prefs";

    public interface MainProcessPrefs {
        String LOCATION_REQUEST_REDIRECTION_ENABLED = "enabled_redirections"; // historical name

        String GmsCore_POWER_EXEMPTION_PROMPT_DISMISSED = "GmsCore_power_exemption_prompt_dismissed_2";
        String GmsCore_BACKGROUND_DATA_EXEMPTION_PROMPT_DISMISSED = "GmsCore_background_data_exemption_prompt_dismissed";
        String NOTIFICATION_DO_NOT_SHOW_AGAIN_PREFIX = "do_not_show_notification_";
    }
}
