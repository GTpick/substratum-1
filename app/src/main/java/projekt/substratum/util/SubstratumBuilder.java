package projekt.substratum.util;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

import kellinwood.security.zipsigner.ZipSigner;

/**
 * @author Nicholas Chum (nicholaschum)
 */

public class SubstratumBuilder {

    /*

    All public methods in this class:

    1. injectAAPT(Context) : initial check/injection for AAPT access on device
    2. initializeCache(Context, package_identifier) : extract assets for theme so no reuse needed
    3. beginAction(Context, package_name, theme_package) : start SubstratumBuilder function
        - this will create an AndroidManifest based on selected package
        - then it will compile using the new work zone

     */

    public Boolean has_errored_out = false;
    public String parse2_themeName;
    public String no_install = "";
    private Context mContext;
    private Boolean enable_signing = true;

    private String getDeviceIMEI() {
        TelephonyManager telephonyManager = (TelephonyManager) mContext.getSystemService(Context
                .TELEPHONY_SERVICE);
        return telephonyManager.getDeviceId();
    }

    private String getDeviceID() {
        return Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.ANDROID_ID);
    }

    private String getThemeName(String package_name) {
        // Simulate the Layers Plugin feature by filtering all installed apps and their metadata
        try {
            ApplicationInfo appInfo = mContext.getPackageManager().getApplicationInfo(
                    package_name, PackageManager.GET_META_DATA);
            if (appInfo.metaData != null) {
                if (appInfo.metaData.getString("Substratum_Theme") != null) {
                    if (appInfo.metaData.getString("Substratum_Author") != null) {
                        return appInfo.metaData.getString("Substratum_Theme");
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("SubstratumLogger", "Unable to find package identifier (INDEX OUT OF BOUNDS)");
        }
        return null;
    }

    public void beginAction(Context context, String theme_pid, String overlay_package, String
            theme_name, String
                                    update_mode_input, String variant, String additional_variant,
                            String base_variant,
                            String versionName) {

        has_errored_out = false;
        mContext = context;
        String work_area;
        Boolean update_mode = Boolean.valueOf(update_mode_input);
        String base_resources = base_variant;

        int typeMode = 1;
        if (additional_variant != null) {
            typeMode = 2;
        }

        // 1. Set work area to asset chosen based on the parameter passed into this class

        work_area = mContext.getCacheDir().getAbsolutePath() + "/SubstratumBuilder/" +
                getThemeName(theme_pid).replaceAll("\\s+", "").replaceAll("[^a-zA-Z0-9]+", "") +
                "/assets/overlays/" + overlay_package;

        // 2. Create a modified Android Manifest for use with aapt

        File root = new File(work_area + "/AndroidManifest.xml");
        Log.e("Filer", root.getAbsolutePath());

        // 2a. Parse the theme's name before adding it into the new manifest to prevent any issues

        String parse1_themeName = theme_name.replaceAll("\\s+", "");
        parse2_themeName = parse1_themeName.replaceAll("[^a-zA-Z0-9]+", "");

        String parse2_variantName = "";
        if (variant != null) {
            String parse1_variantName = variant.replaceAll("\\s+", "");
            parse2_variantName = parse1_variantName.replaceAll("[^a-zA-Z0-9]+", "");
        }
        if (parse2_variantName.length() > 0) parse2_variantName = "." + parse2_variantName;

        String parse2_baseName = "";
        if (base_resources != null) {
            String parse1_baseName = base_resources.replaceAll("\\s+", "");
            parse2_baseName = parse1_baseName.replaceAll("[^a-zA-Z0-9]+", "");
        }
        if (parse2_baseName.length() > 0) parse2_baseName = "." + parse2_baseName;

        if (parse2_themeName.equals("")) {
            parse2_themeName = "no_name";
        }

        // 2b. Create the manifest file based on the new parsed names

        String varianter = parse2_variantName + parse2_baseName;
        varianter.replaceAll("\\s+", "").replaceAll("[^a-zA-Z0-9]+", "");
        if (!has_errored_out) {
            try {
                root.createNewFile();
                FileWriter fw = new FileWriter(root);
                BufferedWriter bw = new BufferedWriter(fw);
                PrintWriter pw = new PrintWriter(bw);
                if (variant != null) {
                    String manifest =
                            "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>\n" +
                                    "<manifest xmlns:android=\"http://schemas.android" +
                                    ".com/apk/res/android\" package=\"" + overlay_package + "." +
                                    parse2_themeName + parse2_variantName + parse2_baseName +
                                    "\"\n" +
                                    "        android:versionName=\"" + versionName + "\"> \n" +
                                    "    <overlay android:targetPackage=\"" + overlay_package +
                                    "\"/>\n" +
                                    "    <application android:label=\"" + overlay_package + "." +
                                    parse2_themeName + parse2_variantName + parse2_baseName +
                                    "\">\n" +
                                    "        <meta-data android:name=\"Substratum_ID\" " +
                                    "android:value=\"" + getDeviceID() + "\"/>\n" +
                                    "        <meta-data android:name=\"Substratum_IMEI\" " +
                                    "android:value=\"!" + getDeviceIMEI() + "\"/>\n" +
                                    "        <meta-data android:name=\"Substratum_Parent\" " +
                                    "android:value=\"" + parse2_themeName + "\"/>\n" +
                                    "        <meta-data android:name=\"Substratum_Variant\" " +
                                    "android:value=\"" + varianter +
                                    "\"/>\n" +
                                    "    </application>\n" +
                                    "</manifest>\n";
                    pw.write(manifest);
                } else {
                    if (base_resources != null) {
                        String manifest =
                                "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>\n" +
                                        "<manifest xmlns:android=\"http://schemas.android" +
                                        ".com/apk/res/android\" package=\"" + overlay_package + "" +
                                        "." +
                                        parse2_themeName + parse2_variantName + parse2_baseName +
                                        "\"\n" +
                                        "        android:versionName=\"" + versionName + "\"> \n" +
                                        "    <overlay android:targetPackage=\"" + overlay_package +
                                        "\"/>\n" +
                                        "    <application android:label=\"" + overlay_package + "" +
                                        "." +
                                        parse2_themeName + parse2_variantName + parse2_baseName +
                                        "\">\n" +
                                        "        <meta-data android:name=\"Substratum_ID\" " +
                                        "android:value=\"" + getDeviceID() + "\"/>\n" +
                                        "        <meta-data android:name=\"Substratum_IMEI\" " +
                                        "android:value=\"!" + getDeviceIMEI() + "\"/>\n" +
                                        "        <meta-data android:name=\"Substratum_Parent\" " +
                                        "android:value=\"" + parse2_themeName + "\"/>\n" +
                                        "        <meta-data android:name=\"Substratum_Variant\" " +
                                        "android:value=\"" + varianter
                                        + "\"/>\n" +
                                        "    </application>\n" +
                                        "</manifest>\n";
                        pw.write(manifest);
                    } else {
                        String manifest =
                                "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>\n" +
                                        "<manifest xmlns:android=\"http://schemas.android" +
                                        ".com/apk/res/android\" package=\"" + overlay_package + "" +
                                        "." +
                                        parse2_themeName + "\"\n" +
                                        "        android:versionName=\"" + versionName + "\"> \n" +
                                        "    <overlay android:targetPackage=\"" + overlay_package +
                                        "\"/>\n" +
                                        "    <application android:label=\"" + overlay_package + "" +
                                        "." +
                                        parse2_themeName + "\">\n" +
                                        "        <meta-data android:name=\"Substratum_ID\" " +
                                        "android:value=\"" + getDeviceID() + "\"/>\n" +
                                        "        <meta-data android:name=\"Substratum_IMEI\" " +
                                        "android:value=\"!" + getDeviceIMEI() + "\"/>\n" +
                                        "        <meta-data android:name=\"Substratum_Parent\" " +
                                        "android:value=\"" + parse2_themeName + "\"/>\n" +
                                        "    </application>\n" +
                                        "</manifest>\n";
                        pw.write(manifest);
                    }
                }
                pw.close();
                bw.close();
                fw.close();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("SubstratumBuilder", "There was an exception creating a new Manifest file!");
                has_errored_out = true;
                Log.e("SubstratumBuilder", "Installation of \"" + overlay_package + "\" has " +
                        "failed.");
            }
        }

        // Compile the new theme apk based on new manifest, framework-res.apk and extracted asset

        if (!has_errored_out) {
            try {
                File type3directory = new File(work_area + "/type3_" + base_resources + "/");
                String commands;
                if (typeMode == 1) {
                    commands = "aapt p -M " + work_area +
                            "/AndroidManifest.xml -S " +
                            work_area +
                            (((base_resources == null) || !type3directory.exists()) ? "/workdir/ " +
                                    "-I " :
                                    "/" + "type3_" + base_resources + "/ -I ") +
                            "/system/framework/framework-res.apk -F " +
                            work_area +
                            "/" + overlay_package + "." + parse2_themeName + "-unsigned.apk " +
                            "-f --include-meta-data --auto-add-overlay\n";
                } else {
                    if (variant != null) {
                        commands = "aapt p -M " + work_area +
                                "/AndroidManifest.xml -S " +
                                work_area +
                                "/" + "type2_" + additional_variant + "/ -S " +
                                work_area +
                                (((base_resources == null) || !type3directory.exists()) ?
                                        "/workdir/ " +
                                                "-I " : "/" + "type3_" + base_resources + "/ -I ") +
                                "/system/framework/framework-res.apk -F " +
                                work_area +
                                "/" + overlay_package + "." + parse2_themeName + "-unsigned" +
                                ".apk " +

                                "-f --include-meta-data --auto-add-overlay\n";
                    } else {
                        commands = "aapt p -M " + work_area +
                                "/AndroidManifest.xml -S " +
                                work_area +
                                (((base_resources == null) || !type3directory.exists()) ?
                                        "/workdir/ " +
                                                "-I " : "/" + "type3_" +
                                        base_resources + "/ -I ") +
                                "/system/framework/framework-res.apk -F " +
                                work_area +
                                "/" + overlay_package + "." + parse2_themeName + "-unsigned" +
                                ".apk " +
                                "-f --include-meta-data --auto-add-overlay\n";
                    }
                }

                String line;
                Process nativeApp = Runtime.getRuntime().exec(commands);

                OutputStream stdin = nativeApp.getOutputStream();
                InputStream stderr = nativeApp.getErrorStream();
                InputStream stdout = nativeApp.getInputStream();
                stdin.write(("ls\n").getBytes());
                stdin.write("exit\n".getBytes());
                stdin.flush();
                stdin.close();

                BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
                while ((line = br.readLine()) != null) {
                    Log.d("OverlayOptimizer", line);
                }
                br.close();
                br = new BufferedReader(new InputStreamReader(stderr));
                while ((line = br.readLine()) != null) {
                    Log.e("SubstratumBuilder", line);
                    has_errored_out = true;
                }
                if (has_errored_out) {
                    Log.e("SubstratumBuilder", "Installation of \"" + overlay_package + "\" has " +
                            "failed.");
                }
                br.close();

                if (!has_errored_out) {
                    // We need this Process to be waited for before moving on to the next function.
                    Log.d("SubstratumBuilder", "Overlay APK creation is running now...");
                    nativeApp.waitFor();
                    File unsignedAPK = new File(work_area + "/" + overlay_package + "." +
                            parse2_themeName + "-unsigned.apk");
                    if (unsignedAPK.exists()) {
                        Log.d("SubstratumBuilder", "Overlay APK creation has completed!");
                    } else {
                        Log.e("SubstratumBuilder", "Overlay APK creation has failed!");
                        has_errored_out = true;
                        Log.e("SubstratumBuilder", "Installation of \"" + overlay_package + "\" " +
                                "overlay has failed.");
                    }
                }
            } catch (Exception e) {
                Log.e("SubstratumBuilder", "Unfortunately, there was an exception trying to " +
                        "create a new " +
                        "APK");
                has_errored_out = true;
                Log.e("SubstratumBuilder", "Installation of \"" + overlay_package + "\" has " +
                        "failed.");
            }
        }

        // Sign the apk

        if (!has_errored_out && enable_signing) {
            try {
                // Delete the previous APK if it exists in the dashboard folder
                Root.runCommand(
                        "rm -r " + Environment.getExternalStorageDirectory().getAbsolutePath() +
                                "/.substratum/" + overlay_package + "." + parse2_themeName +
                                "-unsigned.apk");

                // Sign with the built-in test key/certificate.
                String source = work_area + "/" + overlay_package + "." + parse2_themeName +
                        "-unsigned.apk";
                String destination = Environment.getExternalStorageDirectory().getAbsolutePath() +
                        "/.substratum/" + overlay_package + "." + parse2_themeName + "-signed.apk";

                ZipSigner zipSigner = new ZipSigner();
                zipSigner.setKeymode("testkey");
                zipSigner.signZip(source, destination);

                Log.d("SubstratumBuilder", "APK successfully signed!");
            } catch (Throwable t) {
                Log.e("SubstratumBuilder", "APK could not be signed. " + t.toString());
                has_errored_out = true;
                Log.e("SubstratumBuilder", "Installation of \"" + overlay_package + "\" has " +
                        "failed.");
            }
        }

        // Install the APK silently
        // Superuser needed as this requires elevated privileges to run these commands

        if (!has_errored_out) {
            if (update_mode) {
                try {
                    if (variant != null) {
                        Root.runCommand(
                                "pm install -r " + Environment.getExternalStorageDirectory()
                                        .getAbsolutePath() +
                                        "/.substratum/" + overlay_package + "." + parse2_themeName +
                                        "-signed" +
                                        ".apk");
                        Log.d("SubstratumBuilder", "Silently installing APK...");
                        if (checkIfPackageInstalled(overlay_package + "." + parse2_themeName +
                                parse2_variantName + parse2_baseName, context)) {
                            Log.d("SubstratumBuilder", "Overlay APK has successfully been " +
                                    "installed!");
                        } else {
                            Log.e("SubstratumBuilder", "Overlay APK has failed to install!");
                        }
                    } else {
                        Root.runCommand(
                                "pm install -r " + Environment.getExternalStorageDirectory()
                                        .getAbsolutePath() +
                                        "/.substratum/" + overlay_package + "." + parse2_themeName +
                                        "-signed" +
                                        ".apk");
                        Log.d("SubstratumBuilder", "Silently installing APK...");
                        if (checkIfPackageInstalled(overlay_package + "." + parse2_themeName,
                                context)) {
                            Log.d("SubstratumBuilder", "Overlay APK has successfully been " +
                                    "installed!");
                        } else {
                            Log.e("SubstratumBuilder", "Overlay APK has failed to install!");
                        }
                    }
                } catch (Exception e) {
                    Log.e("SubstratumBuilder", "Overlay APK has failed to install! (Exception)");
                    has_errored_out = true;
                    Log.e("SubstratumBuilder", "Installation of \"" + overlay_package + "\" has " +
                            "failed.");
                }
            } else {
                Log.d("SubstratumBuilder", "Update mode flag disabled, returning one-line " +
                        "parsable command");
                no_install = "pm install -r " + Environment.getExternalStorageDirectory()
                        .getAbsolutePath() +
                        "/.substratum/" + overlay_package + "." + parse2_themeName +
                        "-signed" +
                        ".apk";
            }
        }
    }

    private Boolean checkIfPackageInstalled(String packagename, Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(packagename, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
}
