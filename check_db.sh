#!/bin/bash
adb shell "run-as com.messageai.tactical.dev sqlite3 /data/user/0/com.messageai.tactical.dev/databases/messageai.db 'SELECT COUNT(*) FROM messages;'"

