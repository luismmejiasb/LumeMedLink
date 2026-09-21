#!/bin/sh
# Cancellation guard (ADR-0026) — every broad catch must answer "was I cancelled?".
#
# WHY THIS GATE EXISTS: recognising cancellation BY EXCEPTION TYPE has failed twice in this repo,
# both times in shipped code and both times found by accident.
#
#   F12  `HttpRequestTimeoutException` IS a `CancellationException`, so re-throwing cancellation
#        untouched let Ktor's message — which embeds the full URL and its query — reach the caller.
#   ADR-0026  When a caller's scope dies mid-request, Ktor surfaces the engine's failure WRAPPED,
#        the type-based branch never sees it, and the stack reported a cancelled request to the
#        caller as `AppError.Retryable` — a transient network error. A caller with retry logic
#        would have re-sent a request the user just cancelled, and would have carried on inside a
#        scope being destroyed (§6).
#
# The lesson is not "add another type to the list". It is that a type-based check answers the wrong
# question. `currentCoroutineContext().ensureActive()` asks the only one that discriminates — is MY
# job still alive — and it is correct for every wrapper, including ones that do not exist yet.
#
# The rule, at FILE granularity and honest about it: a file that catches `Throwable` or a bare
# `Exception` must contain an `ensureActive()`. File-level is crude — it does not prove the guard
# is in the RIGHT catch — and that is why each guard also carries its own bait-tested test. This
# gate catches the case that actually happens: somebody adds a broad catch and never thinks about
# cancellation at all.
#
# Rehearsed against bait before being trusted.
#
# Usage: Scripts/check-cancellation-guard.sh   (exit non-zero on violations)

set -u
REPO_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$REPO_ROOT" || exit 1

FAIL=0

# Production code only. A test may catch broadly to ASSERT on the failure; that is the test's job.
FILES=$(find composeApp/src/commonMain composeApp/src/androidMain composeApp/src/iosMain \
    -name '*.kt' -type f 2>/dev/null)

# The one registered exception, with its reason written down — the same discipline the backend's
# UNENFORCEABLE_PATIENT_ROUTES uses, and for the same reason: an exemption nobody can read is an
# exemption nobody can challenge. It lives HERE rather than as a magic comment in the file, because
# changing this list is a change to the gate, which is visible in review.
#
#   core/networking/LumeHttpStack.kt — its broad catch runs inside a Ktor plugin hook, which is NOT
#   the caller's job, so ensureActive() there has nothing cancelled to observe. Measured, not
#   assumed (ADR-0026): the guard was added there first and did not fire. The behaviour it leaves
#   behind is pinned by LumeHttpStackTest.aCancelledCallerSeesRetryableNotCancellation, and the
#   guard it needs lives in every CALLER — which is what the rest of this gate enforces.
#
#   core/session/LogoutContract.kt — its broad catches run inside `withContext(NonCancellable)`,
#   which is the whole point of the file: a caller whose scope dies must not truncate the erase.
#   Inside NonCancellable there is nothing cancelled to observe, so ensureActive() there would be a
#   guard that CANNOT FIRE — the exact shape this repo keeps mistaking for a control. The behaviour
#   it replaces is pinned by LogoutContractTest.aCancelledCallerStillGetsTheWholeErase.
EXEMPT="composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/core/networking/LumeHttpStack.kt
composeApp/src/commonMain/kotlin/com/luismejias/lumemedlink/core/session/LogoutContract.kt"

# Newline-separated above for readability; normalised here because the membership test below is a
# substring match on a SPACE-delimited string, and a newline is not a space. Adding the second entry
# as a new line silently un-exempted the first — a list that stops working when it grows is a list
# that will be wrong exactly when it matters.
EXEMPT_ONE_LINE=$(printf '%s' "$EXEMPT" | tr '\n' ' ')

for f in $FILES; do
    case " $EXEMPT_ONE_LINE " in *" $f "*) continue ;; esac
    # Comment lines stripped first — a KDoc explaining this very rule must not trip it. (This gate
    # would otherwise fail on its own ADR quotations inside the files it guards.)
    code=$(grep -vE '^[[:space:]]*(//|\*|/\*)' "$f")

    # `.*` and not `[^)]*`: the first draft used the latter and its own bait walked through it —
    # a catch annotated `catch (@Suppress("TooGenericExceptionCaught") e: Throwable)` contains a
    # `)` inside the annotation, so the negated class stopped there and never reached the type.
    # Every broad catch in this repo that MATTERS carries exactly that annotation, so the gate was
    # blind to precisely the population it was written for.
    broad=$(echo "$code" | grep -nE 'catch[[:space:]]*\(.*:[[:space:]]*(Throwable|Exception)[[:space:]]*\)' || true)
    [ -z "$broad" ] && continue

    if ! echo "$code" | grep -q 'ensureActive()'; then
        FAIL=1
        echo ""
        echo "FAIL cancellation-guard: broad catch with no ensureActive() in $f"
        echo "  A catch of Throwable/Exception swallows a wrapped cancellation and leaves the caller"
        echo "  running inside a scope that is being destroyed (§6, ADR-0026). Add"
        echo "  currentCoroutineContext().ensureActive() to that catch — recognising cancellation by"
        echo "  exception type has already failed twice here."
        echo "$broad" | sed 's/^/    /'
    fi
done

if [ $FAIL -eq 0 ]; then
    echo "cancellation-guard: OK"
else
    exit 1
fi
