package com.andromyth.jiotv.android.data

data class JioSession(
    val mobile: String,
    val ssoToken: String,
    val authToken: String,
    val refreshToken: String,
    val crmid: String = "",
    val uniqueId: String = "",
    val deviceId: String,
    val userId: String = ""
)

data class TvChannel(
    val id: String,
    val name: String,
    val logoUrl: String = "",
    val group: String = "Other",
    val language: String = "Other",
    val isDrm: Boolean = false
)

data class PlaybackInfo(
    val url: String,
    val mimeType: String,
    val drmLicenseUrl: String? = null
)
