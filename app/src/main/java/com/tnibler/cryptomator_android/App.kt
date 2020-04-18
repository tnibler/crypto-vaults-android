package com.tnibler.cryptomator_android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import com.tnibler.cryptomator_android.data.Db
import com.tnibler.cryptomator_android.unlockVault.UnlockedVaultsService

class App : Application() {
    lateinit var db: Db

    override fun onCreate() {
        super.onCreate()
        db = Db(this)

        NotificationManagerCompat.from(this).createNotificationChannels(listOf(
            NotificationChannel(UnlockedVaultsService.VAULT_UNLOCKED_NC_ID, "vaults unlocked notification", NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(UnlockedVaultsService.VAULT_UNLOCK_PROMPT_NC_ID, "unlock vault prompt", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
            }
        ))
    }
}