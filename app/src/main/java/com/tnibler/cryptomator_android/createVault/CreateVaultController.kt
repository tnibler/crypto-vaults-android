package com.tnibler.cryptomator_android.createVault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.bluelinelabs.conductor.Controller
import com.tnibler.cryptomator_android.App
import com.tnibler.cryptomator_android.data.Vault
import com.tnibler.cryptomator_android.data.VaultFlags
import com.tnibler.cryptomator_android.databinding.CreateVaultBinding
import com.tnibler.cryptomator_android.vault.ByteArrayCharSequence
import com.tnibler.cryptomator_android.vault.VaultAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class CreateVaultController(bundle: Bundle) : Controller(bundle) {
    private val TAG = javaClass.simpleName
    private val uri: Uri = Uri.parse(checkNotNull(bundle.getString(KEY_VAULT_URI)))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        val binding = CreateVaultBinding.inflate(inflater, container, false)
        binding.run {
            binding.createVaultKeyfileNameEdit.setText("masterkey.cryptomator", TextView.BufferType.EDITABLE)
            createVaultConfirmButton.setOnClickListener {
                val root = binding.root
                val passwordChars = CharArray(createVaultPasswordEdit.length())
                createVaultPasswordEdit.text.getChars(0, passwordChars.size, passwordChars, 0)

                val passwordConfirm = CharArray(createVaultPasswordConfirmEdit.length())
                createVaultPasswordEdit.text.getChars(0, passwordConfirm.size, passwordChars, 0)
                if (passwordChars.contentEquals(passwordConfirm)) {
                    Toast.makeText(root.context, "Passwords don't match", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val keyFileName = createVaultKeyfileNameEdit.text.toString()
                if (keyFileName.isBlank()) {
                    Toast.makeText(root.context, "Invalid keyfile name", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val dir = DocumentFile.fromTreeUri(root.context, uri) ?: throw RuntimeException("Could not create DocumentFile")
                // TODO check if dir already contains a vault
                val all = (root.context.applicationContext as App).db.allVaults
                if (all.map { it.rootUri }.contains(uri)) {
                    Toast.makeText(root.context, "Vault already exists", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val db = (root.context.applicationContext as App).db
                GlobalScope.launch {
                    Log.d(TAG, "Creating vault")
                    root.context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    val flags = VaultFlags()
                    val passwordBytes = ByteArray(passwordChars.size)
                    passwordChars.forEachIndexed { index, c -> passwordBytes[index] = c.toByte() }
                    Arrays.fill(passwordChars, 'A')
                    Arrays.fill(passwordConfirm, 'A')
                    VaultAccess.createVault(root.context.contentResolver, dir, keyFileName, byteArrayOf(), ByteArrayCharSequence(passwordBytes), flags)
                    Arrays.fill(passwordBytes, 0)
                    db.putVault(
                        Vault(
                            0,
                            uri,
                            keyFileName,
                            flags
                        )
                    )
                    Log.d(TAG, "Created vault")
                    withContext(Dispatchers.Main) {
                        router.popCurrentController()
                    }
                }

            }
        }
        return binding.root
    }

    companion object {
        const val KEY_VAULT_URI = "KEY_VAULT_URI"
    }
}