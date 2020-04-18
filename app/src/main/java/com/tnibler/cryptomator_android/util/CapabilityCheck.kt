package com.tnibler.cryptomator_android.util

import androidx.documentfile.provider.DocumentFile


/**
 * Creates a new vault at the given directory path.
 *
 * @param pathToVault Path to a not yet existing directory
 * @param masterkeyFilename Name of the masterkey file
 * @param passphrase Passphrase that should be used to unlock the vault
 * @throws NotDirectoryException If the given path is not an existing directory.
 * @throws FileSystemCapabilityChecker.MissingCapabilityException If the underlying filesystem lacks features required to store a vault
 * @throws IOException If the vault structure could not be initialized due to I/O errors
 * @since 1.3.0
 */
fun DocumentFile.checkAllCapabilities() {
    checkReadSupport()
    checkWriteSupport()
    checkLongFileNameSupport()
    checkLongFilePathSupport()
}

fun DocumentFile.checkReadSupport() {
    if (!canRead()) {
        throw MissingCapabilityException(
            Capability.READ
        )
    }
}

fun DocumentFile.checkWriteSupport() {
    if (!canWrite()) {
        throw MissingCapabilityException(
            Capability.WRITE
        )
    }
}

fun DocumentFile.checkLongFileNameSupport() {
    val longName = "A".repeat(96) + ".c9r"
    val checkDir = findOrCreateDirectory("c")
    try {
        checkDir.createFile(MIME_TYPE, longName)
    }
    catch (e: Exception) {
        throw MissingCapabilityException(
            Capability.LONG_FILENAMES
        )
    }
    finally {
        checkDir.delete()
    }
}

fun DocumentFile.checkLongFilePathSupport() {
    val longName = "A".repeat(96) + ".c9r"
    val checkDir = findOrCreateDirectory("c")
    //4x
    try {
        var d = checkDir.findOrCreateDirectory(longName)
        for (i in 1..3) {
            d = d.findOrCreateDirectory(longName)
        }
    }
    catch (e: Exception) {
        throw MissingCapabilityException(
            Capability.LONG_FILE_PATHS
        )
    }
    finally {
        checkDir.delete()
    }
}

enum class Capability {
    READ,
    WRITE,
    LONG_FILENAMES,
    LONG_FILE_PATHS
}

class MissingCapabilityException(capability: Capability) : Exception()
