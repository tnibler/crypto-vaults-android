package com.tnibler.cryptomator_android.listVaults

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.archlifecycle.LifecycleController
import com.tnibler.cryptomator_android.App
import com.tnibler.cryptomator_android.createVault.CreateVaultController
import com.tnibler.cryptomator_android.databinding.ListVaultsBinding
import com.tnibler.cryptomator_android.openVault.OpenVaultController

class ListVaultsController : LifecycleController() {
    private val TAG = javaClass.simpleName
    lateinit var binding: ListVaultsBinding
    lateinit var model: ListVaultsModel
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        binding = ListVaultsBinding.inflate(inflater, container, false)
        binding.run {
            model = ListVaultsModel((applicationContext as App).db, binding.root.context)
            listVaultsCreateButton.setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                }
                startActivityForResult(intent, REQUEST_CREATE)
            }
            listVaultsOpenButton.setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                }
                startActivityForResult(intent, REQUEST_OPEN)
            }
            val adapter = ListVaultsAdapter()
            listVaultsRecyclerView.adapter = adapter
            listVaultsRecyclerView.layoutManager = LinearLayoutManager(binding.root.context)
            adapter.items = model.vaults.value ?: listOf()
            model.vaults.observe(this@ListVaultsController, Observer {
                adapter.items = it
            })
        }
        return binding.root
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CREATE -> {
                if (resultCode != Activity.RESULT_OK) {
                    Log.w(TAG, "Could not open directory to create vault.")
                    return
                }
                router.pushController(RouterTransaction.with(CreateVaultController(Bundle().apply {
                    putString(CreateVaultController.KEY_VAULT_URI, data?.data?.toString())
                })))
            }
            REQUEST_OPEN -> {
                if (resultCode != Activity.RESULT_OK) {
                    Log.w(TAG, "Could not open directory to open vault.")
                    return
                }
                router.pushController(RouterTransaction.with(OpenVaultController(Bundle().apply {
                    putString(CreateVaultController.KEY_VAULT_URI, data?.data?.toString())
                })))
            }
            else -> super.onActivityResult(requestCode, resultCode, data)
        }
    }

    companion object {
        const val REQUEST_CREATE = 123
        const val REQUEST_OPEN = 1234
    }
}