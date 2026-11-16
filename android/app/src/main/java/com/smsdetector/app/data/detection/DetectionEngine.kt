package com.smsdetector.app.data.detection

import com.smsdetector.app.data.db.Category

/**
 * Privacy-first, on-device SMS classifier.
 *
 * No message body ever leaves the device. Rules are versioned so the backend
 * can ship updated signatures (hashes only) without touching message content.
 */
object DetectionEngine {

    data class Result(
        val category: Category,
        val spamScore: Float,      // 0f (safe) .. 1f (certain spam)
        val isSpam: Boolean,
        val otpCode: String?,
        val otpExpiresAtMillis: Long?,
        val label: String?,
        val matchedRule: String?
    )

    private val OTP_CODE = Regex("""\b(\d{4,8})\b""")
    private val URL = Regex("""(https?://|www\.)\S+""", RegexOption.IGNORE_CASE)
    private val SHORT_URL = Regex("""\b(bit\.ly|tinyurl|t\.co|goo\.gl|is\.gd|s\.id)\b""", RegexOption.IGNORE_CASE)

    private val OTP_KEYWORDS = listOf(
        "otp", "one-time", "one time", "verification code", "verify code",
        "verification pin", "passcode", "2fa", "auth code", "security code", "login code"
    )
    private val BANK_KEYWORDS = listOf(
        "bank", "account", "a/c", "debit", "credit", "upi", "transaction",
        "withdrawn", "deposited", "payment", "balance", "ifsc", "card", "emi"
    )
    private val PROMO_KEYWORDS = listOf(
        "offer", "discount", "sale", "cashback", "free", "unsub", "promo",
        "coupon", "deal", "limited time", "buy now", "offer valid", "flat"
    )
    private val SPAM_KEYWORDS = listOf(
        "congratulations", "you won", "lottery", "prize", "claim now", "urgent",
        "tax refund", "suspend", "verify your account now", "click here", "act now",
        "guaranteed", "work from home", "earn rs", "quick money", "cheap loans",
        "kyc update", "account blocked", "linking number", "risk of blocking"
    )

    fun classify(body: String, sender: String? = null): Result {
        val text = body.lowercase()
        var score = 0f
        val matches = mutableListOf<String>()

        // --- OTP / Bank code detection (highest priority) ---
        val hasOtpKeyword = OTP_KEYWORDS.any { text.contains(it) }
        val code = OTP_CODE.find(body)?.value
        if (hasOtpKeyword && code != null) {
            matches += "OTP_CODE"
            val isBank = BANK_KEYWORDS.any { text.contains(it) }
            return Result(
                category = if (isBank) Category.BANK else Category.OTP,
                spamScore = 0f,
                isSpam = false,
                otpCode = code,
                otpExpiresAtMillis = System.currentTimeMillis() + 10 * 60 * 1000L,
                label = if (isBank) "Bank Alert" else "OTP",
                matchedRule = if (isBank) "BANK_OTP" else "OTP_CODE"
            )
        }

        // --- Spam signals ---
        if (text.contains("urgent") || text.contains("act now")) { score += 0.25f; matches += "URGENCY" }
        val spamKeywordHits = SPAM_KEYWORDS.count { text.contains(it) }
        if (spamKeywordHits > 0) {
            // Multiple distinct scam phrases are a stronger signal than one.
            score += (0.35f + 0.15f * (spamKeywordHits - 1)).coerceAtMost(0.6f)
            matches += "SPAM_KEYWORD"
        }
        if (SHORT_URL.containsMatchIn(text)) { score += 0.3f; matches += "SHORT_URL" }
        if (URL.containsMatchIn(text)) { score += 0.12f; matches += "URL" }
        if (sender != null && sender.all { it.isDigit() } && sender.length > 10 &&
            URL.containsMatchIn(text) && score == 0.12f) {
            score += 0.2f; matches += "UNKNOWN_SENDER_URL"
        }
        score = score.coerceIn(0f, 1f)
        if (score >= 0.5f) {
            return Result(
                category = Category.SPAM,
                spamScore = score,
                isSpam = true,
                otpCode = null,
                otpExpiresAtMillis = null,
                label = when {
                    matches.contains("SHORT_URL") -> "Suspicious link"
                    matches.contains("SPAM_KEYWORD") -> "Potential scam"
                    else -> "Suspected spam"
                },
                matchedRule = matches.joinToString("+")
            )
        }

        // --- Bank alerts (no OTP) ---
        if (BANK_KEYWORDS.any { text.contains(it) }) {
            return Result(Category.BANK, 0f, false, null, null, "Bank Alert", "BANK_KEYWORD")
        }

        // --- Promotions ---
        if (PROMO_KEYWORDS.any { text.contains(it) } || (URL.containsMatchIn(text) && score > 0f)) {
            return Result(Category.PROMOTION, score, false, null, null, "Promotion", "PROMO_KEYWORD")
        }

        return Result(Category.PERSONAL, score, false, null, null, null, null)
    }

    /** Extract a display-friendly name from a sender address. */
    fun displayName(sender: String): String = when {
        sender.startsWith("+") -> sender
        sender.length > 10 && sender.all { it.isDigit() } -> sender
        else -> sender.replace(Regex("""[._-]+"""), " ").trim()
    }

    fun initials(name: String): String =
        name.split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "#" }
}
