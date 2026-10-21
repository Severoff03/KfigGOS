# KfigGOS

A small patch by **Moffman** for the stock keyboard re-enabling itself when switching GrapheneOS profiles. It saves each profile's keyboard selection and restores it after switching back and unlocking.

## Installation

Install `KfigGOS.apk` in each profile you want to fix. In **Settings → System → Keyboard**, select the keyboards you want to use. Turn off the stock keyboard in that list if you do not want it, but keep its app installed.

**Installing the APK alone is not enough.** Activate it using one of the methods below.

To enable Developer options, open **Settings → About phone → Build number** and tap it seven times.

### Option 1: Windows / ADB

1. Download [Platform Tools](https://developer.android.com/tools/releases/platform-tools) and extract the ZIP. Open the `platform-tools` folder, type `powershell` in File Explorer's address bar, and press Enter.
2. On the phone, enable **Settings → System → Developer options → USB debugging**. Connect it by USB and accept the debugging prompt.
3. Open and unlock the profile you want to configure, then run these lines in PowerShell, one at a time:

   ```powershell
   $profileId = (.\adb.exe shell am get-current-user).Trim()
   .\adb.exe shell pm grant --user $profileId io.mothman.kfiggos android.permission.WRITE_SECURE_SETTINGS
   .\adb.exe shell am start-foreground-service --user $profileId -n io.mothman.kfiggos/.KeyboardService
   ```

4. Wait three seconds, switch to another profile, then return and check the keyboard list. Repeat step 3 with each profile open and unlocked.

If a command reports an error, stop and check the message before continuing.

### Option 2: Shizuku / no computer

Keep **Secure app spawning** enabled. If Shizuku cannot start with this protection enabled, use Option 1.

1. In Owner, install [Shizuku](https://shizuku.rikka.app/) and [aShell You](https://github.com/DP-Hridayan/aShellYou).
2. Connect to Wi-Fi and enable **Developer options → Wireless debugging**. Follow [Shizuku's pairing instructions](https://shizuku.rikka.app/guide/setup/#start-via-wireless-debugging), then tap **Start** in Shizuku.
3. Open aShell You, choose **Shizuku** mode, and approve its authorization prompt. The terminal receives Shizuku access; KfigGOS itself does not need Shizuku integration.
4. To activate KfigGOS in Owner, run these commands one at a time:

   ```sh
   pm grant --user 0 io.mothman.kfiggos android.permission.WRITE_SECURE_SETTINGS
   am start-foreground-service --user 0 -n io.mothman.kfiggos/.KeyboardService
   ```

Do not add `adb shell` in aShell You. Wait three seconds and test switching profiles.

For a secondary profile:

1. Unlock it and install KfigGOS there. Return to Owner without ending that profile's session.
2. In aShell You, run `pm list users` to find its ID. Repeat the two commands above, replacing **both** `--user 0` arguments with that ID.
3. Return to the secondary profile and select the desired keyboards after activation. Wait three seconds and test switching away and back.

If the profile is stopped or Android refuses the service start, use Option 1 with that profile open and unlocked.

## After activation

Turn off **USB debugging** and **Wireless debugging**. Remove the setup tool's pairing under **Wireless debugging → Paired devices → Forget** before switching debugging off if necessary. If you used Shizuku, revoke aShell You's authorization and stop Shizuku.

The permission stays with KfigGOS. You can close or uninstall the setup tools; they do not need to remain running or be restarted after a normal reboot. If you manually stop KfigGOS, repeat the service-start command to activate it again.

If the stock keyboard was already saved by an earlier version, wait three seconds after the helper starts, then turn it off in the keyboard list once more. KfigGOS then keeps that system keyboard excluded, even if Android re-enables it later. Other keyboard selections continue to be saved automatically.

To intentionally add an excluded system keyboard again, stop KfigGOS, clear its app data in Android settings, choose the new keyboard list, and repeat activation.

**Permission note:** `WRITE_SECURE_SETTINGS` grants access beyond keyboard settings. Only grant it to an APK you trust; switching debugging off does not revoke this permission.
