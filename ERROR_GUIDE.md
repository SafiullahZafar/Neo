# Troubleshooting Neo

In the updated Android app, open **Settings → Recent errors and fixes**.
The latest 30 error groups contain a code, explanation, next step, last occurrence
time and repeat count. Use **Copy error report** when asking for help. History is
local and can be cleared. It excludes raw exception messages, tokens, caller names
and transcripts. This is handled-error history, not a complete Android crash log.

Common codes:

| Code | Meaning / next step |
| --- | --- |
| API_UNREACHABLE | Start Python; restore USB forwarding after reconnecting. |
| API_HTTP_401 / 403 | Pair again with the configured token. |
| API_HTTP_404 | Check matching server version and endpoint. |
| API_TIMEOUT / API_DNS / API_TLS | Check server responsiveness, address or certificate respectively. |
| MIC_PERMISSION | Allow ordinary microphone access in Android settings. |
| SIM_AUDIO_PROTECTED | Direct capture needs privileged access; ordinary Allow buttons cannot grant it. |
| SIM_NOT_ACTIVE / AUDIO_TEST_BUSY | Keep one eligible SIM call active, or stop the previous diagnostic. |
| SIM_NO_TX_DEVICE | Android exposed no telephony output for that strategy. |
| TTS_VOICE_UNAVAILABLE | Install/select an offline Android speech voice and preview it. |
| STT_6 / STT_7 / STT_8 / STT_9 | No speech, no match, service busy or insufficient permission respectively. |
| LOCAL_STORAGE_FAILED | Check free storage and required files. |
| CALL_MONITOR_START_FAILED | Android rejected background monitoring; check role/background access. |
| NOTIFICATION_POST_FAILED | Android rejected posting; check notification settings and review reports in Neo. |

Python HTTP failures now return `detail` plus an `error` object containing a code,
message, action and reference. Unexpected server errors log the same reference and
exception type without request content or exception text. Voice generation reports
timeouts, missing files and file failures separately from unknown worker failures.

If the cause cannot be determined, Neo says so. Successful local speech playback
does **not** establish that a SIM or WhatsApp caller heard it. These changes improve
diagnostics; they do not enable a direct call audio transport.

Phone testing is deferred until reconnection. Install the rebuilt debug APK then
test a wrong pairing token, stopped Python, denied microphone permission and a
voice preview. Check that the relevant error appears and can be copied from history.
Restore the correct settings after each test. A clean history alone is not evidence
that all features work.

## Local validation (2026-09-23)

- Python: 18 tests passed, including sanitized validation/internal errors and a
  missing-file voice-worker failure.
- Android debug APK assembled. Kotlin: 50 of 54 tests passed, including all five
  connection/error-catalog tests. No tests were skipped.
- Four existing call-behaviour tests fail against changes already present before
  this error-reporting work: `CallRulesTest.answeringRequiresAllThreeConditions`
  and three `SpeakerGreetingGateTest` cases. The current code ignores known-contact
  status in `mayAnswer`, starts greetings immediately when ready, extends the
  readiness timeout to 30 seconds and no longer stops after readiness is lost.
  Those existing changes were preserved; the full suite is not passing.
- Android lint completed successfully (zero errors; existing warnings remain).
- No phone installation or physical call test was performed.
