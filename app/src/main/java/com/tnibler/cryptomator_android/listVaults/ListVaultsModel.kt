package com.tnibler.cryptomator_android.listVaults

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.MutableLiveData
import com.tnibler.cryptomator_android.data.Db
import com.tnibler.cryptomator_android.data.Vault
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ListVaultsModel(private val db: Db, private val context: Context) {
    val TAG = javaClass.simpleName
    val vaults: MutableLiveData<List<VaultDisplay>> = MutableLiveData()
    init {
        val vs = db.allVaults
        vaults.value = vs.map { v ->
            val d = DocumentFile.fromTreeUri(context, v.rootUri)
            val name = d?.name ?: "Unnamed vault"// throw IllegalStateException("dir name is null")
            VaultDisplay(v.id, name)
        }
    }

    fun deleteVault(v: Vault) {
        GlobalScope.launch {
            db.deleteVault(v)
        }
    }
}
data class VaultDisplay(val id: Long, val displayName: String)
