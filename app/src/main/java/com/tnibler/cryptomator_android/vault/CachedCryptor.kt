package com.tnibler.cryptomator_android.vault

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.io.BaseEncoding
import org.cryptomator.cryptolib.api.Cryptor
import java.util.concurrent.TimeUnit

class CachedCryptor(val cryptor: Cryptor, private val encoding: BaseEncoding) {
    private val cipherFileNameCache: LoadingCache<NameAndAssocData, String> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(20, TimeUnit.SECONDS)
        .build(object : CacheLoader<NameAndAssocData, String>() {
            override fun load(key: NameAndAssocData): String {
                return cryptor.fileNameCryptor().encryptFilename(encoding, key.name, key.assocData)
            }

        })

    private val clearFileNameCache: LoadingCache<NameAndAssocData, String> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(20, TimeUnit.SECONDS)
        .build(object : CacheLoader<NameAndAssocData, String>() {
            override fun load(key: NameAndAssocData): String {
                return cryptor.fileNameCryptor().decryptFilename(encoding, key.name, key.assocData)
            }

        })

    private val dirIdHashCache: LoadingCache<String, String> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(20, TimeUnit.SECONDS)
        .build(object : CacheLoader<String, String>() {
            override fun load(key: String): String {
                return cryptor.fileNameCryptor().hashDirectoryId(key)
            }
        })

    fun encryptFilename(clearTextName: String, associatedData: ByteArray): String =
        cipherFileNameCache[NameAndAssocData(
            clearTextName,
            associatedData
        )]

    fun decryptFilename(cipherTextName: String, assocData: ByteArray): String =
        clearFileNameCache[NameAndAssocData(
            cipherTextName,
            assocData
        )]

    fun hashDirectoryId(clearTextDirId: String): String =
        dirIdHashCache[clearTextDirId]

    data class NameAndAssocData(val name: String, val assocData: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as NameAndAssocData

            if (name != other.name) return false
            if (!assocData.contentEquals(other.assocData)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + assocData.contentHashCode()
            return result
        }
    }
}