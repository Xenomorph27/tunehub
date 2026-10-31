#!/usr/bin/env bash
# Drives the installed app through its main screens on the CI emulator and saves screenshots.
# Usage: ci/ui-check.sh path/to/app.apk
set -u
APK="$1"
OUT=ui-shots
mkdir -p "$OUT"

shot() { sleep 1; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }

# tap.py finds a node by exact text, text prefix, or content-desc and taps its centre
# uiautomator dump fails now and then while the screen settles, so retry a few times
dump() {
  for _ in 1 2 3 4 5; do
    adb shell rm -f /sdcard/ui.xml
    adb shell uiautomator dump /sdcard/ui.xml > /tmp/dump.log 2>&1
    if adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1; then
      # the CI emulator's launcher sometimes throws an "isn't responding" dialog over us
      if python3 ci/find_node.py /tmp/ui.xml contains "isn't responding" > /dev/null; then
        xy=$(python3 ci/find_node.py /tmp/ui.xml text "Wait") && adb shell input tap $xy
        echo "dismissed system dialog"; sleep 2; continue
      fi
      return 0
    fi
    sleep 2
  done
  echo "dump failed: $(cat /tmp/dump.log)"
  return 1
}

tap() {
  for _ in 1 2 3; do
    dump || continue
    if xy=$(python3 ci/find_node.py /tmp/ui.xml "$@"); then
      adb shell input tap $xy
      echo "tapped $* at $xy"
      return 0
    fi
    sleep 2
  done
  echo "not found: $*"
  return 1
}

# let the freshly booted emulator settle before driving it
sleep 60
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null
adb install -r "$APK"
adb logcat -c
adb shell am start -n com.spitify.app/.ui.MainActivity
sleep 10
shot 01-home

tap text "Search"; sleep 3
shot 02-search-home

tap class "android.widget.EditText"; sleep 1
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
tap desc "Back"; sleep 2

tap text "Albums"; sleep 10
shot 08-search-albums
tap contains "Album •"; sleep 15
shot 09-album
tap desc "Play"; sleep 12
shot 10-after-play

tap desc "Like" || tap desc "Unlike"
sleep 1
# the mini player row: tap its left part to open Now Playing
dump
y=$(python3 ci/find_node.py /tmp/ui.xml desc "Unlike" | cut -d' ' -f2) || y=""
[ -n "$y" ] && adb shell input tap 200 "$y"
sleep 5
shot 11-now-playing
adb shell input swipe 540 1900 540 600 400; sleep 4
shot 12-now-playing-lyrics
adb shell input swipe 540 600 540 1900 300; sleep 2
tap desc "Close"; sleep 2

tap text "Your Library"; sleep 3
shot 13-library
tap desc "Create playlist"; sleep 2
adb shell input text "Road%strip"; sleep 1
tap text "Save"; sleep 3
shot 14-new-playlist

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
