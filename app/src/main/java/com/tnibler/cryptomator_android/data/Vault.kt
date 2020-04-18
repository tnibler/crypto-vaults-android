package com.tnibler.cryptomator_android.data

import android.net.Uri

data class Vault(
    val id: Long,
    val rootUri: Uri,
    val masterKeyFileName: String,
    val flags: VaultFlags
)