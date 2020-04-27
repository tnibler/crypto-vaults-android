package com.tnibler.cryptomator_android.vault

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.tnibler.cryptomator_android.App
import com.tnibler.cryptomator_android.BuildConfig
import com.tnibler.cryptomator_android.data.Vault
import com.tnibler.cryptomator_android.unlockVault.UnlockedVaultsService
import com.tnibler.cryptomator_android.util.alternativeName
import com.tnibler.cryptomator_android.vault.exception.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.ByteBuffer

class VaultDocumentProvider : DocumentsProvider() {
    private val TAG = javaClass.simpleName
    private var service: UnlockedVaultsService? = null
    private lateinit var storageManager: StorageManager
    private lateinit var fileOpener: FileOpener

    private val serviceConnector = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service Connected")
            this@VaultDocumentProvider.service = (service as UnlockedVaultsService.MyBinder).service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service Disconnected")
            this@VaultDocumentProvider.service = null
        }
    }

    override fun onCreate(): Boolean {
        Log.d(TAG, "onCreate")
        val context = requireNotNull(context)
        context.bindService(Intent(this.context, UnlockedVaultsService::class.java), serviceConnector, Context.BIND_AUTO_CREATE)
        storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        fileOpener = FileOpener()
        return true
    }


    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        mode ?: run { throw IllegalArgumentException("mode is null!") }
        Log.d(TAG, "openDocument: documentId=$documentId, mode=$mode")
        val uri = Uri.parse(documentId)
        val vaultId = uri.authority?.toLongOrNull() ?: throw RuntimeException("Invalid vault id: ${uri.authority} from URI $uri")
        val vaultAccess = service!!.getVault(vaultId)
        val context = requireNotNull(context)
        if (vaultAccess == null) {
            signal?.cancel()
            throw IOException("VaultAccess is null in open file call!")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Using proxy file descriptor")
            return fileOpener.openFile(uri, mode, vaultAccess, storageManager, context.contentResolver)
        }
        else {
            Log.d(TAG, "Using legacy file reading")
            val pipe: Array<ParcelFileDescriptor> = ParcelFileDescriptor.createReliablePipe()
            GlobalScope.launch(Dispatchers.IO) {
                val contents = vaultAccess.fileContents(uri.pathSegments)

                //TODO magic number, find a reasonable buffer size
                val byteBuffer = ByteBuffer.allocate(1024)
                val out = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
                val buf = ByteArray(byteBuffer.capacity())
                var read = 0
                while (true) {
                    val size = contents.read(byteBuffer)
                    read += size
                    if (size <= 0) {
                        break
                    }
                    byteBuffer.flip()
                    byteBuffer.get(buf, 0, size)
                    out.write(buf, 0, size)
                    byteBuffer.clear()
                }
                out.close()
            }
            return pipe[0]
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        Log.d(TAG, "queryChildDocuments: $parentDocumentId")
        val parentIdUri = Uri.parse(parentDocumentId)
        val vaultId = parentIdUri.authority?.toLongOrNull() ?: throw RuntimeException("authority is null!")

        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val vaultAccess = service?.getVault(vaultId) ?: run {
            Log.w(TAG, "VaultAccess is null in queryChildDocuments!")
            return result
        }
        val path: List<String> = parentIdUri.path?.split("/") ?: run {
            Log.w(TAG, "Invalid path in URI: $parentIdUri")
            return result
        }
        val children: List<VaultAccess.Companion.File> = vaultAccess.listFilesInDirectory(path)
        children.forEach { file ->
            result.newRow().apply {
                add(Document.COLUMN_DISPLAY_NAME, file.name)
                val mime = when (file.type) {
                    VaultAccess.Companion.FileType.DIRECTORY -> Document.MIME_TYPE_DIR
                    VaultAccess.Companion.FileType.FILE -> {
                        getMimeType(file)
                    }
                    VaultAccess.Companion.FileType.SYMLINK -> TODO()
                }
                add(Document.COLUMN_MIME_TYPE, mime)
                //TODO only if not readonly
                val flags =  Document.FLAG_SUPPORTS_REMOVE or Document.FLAG_SUPPORTS_COPY or Document.FLAG_SUPPORTS_MOVE
                if (mime == Document.MIME_TYPE_DIR) {
                    add(Document.COLUMN_FLAGS, Document.FLAG_DIR_SUPPORTS_CREATE or flags)
                }
                else {
                    add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_WRITE or flags)
                }
                val documentId = Uri.Builder()
                    .scheme(BuildConfig.SCHEME)
                    .authority(parentIdUri.authority)
                    .path(parentIdUri.path + "/" + file.name)
                    .build()
                Log.d(TAG, "child document id: $documentId")
                add(Document.COLUMN_DOCUMENT_ID, documentId)
            }
        }
        return result
    }

    private fun getMimeType(file: VaultAccess.Companion.File): String? {
        if (file.cipherFile.isDirectory) {
            return Document.MIME_TYPE_DIR
        }
        val ext = file.name.split(".").lastOrNull()
        return if (file.name.contains(".") && ext != null) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }
        else {
            "application/binary"
        }
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        val documentId = documentId ?: throw IllegalArgumentException("DocumentId is null!")
        Log.d(TAG, "queryDocument: $documentId")
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        // root document
        val uri = Uri.parse(documentId)
        if (uri.path == "" || uri.path == "/") {
            result.newRow().apply {
                add(Document.COLUMN_DISPLAY_NAME, "Root")
                add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
                add(Document.COLUMN_FLAGS, Document.FLAG_DIR_SUPPORTS_CREATE)
                add(Document.COLUMN_DOCUMENT_ID, documentId)
            }
        }
        else {
            val vaultId = uri.authority?.toLongOrNull() ?: throw RuntimeException("Invalid Vault id!")
            val vaultAccess = service!!.getVault(vaultId) ?: throw SecurityException()
            try {
                val file = vaultAccess.getFileOrDirectory(uri.pathSegments)
                Log.d(TAG, "query file ${file.cipherFile.name}")
                val flags =  Document.FLAG_SUPPORTS_REMOVE or Document.FLAG_SUPPORTS_COPY or Document.FLAG_SUPPORTS_MOVE
                result.newRow().apply {
                    add(Document.COLUMN_DISPLAY_NAME, file.name)
                    add(Document.COLUMN_MIME_TYPE, getMimeType(file))
                    add(
                        Document.COLUMN_FLAGS,
                        if (file.type == VaultAccess.Companion.FileType.DIRECTORY) Document.FLAG_DIR_SUPPORTS_CREATE or flags else Document.FLAG_SUPPORTS_WRITE or flags
                    )
                    add(Document.COLUMN_DOCUMENT_ID, documentId)
                }
            }
            catch (e: FileNotFoundException) {
                return result
            }
        }
        return result
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        Log.d(TAG, "query roots")
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        //FIXME null cast
        val vaults = (context?.applicationContext as App).db.allVaults
        Log.d(TAG, "available vaults: $vaults }}")

        vaults.forEach { vault -> result.newRow().apply {
            val rootDir = DocumentFile.fromTreeUri(requireNotNull(context), vault.rootUri)
            if (rootDir == null) {
                Log.d(TAG, "Vault ${vault.id} deleted")
                return@forEach
            }
            Log.d(TAG, "root: $rootDir")
            add(Root.COLUMN_ROOT_ID, buildRootId(vault))
            add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE)
            add(Root.COLUMN_TITLE, rootDir.name ?: "#Unnamed vault")
            val id = Uri.Builder()
                .scheme(BuildConfig.SCHEME)
                .authority(vault.id.toString())
                .path("")
                .build()
            add(Root.COLUMN_DOCUMENT_ID, id.toString())
            Log.d(TAG, "rootId: $id")
        } }
        return result
    }

    override fun createDocument(
        parentDocumentId: String?,
        mimeType: String?,
        displayName: String?
    ): String {
        displayName ?: throw IllegalArgumentException("Display name can't be null!")
        Log.d(TAG, "createDocument: parentDocumentId=$parentDocumentId, mimeType=$mimeType, displayName=$displayName")
        val uri = Uri.parse(parentDocumentId)
        val vaultAccess = getVaultAccess(uri.authority?.toLongOrNull() ?: throw RuntimeException("Failed to parse vault id from document id '$parentDocumentId'"))

        val actualDisplayName = vaultAccess.createFileOrDirectory(uri.pathSegments, displayName, mimeType ?: "*/*", ::alternativeName)
        val id = Uri.Builder()
            .scheme(BuildConfig.SCHEME)
            .authority(uri.authority)
            .path(uri.path + "/" + actualDisplayName)
            .build()
        Log.d(TAG, "created directory with id $id")
        return id.toString()
    }

    override fun copyDocument(sourceDocumentId: String?, targetParentDocumentId: String?): String {
        Log.d(TAG, "copyDocument: source=$sourceDocumentId, target=$targetParentDocumentId")
        val uri = Uri.parse(targetParentDocumentId)
        val vaultAccess = getVaultAccess(uri.authority?.toLongOrNull() ?: throw RuntimeException("Failed to parse vault id from document id '$targetParentDocumentId'"))
        val targetPath = Uri.parse(targetParentDocumentId).pathSegments
        val sourcePath = Uri.parse(sourceDocumentId).pathSegments
        throw UnsupportedOperationException()
    }

    override fun moveDocument(
        sourceDocumentId: String?,
        sourceParentDocumentId: String?,
        targetParentDocumentId: String?
    ): String {
        return super.moveDocument(sourceDocumentId, sourceParentDocumentId, targetParentDocumentId)
    }

    override fun deleteDocument(documentId: String?) {
        val uri = Uri.parse(documentId)
        val vaultAccess = getVaultAccess(uri.authority?.toLongOrNull() ?: throw IllegalArgumentException())
        vaultAccess.deleteDocument(uri.pathSegments)
    }

    override fun removeDocument(documentId: String?, parentDocumentId: String?) {
        deleteDocument(documentId)
    }

    override fun isChildDocument(parentDocumentId: String?, documentId: String?): Boolean {
        val path = Uri.parse(documentId).pathSegments
        val parentPath = Uri.parse(parentDocumentId).pathSegments
        return parentPath.size <= path.size && parentPath.zip(path) { a, b -> a == b}.fold(true) { a, b -> a && b }
    }

    override fun renameDocument(documentId: String?, displayName: String?): String {
        displayName ?: throw IllegalArgumentException("New name can't be null")
        val uri = Uri.parse(documentId)
        val vaultAccess = getVaultAccess(uri.authority?.toLong() ?: throw IllegalArgumentException())
        vaultAccess.renameDocument(uri.pathSegments, displayName)
        val id = Uri.Builder()
            .scheme(BuildConfig.SCHEME)
            .authority(uri.authority)
            .apply {
                uri.pathSegments.dropLast(1).forEach { appendPath(it) }
            }
            .appendPath(displayName)
            .build()
        return id.toString()
    }

    private fun getVaultAccess(vaultId: Long): VaultAccess {
        return service!!.getVault(vaultId) ?: throw SecurityException("Unlock vault to proceed")
    }

    private fun buildRootId(vault: Vault): String {
        return "root#${vault.id}"
    }

    companion object {
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS
//            Document.COLUMN_SIZE
        )

    }
}