package com.tnibler.cryptomator_android.unlockVault

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.tnibler.cryptomator_android.App
import com.tnibler.cryptomator_android.databinding.UnlockVaultBinding
import com.tnibler.cryptomator_android.vault.VaultAccess
import kotlinx.android.synthetic.main.create_vault.*
import org.cryptomator.cryptolib.api.InvalidPassphraseException
import org.cryptomator.cryptolib.api.UnsupportedVaultFormatException

class UnlockVaultActivity : Activity() {
    private var service: UnlockedVaultsService? = null
    private val TAG = javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = UnlockVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindService(Intent(this, UnlockedVaultsService::class.java), serviceConnector, Context.BIND_AUTO_CREATE)
        binding.run {
            unlockVaultButton.setOnClickListener {
                if (service == null) {
                    Log.d(TAG, "Service is null")
                    return@setOnClickListener
                }
                val passwordChars = CharArray(unlockVaultPwdEdit.length())
                unlockVaultPwdEdit.text.getChars(0, passwordChars.size, passwordChars, 0)
                val passwordBytes = ByteArray(passwordChars.size)
                passwordChars.forEachIndexed { index, c -> passwordBytes[index] = c.toByte() }
                passwordChars.fill('A' )
                val vaultId = intent.extras?.getLong(KEY_VAULT_ID) ?: throw IllegalArgumentException("no vault id passed")
                val vault = (applicationContext as App).db.getVault(vaultId)
                val rootDocument = DocumentFile.fromTreeUri(this@UnlockVaultActivity, vault.rootUri) ?: throw RuntimeException("could not open root directory")
                try {
                    val vaultAccess =
                        VaultAccess(
                            rootDocument,
                            vault.masterKeyFileName,
                            passwordBytes,
                            byteArrayOf(),
                            contentResolver,
                            vault.flags
                        )
                    passwordBytes.fill(0)
                    service!!.setVaultAccess(vaultId, vaultAccess)
                    finish()
                }
                catch (e: InvalidPassphraseException) {
                    Toast.makeText(this@UnlockVaultActivity, "Wrong password. Failed unlocking vault.", Toast.LENGTH_LONG).show()
                }
                catch (e: UnsupportedVaultFormatException) {
                    Toast.makeText(this@UnlockVaultActivity, "Unsupported vault format", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val serviceConnector = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service Connected")
            this@UnlockVaultActivity.service = (service as UnlockedVaultsService.MyBinder).service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service Disconnected")
            this@UnlockVaultActivity.service = null
        }
    }

    companion object {
        const val KEY_VAULT_ID = "vaultId"
    }

}
