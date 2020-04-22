package com.tnibler.cryptomator_android.openVault

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.tnibler.cryptomator_android.data.Db
import com.tnibler.cryptomator_android.data.Vault
import com.tnibler.cryptomator_android.data.VaultFlags
import com.tnibler.cryptomator_android.vault.VaultAccess

class OpenVaultModel(val vaultRootUri: String, val context: Context, val db: Db) {
    private val TAG = javaClass.simpleName
    private val root: DocumentFile
    private val keyFile: DocumentFile
    private val flags: VaultFlags = VaultFlags() //TODO
    init {
        val uri = Uri.parse(vaultRootUri) ?: throw IllegalArgumentException("Cannot parse vault root Uri!")
        root = DocumentFile.fromTreeUri(context, uri) ?: throw RuntimeException("Could not find root directory!")
        val defaultKeyfile = root.findFile("masterkey.cryptomator")
        if (defaultKeyfile != null) {
            Log.d(TAG, "Using default keyfile")
            keyFile = defaultKeyfile
        }
        else {
            TODO()
        }
    }

    fun open(password: ByteArray, contentResolver: ContentResolver): Boolean {
        try {
            val masterKeyFileName = keyFile.name ?: throw RuntimeException()
            val vault = VaultAccess(
                rootDocumentFile = root,
                passphrase = password,
                masterKeyFilename = masterKeyFileName,
                pepper = byteArrayOf(),
                contentResolver = contentResolver,
                flags = flags
            )
            db.putVault(
                Vault(
                    id = 0,
                    rootUri = root.uri,
                    masterKeyFileName = masterKeyFileName,
                    flags = flags
                )
            )
            return true
        }
        catch (e: Exception) {
            return false
        }
    }
}