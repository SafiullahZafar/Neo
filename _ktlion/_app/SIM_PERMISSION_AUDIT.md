# Normal APK permission audit

No additional permission is needed for the new status/guided-test controls.
No privileged permission is requested. No paid service has been added.

| Access | Existing purpose | Grant mechanism / SIM requirement |
| --- | --- | --- |
| READ_PHONE_STATE | Phone-state observation | Runtime; shown as phone access |
| READ_CONTACTS | Verify saved contacts for pickup | Runtime; required for contact filtering |
| RECORD_AUDIO | Explicit diagnostic capture, voice samples, optional dictation | Runtime; does not grant call-stream capture |
| POST_NOTIFICATIONS | App call/report notices | Runtime Android 13+; NOT_REQUIRED before 13, separate app notification switch shown |
| Default phone role | InCallService and Call.answer() | Android role chooser; not a runtime permission |
| MODIFY_AUDIO_SETTINGS | Diagnostic app-session effects | Normal install-time permission; does not unlock telephony routing |
| FOREGROUND_SERVICE | Existing monitoring service | Normal install-time permission |
| FOREGROUND_SERVICE_PHONE_CALL | Existing phone-call foreground service on newer Android | Install-time declaration; role/eligibility requirements still apply |
| MANAGE_OWN_CALLS | Existing foreground phone-service eligibility declaration | Normal permission; does not grant caller audio or implement a self-managed media endpoint |
| ANSWER_PHONE_CALLS | Existing declaration | No extra runtime request needed by the default-dialer Call.answer() path |
| READ_CALL_LOG | Existing declaration for legacy call-related access | Not requested by the new SIM diagnostics; no call-log database reads added |
| CAMERA | Existing explicit presence check | Optional runtime access; NOT_REQUIRED for SIM audio |
| INTERNET | Existing pairing/API/voice-preview requests | Normal install-time permission; no call audio sent by diagnostics |
| RECEIVE_BOOT_COMPLETED / WAKE_LOCK | Existing declarations | No runtime prompt; declarations alone do not establish an always-on assistant |
| CALL_PHONE | Not declared | NOT_REQUIRED: this flow does not originate calls |
| CAPTURE_AUDIO_OUTPUT / MODIFY_PHONE_STATE | Not declared | Privileged/restricted; ordinary Allow dialogs cannot grant these |

The unified SIM section skips granted permissions and exposes Android settings
for later revocation. Previously denied permissions that Android may no longer
prompt for are directed to app settings. Missing default-dialer status uses the
default-app settings; existing role selection remains in the main Settings page.

System diagnostics report the app UID, public system-app flag and effective
protected capture permission. They do not infer privilege from root binaries,
request `su`, or claim root automatically supplies modem/HAL audio access.

Reference: https://developer.android.com/media/platform/sharing-audio-input
