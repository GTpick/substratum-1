/*
 * Copyright (c) 2016-2017 Projekt Substratum
 * This file is part of Substratum.
 *
 * Substratum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Substratum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Substratum.  If not, see <http://www.gnu.org/licenses/>.
 */

package projekt.substratum.services.packages;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import projekt.substratum.InformationActivity;
import projekt.substratum.MainActivity;
import projekt.substratum.R;
import projekt.substratum.common.Broadcasts;
import projekt.substratum.common.Packages;
import projekt.substratum.common.References;
import projekt.substratum.common.Systems;
import projekt.substratum.common.Theming;
import projekt.substratum.util.compilers.CacheCreator;
import projekt.substratum.util.helpers.NotificationCreator;

import static projekt.substratum.common.References.INTERFACER_PACKAGE;
import static projekt.substratum.common.References.KEY_RETRIEVAL;
import static projekt.substratum.common.References.metadataEncryption;
import static projekt.substratum.common.References.metadataEncryptionValue;

public class PackageModificationDetector extends BroadcastReceiver {

    private final static String TAG = "SubstratumDetector";
    private SharedPreferences prefs;
    private Context mContext;
    private String package_name;
    private Boolean replacing;

    @Override
    public void onReceive(Context context, Intent intent) {
        this.mContext = context;
        Broadcasts.sendOverlayRefreshMessage(mContext);

        Uri packageName = intent.getData();
        if (packageName != null) {
            package_name = packageName.toString().substring(8);
        } else {
            return;
        }

        if (!package_name.endsWith(".icon")) Broadcasts.sendRefreshManagerMessage(context);

        // First, check if the app installed is actually a substratum theme
        try {
            ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(
                    package_name, PackageManager.GET_META_DATA);
            if (appInfo.metaData != null) {
                String check_theme_name =
                        appInfo.metaData.getString(References.metadataName);
                String check_theme_author =
                        appInfo.metaData.getString(References.metadataAuthor);
                if (check_theme_name == null && check_theme_author == null) return;
            } else {
                return;
            }
        } catch (Exception e) {
            return;
        }

        // When it is a proper theme, then we can continue
        replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
        prefs = context.getSharedPreferences("substratum_state", Context.MODE_PRIVATE);

        // Let's add it to the list of installed themes on shared prefs
        SharedPreferences mainPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> installed_themes =
                mainPrefs.getStringSet("installed_themes", new HashSet<>());
        Set<String> installed_sorted = new TreeSet<>();

        int beginning_size = installed_themes.size();
        if (!installed_themes.contains(package_name)) {
            installed_themes.add(package_name);
            installed_sorted.addAll(installed_themes);
        }
        if (installed_themes.size() > beginning_size) {
            mainPrefs.edit().putStringSet("installed_themes", installed_sorted).apply();
        }

        // Legacy check to see if an OMS theme is guarded from being installed on legacy
        if (!Systems.checkOMS(context)) {
            try {
                ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(
                        package_name, PackageManager.GET_META_DATA);
                if (appInfo.metaData != null) {
                    Boolean check_legacy =
                            appInfo.metaData.getBoolean(References.metadataLegacy);
                    if (!check_legacy) {
                        Log.e(TAG, "Device is non-OMS, while an " +
                                "OMS theme is installed, aborting operation!");

                        String parse = String.format(mContext.getString(
                                R.string.failed_to_install_text_notification),
                                appInfo.metaData.getString(
                                        References.metadataName));

                        Intent showIntent = new Intent();
                        PendingIntent contentIntent = PendingIntent.getActivity(
                                mContext, 0, showIntent, 0);

                        new NotificationCreator(
                                context,
                                mContext.getString(
                                        R.string.failed_to_install_title_notification),
                                parse,
                                true,
                                contentIntent,
                                R.drawable.notification_warning_icon,
                                null,
                                Notification.PRIORITY_MAX,
                                References.notification_id).createNotification();

                        Packages.uninstallPackage(mContext, package_name);
                        return;
                    }
                }
            } catch (Exception e) {
                // Suppress warning
            }
        }

        if (replacing) {
            // We need to check if this is a new install or not
            Log.d(TAG, "'" + package_name + "' has been updated.");
            if (!Theming.isCachingEnabled(context)) {
                Log.d(TAG, "'" + package_name +
                        "' has been updated with caching mode disabled.");

                Bitmap bitmap = Packages.getBitmapFromDrawable(
                        Packages.getAppIcon(context, package_name));

                new NotificationCreator(
                        context,
                        getThemeName(package_name) + " " + mContext.getString(
                                R.string.notification_theme_updated),
                        mContext.getString(R.string.notification_theme_updated_content),
                        true,
                        getPendingIntent(package_name),
                        R.drawable.notification_updated,
                        bitmap,
                        Notification.PRIORITY_MAX,
                        ThreadLocalRandom.current().nextInt(0, 1000)).createNotification();
            } else {
                Log.d(TAG, "'" + package_name +
                        "' has been updated with caching mode enabled.");
                prefs.edit().putBoolean("is_updating", true).apply();
                new ThemeCacher(this).execute("");
            }
        } else if (!Theming.isCachingEnabled(context)) {
            // This is a new install, but caching mode is disabled, so pass right through!
            Log.d(TAG, "'" + package_name + "' has been installed with caching mode disabled.");

            new NotificationCreator(
                    context,
                    getThemeName(package_name) + " " + mContext.getString(
                            R.string.notification_theme_installed),
                    mContext.getString(R.string.notification_theme_installed_content),
                    true,
                    getPendingIntent(package_name),
                    R.drawable.notification_icon,
                    BitmapFactory.decodeResource(
                            mContext.getResources(), R.mipmap.main_launcher),
                    Notification.PRIORITY_MAX,
                    ThreadLocalRandom.current().nextInt(0, 1000)).createNotification();
        } else {
            Log.d(TAG, "'" + package_name + "' has been installed with caching mode enabled.");
            new ThemeCacher(this).execute("");
        }
        Broadcasts.sendRefreshMessage(context);
    }

