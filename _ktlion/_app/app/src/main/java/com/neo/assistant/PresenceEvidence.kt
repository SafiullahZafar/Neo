package com.neo.assistant

/** Presence is an observation, never a claim about the owner's identity or sleep. */
class PresenceEvidence {
    private var samples = 0
    private var faceSamples = 0
    fun observe(faceCount: Int) {
        samples++
        if (faceCount > 0) faceSamples++
    }
    fun result(): String = if (samples >= 5 && faceSamples >= 3)
        "A face was visible in several frames. This does not identify you or establish whether you can answer."
    else "Presence is uncertain. Poor light, the camera angle or a covered lens can hide a face. Neo will not assume you are away or sleeping."
}
