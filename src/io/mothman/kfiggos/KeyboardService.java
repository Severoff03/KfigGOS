// Made by Mothman
package io.mothman.kfiggos;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.UserManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class KeyboardService extends Service {
    private static final String CHANNEL = "keyboard_guard";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences preferences;
    private UserManager users;
    private KeyguardManager keyguard;
    private InputMethodManager keyboards;
    private KeyboardState saved;
    private KeyboardState pending;
    private boolean restoring;
    private boolean listening;
    private int attempt;
    private int restoreWrites;
    private long retryAfter;
    private String status = "Starting";
    private final Runnable remember = this::remember;
    private final Runnable restore = this::restore;
    private final Runnable unlocked = this::beginRestore;

    private final ContentObserver observer = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange, Uri uri) {
            if (restoring || !ready()) return;
            try {
                KeyboardState current = read();
                pending = current.valid() && ready() && !shouldRestore(current) ? current : null;
            } catch (RuntimeException exception) {
                fail("Cannot read keyboard settings.", exception);
                return;
            }
            handler.removeCallbacks(remember);
            handler.postDelayed(remember, 200);
        }
    };

    private final BroadcastReceiver events = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_USER_BACKGROUND.equals(action)
                    || Intent.ACTION_SCREEN_OFF.equals(action)) {
                handler.removeCallbacks(remember);
                handler.removeCallbacks(restore);
                handler.removeCallbacks(unlocked);
                if (!restoring && pending != null) persist(pending);
                pending = null;
                restoring = false;
            } else if (Intent.ACTION_USER_FOREGROUND.equals(action)
                    || Intent.ACTION_USER_PRESENT.equals(action)
                    || Intent.ACTION_USER_UNLOCKED.equals(action)) {
                handler.removeCallbacks(remember);
                handler.removeCallbacks(unlocked);
                pending = null;
                handler.postDelayed(unlocked, 200);
            } else if (Intent.ACTION_INPUT_METHOD_CHANGED.equals(action)) {
                observer.onChange(false, null);
            }
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences("keyboards", MODE_PRIVATE);
        users = getSystemService(UserManager.class);
        keyguard = getSystemService(KeyguardManager.class);
        keyboards = getSystemService(InputMethodManager.class);
        if (preferences.contains("enabled")) {
            saved = new KeyboardState(preferences.getString("enabled", ""),
                    preferences.getString("selected", ""), preferences.getInt("subtype", -1));
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL,
                "Keyboard protection", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        startForeground(1, notification("Waiting for an unlocked profile"));
        if (!hasPermission()) {
            fail("Grant WRITE_SECURE_SETTINGS through ADB, then start the service again.");
            return;
        }
        for (String setting : new String[] {Settings.Secure.ENABLED_INPUT_METHODS,
                Settings.Secure.DEFAULT_INPUT_METHOD, Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE}) {
            getContentResolver().registerContentObserver(Settings.Secure.getUriFor(setting),
                    false, observer);
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_BACKGROUND);
        filter.addAction(Intent.ACTION_USER_FOREGROUND);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        filter.addAction(Intent.ACTION_INPUT_METHOD_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(events, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(events, filter);
        }
        listening = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!hasPermission()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        beginRestore();
        return START_STICKY;
    }

    private boolean hasPermission() {
        return checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean ready() {
        return users.isUserUnlocked() && users.isUserForeground() && !keyguard.isDeviceLocked();
    }

    private KeyboardState read() {
        List<String> enabled = new ArrayList<>();
        for (InputMethodInfo keyboard : keyboards.getEnabledInputMethodList()) {
            StringBuilder entry = new StringBuilder(keyboard.getId());
            for (InputMethodSubtype subtype : keyboards.getEnabledInputMethodSubtypeList(keyboard, false)) {
                entry.append(';').append(subtype.hashCode());
            }
            enabled.add(entry.toString());
        }
        String selected = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        int subtype = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE, -1);
        return new KeyboardState(String.join(":", enabled), selected == null ? "" : selected, subtype);
    }

    private void remember() {
        if (restoring || !ready()) return;
        try {
            KeyboardState current = read();
            if (!current.valid()) return;
            if (shouldRestore(current)) {
                pending = null;
                if (SystemClock.elapsedRealtime() >= retryAfter) beginRestore();
                return;
            }
            if (ready()) persist(current);
            pending = null;
        } catch (RuntimeException exception) {
            fail("Cannot read keyboard settings.", exception);
        }
    }

    private boolean shouldRestore(KeyboardState current) {
        if (saved == null) return false;
        Set<String> installed = new HashSet<>();
        Set<String> systemKeyboards = new HashSet<>();
        for (InputMethodInfo keyboard : keyboards.getInputMethodList()) {
            installed.add(keyboard.getId());
            if ((keyboard.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                systemKeyboards.add(keyboard.getId());
            }
        }
        return saved.available(installed) != null && current.addsSystemKeyboard(saved, systemKeyboards);
    }

    private void persist(KeyboardState current) {
        if (!current.valid() || current.sameAs(saved)) return;
        if (!preferences.edit().putString("enabled", current.enabled)
                .putString("selected", current.selected).putInt("subtype", current.subtype).commit()) {
            fail("Could not save the keyboard list.");
            return;
        }
        saved = current;
        showStatus("Keyboard list saved for this profile");
    }

    private void beginRestore() {
        handler.removeCallbacks(remember);
        handler.removeCallbacks(restore);
        handler.removeCallbacks(unlocked);
        pending = null;
        restoring = false;
        if (!ready()) return;
        if (saved == null) {
            remember();
            return;
        }
        restoring = true;
        attempt = 0;
        restore();
    }

    private void restore() {
        if (!ready()) {
            restoring = false;
            return;
        }
        try {
            Set<String> installed = new HashSet<>();
            for (InputMethodInfo keyboard : keyboards.getInputMethodList()) installed.add(keyboard.getId());
            KeyboardState target = saved.available(installed);
            if (target == null) {
                restoring = false;
                showStatus("Saved keyboards unavailable; keeping the system fallback");
                return;
            }
            KeyboardState current = read();
            target = target.preservingInput(current);
            if (attempt == 3) {
                restoring = false;
                if (target.sameAs(current)) {
                    showStatus("Protecting this profile's keyboard list");
                    handler.post(remember);
                } else {
                    retryAfter = SystemClock.elapsedRealtime() + 30_000;
                    showStatus("Android did not accept the saved keyboard list");
                    Log.w("KfigGOS", "Restoration rejected; automatic retries paused for 30 seconds");
                }
                return;
            }
            if (!target.sameAs(current)) {
                put(Settings.Secure.ENABLED_INPUT_METHODS, target.unionWith(current));
                if (!target.selected.equals(current.selected)) {
                    put(Settings.Secure.DEFAULT_INPUT_METHOD, target.selected);
                    put(Settings.Secure.SELECTED_INPUT_METHOD_SUBTYPE, Integer.toString(target.subtype));
                }
                put(Settings.Secure.ENABLED_INPUT_METHODS, target.enabled);
                restoreWrites++;
            }
            attempt++;
            handler.postDelayed(restore, 200);
        } catch (RuntimeException exception) {
            fail("Cannot restore keyboard settings. Check the ADB permission.", exception);
        }
    }

    private void put(String key, String value) {
        if (!Settings.Secure.putString(getContentResolver(), key, value)) {
            throw new IllegalStateException("Write rejected: " + key);
        }
    }

    private Notification notification(String text) {
        return new Notification.Builder(this, CHANNEL).setContentTitle("KfigGOS")
                .setContentText(text).setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_SECRET).build();
    }

    private void showStatus(String text) {
        if (status.equals(text)) return;
        status = text;
        getSystemService(NotificationManager.class).notify(1, notification(text));
    }

    private void fail(String text) {
        Log.e("KfigGOS", text);
        showStatus(text);
        stopForeground(STOP_FOREGROUND_DETACH);
        stopSelf();
    }

    private void fail(String text, RuntimeException exception) {
        Log.e("KfigGOS", text, exception);
        fail(text);
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (listening) {
            unregisterReceiver(events);
            getContentResolver().unregisterContentObserver(observer);
        }
        super.onDestroy();
    }

    @Override protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("KfigGOS: " + status);
        writer.println("Permission: " + hasPermission() + ", profile ready: " + ready());
        writer.println("Saved keyboard list: " + (saved != null) + ", restoring: " + restoring);
        writer.println("Saved enabled keyboards: " + (saved == null ? "none" : saved.enabled));
        writer.println("Restore writes: " + restoreWrites + ", retry delay: "
                + Math.max(0, retryAfter - SystemClock.elapsedRealtime()) + " ms");
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
