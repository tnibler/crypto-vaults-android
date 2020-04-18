package com.tnibler.cryptomator_android.data

import android.content.Context
import android.net.Uri
import io.paperdb.Paper

class Db(context: Context) {
    val TAG = javaClass.simpleName
    init {
        Paper.init(context)
//        Paper.book().destroy()
    }

    fun getVault(id: Long): Vault {
        val dbVault = Paper.book().read<List<DbVault>>(
            KEY_VAULTS, listOf()).find { it.id == id } ?: throw IllegalArgumentException("Vault id does not exist!")
        return dbVault.toModel()
    }

    fun putVault(vault: Vault) {
        val dbVault =
            DbVault.fromModel(
                vault
            )
        val all = Paper.book().read<List<DbVault>>(
            KEY_VAULTS, listOf()).filter { it.id != dbVault.id }
        if (dbVault.id <= 0) {
            val maxId = Paper.book().read<Long>(KEY_MAX_ID, 0)
            Paper.book().write<List<DbVault>>(
                KEY_VAULTS, all + dbVault.copy(id = maxId + 1))
            Paper.book().write(KEY_MAX_ID, maxId + 1)
        }
        else {
            Paper.book().write(KEY_VAULTS, all + dbVault)
        }
    }

    val allVaults: List<Vault>
        get() = Paper.book().read<List<DbVault>>(
            KEY_VAULTS, listOf()).map { it.toModel() }

    fun deleteVault(v: Vault) {
        val all = Paper.book().read<List<DbVault>>(
            KEY_VAULTS, listOf())
        Paper.book().write(KEY_VAULTS, all.filter { it.id != v.id })
    }

    companion object {
        private const val KEY_VAULTS = "vaults"
        private const val KEY_MAX_ID = "maxId"
    }
    data class DbVault(val id: Long, val rootUri: String, val masterKeyFileName: String, val flags: VaultFlags) {
        fun toModel(): Vault =
            Vault(
                id,
                Uri.parse(rootUri),
                masterKeyFileName,
                flags
            )

        companion object {
            fun fromModel(vault: Vault): DbVault =
                DbVault(
                    vault.id,
                    vault.rootUri.toString(),
                    vault.masterKeyFileName,
                    vault.flags
                )
        }
    }
}

