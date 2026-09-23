# Connected-device SIM audio findings — 2026-09-22

These are read-only findings, not a working call-audio implementation.

## Observed on the actual phone

- Model: Xiaomi 23129RN51X (`blue_global`), Android 16.
- Xiaomi's Redmi A3 quick-start guide identifies model 23129RN51X as Redmi A3:
  https://gl123.alsgp0.mi-fds.com/gl123/Redmi/Redmi%20A3/%E9%87%8F%E4%BA%A7/C3Y_%E5%85%A5%E9%97%A8%E6%8C%87%E5%8D%97%20en_ar_fr_A_20231127.pdf
- Observed firmware increment: `V816.0.9.0.WGRMIXM`; verified boot state: `green`.
- Build type: `user`; `ro.boot.flash.locked=1` (bootloader reports locked).
- Neo is an ordinary debuggable application, not a system app. Its app UID is
  non-root. This does not conclusively prove the entire device has no root tools.
- READ_PHONE_STATE, ANSWER_PHONE_CALLS, RECORD_AUDIO, READ_CONTACTS and notification
  permissions are already granted. These are not the missing audio privilege.
- Neo has no granted CAPTURE_AUDIO_OUTPUT or MODIFY_PHONE_STATE permission.
- The readable `/vendor/etc/audio_policy_configuration.xml` defines
  `AUDIO_DEVICE_OUT_TELEPHONY_TX`, named `Telephony Tx`.
- Its sources include built-in/headset/Bluetooth/USB microphone inputs and
  `incall_music_uplink`.
- The `incall_music_uplink` mix port uses `AUDIO_OUTPUT_FLAG_INCALL_MUSIC` and lists
  stereo PCM16/PCM32 at 44100/48000 Hz. This is a declared system route, not proof
  it is exposed to this APK or usable in a live call.

## Why another normal permission prompt does not solve this

Android documents direct call capture as requiring privileged access:
https://developer.android.com/media/platform/sharing-audio-input

AOSP AudioPolicyService checks MODIFY_PHONE_STATE for API_OUTPUT_TELEPHONY_TX
and rejects unauthorized callers. Call-redirection flags introduce additional
CALL_AUDIO_INTERCEPTION checks; they are not a public-app bypass:
https://android.googlesource.com/platform/frameworks/av/+/refs/heads/main/services/audiopolicy/service/AudioPolicyInterfaceImpl.cpp

MODIFY_PHONE_STATE is not available to ordinary third-party applications:
https://developer.android.com/reference/android/Manifest.permission#MODIFY_PHONE_STATE

The AOSP check is source evidence, not a disassembly or live tracing of Xiaomi's
vendor implementation. The specific firmware's end-to-end RX/TX remains untested.
There is no verified normal-APK direct route here. Enumerating or selecting the
telephony device cannot grant the missing authorization.

## What would be needed next

For a phone-only direct implementation: an OEM-supported privileged integration,
or a separately reviewed compatible system image with the necessary privileges
and working RX/TX HAL routes. Bootloader unlock/root alone is not a guarantee.
No unlock, root, flash, system partition edit or hidden-API workaround was done.

A separate free acoustic experiment could use the existing PC microphone and
speaker beside the phone on speakerphone. The PC would process speech and play
replies into the phone microphone. That is not direct cellular audio, depends on
available hardware and acoustic behavior, and has not been verified. It requires
explicit foreground tests before any continuous agent is enabled. It does not
meet a phone-only requirement.

The owner subsequently explicitly rejected a laptop-dependent solution. That
experiment is therefore out of scope. The target remains phone-only operation.

Manufacturer guidance says bootloader unlocking on later Xiaomi models clears
user data and describes eligibility constraints; this is not evidence that a
particular unlock process or custom firmware supports this Redmi A3 build:
https://www.mi.com/global/support/faq/details/KA-07238/

There is currently no verified privileged RX/TX implementation, compatible
system-image installation plan or recovery-tested firmware package prepared for
this phone. Unlocking first would not establish feasibility and is not an
implementation step justified by the present evidence. No unlock or flash has
been attempted. Broad device access authorization is not a substitute for the
missing OEM/platform privileges and device-specific audio implementation.

No extra diagnostic UI or fake permission has been added in response to these
findings. The requested fully working SIM conversation remains incomplete.
