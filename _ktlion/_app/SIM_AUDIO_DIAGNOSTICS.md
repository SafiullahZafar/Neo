# SIM audio investigation

## Status

### Settings/connection recovery, 2026-09-22

The connected Xiaomi phone already had all four requested runtime permissions
and Neo as default dialer. The old buttons silently returned in this case. The
permission button now explains that access is already granted; the default-app
button opens Android settings even when Neo already holds the role.
The rebuilt APK was installed successfully. On-device checks confirmed the
already-granted feedback and opening Google's Android default-app permission
controller. No default-app selection was changed during verification.

Validation: 51 Android/JVM tests passed, no skips/failures; lint completed with
zero errors and 126 warnings. Python server readiness responded successfully.

Python was healthy, the phone used the correct localhost address and had a saved
pairing token, but ADB reverse forwarding was absent. Restoring
`adb reverse tcp:8765 tcp:8765` fixed the issue: the actual phone's readiness
button displayed **Python connected**. Connection errors now distinguish refused
connections, timeouts, rejected tokens, invalid configuration and outdated APIs.

This confirms app-to-Python connectivity only. SIM media transport still reports
NOT_CONNECTED; no completed real-call audio diagnostics were available at this
point. Speaker-only speech is not proof that the remote phone hears Neo.

### Earlier audio implementation validation

The diagnostic tools are implemented. **Actual SIM input/output is UNVERIFIED.**
No physical phone was attached during this change. No source, speaker condition,
output route or complete conversational turn has been verified on hardware.
Existing auto-answer was preserved; its earlier successful tests are not proof
of call audio access or a fresh regression test.

Physical-device status: `adb devices -l` listed no attached device. The APK has
not been installed or tested against a remote caller during this change.
The owner chose to perform the physical test later.

Latest validation: debug APK build succeeded; 49 Android/JVM tests passed with
zero skips/failures; 14 Python tests passed; lint completed with zero errors and
126 warnings (including compatibility/deprecation and UI text warnings).

The other AI's proposal is a useful test plan, not proof that ordinary permission
prompts unlock cellular audio. Android documents privileged call capture:
https://developer.android.com/media/platform/sharing-audio-input
AudioTrack preferred devices are preferences, not guaranteed routes:
https://developer.android.com/reference/android/media/AudioTrack

## Use on the phone

1. Install `app/build/outputs/apk/debug/app-debug.apk`. Open Neo > Settings >
   **SIM Assistant status, permissions and live test**. The existing voice-settings
   diagnostics button opens the same page. Python and an API key are not needed
   for the on-phone audio test. Use Request missing SIM permissions as needed;
   already granted permissions are skipped. Android Settings manages revocation.
2. Neo must be the default phone app. Connect exactly one SIM test call with a
   willing participant. The screen must show ACTIVE (verified SIM account).
   The existing contact filter and answer delay still govern automatic pickup.
3. Set Speaker OFF using the call controls, return to diagnostics and confirm
   both participants agree. Disable the older automatic speaker experiment for
   these tests so it does not compete with the selected route.
4. Choose MIC, VOICE_COMMUNICATION or VOICE_RECOGNITION. Press **Test caller
   capture**. The owner stays silent; the remote participant says “Neo test four
   seven two” during the five-second sample. Grant mic permission if requested,
   then press the button again. Review actual input route, signal energy,
   app-session processing, and offline phrase-match result.
5. Select VOICE_CALL, VOICE_DOWNLINK and VOICE_UPLINK individually. Without
   CAPTURE_AUDIO_OUTPUT, each reports PERMISSION_DENIED before attempting capture.
   Neo does not add that privileged permission to the ordinary APK.
6. Try each playback strategy: media TTS, communication attributes TTS, preferred
   telephony AudioTrack. Listen on the other phone. After local completion select
   YES or NO. This confirms audibility only, never a direct uplink path. A missing
   telephony output is reported rather than silently replaced with speaker audio.
7. Repeat with Speaker ON, and repeat capture with AEC/NS enabled and disabled
   where available. AGC is inspected but not changed. Speaker OFF can still leak
   audio from the earpiece: a phrase match alone is not proof of direct capture.
8. Stop / Take over, call ending, route changes and leaving the screen cancel
   tests. Stop does not end the call. Verify normal manual calls and auto-answer
   still behave as before. WhatsApp behavior is outside this SIM-only diagnostic.

## Guided round-trip test

After auto-answer connects, keep the SIM page visible, select a recording source
and playback strategy, and press **Run Full SIM AI Test**. The remote participant
says “Hello Neo test seven eight nine.” The app shows the recognized text in
memory. Only a phrase match triggers the offline reply “I can hear you. This is
Neo.” Confirm YES/NO from the other phone. No match, unavailable STT or a changed
call/route prevents the reply. Leaving the page cancels rather than starting an
unattended recording after you return.

