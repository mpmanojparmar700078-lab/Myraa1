package com.example.brain

import java.util.regex.Pattern

object PrivacyFilter {

    private val API_KEY_PATTERN = Pattern.compile("(?i)(AIza[0-9A-Za-z_-]{20,50}|[a-z0-9_-]{20,64}(?:key|secret|token))")
    private val PASSWORD_PATTERN = Pattern.compile("(?i)(password\\s*[:=]\\s*\\S+|pass\\s*[:=]\\s*\\S+|passcode\\s*[:=]\\s*\\S+)")
    private val OTP_PATTERN = Pattern.compile("(?i)\\b(otp|one\\s*time\\s*password|verification\\s*code|pin)\\b.*?\\b(\\d{4,8})\\b")
    private val CREDIT_CARD_PATTERN = Pattern.compile("\\b(?:\\d{4}[- ]?){3}\\d{4}\\b")

    fun isSensitive(text: String): Boolean {
        if (text.isBlank()) return false
        if (API_KEY_PATTERN.matcher(text).find()) return true
        if (PASSWORD_PATTERN.matcher(text).find()) return true
        if (OTP_PATTERN.matcher(text).find()) return true
        if (CREDIT_CARD_PATTERN.matcher(text).find()) return true

        val lower = text.lowercase()
        val sensitiveKeywords = listOf("api key", "my password is", "mera password", "bank pin", "atm pin", "cvv", "secret key")
        return sensitiveKeywords.any { lower.contains(it) }
    }

    fun redactSensitive(text: String): String {
        var clean = text
        clean = API_KEY_PATTERN.matcher(clean).replaceAll("[REDACTED_API_KEY]")
        clean = PASSWORD_PATTERN.matcher(clean).replaceAll("password: [REDACTED]")
        clean = OTP_PATTERN.matcher(clean).replaceAll("OTP: [REDACTED]")
        clean = CREDIT_CARD_PATTERN.matcher(clean).replaceAll("[REDACTED_CARD]")
        return clean
    }
}
