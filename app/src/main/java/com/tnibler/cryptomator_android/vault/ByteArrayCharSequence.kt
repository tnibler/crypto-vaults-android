package com.tnibler.cryptomator_android.vault

class ByteArrayCharSequence(val byteArray: ByteArray) : CharSequence {
    override val length: Int
        get() = byteArray.size

    override fun get(index: Int): Char = byteArray[index].toChar()

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        throw UnsupportedOperationException("This should not be called since we do not want copies of the passphrase to be made/")
    }

}