This is one controlled audio exchange, not a general AI agent. Recognition
engines can ignore external sample input, and acoustic leakage remains possible.
Even a phrase match plus audibility confirmation does not enable automatic AI.
Direct versus acoustic capability requires additional hardware evidence; the
standard bridge stays CALL_CONTROL_ONLY. AEC/NS comparisons still affect only
the app's session. Half-duplex acoustic conversation and barge-in remain
unimplemented until usable input/output is verified, as required by the task.

The page shows effective app privileges without running `su`, probing protected
partitions or flashing anything. A normal app UID does not rule out a rooted
device; device-wide root remains UNKNOWN. The optional privileged interface is
isolated in `privileged/`, outside the APK. It has no system implementation.

## Python readiness

Restart the Python server from the project root after updating:

```powershell
cd D:\development\python-related\Neo
.\.venv\Scripts\python.exe main.py
```

If a server is already running in your terminal, stop that instance with Ctrl+C
before restarting. The SIM page's **Check paired Python SIM readiness** uses the
existing encrypted pairing token and authenticated `/v1/sim/readiness` route.
With a USB connection the existing forwarding is:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8765 tcp:8765
```

The response explicitly reports NOT_CONNECTED/UNAVAILABLE for live SIM media and
conversation. It reports Vosk/Piper file presence separately from model loading;
no desktop model is loaded and no PC microphone is opened by this check. No
audio is uploaded, no paid API/key is added, and Python connectivity cannot
override Android's audio restrictions. The existing desktop speech pipeline is
PC microphone/speaker code, not a SIM media transport or general conversational
LLM service. Vosk/Piper model files were absent in this workspace during audit.

## Interpretation and storage

Results are stored locally, with timestamp, attempt ID, Android build, Neo
version, source/strategy/effect settings, speaker condition and route. Forty
entries are retained; use Delete diagnostic results to clear them. Caller names
and numbers are not logged. No diagnostic samples or transcripts go to Python.

Capture is bounded to five seconds/160 KB PCM. Offline recognition of that sample
is attempted only on Android 13+ with an installed on-device recognizer. There is
no cloud recognizer fallback or model download. Engines may not honor supplied
audio extras, so phrase matches remain tentative. Only the fixed-phrase match
is saved, not incidental recognized content. Temporary audio is deleted on
completion/cancel; a process crash can leave app-private cache files until the
next diagnostics launch (which removes diagnostic cache files).

SILENCE_ONLY means measured zero/integer-zero RMS, not proof of a particular
Android restriction. AVAILABLE means signal exists with unknown origin; mic
permission alone does not mean recording works during a call. LOCAL_PLAYBACK and
YES confirmation do not prove direct injection. A NO result does not identify
echo cancellation as the cause. App-session AEC/NS controls do not disable or
inspect the phone's cellular DSP.

The standard bridge advertises CALL_CONTROL_ONLY. Enumeration, waveform energy,
phrase recognition, and user audibility confirmation cannot automatically promote
it to direct RX, TX or full duplex. No fake live conversation, VAD/barge-in loop,
or cloned-voice live pipeline is started. Two-way direct audio needs stronger
device-level evidence before implementation can be enabled.

## Implementation

Reused: existing InCallService pickup/filter/timer, offline voice preference,
speaker experiment (explicit only), settings navigation and permissions.

Added: NeoCallAudioBridge and evidence types; SimAudioSession observation;
StandardSimCallAudioBridge; SimAudioDiagnosticsActivity; CapturedAudioRecognizer;
bounded DiagnosticPcm parser and unit tests. InCallService now also observes
manual/outgoing calls for verified SIM diagnostics, without changing pickup.

Only new permission: normal MODIFY_AUDIO_SETTINGS for app audio effect tests.
No cellular audio mode change, volume change, forced speaker route, root or
privileged permission is added. Communication attributes are tested while the
cellular stack retains control of MODE_IN_CALL.

The follow-up adds no manifest permissions: all needed normal permissions were
already declared. It adds `SimAccessStatus`, structured recognition/phrase checks,
a guided test on the existing activity, and the Python readiness endpoint. See
[the permission audit](SIM_PERMISSION_AUDIT.md) for grantability and usage.

If protected capture is denied and no verified direct transmission exists, stop
adding permissions. See [the isolated privileged architecture](privileged/README.md).
An OEM-supported device-specific audio implementation is the next direct-audio
path, not another runtime Allow button. The existing free acoustic experiment
remains unverified and does not provide an enabled conversational fallback.