    private String getThemeName(String package_name) {
        // Simulate the Layers Plugin feature by filtering all installed apps and their metadata
        try {
            ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(
                    package_name, PackageManager.GET_META_DATA);
            if (appInfo.metaData != null) {
                if (Systems.checkOMS(mContext) &&
                        appInfo.metaData.getString(References.metadataName) != null &&
                        appInfo.metaData.getString(References.metadataAuthor) != null) {
                    return appInfo.metaData.getString(References.metadataName);
                } else if (appInfo.metaData.getBoolean(References.metadataLegacy, false) &&
                        appInfo.metaData.getString(References.metadataName) != null &&
                        appInfo.metaData.getString(References.metadataAuthor) != null) {
                    return appInfo.metaData.getString(References.metadataName);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Unable to find package identifier (INDEX OUT OF BOUNDS)");
        }
        return null;
    }

    public PendingIntent getPendingIntent(String package_name) {
        Intent notificationIntent;
        PendingIntent pIntent = null;
        try {
            Intent myIntent =
                    Theming.sendLaunchIntent(
                            mContext, package_name, false, null, true);
            if (myIntent != null) {
                TaskStackBuilder stackBuilder = TaskStackBuilder.create(mContext);
                stackBuilder.addParentStack(InformationActivity.class);
                stackBuilder.addNextIntent(myIntent);
                pIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_CANCEL_CURRENT);
            } else {
                notificationIntent = new Intent(mContext, MainActivity.class);
                pIntent = PendingIntent.getActivity(
                        mContext,
                        0,
                        notificationIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT);
            }
        } catch (Exception e) {
            // Suppress warning
        }
        return pIntent;
    }

    private static class ThemeCacher extends AsyncTask<String, Integer, String> {

        Boolean success = false;
        private WeakReference<PackageModificationDetector> ref;
        private LocalBroadcastManager localBroadcastManager;
        private KeyRetrieval keyRetrieval;
        private Intent securityIntent;
        private Cipher cipher;
        private Handler handler = new Handler();
        private Runnable runnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Waiting for encryption key handshake approval...");
                if (securityIntent != null) {
                    Log.d(TAG, "Encryption key handshake approved!");
                    handler.removeCallbacks(runnable);
                } else {
                    Log.d(TAG, "Encryption key still null...");
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    handler.postDelayed(this, 100);
                }
            }
        };

        ThemeCacher(PackageModificationDetector packageModificationDetector) {
            ref = new WeakReference<>(packageModificationDetector);
        }

