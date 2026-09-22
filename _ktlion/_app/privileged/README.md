# Optional PrivilegedSimCallAudioBridge

This directory is deliberately **not an Android source set or dependency**. No
privileged implementation is included or selected by the normal APK.

A device-specific `PrivilegedSimCallAudioBridge` would implement the existing
`NeoCallAudioBridge` interface. Initialization must verify effective privileges,
the current SIM session, the actual routed input/output, and measured endpoint
results. It must fail closed rather than delegate to speaker playback as if that
were direct telephony audio. Expose direct RX/TX capabilities only after verified
device-specific tests; invalidate them on device/OS/route changes.

Prerequisites to investigate with an OEM/platform engineer:

- An OEM-supported system image or custom ROM, correct signing/privileged
  installation and permission allowlisting for the device's Android release.
- Effective `CAPTURE_AUDIO_OUTPUT` for protected call capture sources. The normal
  permission dialog and default-dialer role cannot grant it.
- Device audio policy/HAL support for telephony downlink capture **and separate
  uplink injection**. Capture permission alone does not provide transmission.
- A supported streaming IPC boundary if audio lives in an OEM system service,
  explicit authenticated access, bounded buffers and immediate cancellation.
- Validation with a separate remote phone, acoustic isolation, both speaker
  settings, interruption, hangup, route changes and manual takeover.

Root alone is not evidence that the HAL exposes usable audio. No root, system
partition modification, hidden API or privileged permission request is performed
by Neo. If this phone cannot expose both directions, use different hardware or a
telephony/VoIP endpoint whose media streams Neo legitimately owns. That would be a
separate integration and does not promise free cellular service or WhatsApp media
access.

Until these prerequisites are met, the standard adapter remains CALL_CONTROL_ONLY
and full live STT/AI/TTS conversation stays disabled.
