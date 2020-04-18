package com.tnibler.cryptomator_android

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.tnibler.cryptomator_android.databinding.MainBinding
import com.tnibler.cryptomator_android.listVaults.ListVaultsController
import io.paperdb.Paper

class MainActivity : AppCompatActivity() {
    val TAG = javaClass.simpleName

    private lateinit var binding: MainBinding
    private lateinit var router: Router
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        router = Conductor.attachRouter(this, binding.changeHandlerFrame, savedInstanceState)
        if (!router.hasRootController()) {
            router.setRoot(RouterTransaction.with(ListVaultsController()))
        }
        Paper.init(this)
//        Paper.book().destroy()

//        binding.run {
//            button.setOnClickListener {
//                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
//                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
//                }
//                startActivityForResult(intent, REQUEST_OPEN)
//            }
//        }
//        Log.d(TAG, "Vaults: ")
////        GlobalScope.launch {
//            Paper.book().read<List<String>>("vaults", listOf()).forEach { uri ->
//                val df = DocumentFile.fromTreeUri(this@MainActivity, Uri.parse(uri)) ?: throw IllegalStateException("DocumentFile is null!")
//                Log.d(TAG, df.name ?: "")
//                Toast.makeText(this@MainActivity, df.name, Toast.LENGTH_SHORT).show()
//            }
////        }
    }

}

