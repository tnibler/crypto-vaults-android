package com.tnibler.cryptomator_android.vault.exception

import androidx.documentfile.provider.DocumentFile

class FileNotFoundException(val document: DocumentFile, val path: String) : RuntimeException() {

    override fun toString(): String {
        return super.toString() + " ${document.uri}, $path"
    }
}
