# Experimental speakerphone greeting

This is an acoustic experiment, not call-audio injection or a conversation bot.
No Gemini key, Python service, cloned voice, or microphone recording is needed.
Android may mute the speech during a call or cancel it as echo. A completed TTS
callback proves only local playback processing, not that the caller heard it.

## Enable and test

1. Open **Neo > Settings > Choose delay and preview voices**.
2. Choose **Use Android offline default**, then **Preview selected voice**. Install
   an offline voice using Android speech settings if the preview cannot play.
3. Enable **Experimental speaker greeting**. It starts off, so ordinary call
   pickup does not unexpectedly broadcast a greeting.
4. Enable known-contact auto-answer and the necessary contact/phone/notification
   access in Neo Settings. For the first test, leave Neo visible on an unlocked phone.
5. Call from a saved contact on another phone. Keep the phones in different rooms
   or far enough apart that the tester cannot hear Neo directly through the air.
6. For SIM, Neo requests speakerphone after its answer reaches ACTIVE, and tries
   to restore the previous route after the greeting. For WhatsApp, tap **Speaker**
   in WhatsApp after pickup. Neo cannot reliably control another app's audio route.
7. A greeting starts once after a short settling delay if call activity is still
   detected. WhatsApp notification formats vary; when readiness cannot be detected,
   the attempt expires instead of claiming success. With the call still connected
   and Speaker on, return to Neo's voice settings and tap **Try greeting during
   current call** as a manual test. This does not answer a new call.
8. The other phone's user must report whether they heard the greeting over the call.
   If they only hear silence, the acoustic route did not work on this device/setup.
   Increasing permissions or adding Gemini will not establish a missing audio path.

The greeting uses the owner name and explicitly selected availability from the
owner-status screen. It identifies Neo as automated and says it cannot hear or
record replies. It never claims the owner is asleep or away based on camera input.

Use **Stop speaker greeting** or turn the experiment off to cancel. Call ending,
loss of call readiness, service shutdown and a 45-second watchdog also stop the
attempt. The switch does not disable auto-answer; use the separate monitoring
switch for that. Volume and microphone mute settings are not changed. Bluetooth
or headphones may route the speech away from the phone speaker; disconnect them
for this test. Caller audio, transcripts and two-way responses are not captured.

Automated tests verify start/timeout/cancellation gating. Actual speaker routing,
TTS during calls, echo cancellation and remote audibility need a physical test.
