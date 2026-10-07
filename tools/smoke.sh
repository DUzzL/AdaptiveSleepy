#!/usr/bin/env bash
# Smoke test on a real dedicated server: starts a fresh 26.3 server with the mod
# (./gradlew runSmoke, world in run-smoke/), drives /gamerule, /weather and a datapack
# function through the console, and checks the server log after every step.
# Covers: managed base, thunderstorm raise, the 26.3 "already set" check while raised,
# base adoption, the silent (function) revert and restore after the storm.
# Not covered (needs a real player): /veto sleep, the sleep action bar.
#
# Usage: tools/smoke.sh            last line is "SMOKE PASS" or "SMOKE FAIL: ..."
# Env:   SMOKE_PORT (default 25599), SMOKE_BOOT_TIMEOUT seconds (default 600)
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT=$PWD
RUN=$ROOT/run-smoke
LOG=$RUN/logs/latest.log
GRADLE_LOG=$ROOT/build/smoke-gradle.log
PORT=${SMOKE_PORT:-25599}
BOOT_TIMEOUT=${SMOKE_BOOT_TIMEOUT:-600}

rm -rf "$RUN"
mkdir -p "$RUN/world/datapacks" "$ROOT/build"
cp -r tools/smoke/datapack "$RUN/world/datapacks/adaptivesleepy-smoke"
echo "eula=true" > "$RUN/eula.txt"
cat > "$RUN/server.properties" <<EOF
level-type=minecraft\:flat
online-mode=false
server-port=$PORT
white-list=false
sync-chunk-writes=false
EOF

FIFO=$(mktemp -u)
mkfifo "$FIFO"
./gradlew --console=plain runSmoke < "$FIFO" > "$GRADLE_LOG" 2>&1 &
SERVER_PID=$!
exec 3> "$FIFO"
rm -f "$FIFO"

fail() {
    echo "SMOKE FAIL: $*"
    echo "--- last log lines ($LOG) ---"
    tail -n 30 "$LOG" 2>/dev/null || tail -n 30 "$GRADLE_LOG"
    { echo stop >&3; } 2>/dev/null || true
    sleep 5
    kill "$SERVER_PID" 2>/dev/null || true
    exit 1
}

waited=0
until grep -q 'Done (' "$LOG" 2>/dev/null; do
    kill -0 "$SERVER_PID" 2>/dev/null || fail "server exited during boot (see $GRADLE_LOG)"
    (( waited++ < BOOT_TIMEOUT )) || fail "server did not boot within ${BOOT_TIMEOUT}s"
    sleep 1
done
sleep 2
grep -qF 'Managing players_sleeping_percentage: base 100%, currently 100%' "$LOG" \
    || fail "mod did not initialize (no 'Managing players_sleeping_percentage' line)"

# step <command> <seconds to wait> <expected log text>...
step() {
    local command=$1 wait=$2
    shift 2
    local from
    from=$(wc -l < "$LOG")
    echo "$command" >&3
    sleep "$wait"
    local fresh
    fresh=$(tail -n +"$((from + 1))" "$LOG")
    for expected in "$@"; do
        grep -qF -- "$expected" <<< "$fresh" || fail "'/$command' did not log: $expected"
    done
    echo "ok  /$command"
}

RULE='Game rule players_sleeping_percentage'
step "gamerule players_sleeping_percentage 30" 1 "$RULE is now set to 30"
step "weather thunder" 8 "Set the weather to rain and thunder"
step "gamerule players_sleeping_percentage" 1 "$RULE is currently set to 50" \
    "AdaptiveSleepy: base is 30% — currently raised (thunderstorm)"
# 26.3 rejects a set equal to the current value; while raised, that value must still become the base.
step "gamerule players_sleeping_percentage 50" 1 "$RULE is now set to 50"
step "gamerule players_sleeping_percentage 50" 1 "$RULE is already set to 50"
step "gamerule players_sleeping_percentage 40" 1 "$RULE is now set to 40" \
    "AdaptiveSleepy: base saved as 40% — currently raised to 50% (thunderstorm)"
step "function adaptivesleepy_smoke:set77" 1 \
    "players_sleeping_percentage was set to 77 by a function/datapack — restoring the managed value"
step "gamerule players_sleeping_percentage" 1 "$RULE is currently set to 50"
step "weather clear" 8 "Set the weather to clear"
step "gamerule players_sleeping_percentage" 1 "$RULE is currently set to 40"

echo stop >&3
exec 3>&-
for _ in $(seq 60); do
    kill -0 "$SERVER_PID" 2>/dev/null || break
    sleep 1
done
kill -0 "$SERVER_PID" 2>/dev/null && fail "server did not stop within 60s"
wait "$SERVER_PID" || fail "gradle/server exited with an error on /stop (see $GRADLE_LOG)"
# "No key layers in MapLike[{}]" is logged by vanilla 26.3 itself on world creation.
if grep -E 'ERROR|Exception' "$LOG" | grep -vqF 'No key layers'; then
    fail "errors in the server log"
fi
echo "SMOKE PASS"
