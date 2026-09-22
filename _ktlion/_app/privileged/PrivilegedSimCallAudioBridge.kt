package com.neo.assistant.privileged

import com.neo.assistant.NeoCallAudioBridge

/**
 * OEM implementation boundary ONLY. This directory is excluded from normal APKs.
 * An implementation must measure both directions on its supported system image;
 * no default implementation or privileged permission grant is supplied here.
 */
interface PrivilegedSimCallAudioBridge : NeoCallAudioBridge {
    fun verifySystemAudioAuthorization(): Boolean
    fun supportedSystemBuildFingerprint(): String
}
