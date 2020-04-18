package com.tnibler.cryptomator_android.unlockVault

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.TaskStackBuilder
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.tnibler.cryptomator_android.R
import com.tnibler.cryptomator_android.vault.VaultAccess

//handles unlocking vaults and keeping keys in memory until timeout occurs
//uses SYSTEM_ALERT_WINDOW to ask for passphrase to access vault
class UnlockedVaultsService : Service() {
    private var pendingDeleteIntent: PendingIntent? = null
    private lateinit var notificationManager: NotificationManagerCompat
    private val vaults = mutableMapOf<Long, VaultAccess>()
    private val binder: IBinder = MyBinder()
    private val TAG = javaClass.simpleName

    inner class MyBinder : Binder() {
        val service: UnlockedVaultsService
            get() = this@UnlockedVaultsService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        notificationManager = NotificationManagerCompat.from(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        val deleteIntent = Intent(this, UnlockedVaultsService::class.java).apply {
            action =
                ACTION_CLOSE_ALL
        }
        pendingDeleteIntent = PendingIntent.getService(this, 0, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = Notification.Builder(this,
            VAULT_UNLOCKED_NC_ID
        )
//            .setSmallIcon(R.drawable.notification_ic_keyboard_key_24dp)
            .setContentTitle("Cryptomator service")
//            .setContentText(getString(R.string.keyboard_notification_entry_content_text, entryUsername))
            .setAutoCancel(false)
            .setContentIntent(null)
            .setDeleteIntent(pendingDeleteIntent).build()

        notificationManager.cancel(NOTIFICATION_ID)
        notificationManager.notify(NOTIFICATION_ID, notification)

        return START_NOT_STICKY
    }

    fun getVault(vaultId: Long): VaultAccess? {
        val r = vaults[vaultId]
        if (r != null) {
            return r
        }
        else {
            val intent = Intent(this, UnlockVaultActivity::class.java)
                .putExtra(UnlockVaultActivity.KEY_VAULT_ID, vaultId)
            val not = Notification.Builder(this,
                VAULT_UNLOCK_PROMPT_NC_ID
            )
                .setContentTitle("Unlock Vault")
                .setContentText(vaultId.toString())
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentIntent(TaskStackBuilder.create(this).run {
                    addNextIntentWithParentStack(intent)
                    getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
                }).build()
            notificationManager.notify(3, not)
            return null
        }
    }

    fun setVaultAccess(vaultId: Long, vaultAccess: VaultAccess) {
        vaults[vaultId] = vaultAccess
    }

    override fun onBind(intent: Intent?): IBinder? = binder

    companion object {
        private const val ACTION_CLOSE_ALL = "CloseAll"
        private const val NOTIFICATION_ID = 1
        const val VAULT_UNLOCKED_NC_ID = "VaultsUnlocked"
        const val VAULT_UNLOCK_PROMPT_NC_ID = "UnlockVaultPrompt"
    }
}

class VaultLockedException : RuntimeException()
