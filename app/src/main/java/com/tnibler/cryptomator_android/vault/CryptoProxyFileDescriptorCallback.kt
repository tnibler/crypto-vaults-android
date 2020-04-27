package com.tnibler.cryptomator_android.vault

import android.content.ContentResolver
import android.net.Uri
import android.os.ProxyFileDescriptorCallback
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import kotlin.math.ceil
import kotlin.math.min

class CryptoProxyFileDescriptorCallback(
    val uri: Uri,
    val vaultAccess: VaultAccess,
    val contentResolver: ContentResolver,
    val mode: String
) : ProxyFileDescriptorCallback() {

    private val TAG = javaClass.simpleName
    private var cipherFile = vaultAccess.getFileOrDirectory(uri.pathSegments).run {
        if (type != VaultAccess.Companion.FileType.FILE) {
            throw IllegalArgumentException("File is not file!")
        }
        cipherFile
    }
    private val cryptor = vaultAccess.cryptor

    override fun onGetSize(): Long {
        return vaultAccess.getFileSize(cipherFile)
    }

    override fun onRead(offset: Long, size: Int, data: ByteArray?): Int {
        Log.d(TAG, "onRead: offset=$offset, size=$size, onGetSize=${onGetSize()}")
        Log.d(TAG, "cipher file size: ${cipherFile.length()}")
        if (data == null) {
            return -1
        }
        if (offset > onGetSize()) {
            return 0
        }
        val cleartextChunkSize = cryptor.fileContentCryptor().cleartextChunkSize()
        val ciphertextChunkSize = cryptor.fileContentCryptor().ciphertextChunkSize()
        val firstChunkNo = (offset / cleartextChunkSize)
        val offsetInFirstChunk = offset % cleartextChunkSize
        val lastChunkNo = ((offset + size) / cleartextChunkSize)

        val cipherText = contentResolver.openInputStream(cipherFile.uri) ?: throw RuntimeException("Failed opening cipher file")

        val headerSize = cryptor.fileHeaderCryptor().headerSize()
        val cipherHeader = ByteArray(headerSize)
        if (cipherText.read(cipherHeader, 0, headerSize) != headerSize) {
            throw RuntimeException("Failed reading entire header!")
        }
        Log.d(TAG, "cipherHeader: ${cipherHeader.toString(Charset.defaultCharset())}")

        val header = cryptor.fileHeaderCryptor().decryptHeader(ByteBuffer.wrap(cipherHeader))

        // read encrypted chunk from inputstream into this and decrypt from here
        val cipherChunkBuffer = ByteArray(ciphertextChunkSize)

        Log.d(TAG, "cleartext chunk size: $cleartextChunkSize")

        Log.d(TAG, "Skipping ${firstChunkNo * ciphertextChunkSize} bytes of ciphertext")
        cipherText.skip(firstChunkNo * ciphertextChunkSize)
        var totalWritten = 0L
        for (chunkNumber in firstChunkNo..lastChunkNo) {
            Log.d(TAG, "Decrypting chunk number $chunkNumber, written=$totalWritten")
            val cipherTextRead = cipherText.read(cipherChunkBuffer, 0, ciphertextChunkSize)
            if (cipherTextRead < 0) {
                Log.e(TAG, "Failed reading from cipher file: got return value ${cipherTextRead}")
            }
            // probably should not call wrap every time but reuse buffer
            val decryptedChunk = cryptor.fileContentCryptor().decryptChunk(ByteBuffer.wrap(cipherChunkBuffer).apply {
                position(0)
                limit(cipherTextRead)
            }, chunkNumber, header, true)
            val clearTextSize = decryptedChunk.limit() - decryptedChunk.position()
            Log.d(TAG, "decrypted $clearTextSize cleartext bytes from chunk $chunkNumber")

            if (chunkNumber == firstChunkNo) {
                val toRead = min(clearTextSize - offsetInFirstChunk, size - totalWritten)
                Log.d(TAG, "toRead=$toRead")
                decryptedChunk.position(offsetInFirstChunk.toInt())
                decryptedChunk.get(data, 0, toRead.toInt())
                Log.d(TAG, "reading $toRead bytes from chunk $chunkNumber")
                totalWritten += toRead
            }
            else {
                val toRead = min(clearTextSize, size - totalWritten.toInt())
                Log.d(TAG, "toRead=$toRead")
                Log.d(TAG, "reading $toRead bytes from chunk $chunkNumber")
                decryptedChunk.get(data, totalWritten.toInt(), toRead)
                totalWritten += toRead
            }
        }
        Log.d(TAG, "requested $size bytes, wrote $totalWritten")
        return totalWritten.toInt()
    }

    private fun shouldGenerateNewHeader(): Boolean {
        return false
    }

    override fun onWrite(offset: Long, size: Int, data: ByteArray?): Int {
        data ?: return -1
        Log.d(TAG, "onWrite: offset=$offset, size=$size")
        Log.d(TAG, "cipher file size: ${cipherFile.length()}")
        val cleartextChunkSize = cryptor.fileContentCryptor().cleartextChunkSize()
        val ciphertextChunkSize = cryptor.fileContentCryptor().ciphertextChunkSize()
        val headerSize = cryptor.fileHeaderCryptor().headerSize()
        when {
            mode == "w" -> {
                val newHeader = cryptor.fileHeaderCryptor().create()
                val fileOut = contentResolver.openOutputStream(cipherFile.uri, "w") ?: throw RuntimeException()
                val cipherHeader = cryptor.fileHeaderCryptor().encryptHeader(newHeader)
                fileOut.write(cipherHeader.array())
                val dataBuffer = ByteBuffer.wrap(data).limit(min(size, cleartextChunkSize)) as ByteBuffer
                val numberOfChunks = ceil(1.0 * size / cleartextChunkSize).toLong()
                for (chunkNumber in 0 until numberOfChunks) {
                    val encrypted = cryptor.fileContentCryptor().encryptChunk(dataBuffer, chunkNumber, newHeader)
                    fileOut.write(encrypted.array())
                    dataBuffer.position(dataBuffer.limit())
                    dataBuffer.limit(min(dataBuffer.limit() + cleartextChunkSize, size))
                }
                return size
            }
            shouldGenerateNewHeader() -> {
                val newHeader = cryptor.fileHeaderCryptor().create()
                val newFile = cipherFile.parentFile?.createFile("application/binary", cipherFile.name + ".tmp") ?: throw RuntimeException("Failed creating temporary file!")
                val encryptedHeader = cryptor.fileHeaderCryptor().encryptHeader(newHeader)
                val newFileOutputStream = contentResolver.openOutputStream(newFile.uri) as FileOutputStream
                newFileOutputStream.write(encryptedHeader.array())
                val fileIn = contentResolver.openInputStream(cipherFile.uri) as? FileInputStream ?: throw RuntimeException("Failed opening cipher file")
                val cipherHeader = ByteArray(headerSize)
                if (fileIn.read(cipherHeader) != headerSize && mode != "w") {
                    throw RuntimeException("Failed reading entire header!")
                }
                val header = cryptor.fileHeaderCryptor().decryptHeader(ByteBuffer.wrap(cipherHeader))
                val numberOfChunks = ceil(onGetSize().toDouble() / cleartextChunkSize).toInt()

                val buffer  = ByteBuffer.wrap(ByteArray(ciphertextChunkSize))
                val nextOriginalChunk: (Long) -> ByteBuffer = { chunkNumber ->
                    val read = fileIn.read(buffer.array())
                    if (read < 0) {
                        throw RuntimeException("Failed reading from file ${cipherFile.uri}")
                    }
                    Log.d(TAG, "read $read bytes from file, buffer size ${buffer.capacity()}")
                    buffer.position(0)
                    buffer.limit(read)
                    cryptor.fileContentCryptor().decryptChunk(buffer, chunkNumber, header, true).apply {
                        Log.d(TAG, "nextChunk decrypted: position=${position()}, limit=${limit()}")
                    }
                }
                val skipChunks: (Int) -> Unit = { n ->
                    val toSkip = (n * ciphertextChunkSize).toLong()
                    Log.d(TAG, "Skipping $toSkip bytes")
                    if (fileIn.skip(toSkip) != toSkip) {
                        throw RuntimeException("Wrong number of bytes skipped")
                    }
                }

                val writeHelper = WriteHelper(nextOriginalChunk = nextOriginalChunk,
                    skipChunks = skipChunks,
                    numberOfOriginalChunks = numberOfChunks,
                    data = data,
                    chunkSize = cleartextChunkSize,
                    offset = offset,
                    size = size,
                    truncate = mode == "w")

                while (writeHelper.available()) {
                    val chunk = writeHelper.nextChunk()
//                Log.d(TAG, "writing chunk: ${chunk.data.array().toString(Charset.forName("UTF-8"))}")
                    val encrypted = cryptor.fileContentCryptor().encryptChunk(chunk.data, chunk.number.toLong(), newHeader)
                    newFileOutputStream.write(encrypted.array())
                }
                //delete old file
                val n = checkNotNull(cipherFile.name)
                cipherFile.delete()
                newFile.renameTo(n)
                Log.d(TAG, "Size of final file: ${newFile.length()}")
                cipherFile = newFile
            }
            else -> {
                val cipherHeader = ByteArray(headerSize)
                val fileIn = contentResolver.openInputStream(cipherFile.uri) as? FileInputStream ?: throw RuntimeException("Failed opening cipher file")
                if (fileIn.read(cipherHeader) != headerSize && mode != "w") {
                    throw RuntimeException("Failed reading entire header!")
                }
                val header = cryptor.fileHeaderCryptor().decryptHeader(ByteBuffer.wrap(cipherHeader))
                Log.d(TAG, "decrypted header")


                val numberOfChunks = ceil(onGetSize().toDouble() / cleartextChunkSize).toInt()
                val firstChunkWithData = offset % cleartextChunkSize
                val lastChunkWithData = (offset + size) / cleartextChunkSize
                val firstAndLastBuffer = listOf(ByteBuffer.allocate(ciphertextChunkSize)) +
                        if (lastChunkWithData != firstChunkWithData)
                            listOf(ByteBuffer.allocate(ciphertextChunkSize))
                        else listOf()
                val r1 = fileIn.channel.read(firstAndLastBuffer.first(), headerSize.toLong())
                Log.d(TAG, "read $r1 from first chunk with data $firstChunkWithData")
                firstAndLastBuffer.first().limit(r1).position(0)
                if (lastChunkWithData != firstChunkWithData) {
                    val r2 = fileIn.channel.read(firstAndLastBuffer.last(), headerSize + lastChunkWithData * ciphertextChunkSize)
                    Log.d(TAG, "read $r2 from last chunk with data $lastChunkWithData")
                    firstAndLastBuffer.last().limit(r2).position(0)
                }
                fileIn.close()

                val nextOriginalChunk = { chunkNumber: Long ->
                    val b = when (chunkNumber) {
                        firstChunkWithData -> firstAndLastBuffer.first()
                        lastChunkWithData -> firstAndLastBuffer.last()
                        else -> throw IllegalArgumentException()
                    }
                    cryptor.fileContentCryptor().decryptChunk(b, chunkNumber, header, true)
                }
                val skipChunks = { n: Int -> }
                val writeHelper = WriteHelper(nextOriginalChunk = nextOriginalChunk,
                    skipChunks = skipChunks,
                    numberOfOriginalChunks = numberOfChunks,
                    data = data,
                    chunkSize = cleartextChunkSize,
                    offset = offset,
                    size = size,
                    truncate = false)
                val fileOutChannel = (contentResolver.openOutputStream(cipherFile.uri, "rw") as FileOutputStream).channel
                var newCipherSize: Long = headerSize.toLong()
                while (writeHelper.available()) {
                    val chunk = writeHelper.nextChunk()
                    val encrypted = cryptor.fileContentCryptor().encryptChunk(chunk.data, chunk.number.toLong(), header)
                    val dec = cryptor.fileContentCryptor().decryptChunk(encrypted, chunk.number.toLong(), header, true)
                    Log.d(TAG, "data: '${chunk.data.array().toString(Charset.defaultCharset()).take(size)}, ${chunk.data.remaining()} bytes'")
                    Log.d(TAG, "writing chunk #${chunk.number} to position ${headerSize + chunk.number * ciphertextChunkSize}")
                    Log.d(TAG, "encrypted chunk size ${encrypted.remaining()}")
                    newCipherSize += encrypted.remaining()
                    fileOutChannel.write(encrypted,
                        (headerSize + chunk.number * ciphertextChunkSize).toLong()
                    )
                }
                fileOutChannel.truncate(newCipherSize)
                fileOutChannel.close()
            }
        }


        return min(size, (onGetSize() - offset).toInt()).apply { Log.d(TAG, "Returning $this") }
    }

    override fun onFsync() {
        Log.d(TAG, "onFsync")
        super.onFsync()
    }

    override fun onRelease() {
        Log.d(TAG, "release")
    }
}
