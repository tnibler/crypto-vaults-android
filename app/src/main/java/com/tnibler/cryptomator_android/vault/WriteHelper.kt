package com.tnibler.cryptomator_android.vault

import android.util.Log
import java.lang.Integer.max
import java.lang.Integer.min
import java.nio.ByteBuffer

/**
 * Helper to replace the necessary bytes from the original input with data to be written
 * @param nextOriginalChunk function to fill a ByteBuffer with the cleartext data of the next chunk to be processed
 * @param offset offset at which the data should be written
 */
class WriteHelper(private val nextOriginalChunk: (Long) -> ByteBuffer,
                  private val skipChunks: (Int) -> Unit,
                  private val numberOfOriginalChunks: Int,
                  private val offset: Long,
                  private val data: ByteArray,
                  private val size: Int,
                  private val chunkSize: Int) {

    private val TAG = javaClass.simpleName
    private val firstChunkWithData: Int = (offset / chunkSize).toInt()
    private val lastChunkWithData: Int = ((offset + size) / chunkSize).toInt()
    private val lastChunk = max(numberOfOriginalChunks - 1, lastChunkWithData)
    private var currentChunk: Int = 0
    private val dataBuffer = ByteBuffer.wrap(data).apply { limit(size) }
    private var readFromData: Int = 0
    private val buffer = ByteBuffer.allocate(chunkSize)
    init {
        Log.d(TAG, "firstChunkWithData=$firstChunkWithData, lastChunkWithData=$lastChunkWithData, lastChunk=$lastChunk")
    }

    fun nextChunk(): Chunk {
        Log.d(TAG, "nextChunk: currentChunk=$currentChunk, readFromData=$readFromData")
        if (currentChunk in (firstChunkWithData + 1) until lastChunkWithData) {
            //whole chunk comes from data
            val toReadFromData = min(dataBuffer.remaining(), chunkSize)
            Log.d(TAG, "whole chunk from data, reading $toReadFromData bytes from data")
            dataBuffer.get(buffer.array(), 0, toReadFromData)
            readFromData += toReadFromData
            return Chunk(buffer, currentChunk).also {
                currentChunk++
            }
        }

        val originalChunk = nextOriginalChunk(currentChunk.toLong())
        val chunk = ByteBuffer.allocate(chunkSize)
        chunk.limit(originalChunk.remaining())
        Log.d(TAG, "reading ${originalChunk.remaining()} bytes from original chunk")
        originalChunk.get(chunk.array(), 0, originalChunk.remaining())
        if (currentChunk == firstChunkWithData) {
            //first chunk where some part comes from data
            val offsetInChunk: Int = (offset % chunkSize).toInt()
            val toReadFromData = min(dataBuffer.limit() - dataBuffer.position(), chunkSize - offsetInChunk)
            Log.d(TAG, "first chunk with data, offsetInChunk=$offsetInChunk, reading $toReadFromData bytes from data")
            dataBuffer.get(chunk.array(), offsetInChunk, toReadFromData)
            if (currentChunk == lastChunkWithData) {
                chunk.limit(max(chunk.limit(), toReadFromData))
            }
            readFromData += toReadFromData
        }
        else if (currentChunk == lastChunkWithData) {
            //last chunk where some part comes form data
            val toReadFromData = dataBuffer.remaining()
            Log.d(TAG, "last chunk with data, reading $toReadFromData bytes from data")
            dataBuffer.get(chunk.array(), 0, toReadFromData)
            chunk.limit(max(chunk.limit(), toReadFromData))
            Log.d(TAG, "setting chunk limit to ${chunk.limit()}")
            readFromData += toReadFromData
        }
        return Chunk(chunk, currentChunk).also {
            currentChunk++
        }
    }

    /**
     * skip up to the first chunk that contains some new bytes from data
     */
    fun skipUntouchedChunks() {
        Log.d(TAG, "Skipping $firstChunkWithData chunks")
        skipChunks(firstChunkWithData)
    }

    fun available(): Boolean = currentChunk <= lastChunk

    data class Chunk(val data: ByteBuffer, val number: Int)
}