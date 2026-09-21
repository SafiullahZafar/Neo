# Real call activity and current limits

Open **Settings > Open call activity and owner status**. Save a preferred name and
Unknown, Busy or Away. Availability is explicitly selected by the owner; camera
observations are not used to identify the owner or infer sleep. Each call snapshots
the greeting preview from these settings. It is marked **not sent**.

## What is implemented

- SIM InCallService waits for the saved answer delay and rechecks the monitoring switch, contact
  permission/lookup, and ringing state before requesting an answer. Unknown callers
  are not answered. Each call owns its timer; answering or ending cancels it.
- The phone-state receiver observes only when Neo is not the default dialer. It no
  longer competes with InCallService using hidden APIs, shell commands or headset
  toggles. These could report false success or toggle an already answered call.
- WhatsApp uses an explicit answer action only, with duplicate notification
  suppression and cancellation on notification removal. A phone URI provided in
  notification people metadata must resolve to a saved contact. Missing identity
  fails closed, so some WhatsApp versions will not be eligible for auto-answer.
  Sending an action is recorded as a request, never proof of a connected call.
- The latest 100 call event records stay in private app storage. This is event
  history, **not audio recording**. Records contain caller label, timestamps,
  lifecycle events, unread state and the prepared greeting. Local deletion and
  explicit mark-reviewed controls are available. This history is not uploaded to
  the Python API, which still accepts practice reports only.
- Generic notifications announce new activity. Phone unlock repeats the summary
  while the monitoring service is alive; opening Neo also checks unread records.
  Permission denial, notification category settings, force-stop and OS background
  restrictions can prevent delivery. The history remains available in the app.

## Not yet available

There is no supported caller-audio transport in this project for SIM or WhatsApp.
Pickup does not provide microphone/call-stream ownership. Greetings are not
transmitted, caller speech is not transcribed, and conversations are not recorded.
Local speaker TTS is not a verified substitute for transmitting audio to a caller.
A supported telephony/VoIP audio integration is required before these features can
be implemented honestly. See Android's audio-input sharing documentation:
https://developer.android.com/media/platform/sharing-audio-input

The existing default-dialer integration is experimental; a complete replacement
dialer UI and physical-device validation are still required. This version does not
claim continuous operation after force-stop or reboot.

## Later device checks (not performed during development)

1. Install the debug APK; allow notifications and contacts explicitly. Review the
   Android phone/default-dialer and WhatsApp notification-access settings.
2. Turn monitoring on. Ring from a saved contact: answer before the selected delay and
   confirm Neo does not also request pickup. Repeat without answering; inspect the
   event history for request and connection states separately.
3. Ring from an unknown number, then with contacts access revoked: neither should
   be auto-answered. Switch monitoring off during the countdown; pickup must stop.
4. End a ringing call before the selected delay. No later action should run. Check repeat
   calls and call waiting independently on the actual device.
5. Repeat with WhatsApp. If caller metadata is missing, expect a blocked/unverified
   entry. Notification removal means outcome unknown, not necessarily call ended.
6. Lock/unlock the phone with unread events. Confirm the generic summary opens real
   call activity. Mark reviewed, repeat unlock, and ensure no new unread reminder.
7. Deny notifications: history should remain available. Delete local activity and
   confirm Android/WhatsApp's own call histories are untouched.
8. Review greeting previews for all three owner statuses. Do not expect callers to
   hear them or expect any audio file/transcript from these tests.

## Adjustable delay and offline voices

In **Settings > Choose delay and preview voices**, choose 1?60 seconds and tap
**Save answer delay**. Six seconds is only the initial default. SIM, eligible
WhatsApp and practice calls snapshot this value when ringing begins. Existing
ringing timers are unchanged; ending or answering still cancels pending pickup.
Carrier voicemail/ring duration can prevent longer delays from being reached.
The Python policy remains a default for its demo API, not a device-setting override.

The voice picker lists installed offline voices exposed by the current Android TTS
engine, excluding voices marked as needing downloads or network access. Preview,
stop and save a voice. Neo charges nothing and uses no paid voice API; third-party
engine pricing is outside Neo's control. No bundled voice catalog is claimed.
The chosen voice is used for previews, practice replies and spoken reports. A
missing saved voice does not silently switch to online synthesis. Open Android
speech settings to manage voice data, then refresh the picker. Selecting a voice
changes speech sound, not the language of the English greeting text.

Later device tests: try 1, 20 and 60 seconds; pick up early; change the saved delay
while a call is ringing and check that only later calls change. With airplane mode
on, preview multiple installed voices, save one and verify a practice reply. Test
missing voice data, leaving the screen during playback, and Android TTS failure.
Android voice API: https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine
