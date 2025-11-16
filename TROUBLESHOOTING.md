# VideoCutter Troubleshooting Guide

## ❌ App Closes Immediately

### Root Cause (v1.0.0)
The HTML file was named `index.html.` (with trailing dot) instead of `index.html`, causing the WebView to fail loading.

**Status**: ✅ Fixed in v1.0.1

### Debugging Steps

#### 1. Check Logcat
```bash
# Enable device developer mode (tap Build Number 7 times in About)
# Enable USB Debugging

# View app logs in real-time:
adb logcat | grep -E "VideoCutter|MainActivity|WebView"

# Or save to file:
adb logcat > logcat.txt
grep -i "videocutter\|crash\|exception" logcat.txt
```

#### 2. Clear App Cache/Data
If app still crashes after install:

**Via adb**:
```bash
adb shell pm clear com.example.videocutter
```

**Manually**:
1. Settings → Apps → VideoCutter (or Video Cutter)
2. Storage → Clear Cache (or Clear Storage for full reset)
3. Uninstall and reinstall APK

#### 3. Check Android Version
```bash
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk_int
```

Expected: SDK 30+ (Android 11+)

---

## 🔧 Installation Issues

### "Unknown App" or "Not Installed"
```bash
# Use adb to install:
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Verify installation:
adb shell pm list packages | grep videocutter
```

### Permission Denied
```bash
# Ensure USB debugging is enabled
adb devices

# Grant necessary permissions:
adb shell pm grant com.example.videocutter android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.example.videocutter android.permission.WRITE_EXTERNAL_STORAGE
```

---

## 📊 Log Analysis

### Common Crash Patterns

#### "Couldn't find index.html"
```
E/WebView: Error loading file:///android_asset/www/index.html
```
**Solution**: Ensure `app/src/main/assets/www/index.html` exists (not `index.html.`)

#### "AndroidBridge not found"
```
E/Console: ERROR: AndroidBridge tidak ditemukan
```
**Cause**: JavaScript ran before NativeBridge was injected
**Solution**: Ensure WebView settings have `javaScriptEnabled = true`

#### "Process already running"
```
E/NativeBridge: startProcess: Process already running
```
**Cause**: User clicked button while previous process still running
**Solution**: Wait or implement stop button

#### OutOfMemory or Device Storage
```
E/FFmpegKit: Cannot write output file
```
**Solution**: 
- Free device storage
- Check output directory: `/Android/data/com.example.videocutter/files/outputs/`

---

## 🧪 Testing Checklist

- [ ] App opens without crash
- [ ] WebView loads HTML interface
- [ ] Button click doesn't cause crash
- [ ] Error messages display in app log
- [ ] Can view logcat output via `adb logcat`
- [ ] Works on Android 13 (OneUI 5.1)
- [ ] Works on Android 11 (API 30)
- [ ] Works on Android 16 (if available)

---

## 📋 Files to Check

```
app/src/main/
├── AndroidManifest.xml           # Permissions, activity
├── java/
│   └── com/example/videocutter/
│       ├── MainActivity.kt        # WebView + NativeBridge setup
│       └── FFmpegShim.kt          # Placeholder FFmpeg (needs real lib)
├── assets/
│   └── www/
│       └── index.html             # ✅ Now correct (was index.html.)
└── res/
    └── layout/
        └── activity_main.xml      # WebView layout
```

---

## 🚀 Next Steps for Production

1. **Re-enable FFmpegKit** (`app/build.gradle`):
   ```gradle
   implementation 'com.arthenica:ffmpeg-kit-full-gpl:5.1.LTS'
   ```

2. **Update MainActivity** imports:
   ```kotlin
   import com.arthenica.ffmpegkit.FFmpegKit
   import com.arthenica.ffmpegkit.ReturnCode
   ```

3. **Remove FFmpegShim.kt** (local placeholder)

4. **Test on real device** with actual video files

5. **Build release APK** with signing:
   ```bash
   ./gradlew assembleRelease
   ```

---

## 📞 Support

If issues persist:
1. Save logcat output: `adb logcat > debug.log`
2. Share device info:
   - Android version: `adb shell getprop ro.build.version.release`
   - Device model: `adb shell getprop ro.product.model`
   - Exact error message from logcat

3. Check GitHub issues: https://github.com/thrdchld/video-cutter-kotlin/issues
