package com.neo.assistant

import org.junit.Assert.*
import org.junit.Test

class PresenceEvidenceTest {
    @Test fun missingCameraFramesRemainUncertain() {
        assertTrue(PresenceEvidence().result().startsWith("Presence is uncertain"))
    }
    @Test fun noFaceDoesNotBecomeAwayOrSleeping() {
        val evidence = PresenceEvidence()
        repeat(30) { evidence.observe(0) }
        assertTrue(evidence.result().startsWith("Presence is uncertain"))
    }
    @Test fun singleFaceFrameIsNotEnough() {
        val evidence = PresenceEvidence()
        evidence.observe(1)
        repeat(20) { evidence.observe(0) }
        assertTrue(evidence.result().startsWith("Presence is uncertain"))
    }
    @Test fun repeatedFaceObservationsStillDoNotIdentifyOwner() {
        val evidence = PresenceEvidence()
        repeat(5) { evidence.observe(1) }
        assertTrue(evidence.result().startsWith("A face was visible"))
        assertTrue(evidence.result().contains("does not identify you"))
    }
}
