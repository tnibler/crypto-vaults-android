package com.tnibler.cryptomator_android.cryptomator

object Constants {
    const val VAULT_VERSION = 7
    const val MASTERKEY_BACKUP_SUFFIX = ".bkup"
    const val DATA_DIR_NAME = "d"
    const val MAX_CIPHERTEXT_NAME_LENGTH = 220 // inclusive. calculations done in https://github.com/cryptomator/cryptofs/issues/60#issuecomment-523238303
    const val MAX_CLEARTEXT_NAME_LENGTH = 146 // inclusive. calculations done in https://github.com/cryptomator/cryptofs/issues/60#issuecomment-523238303
    const val ROOT_DIR_ID = ""
    const val CRYPTOMATOR_FILE_SUFFIX = ".c9r"
    const val DEFLATED_FILE_SUFFIX = ".c9s"
    const val DIR_FILE_NAME = "dir.c9r"
    const val SYMLINK_FILE_NAME = "symlink.c9r"
    const val CONTENTS_FILE_NAME = "contents.c9r"
    const val INFLATED_FILE_NAME = "name.c9s"
    const val MAX_SYMLINK_LENGTH = 32767 // max path length on NTFS and FAT32: 32k-1
    const val MAX_DIR_FILE_LENGTH =
        36 // UUIDv4: hex-encoded 16 byte int + 4 hyphens = 36 ASCII chars
    const val SEPARATOR = "/"
}