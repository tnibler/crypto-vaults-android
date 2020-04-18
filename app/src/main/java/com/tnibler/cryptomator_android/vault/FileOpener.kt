package com.tnibler.cryptomator_android.vault

import android.content.ContentResolver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.util.Log

class FileOpener(private val looper: Looper = Looper.myLooper()!!) {
    private val handler = Handler(looper)
    private val TAG = javaClass.simpleName

    fun openFile(uri: Uri, mode: String, vaultAccess: VaultAccess, storageManager: StorageManager, contentResolver: ContentResolver): ParcelFileDescriptor {
        Log.d(TAG, "Opening proxy fd for file $uri, mode=$mode")
        return storageManager.openProxyFileDescriptor(ParcelFileDescriptor.parseMode(mode),
            CryptoProxyFileDescriptorCallback(
                uri,
                vaultAccess,
                contentResolver,
                mode
            ),
            handler)
    }
}
