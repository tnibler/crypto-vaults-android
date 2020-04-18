package com.tnibler.cryptomator_android.vault.exception

import android.net.Uri

class NotDirectoryException(uri: Uri) : RuntimeException(uri.toString())