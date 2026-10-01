package com.akash.qrdrawer.data

import java.net.URLDecoder

/** Fields pre-filled from a decoded UPI QR. All values are blank when the payload is not UPI. */
data class UpiDetails(
    val upiId: String = "",
    val payeeName: String = "",
    val bank: String = "",
) {
    val isUpi: Boolean get() = upiId.isNotBlank()
}

/** Parses `upi://pay?pa=…&pn=…`. Any other payload returns empty [UpiDetails]. */
fun parseUpi(payload: String): UpiDetails {
    val trimmed = payload.trim()
    if (!trimmed.startsWith("upi:", ignoreCase = true)) return UpiDetails()
    val query = trimmed.substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty()) return UpiDetails()
    val params = query.split('&').mapNotNull { part ->
        val key = part.substringBefore('=').trim().lowercase()
        if (key.isEmpty() || !part.contains('=')) return@mapNotNull null
        key to decode(part.substringAfter('='))
    }.toMap()
    val upiId = params["pa"].orEmpty().trim()
    return UpiDetails(
        upiId = upiId,
        payeeName = params["pn"].orEmpty().trim(),
        bank = bankForHandle(upiId),
    )
}

private fun decode(value: String): String =
    runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

private val HANDLE_BANKS = linkedMapOf(
    "okhdfcbank" to "HDFC Bank",
    "hdfcbank" to "HDFC Bank",
    "oksbi" to "SBI",
    "sbi" to "SBI",
    "okicici" to "ICICI Bank",
    "icici" to "ICICI Bank",
    "okaxis" to "Axis Bank",
    "axisbank" to "Axis Bank",
    "axl" to "Axis Bank",
    "kotak" to "Kotak Mahindra Bank",
    "kmbl" to "Kotak Mahindra Bank",
    "ybl" to "PhonePe",
    "ibl" to "PhonePe",
    "paytm" to "Paytm",
    "ptyes" to "Paytm",
    "ptaxis" to "Paytm",
    "pthdfc" to "Paytm",
    "ptsbi" to "Paytm",
    "apl" to "Amazon Pay",
    "yapl" to "Amazon Pay",
    "upi" to "BHIM",
    "pnb" to "Punjab National Bank",
    "barodampay" to "Bank of Baroda",
    "idfcbank" to "IDFC First Bank",
    "idfcfirst" to "IDFC First Bank",
    "yesbank" to "Yes Bank",
    "indus" to "IndusInd Bank",
    "fbl" to "Federal Bank",
    "federal" to "Federal Bank",
    "unionbank" to "Union Bank",
    "cnrb" to "Canara Bank",
    "jupiteraxis" to "Jupiter",
    "fam" to "FamPay",
)

/** Best-effort bank/app name from a VPA handle, e.g. `name@oksbi` → SBI. */
fun bankForHandle(upiId: String): String {
    val handle = upiId.substringAfter('@', missingDelimiterValue = "").lowercase()
    return HANDLE_BANKS[handle].orEmpty()
}

/** Masks the user part of a UPI ID so the drawer never shows it in full: `akashtiwari@okhdfc` → `akash•••@okhdfc`. */
fun maskUpiId(upiId: String): String {
    val at = upiId.indexOf('@')
    if (at <= 0) return upiId
    val user = upiId.substring(0, at)
    val visible = when {
        user.length <= 2 -> 1
        user.length <= 6 -> user.length / 2
        else -> 5
    }
    return user.take(visible) + "•••" + upiId.substring(at)
}
