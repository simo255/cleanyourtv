#!/data/data/com.termux/files/usr/bin/sh
# Run the existing Google TV snapshot importer entirely on the Android TV itself.
# Install python and android-tools from Termux first; authorize this ADB client on the TV.
set -u
umask 077

sync_root="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
sync_serial="${TVHOME_ADB_SERIAL:-${ONN_ADB_SERIAL:-127.0.0.1:5555}}"
sync_state="${XDG_STATE_HOME:-$HOME/.local/state}/tvhome-google-tv"

for sync_program in adb python; do
    if ! command -v "$sync_program" >/dev/null 2>&1; then
        echo "Missing $sync_program. In Termux run: pkg install python android-tools" >&2
        exit 1
    fi
done
if [ ! -f "$sync_root/sync-google-tv.py" ] || [ ! -f "$sync_root/configure-recommendations.py" ]; then
    echo "Keep this script alongside sync-google-tv.py and configure-recommendations.py." >&2
    exit 1
fi

mkdir -p "$sync_state" || exit 1
# Only one process may move the TV focus at a time. Never remove someone else's lock.
if ! mkdir "$sync_state/.refresh-lock" 2>/dev/null; then
    echo "A refresh is already running. If Termux was killed, first restore the custom Home" >&2
    echo "and remove the empty $sync_state/.refresh-lock directory before retrying." >&2
    exit 1
fi
trap 'rmdir "$sync_state/.refresh-lock" 2>/dev/null || :' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
adb connect "$sync_serial" || exit 1
if [ "$(adb -s "$sync_serial" get-state 2>/dev/null)" != device ]; then
    echo "Authorize the Termux ADB key on the TV, then run this command again." >&2
    exit 1
fi

echo "Google TV will temporarily occupy the screen. Leave the remote untouched."
python "$sync_root/sync-google-tv.py" --adb adb --serial "$sync_serial" --public-pages 0 "$@" \
    > "$sync_state/last-refresh.log" 2>&1
sync_status=$?
cat "$sync_state/last-refresh.log"
printf '%s\n' "$sync_status" > "$sync_state/last-refresh.exit"
exit "$sync_status"