        @Override
        protected void onPostExecute(String result) {
            PackageModificationDetector packageModificationDetector = ref.get();
            if (packageModificationDetector != null) {
                if (success) {
                    if (packageModificationDetector.replacing) {
                        new NotificationCreator(
                                packageModificationDetector.mContext,
                                packageModificationDetector.getThemeName(
                                        packageModificationDetector.package_name) + " " +
                                        packageModificationDetector.mContext.getString(
                                                R.string.notification_theme_updated),
                                packageModificationDetector.mContext.getString(
                                        R.string.notification_theme_updated_content),
                                true,
                                packageModificationDetector.getPendingIntent(
                                        packageModificationDetector.package_name),
                                R.drawable.notification_updated,
                                ((BitmapDrawable)
                                        Packages.getAppIcon(
                                                packageModificationDetector.mContext,
                                                INTERFACER_PACKAGE
                                        )).getBitmap(),
                                Notification.PRIORITY_MAX,
                                ThreadLocalRandom.current().nextInt(0, 1000)).createNotification();
                    } else {
                        new NotificationCreator(
                                packageModificationDetector.mContext,
                                packageModificationDetector.getThemeName(
                                        packageModificationDetector.package_name) + " " +
                                        packageModificationDetector.mContext.getString(
                                                R.string.notification_theme_installed),
                                packageModificationDetector.mContext.getString(
                                        R.string.notification_theme_installed_content),
                                true,
                                packageModificationDetector.getPendingIntent(
                                        packageModificationDetector.package_name),
                                R.drawable.notification_icon,
                                BitmapFactory.decodeResource(
                                        packageModificationDetector.mContext.getResources(),
                                        R.mipmap.main_launcher),
                                Notification.PRIORITY_MAX,
                                ThreadLocalRandom.current().nextInt(0, 1000)).createNotification();
                    }
                } else {
                    Log.d(TAG, "Process was interrupted by the user, rolling back changes...");
                }
                packageModificationDetector.prefs.edit().putBoolean("is_updating", false).apply();
            }
        }

        @Override
        protected String doInBackground(String... sUrl) {
            PackageModificationDetector packageModificationDetector = ref.get();
            if (packageModificationDetector != null) {
                boolean cacheable = Packages.isPackageDebuggable(
                        packageModificationDetector.mContext,
                        packageModificationDetector.package_name);
                if (!cacheable) return null;

                String encrypt_check =
                        Packages.getOverlayMetadata(
                                packageModificationDetector.mContext,
                                packageModificationDetector.package_name,
                                metadataEncryption);

                if (encrypt_check != null && encrypt_check.equals(metadataEncryptionValue)) {
                    Log.d(TAG, "This overlay for " +
                            Packages.getPackageName(
                                    packageModificationDetector.mContext,
                                    packageModificationDetector.package_name) +
                            " is encrypted, passing handshake to the theme package...");

                    Theming.getThemeKeys(
                            packageModificationDetector.mContext,
                            packageModificationDetector.package_name);

                    keyRetrieval = new KeyRetrieval();
                    IntentFilter if1 = new IntentFilter(KEY_RETRIEVAL);
                    localBroadcastManager = LocalBroadcastManager.getInstance(
                            packageModificationDetector.mContext);
                    localBroadcastManager.registerReceiver(keyRetrieval, if1);

                    int counter = 0;
                    handler.postDelayed(runnable, 100);
                    while (securityIntent == null && counter < 5) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        counter++;
                    }
                    if (counter > 5) {
                        Log.e(TAG, "Could not receive handshake in time...");
                        return null;
                    }

                    if (securityIntent != null) {
                        try {
                            byte[] encryption_key =
                                    securityIntent.getByteArrayExtra("encryption_key");
                            byte[] iv_encrypt_key =
                                    securityIntent.getByteArrayExtra("iv_encrypt_key");

                            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                            cipher.init(
                                    Cipher.DECRYPT_MODE,
                                    new SecretKeySpec(encryption_key, "AES"),
                                    new IvParameterSpec(iv_encrypt_key)
                            );
                        } catch (Exception e) {
                            e.printStackTrace();
                            return null;
                        }
                    }
                }
                success = new CacheCreator().initializeCache(
                        packageModificationDetector.mContext,
                        packageModificationDetector.package_name,
                        cipher);
            }
            return null;
        }

        class KeyRetrieval extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                securityIntent = intent;
            }
        }
    }
}