package com.tnibler.cryptomator_android.vault

import android.content.ContentResolver
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.google.common.io.BaseEncoding
import com.tnibler.cryptomator_android.cryptomator.Constants
import com.tnibler.cryptomator_android.data.VaultFlags
import com.tnibler.cryptomator_android.util.*
import com.tnibler.cryptomator_android.vault.exception.ErrorCreatingFile
import com.tnibler.cryptomator_android.vault.exception.ErrorWritingToFile
import com.tnibler.cryptomator_android.vault.exception.KeyFileNotFoundException
import org.cryptomator.cryptolib.Cryptors
import org.cryptomator.cryptolib.DecryptingReadableByteChannel
import org.cryptomator.cryptolib.api.Cryptor
import org.cryptomator.cryptolib.api.InvalidPassphraseException
import org.cryptomator.cryptolib.api.KeyFile
import org.cryptomator.cryptolib.api.UnsupportedVaultFormatException
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.charset.Charset
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.text.Normalizer

class VaultAccess(private val rootDocumentFile: DocumentFile,
                  private val masterKeyFilename: String,
                  passphrase: String,
                  private val pepper: ByteArray,
                  private val contentResolver: ContentResolver,
                  val flags: VaultFlags) {
    val cryptor = getCryptor(contentResolver, rootDocumentFile, passphrase, pepper, masterKeyFilename)
    private val cachedCryptor =
        CachedCryptor(
            cryptor,
            BaseEncoding.base64Url()
        )

    @Throws(UnsupportedVaultFormatException::class, InvalidPassphraseException::class)
    private fun getCryptor(
        contentResolver: ContentResolver,
        documentTree: DocumentFile,
        passphrase: String,
        pepper: ByteArray,
        masterKeyFilename: String
    ): Cryptor {
        Log.d(TAG, "Initializing cryptor for vault ${rootDocumentFile.uri}")
        documentTree.checkIsDirectory()
        documentTree.checkReadSupport()
        val keyFileDocument = documentTree.findFile(masterKeyFilename)?.apply { checkIsFile() }
            ?: throw KeyFileNotFoundException()
        val contents = contentResolver.openInputStream(keyFileDocument.uri)?.readBytes()
            ?: throw RuntimeException("Can't read from keyfile '${masterKeyFilename}'")
        val keyFile = KeyFile.parse(contents)
        return cryptorProvider.createFromKeyFile(
            keyFile,
            passphrase,
            pepper,
            Constants.VAULT_VERSION
        )
    }

    /**
     * @return all files in the directory specified by the given path
     */
    fun listFilesInDirectory(path: List<String>): List<File> {
        Log.d(TAG, "listFiles: $path")
        val cipherDirectory = getCipherDirectory(
            path,
            parentDir = null,
            parentId = Constants.ROOT_DIR_ID.toByteArray(Charset.forName("UTF-8"))
        )
        return decryptFilesInDir(cipherDirectory.directory, cipherDirectory.id)
    }

    /**
     * @return the file or directory specified by the given path
     */
    fun getFileOrDirectory(path: List<String>): File {
        Log.d(TAG, "getFileOrDirectory: path=$path")
        val cipherParentDirectory = getCipherDirectory(path.dropLast(1), null)
        Log.d(TAG, "Parent dir cipher name ${cipherParentDirectory.directory.name}")
        val cipherName = cachedCryptor.encryptFilename(path.last(), cipherParentDirectory.id)
        val cipherFile = cipherParentDirectory.directory.getFile(cipherName + Constants.CRYPTOMATOR_FILE_SUFFIX)
        return when {
            cipherFile.isDirectory -> {
                val cipherDir = getCipherDirectory(path.takeLast(1), cipherParentDirectory.directory, cipherParentDirectory.id).directory
                File(
                    path.last(),
                    FileType.DIRECTORY,
                    cipherDir
                )
            }
            cipherFile.isFile -> {
                getCipherFile(path)
            }
            else -> {
                throw RuntimeException("neither file nor directory!")
            }
        }
    }

    fun getFileSize(cipherFile: DocumentFile): Long {
        return Cryptors.cleartextSize(cipherFile.length(), cryptor)
    }

    private fun getCipherFile(path: List<String>): File {
        Log.d(TAG, "getCipherFile: $path")
        val cipherParentDirectory = getCipherDirectory(path.dropLast(1))
        val dir = cipherParentDirectory.directory
        val dirId = cipherParentDirectory.id
        Log.d(TAG, "getCipherFile: parentDir ${dir.uri}, parentId ${dirId.toString(Charset.forName("UTF-8"))}")
        val cipherName = cachedCryptor.encryptFilename(path.last(), dirId)
        val cipherFile = dir.getFile(cipherName + Constants.CRYPTOMATOR_FILE_SUFFIX)
        return File(
            path.last(),
            FileType.FILE,
            cipherFile
        )
    }

    private fun getCipherDirectory(
        path: List<String>,
        parentDir: DocumentFile? = null,
        parentId: ByteArray = Constants.ROOT_DIR_ID.toByteArray()
    ): CipherDirectory {
        Log.d(TAG, "getCipherDirectory: path=$path")
        val rootHash = cachedCryptor.hashDirectoryId("")
//        val rootDir = cachedCryptor.childDocument(rootDocumentFile, Constants.DATA_DIR_NAME).run {
//            cachedCryptor.childDocument(this, rootHash.substring(0, 2)).run {
//                cachedCryptor.childDocument(this, rootHash.substring(2))
//            }
//        }
        val rootDir = rootDocumentFile.findDirectory(Constants.DATA_DIR_NAME) //where the files in the vault root are actually stored
            .findDirectory(rootHash.substring(0..1))
            .findDirectory(rootHash.substring(2))
        val path = path.dropWhile { it == "" }
        if (path.isEmpty()) {
            return CipherDirectory(
                parentDir ?: rootDir,
                parentId
            )
        }
        //TODO long filenames
        val cipherPathFirst = cachedCryptor.encryptFilename(path.first(), parentId)
        Log.d(TAG, "ciphered path.first: $cipherPathFirst")
        val dirFile = (parentDir ?: rootDir).findDirectory(cipherPathFirst + Constants.CRYPTOMATOR_FILE_SUFFIX)
            .findFile(Constants.DIR_FILE_NAME) ?: throw RuntimeException("Missing dir file!")
        val dirId = contentResolver.openInputStream(dirFile.uri)?.readBytes() ?: throw RuntimeException("Could not read dir file")
        Log.d(TAG, "read dir ID $dirId")
        val h = cachedCryptor.hashDirectoryId(dirId.toString(Charset.forName("UTF-8")))
        Log.d(TAG, "hashed dir ID $h")
        val d = rootDocumentFile.findDirectory(Constants.DATA_DIR_NAME)
            .findDirectory(h.substring(0..1))
            .findDirectory(h.substring(2))
        return getCipherDirectory(
            path.drop(1),
            d,
            dirId
        )
    }

    private fun decryptFilesInDir(dir: DocumentFile, dirId: ByteArray): List<File> {
        return dir.listFiles().map { child ->
            Log.d(
                TAG, "decrypting cipher filename: ${child.name?.removeSuffix(
                    Constants.CRYPTOMATOR_FILE_SUFFIX
                )}, dirId ${dirId.toString(
                Charset.forName("UTF-8"))}")
            //FIXME what to do if name is null here
            val name = cachedCryptor.decryptFilename(child.name?.removeSuffix(Constants.CRYPTOMATOR_FILE_SUFFIX) ?: throw RuntimeException("Filename is null!"), dirId)
            //TODO Symlinks
            val type = if (child.isDirectory) FileType.DIRECTORY else FileType.FILE
            File(
                name,
                type,
                child
            )
        }
    }

    fun fileContents(path: List<String>): ReadableByteChannel {
        val file = getCipherFile(path)
        val contents = contentResolver.openInputStream(file.cipherFile.uri) ?: throw RuntimeException("Could not open File")
        return DecryptingReadableByteChannel(Channels.newChannel(contents), cryptor, true)
    }

    companion object {
        private val TAG = "VaultAccess"
        private val strongSecureRandom: SecureRandom
            get() {
                try {
                    return SecureRandom.getInstanceStrong()
                } catch (e: NoSuchAlgorithmException) {
                    throw IllegalStateException("Strong random algorithm must exist!")
                }
            }
        private val cryptorProvider = Cryptors.version1(strongSecureRandom)

        /**
         * Creates a new vault in the given directory.
         *
         * @param documentTree Directory where vault will be stored
         * @param masterkeyFilename Name of the masterkey file
         * @param passphrase Passphrase that should be used to unlock the vault
         * @throws NotDirectoryException If the given path is not an existing directory.
        //     * @throws FileSystemCapabilityChecker.MissingCapabilityException If the underlying filesystem lacks features required to store a vault
        //     * @throws IOException If the vault structure could not be initialized due to I/O errors
         */
        fun createVault(
            contentResolver: ContentResolver,
            documentTree: DocumentFile,
            masterKeyFilename: String,
            pepper: ByteArray = byteArrayOf(),
            passphrase: CharSequence,
            flags: VaultFlags
        ): VaultAccess {
            documentTree.listFiles().forEach { it.delete() }
            documentTree.checkIsDirectory()
            documentTree.checkAllCapabilities()
            cryptorProvider.createNew().use { cryptor ->
                val keyFileContents = cryptor.writeKeysToMasterkeyFile(
                    Normalizer.normalize(passphrase, Normalizer.Form.NFC), pepper,
                    Constants.VAULT_VERSION
                ).serialize()
                documentTree.checkNotExists(masterKeyFilename)
                val keyFile = documentTree.createFile(MIME_TYPE, masterKeyFilename)
                    ?: throw ErrorCreatingFile()
                val s = contentResolver.openOutputStream(keyFile.uri, "w")
                    ?: throw ErrorWritingToFile()
                s.write(keyFileContents)

                val rootDirHash = cryptor.fileNameCryptor().hashDirectoryId(Constants.ROOT_DIR_ID)
                documentTree.createNestedDirectories(
                    Constants.DATA_DIR_NAME,
                    rootDirHash.substring(0..1),
                    rootDirHash.substring(2)
                )
            }
            assert(
                containsVault(
                    documentTree,
                    masterKeyFilename
                )
            )
            return VaultAccess(
                rootDocumentFile = documentTree,
                masterKeyFilename = masterKeyFilename,
                passphrase = passphrase.toString(),
                pepper = pepper,
                contentResolver = contentResolver,
                flags = flags
            )
        }

        fun containsVault(documentTree: DocumentFile, masterKeyFilename: String): Boolean {
            val mk = documentTree.findFile(masterKeyFilename)?.canRead() ?: false
            val dataDir = documentTree.findFile(Constants.DATA_DIR_NAME)?.isDirectory ?: false
            return mk && dataDir
        }

        enum class FileType {
            DIRECTORY,
            FILE,
            SYMLINK
        }

        data class File(val name: String, val type: FileType, val cipherFile: DocumentFile)
        data class CipherDirectory(val directory: DocumentFile, val id: ByteArray) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as CipherDirectory

                if (directory != other.directory) return false
                if (!id.contentEquals(other.id)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = directory.hashCode()
                result = 31 * result + id.contentHashCode()
                return result
            }
        }
    }
}

