# Quick Log Check

Run these commands to see ALL relevant logs:

## Option 1: See everything (recommended)
```powershell
adb logcat -c
adb logcat
```
Then manually look for MessageListener lines.

## Option 2: PowerShell filter
```powershell
adb logcat | Select-String "MessageListener"
```

## Option 3: Check if app is even running
```powershell
adb logcat | Select-String "MessageAI|tactical"
```

## What to verify:
1. Did you run `./gradlew installDevDebug` after my last changes?
2. Do you see ANY logs from the app at all?
3. When you open a chat, do you see the "Starting listener" log?

If you see ZERO MessageListener logs, the new build didn't install.

