# Neo Android local demo

Targets Android 10 and later, including the stated Oppo F7 / ColorOS 7.1.
This is a scenario runner, **not a working SIM-call assistant**. Contacts, camera and microphone are optional runtime permissions managed in Settings. Phone-call permissions are not requested because real-call handling is not connected. Internet access connects the Python API.
Python code and its private `.env` remain at the Neo root. The speech module
now reads model locations from that configuration. The local API connects greeting policy and report uploads. See `../../RUN_LOCAL.md` for pairing instructions.

## Try it

Open this directory in Android Studio with JDK 17 or newer, SDK platform 37,
and Gradle 9.1.0. Build the debug APK and install it on the phone.

1. Open **Test**, then **Test saved contact**. Wait six seconds: Neo speaks its greeting.
2. Type a simulated caller message and save. The report includes both sides.
3. Repeat but tap **I'll take this call**, **Decline call**, or **End test call** before
   six seconds: Neo must not speak.
4. Choose **Test unknown number**: Neo must never answer it.
5. Reopen the app to see reports. Read-aloud is optional. Reports stay in app
   private storage, are capped at 100, and can be cleared. Backup is disabled.

Each scenario times out after 60 seconds. Leaving the screen cancels it.
The Home screen includes a switch to silence demo greetings. Reports are
spoken only on request. Clearing reports requires confirmation.
Text-to-speech uses the phone's configured engine; available voices and any
engine network usage depend on the device. This is not caller audio routing.

## Remaining work before real calls

- Verify the chosen SIM/VoIP/forwarding audio path before enabling auto-answer.
  Do not auto-answer into silence while that path is missing.
- For SIM call management, implement the Android default-dialer role and a
  complete incoming/ongoing-call interface, including emergency dialing.
- Integrate a user-approved contact list, normalized number matching, denied
  permission handling, and private/withheld-number exclusion.
- For hosted conversations, select a provider and carrier-supported forwarding
  setup. Check caller-ID preservation, contact filtering before answer, ring
  timeout control, authentication, and two-way audio. Forwarding does not
  inherently preserve an exact six-second timer or contacts-only behavior.
- Add transcripts, assistant reply history, a private notification, and report
  synchronization. Never fabricate reports for conversations not captured.
- Test actual incoming calls, locked screen, reboot, loss of network, ColorOS
  battery restrictions, user takeover, disconnects, and simultaneous calls.

Android does not grant normal apps unrestricted SIM-call audio access simply
because the user grants microphone permission or selects a default dialer.
See https://developer.android.com/media/platform/sharing-audio-input and
https://developer.android.com/develop/connectivity/telecom/dialer-app.

## Connected preview (0.2)

Use Home > Connect to Python with the private pairing token. Reports upload automatically when paired and show pending/saved status. Manual sync retries when Python returns. This is upload-only; clearing local reports does not remove server copies. Real SIM calls remain disconnected.

## Permissions and presence preview (0.3)

Settings shows the actual Android permission state and opens system app settings
for revoking or re-enabling access. Each optional feature explains its purpose
before requesting permission. Denial does not stop sample calls or report viewing.
After the first successful API pairing, Neo offers access setup; it never grants
permissions itself.

- Contacts: select a real phone contact for a **simulated** call. No call is
  placed. The full address book and number are not uploaded; the selected name
  can appear in a demo report sent to your Python service.
- Camera: a user-started front-camera preview checks multiple frames using
  hardware face metadata where available, then closes the camera. Unsupported
  hardware and missing faces produce uncertainty. This does not identify the
  owner, detect sleep, or control automatic answering. No frames are stored or
  sent to Python. The Settings result includes the time of the past observation.
- Microphone: dictate a practice message after allowing access. Prefer on-device
  recognition where available; Android's fallback recognizer may process audio
  online. Confirm the text before saving it. Dictation stops after 20 seconds
  or when leaving the app. It does not capture real-call audio.
- Pause demo assistance in Settings. Unknown callers remain excluded. Take over
  after Neo starts, and expired calls cannot trigger delayed assistant replies.

Device checks still required: allow/deny each permission, permanently deny and
re-enable via Android settings, revoke during use, try unsupported camera/voice
hardware, and leave the app while camera/dictation is active.

## Event notifications (0.4)

Settings now contains notification access, a master switch, individual switches
for practice calls / reports / Python sync, Android category settings, and a
Send a test notification button. Android 13+ asks for POST_NOTIFICATIONS;
Android 10-12 use system notification settings. Denial leaves the demo usable.

Notifications are generated by actual local events: practice ringing, the
assistant starting its demo reply, a completed call report, confirmed uploads,
and reports waiting after a failed sync. Repeated sync failures alert at most
once per five minutes per app session; successful reconnect resets the throttle.
Empty health checks do not post sync alerts. Channel settings and Do Not Disturb
can silence notifications. Caller names, message text, and pairing tokens are
never included. A notification tap opens the relevant Neo screen.

The call-status notification is removed when the practice call ends and expires
if the process stops. Report/sync notifications are capped to the latest per
category. These are not real SIM-call alerts, remote push messages, or an
always-running background phone service.

Device test checklist: deny/allow notifications, toggle each category, block a
channel in Android, send a test, run a known and unknown practice call, tap the
report notification, and retry offline sync. Hardware/device testing is pending.
