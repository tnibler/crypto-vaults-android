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
        val vaultId = uri.authority?.toLong() ?: throw RuntimeException("Invalid vault id: ${uri.authority} from URI $uri")
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
        val vaultId = parentIdUri.authority?.toLong() ?: throw RuntimeException("authority is null!")

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
                if (mime == Document.MIME_TYPE_DIR) {
                    add(Document.COLUMN_FLAGS, Document.FLAG_DIR_SUPPORTS_CREATE)
                }
                else {
                    add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_WRITE)
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

    private fun getVaultForDocument(documentId: String, context: Context): Vault {
        val uri = Uri.parse(documentId)
        val vaultId = uri.authority?.toLong() ?: throw RuntimeException("Invalid Vault id!")
        return (context.applicationContext as App).db.getVault(vaultId)
    }

    private fun getMimeType(file: VaultAccess.Companion.File): String? {
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
            val vaultId = uri.authority?.toLong() ?: throw RuntimeException("Invalid Vault id!")
            val vault = getVaultForDocument(documentId, requireNotNull(context))
            val root = DocumentFile.fromTreeUri(requireNotNull(context), vault.rootUri) ?: throw RuntimeException("No root document!")
            val vaultAccess = service!!.getVault(vaultId) ?: return result
            Log.d(TAG, "uri pathsegments: ${uri.pathSegments}, ${uri.path}")
            val file = vaultAccess.getFileOrDirectory(uri.pathSegments)
            result.newRow().apply {
                add(Document.COLUMN_DISPLAY_NAME, file.name)
                add(Document.COLUMN_MIME_TYPE, getMimeType(file))
                add(Document.COLUMN_FLAGS, if (file.type == VaultAccess.Companion.FileType.DIRECTORY) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE)
                add(Document.COLUMN_DOCUMENT_ID, documentId)
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