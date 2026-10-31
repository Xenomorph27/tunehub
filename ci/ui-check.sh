#!/usr/bin/env bash
# Drives the installed app through its main screens on the CI emulator and saves screenshots.
# Usage: ci/ui-check.sh path/to/app.apk
set -u
APK="$1"
OUT=ui-shots
mkdir -p "$OUT"

shot() { sleep 1; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }

# tap.py finds a node by exact text, text prefix, or content-desc and taps its centre
tap() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1
  xy=$(python3 ci/find_node.py /tmp/ui.xml "$@") || { echo "not found: $*"; return 1; }
  adb shell input tap $xy
  echo "tapped $* at $xy"
}

adb install -r "$APK"
adb logcat -c
adb shell am start -n com.spitify.app/.ui.MainActivity
sleep 10
shot 01-home

tap text "Search"; sleep 3
shot 02-search-home

tap text "What do you want to listen to?"; sleep 1
adb shell input text "arijit%ssingh"; adb shell input keyevent 66
sleep 14
shot 03-search-all
adb shell input swipe 540 1800 540 700 400; sleep 2
shot 04-search-all-scrolled

tap text "Artists"; sleep 10
shot 05-search-artists
tap text "Arijit Singh"; sleep 18
shot 06-artist
adb shell input swipe 540 1800 540 600 400; sleep 2
shot 07-artist-scrolled
adb shell input keyevent 4; sleep 2

tap text "Albums"; sleep 10
shot 08-search-albums
tap prefix "Album •"; sleep 15
shot 09-album
tap text "1"; sleep 12
shot 10-after-play

tap desc "Like" || tap desc "Unlike"
sleep 1
# the mini player row: tap its left part to open Now Playing
adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1
y=$(python3 ci/find_node.py /tmp/ui.xml desc "Unlike" | cut -d' ' -f2) || y=""
[ -n "$y" ] && adb shell input tap 200 "$y"
sleep 5
shot 11-now-playing
adb shell input swipe 540 1900 540 600 400; sleep 4
shot 12-now-playing-lyrics
adb shell input keyevent 4; sleep 2

tap text "Your Library"; sleep 3
shot 13-library
tap desc "Create playlist"; sleep 2
adb shell input text "Road%strip"; sleep 1
tap text "Save"; sleep 3
shot 14-new-playlist
adb shell input keyevent 4; sleep 2

tap text "Home"; sleep 3
shot 15-home-after
tap desc "Settings"; sleep 3
shot 16-settings

adb logcat -d > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt"; then
  echo "::error::App crashed"
  grep -A40 "FATAL EXCEPTION" "$OUT/logcat.txt" | head -80
fi
exit 0
