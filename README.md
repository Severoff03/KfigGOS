# KfigGOS

**Your profile. Your keyboards.**

A small, headless Android helper by **Mothman**. It remembers each profile's enabled keyboards and restores them after switching back and unlocking.

Gboard stays selected. The stock keyboard stays out of the language switcher when it was not in your saved list.

---

## At a glance

| | |
| --- | --- |
| Platform | GrapheneOS / Android 12 or newer |
| Interface | No screens, no launcher icon |
| Background work | A silent foreground service, driven by system events |
| Storage | One small, private settings file per installed user |
| Network | No internet permission, analytics, ads, or external libraries |
| Setup | One-time manual activation through ADB or a Shizuku terminal; no root required |

Choose one of the two methods below. **Installing the APK alone does not activate KfigGOS.** Android requires a permission you grant manually, followed by a one-time service start.

## Option 1: Windows

### 1. Prepare your computer and phone

- Download [Google's Platform Tools for Windows](https://developer.android.com/tools/releases/platform-tools) and extract the ZIP.
- On your phone, open **Settings → About phone → Build number** and tap it seven times. Then open **Settings → System → Developer options** and enable **USB debugging**.
- Connect the phone to your computer with a USB data cable. Unlock it and accept **Allow USB debugging** when prompted. The prompt may appear when you first run an ADB command.
- Open the extracted `platform-tools` folder. Type `powershell` in File Explorer's address bar and press Enter.

### 2. Choose your keyboards

Switch to the profile you want to fix and unlock it. Install `KfigGOS.apk` in that profile if needed.

In **Settings → System → Keyboard**, enable Gboard or your preferred keyboards and select the one you want to use. Turn off the stock keyboard **in the keyboard list** if you do not want it in the switcher. Keep the stock keyboard app installed and enabled.

### 3. Paste these three lines into PowerShell

Run each line in order; continue only if the previous command finishes without an error:

```powershell
$profileId = (.\adb.exe shell am get-current-user).Trim()
.\adb.exe shell pm grant --user $profileId io.mothman.kfiggos android.permission.WRITE_SECURE_SETTINGS
.\adb.exe shell am start-foreground-service --user $profileId -n io.mothman.kfiggos/.KeyboardService
```

The first line finds the active profile automatically. The second grants KfigGOS broad access to write secure system settings; you authorize this manually by running the command. The third starts the helper and saves the current keyboard list.

Wait about two seconds, switch to another profile, then return and unlock. Check whether your keyboard list is restored.

**Repeat steps 2–3 for each full profile you want to fix.** After finishing all profiles, follow the debugging shutdown steps below.

No new APK is needed for this setup. After activation, the service starts again after the profile boots and unlocks. Notifications are optional: enable them in KfigGOS's system app settings if you want to see its silent status notification.

### If a command fails

- **`unauthorized`** — unlock the phone and accept the USB debugging prompt.
- **`no devices/emulators found`** — check USB debugging and use a cable that supports data.
- **Unknown package / package not installed for this user** — install the APK in the profile currently open on the phone, then repeat step 3.
- **`SecurityException` or permission denied** — keep the exact error message for troubleshooting; do not change other security settings.

If Android later restricts background activity, set KfigGOS battery usage to **Unrestricted** in that profile's system settings.

## Option 2: Shizuku (no computer)

Use **Shizuku + aShell You** to run the setup commands directly on your phone. Shizuku supplies shell privileges to the terminal; KfigGOS then runs independently using its manually granted permission. The existing APK supports this setup method; KfigGOS does not appear as a Shizuku client in the authorized-app list.

**GrapheneOS compatibility:** [Shizuku's official guide](https://shizuku.rikka.app/guide/setup/) notes that Secure app spawning can prevent Shizuku from starting. **Keep Secure app spawning enabled.** If Shizuku cannot run with your existing protections enabled, use Option 1 instead. This guide does not ask you to disable GrapheneOS protections, unlock the bootloader, or obtain root. This setup route has not been tested on a physical GrapheneOS device here.

### 1. Start Shizuku

1. In Owner, install [Shizuku](https://shizuku.rikka.app/) and [aShell You](https://github.com/DP-Hridayan/aShellYou) from their official download links.
2. Connect the phone to Wi-Fi. Enable Developer options as described in Option 1, then open **Settings → System → Developer options → Wireless debugging** and enable it.
3. In Shizuku, choose **Pairing** under the wireless-debugging startup method. In Android's wireless-debugging settings, select **Pair device with pairing code**, then enter the code through Shizuku's pairing prompt/notification.
4. Return to Shizuku and tap **Start**. Continue only when Shizuku reports that it is running.
5. Open aShell You, choose its **Shizuku** execution mode, and approve aShell You's Shizuku authorization prompt. Permission is granted to the terminal, not directly to KfigGOS.

Follow the [official Shizuku startup instructions](https://shizuku.rikka.app/guide/setup/#start-via-wireless-debugging) if the pairing screens differ on your device. Do not change unrelated security settings if startup fails.

### 2. Activate KfigGOS

For Owner, install `KfigGOS.apk` in Owner and configure your keyboard list as described in Option 1. Run this in aShell You to check the terminal mode:

```sh
id
```

For the wireless-debugging method, the output should include **`uid=2000(shell)`**. A regular app UID means the terminal is not using Shizuku; select Shizuku mode before continuing.

Then run these commands **one at a time**, continuing only if each succeeds:

```sh
pm grant --user 0 io.mothman.kfiggos android.permission.WRITE_SECURE_SETTINGS
am start-foreground-service --user 0 -n io.mothman.kfiggos/.KeyboardService
```

Do not add `adb shell` or PowerShell syntax in this terminal. These are Android shell commands. They grant the same broad `WRITE_SECURE_SETTINGS` permission as Option 1; using Shizuku does not narrow it.

Wait about two seconds and test switching away from Owner and back.

### 3. Set up secondary profiles

1. Visit the secondary profile, unlock it, and install KfigGOS there. Return to Owner using the profile switcher, leaving that profile's session running for this setup step.
2. In aShell You's Shizuku mode, list profile IDs:

   ```sh
   pm list users
   ```

3. Repeat the grant and start commands above, replacing **both** occurrences of `--user 0` with the secondary profile's ID, such as `--user 10`. Do not assume an Owner installation configures other profiles.
4. Return to that secondary profile and choose the desired keyboards **after the service has started**. This lets the running helper save the intended list in that profile. Wait about two seconds, switch away, and return to check it.

If Android reports that the user is stopped/locked or refuses a background service start, use Option 1 with that profile open and unlocked. Do not disable profile-locking protections to force this method to work.

### 4. Finish and disconnect

After all intended profiles are configured, revoke aShell You's authorization in Shizuku, stop Shizuku, and follow **After setup — turn debugging off** below. You can uninstall Shizuku and aShell You if you no longer need them.

**KfigGOS does not need Shizuku running afterwards or restarted after each reboot.** Shizuku is only a setup tool in this method. If you later need to run setup, restart, or revoke commands, start and authorize the terminal again temporarily. The security review below covers KfigGOS itself, not these external tools.

## After setup — turn debugging off

**KfigGOS does not need a permanent ADB connection, LADB, Shizuku, or an active debugging service.** ADB/Shizuku is only used to grant the permission and start the helper during setup.

After granting the permission, starting the helper, and checking that it works:

1. In **Settings → System → Developer options**, turn off **Wireless debugging** and **USB debugging**.
2. If you used LADB or Shizuku, remove its pairing under **Wireless debugging → Paired devices → Forget**. You can do this before turning wireless debugging off if your device hides that list while it is disabled. Stop Shizuku and revoke the terminal's Shizuku authorization if applicable. You can then close or uninstall the setup tools.
3. Disconnect any USB cable used for setup.

The `WRITE_SECURE_SETTINGS` grant remains attached to KfigGOS; turning debugging off, stopping Shizuku, or removing the setup tools does not revoke it. A normal reboot does not require leaving debugging on or repeating the permission grant. KfigGOS's boot receiver starts the service after the profile boots and unlocks, subject to Android's background restrictions.

You may need to enable debugging temporarily again to restart the helper after a manual stop, troubleshoot it, or revoke its permission. KfigGOS does not enable debugging, pair ADB clients, grant itself permissions, or change GrapheneOS security options.

See [Android's ADB authorization and pairing instructions](https://developer.android.com/tools/adb#connect-to-a-device-over-wi-fi).

## Everyday use

Change your keyboard selection normally in Android settings. KfigGOS saves enabled keyboards, explicit language subtypes, the selected keyboard, and its selected subtype.

When you return to a profile, it reapplies the saved selection after unlock and checks it four times over roughly two seconds. It ignores transitional changes during this short window. Change keyboard preferences after that window.

There is no polling loop, scheduled job, network traffic, or wake lock. Between keyboard, lock, and profile events, the service is idle. Android requires a foreground-service notification; its channel is silent.

## Limits

- Install and activate the helper separately in each full user. Work profiles and Private Space are not supported.
- Restoration begins after unlock; the stock keyboard package is never disabled or uninstalled. A restored selection can still affect which keyboards are offered on a later lock screen. Leaving the stock package installed is not a guarantee that every keyboard failure can be recovered from.
- Uninstalled or disabled keyboard services are excluded from restoration. If no saved keyboard remains available, the current system fallback is left alone.
- Android can stop apps. Force-stop, **Stop** in Active apps, or a killed background profile can interrupt protection. A manual stop requires starting the helper again through ADB.
- Changes made while the helper is stopped cannot be remembered. Starting it again restores its previous snapshot. Clear its data first if you want to adopt a new initial list.
- This is a workaround, not an OS patch. Changes made by Android after the restoration window can still take effect. Test it on your device before relying on it. Physical-device profile switching has not been tested here.

## Security and review scope

**Granting `WRITE_SECURE_SETTINGS` adds risk.** Android does not limit this permission to keyboard settings. If KfigGOS were compromised, or a malicious update signed with its signing key were installed, code running as the app could modify other secure settings allowed by Android. Turning debugging off closes the debugging connection; it does not remove this app permission. Android documents this permission as not intended for ordinary third-party apps.

The current code writes only `enabled_input_methods`, `default_input_method`, and `selected_input_method_subtype`. This is a restriction in the code, not an Android-enforced permission boundary.

A source and packaged-APK review on **2026-10-21**, covering the current Java code, manifest, build scripts, and existing **1.0.0 APK**, found:

| Area | Observation |
| --- | --- |
| External entry point | The service is exported for manual ADB activation and requires the caller to hold `android.permission.DUMP`. Ordinary apps cannot start it without that permission. It exposes no bound API and does not consume caller-provided setting keys, values, commands, or URLs. |
| Broadcasts | The boot receiver is not exported. The running service listens only for protected Android system broadcasts; Android 13 and newer also use `RECEIVER_NOT_EXPORTED`. |
| App data | The saved keyboard list uses `MODE_PRIVATE`; Android backup is disabled. No code reads typed text, passwords, clipboard contents, or other apps' private data. |
| Code and network | No internet permission, network client, shell execution, dynamic code loading, native libraries, or bundled third-party libraries were found in the app. These reduce exposure; their absence does not prove the app is secure. |
| Existing APK | Its signature verifies. Its packaged manifest has no debugging flag enabled, and its archive contains no signing keystore or signing password. Signature verification checks integrity, not whether the code is safe. |
| Signing credentials | The private key and password are excluded from Git and release packages. They remain together on the build computer, so access to those local files must be protected. A stolen key can be used to sign malicious updates. |

No obvious path was found in this static review for an ordinary, unprivileged app to make KfigGOS write arbitrary settings. **This is not a security certification or a guarantee that vulnerabilities are absent.** No malicious-client runtime tests or penetration tests on a GrapheneOS device were performed. The existing keyboard-state tests check restoration logic, not exploit resistance.

Remaining risks include the broad permission, trust in signed updates, and keyboard availability if restoration races with a profile/lock transition or selects a keyboard that later fails. The helper checks the profile's unlocked/foreground state before restoring, but that check is not atomic with locking or switching users.

To remove the permission, temporarily reconnect through ADB, stop the service, then revoke it. This example uses Owner (`0`); replace it with the intended profile ID:

```sh
adb shell am stopservice --user 0 -n io.mothman.kfiggos/.KeyboardService
adb shell pm revoke --user 0 io.mothman.kfiggos android.permission.WRITE_SECURE_SETTINGS
```

In LADB or aShell You's Shizuku mode, run the same commands without `adb shell`. If you no longer trust the app, uninstall it from every profile where it is installed. No permission changes are performed by this documentation or by the security review itself.

References: [WRITE_SECURE_SETTINGS](https://developer.android.com/reference/android/Manifest.permission#WRITE_SECURE_SETTINGS), [service permission checks](https://developer.android.com/guide/topics/manifest/service-element#prmsn), [protected system broadcasts in GrapheneOS](https://github.com/GrapheneOS/platform_frameworks_base/blob/16-qpr2/core/res/AndroidManifest.xml).

## Check or remove

View service status:

```sh
adb shell dumpsys activity service io.mothman.kfiggos/.KeyboardService
adb logcat -d -s KfigGOS:E
```

Forget the saved list and stop the helper for one profile:

```sh
adb shell pm clear --user 0 io.mothman.kfiggos
```

Then configure your keyboards and repeat the start command. To remove the app from that profile:

```sh
adb shell pm uninstall --user 0 io.mothman.kfiggos
```

Removal leaves your current Android keyboard settings in place.

## Build

No Gradle, Kotlin, AndroidX, or downloaded dependencies. The app uses three small Java classes and one manifest.

Requirements: Windows PowerShell 5.1, JDK 21, Android SDK platform **36**, and build tools **36.0.0**. The builder uses `ANDROID_HOME` and `JAVA_HOME`, with defaults for a standard Android Studio installation.

On a fresh clone, generate the local release launcher once using the included .NET Framework compiler:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\Build-Releases.ps1 -Bootstrap
```

Then run the dedicated builder from PowerShell:

```powershell
& '.\Build Releases.exe' --validate
& '.\Build Releases.exe'
```

For a specific higher version:

```powershell
& '.\Build Releases.exe' --version 1.1.0
```

The first build uses the manifest version. Later builds increment the patch version by default. The builder prevents concurrent builds, uses a temporary drive for Unicode paths, compiles the app, runs the keyboard-state tests, signs and verifies the APK, and writes the release under `App/versions/<version>/` with a checksum, metadata, changelog, and build log. It does not overwrite existing releases.

Keep `Builder/signing.p12` and `Builder/signing-password.txt` private and backed up. The same signing key is needed for updates; both files are excluded from Git. They are created automatically on the first build. No signing secrets are included in the APK or release folder.

Only source code, tests, the manifest, build scripts, and this README belong in Git. The generated launcher, release folders, temporary files, logs, and signing credentials are ignored. Share APKs and checksums through GitHub Releases. When uploading through GitHub's website, select source files explicitly: `.gitignore` does not filter browser uploads.

## References

- [Android secure settings](https://developer.android.com/reference/android/provider/Settings.Secure)
- [Android foreground service requirements](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use)
- [GrapheneOS keyboard discussion](https://discuss.grapheneos.org/d/14769-how-to-disable-the-standard-keyboard-app/9)

<sub>Made by Mothman · Independent project; not affiliated with GrapheneOS or Google.</sub>
