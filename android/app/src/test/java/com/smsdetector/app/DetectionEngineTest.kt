package com.smsdetector.app

import com.smsdetector.app.data.db.Category
import com.smsdetector.app.data.detection.DetectionEngine
import org.junit.Assert.*
import org.junit.Test

/** Unit tests for the on-device classifier — no Android runtime required. */
class DetectionEngineTest {

    @Test
    fun detectsOtpCode() {
        val r = DetectionEngine.classify("Your OTP 839201 for login verification. Valid for 10 min.")
        assertEquals(Category.OTP, r.category)
        assertEquals("839201", r.otpCode)
        assertFalse(r.isSpam)
        assertNotNull(r.otpExpiresAtMillis)
    }

    @Test
    fun detectsBankOtpWithVerifiedLabel() {
        val r = DetectionEngine.classify("Bank Alert: OTP 445566 for your UPI transaction. Do not share.")
        assertEquals(Category.BANK, r.category)
        assertEquals("Bank Alert", r.label)
        assertEquals("445566", r.otpCode)
    }

    @Test
    fun detectsScamWithShortLink() {
        val r = DetectionEngine.classify(
            "URGENT: You won a prize! Claim now at bit.ly/free-cash. Act now!",
            "+91 99999 11111"
        )
        assertEquals(Category.SPAM, r.category)
        assertTrue(r.isSpam)
        assertTrue(r.spamScore >= 0.5f)
        assertEquals("Suspicious link", r.label)
    }

    @Test
    fun detectsTaxScamKeywords() {
        val r = DetectionEngine.classify("Tax refund pending. Verify your account now or risk of blocking.")
        assertTrue(r.isSpam)
        assertEquals(Category.SPAM, r.category)
    }

    @Test
    fun detectsPromotion() {
        val r = DetectionEngine.classify("Flat 50% off! Cashback offer valid till Sunday. Unsub T&CA.")
        assertEquals(Category.PROMOTION, r.category)
        assertFalse(r.isSpam)
    }

    @Test
    fun personalMessageIsSafe() {
        val r = DetectionEngine.classify("Hey! Did you get a chance to inspect the new release mockup?")
        assertEquals(Category.PERSONAL, r.category)
        assertFalse(r.isSpam)
        assertEquals(0f, r.spamScore, 0.001f)
    }

    @Test
    fun extractsInitialsAndDisplayName() {
        assertEquals("ER", DetectionEngine.initials("Elena Rostova"))
        assertEquals("Design Team", DetectionEngine.displayName("Design.Team"))
    }
}
