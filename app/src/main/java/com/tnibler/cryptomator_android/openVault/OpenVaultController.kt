package com.tnibler.cryptomator_android.openVault

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.bluelinelabs.conductor.Controller
import com.tnibler.cryptomator_android.App
import com.tnibler.cryptomator_android.createVault.CreateVaultController
import com.tnibler.cryptomator_android.databinding.OpenVaultBinding

class OpenVaultController(args: Bundle) : Controller(args) {
    lateinit var binding: OpenVaultBinding
    lateinit var model: OpenVaultModel
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        binding = OpenVaultBinding.inflate(inflater, container, false)
        binding.run {
            model = OpenVaultModel(args.getString(CreateVaultController.KEY_VAULT_URI) ?: throw IllegalArgumentException("Missing vault uri argument!"), root.context,
                (root.context.applicationContext as App).db)
            openVaultOpenButton.setOnClickListener {
                val pwd = openVaultPasswordEdit.text.toString()
                if (model.open(pwd, root.context.contentResolver)) {
                    router.popCurrentController()
                }
                else {
                    Toast.makeText(root.context, "Failed to open vault", Toast.LENGTH_LONG).show()
                }
            }
        }
        return binding.root
    }
}
