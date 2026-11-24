package com.allen.vybe.auth

/** Only public numeric status is displayed; never expose exception messages or tokens. */
fun authorizationErrorMessage(statusCode: Int?): String = when (statusCode) {
    10 -> "Google rejected the OAuth configuration (status 10). In Cloud Console, verify the Android client package com.allen.vybe and this APK's signing SHA-1."
    7 -> "Google authorization could not reach the network (status 7). Check your connection and retry."
    4 -> "Google requires an account sign-in on this device (status 4). Check your Google account and retry."
    16, 12501 -> "Google authorization was cancelled (status $statusCode). You can connect again when ready."
    null -> "Google authorization did not complete. Try again and finish account selection and consent on your device."
    else -> "Google authorization failed (status $statusCode). Check the Android OAuth client, consent screen test users and Google Play services, then retry."
}
