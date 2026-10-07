# ADR-0034 — The unlock-attempt ceiling survives the process

- **Status**: Accepted
- **Date**: 2026-10-07
- **Related**: ADR-0032 (which named this and left it open), ADR-0011 (the tier-2 gate whose attempts
  are counted), ADR-0014 (the logout erases the namespace), §8.3, task `0007`, and task `0009` (the
  shell state that rotation recreates).

## Context

`SessionLock` kept the count of failed unlock attempts in a `private var`, and `SessionLock` lives in
the composition (`remember` in `App()`). Anything that ends the process — or recreates the
composition — reset it. The ceiling this app believed it enforced was not the one enforced, and the
promise of §8.3 is this app's, not the operating system's.

## Decision

1. **The count lives in the tier-1 `SecureStore`**, under `SecureStoreKey.FAILED_UNLOCK_ATTEMPTS`,
   behind a small `FailedAttemptLedger`. Being in the enum puts it inside the logout's namespace wipe
   and inside `SecureStoreWipeTest` with no one having to remember either. Not in plain preferences:
   inside `core/` no gate would refuse that, so this is §8.4 held by hand.
2. **Read before prompting.** A budget spent in an earlier process ends the session without offering
   one more try.
3. **A miss is written before the verdict is returned**, so the only window in which a killed process
   loses it is the one between the operating system's answer and that write.
4. **A count the store cannot read, write or parse ends the session**, with a reason of its own,
   `ATTEMPTS_UNRECORDABLE`. A corrupt value never reads as zero. It is the family's direction for a
   lock that cannot tell whether it should open: a ceiling that cannot be enforced is not offered.
5. **Cleared** on a successful unlock (best effort: a count that survives costs the next lock some
   attempts, the safe direction), on `sessionEstablished()` and on `sessionEnded()`. The last two
   propagate a store failure; the logout contract reports `LOCK_STATE` as failed, after the window is
   already closed.
6. **Cancelling writes nothing** — ADR-0020's mirror, unchanged.

## Rejected: charging the attempt before the prompt

Charging up front and refunding on a dismissal would also cost an attempt to a process killed while
the prompt is up. It buys nothing while dismissing is free: anyone able to kill the process in the
middle of a prompt can dismiss it instead, and a mismatch does not end the prompt anyway. It would add
a refund path to a security policy for no change in what an attacker can do.

## What the ceiling bounds, stated plainly

The ceiling counts **prompt sessions that end in a failure verdict**, not wrong fingers. On Android a
mismatch leaves the prompt open (`onAuthenticationFailed` does not resume) and the terminal verdict
arrives as an error, typically the operating system's own lockout; and dismissing costs nothing
(ADR-0011). So against someone trying fingers, the bound that bites first is the **operating system's
biometric lockout**, which this app does not configure; this ceiling bounds how many lockout cycles
one session survives. That was already true before this ADR. It is written down because "five
attempts" reads as five fingers, and it is not.

## Consequences

- **The default (5) is still fixed by no ADR**, this one included. Changing it deserves one.
- **On Android a tampered count reads as a fresh budget**, through task `0003`'s defect:
  `KeystoreSecureStore.get()` returns `null` for a blob that fails GCM authentication, which is the
  value "never written". Reaching that file takes the app's private storage — root, or a backup path
  F6 closed. Whether `get()` should throw instead is `0003`'s ADR; when it does, this path fails closed
  with everything else.
- **Rotation no longer resets the count.** It still recreates the lock locked — the other half of task
  `0009`, which waits for the shell's ViewModel.
- Verified by eight tests in `SessionLockTest` on both targets, each seen red by a bait aimed at it:
  the count back in memory (the headline test fails, `theCeilingSurvivesTheProcess`), the spent
  budget not checked, an unreadable or corrupt count read as zero, a miss let through unrecorded, and
  a success or a logout that no longer forgets. The baits were run once, not wired into
  `rehearse-gates.sh`: these are tests, and the suite is the control that runs on every build.
