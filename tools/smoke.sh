#!/usr/bin/env bash
# Smoke test on a real dedicated server: starts a fresh 26.3 server with the mod
# (./gradlew runSmoke, world in run-smoke/), drives /gamerule, /weather and a datapack
# function through the console, and checks the server log after every step.
# Covers: managed base, thunderstorm raise, the 26.3 "already set" check while raised,
# base adoption, the silent (function) revert and restore after the storm.
# Not covered (needs a real player): /veto sleep, the sleep action bar.
#
# Usage: tools/smoke.sh    exit 0 and last line "SMOKE PASS"; on failure exit 1, a
#                          "SMOKE FAIL: <step>" line and the end of the server log.
# Env:   SMOKE_PORT (default 25599), SMOKE_BOOT_TIMEOUT seconds (default 600),
#        SMOKE_STEP_TIMEOUT seconds per step (default 30)
set -euo pipefail
trap '' PIPE  # a write to a dead server must fail the step, not kill the script

cd "$(dirname "$0")/.."
ROOT=$PWD
RUN=$ROOT/run-smoke
LOG=$RUN/logs/latest.log
GRADLE_LOG=$ROOT/build/smoke-gradle.log
PORT=${SMOKE_PORT:-25599}
BOOT_TIMEOUT=${SMOKE_BOOT_TIMEOUT:-600}
STEP_TIMEOUT=${SMOKE_STEP_TIMEOUT:-30}
SERVER_PID=

# Always take the server down, also on failure and Ctrl+C.
cleanup() {
    [[ -n $SERVER_PID ]] || return 0
    if kill -0 "$SERVER_PID" 2>/dev/null; then
        { echo stop >&3; } 2>/dev/null || true
        for _ in $(seq 30); do
            kill -0 "$SERVER_PID" 2>/dev/null || break
            sleep 1
        done
        kill "$SERVER_PID" 2>/dev/null || true
    fi
    wait "$SERVER_PID" 2>/dev/null || true
    SERVER_PID=
}
trap cleanup EXIT
trap 'exit 130' INT TERM

fail() {
    echo "SMOKE FAIL: $*"
    echo "--- last log lines ($LOG) ---"
    tail -n 30 "$LOG" 2>/dev/null || tail -n 30 "$GRADLE_LOG" 2>/dev/null || true
    exit 1
}

rm -rf "$RUN"
mkdir -p "$RUN/world/datapacks" "$ROOT/build"
cp -r tools/smoke/datapack "$RUN/world/datapacks/adaptivesleepy-smoke"
echo "eula=true" > "$RUN/eula.txt"
# pause-when-empty-seconds=0: without players vanilla stops ticking after 60 s, which would
# freeze the weather and the mod's tick handlers.
cat > "$RUN/server.properties" <<EOF
level-type=minecraft\:flat
online-mode=false
server-port=$PORT
white-list=false
pause-when-empty-seconds=0
sync-chunk-writes=false
EOF

FIFO=$(mktemp -u)
mkfifo "$FIFO"
./gradlew --console=plain runSmoke < "$FIFO" > "$GRADLE_LOG" 2>&1 &
SERVER_PID=$!
exec 3> "$FIFO"
rm -f "$FIFO"

waited=0
until grep -q 'Done (' "$LOG" 2>/dev/null; do
    kill -0 "$SERVER_PID" 2>/dev/null || fail "server exited during boot (see $GRADLE_LOG)"
    (( waited++ < BOOT_TIMEOUT )) || fail "server did not boot within ${BOOT_TIMEOUT}s"
    sleep 1
done
grep -qF 'Managing players_sleeping_percentage: base 100%, currently 100%' "$LOG" \
    || fail "mod did not initialize (no 'Managing players_sleeping_percentage' line)"

# step [--repeat] <command> <expected log text>...: sends the command, then waits up to
# STEP_TIMEOUT for every expected text to appear in the log lines written after it.
# --repeat re-sends the command every 2 s (for state that changes over time, like weather).
step() {
    local repeat=0
    if [[ $1 == --repeat ]]; then
        repeat=1
        shift
    fi
    local command=$1
    shift
    local from
    from=$(wc -l < "$LOG")
    echo "$command" >&3 || fail "server is gone (could not send /$command)"
    local deadline=$((SECONDS + STEP_TIMEOUT)) resend=$((SECONDS + 2)) fresh missing
    while :; do
        fresh=$(tail -n +"$((from + 1))" "$LOG")
        missing=
        for expected in "$@"; do
            grep -qF -- "$expected" <<< "$fresh" || { missing=$expected; break; }
        done
        [[ -z $missing ]] && break
        kill -0 "$SERVER_PID" 2>/dev/null || fail "server exited during /$command"
        (( SECONDS < deadline )) || fail "'/$command' did not log within ${STEP_TIMEOUT}s: $missing"
        if (( repeat && SECONDS >= resend )); then
            echo "$command" >&3 || fail "server is gone (could not send /$command)"
            resend=$((SECONDS + 2))
        fi
        sleep 0.5
    done
    echo "ok  /$command"
}

RULE='Game rule players_sleeping_percentage'
step "gamerule players_sleeping_percentage 30" "$RULE is now set to 30"
step "weather thunder" "Set the weather to rain and thunder"
# isThundering() turns true only after the thunder level ramps up (~5 s at 20 TPS): poll.
step --repeat "gamerule players_sleeping_percentage" "$RULE is currently set to 50" \
    "AdaptiveSleepy: base is 30% — currently raised (thunderstorm)"
# 26.3 rejects a set equal to the current value; while raised, that value must still become the base.
step "gamerule players_sleeping_percentage 50" "$RULE is now set to 50"
step "gamerule players_sleeping_percentage 50" "$RULE is already set to 50"
step "gamerule players_sleeping_percentage 40" "$RULE is now set to 40" \
    "AdaptiveSleepy: base saved as 40% — currently raised to 50% (thunderstorm)"
step "function adaptivesleepy_smoke:set77" \
    "players_sleeping_percentage was set to 77 by a function/datapack — restoring the managed value"
step "gamerule players_sleeping_percentage" "$RULE is currently set to 50"
step "weather clear" "Set the weather to clear"
step --repeat "gamerule players_sleeping_percentage" "$RULE is currently set to 40"

echo stop >&3 || fail "server is gone before /stop"
exec 3>&-
for _ in $(seq 60); do
    kill -0 "$SERVER_PID" 2>/dev/null || break
    sleep 1
done
kill -0 "$SERVER_PID" 2>/dev/null && fail "server did not stop within 60s"
status=0
wait "$SERVER_PID" || status=$?
SERVER_PID=
(( status == 0 )) || fail "gradle/server exited with code $status on /stop (see $GRADLE_LOG)"
# "No key layers in MapLike[{}]" is logged by vanilla 26.3 itself on world creation.
errors=$(grep -E 'ERROR|Exception' "$LOG" | grep -vF 'No key layers' || true)
[[ -z $errors ]] || fail "errors in the server log: $(head -n 3 <<< "$errors")"
echo "SMOKE PASS"
