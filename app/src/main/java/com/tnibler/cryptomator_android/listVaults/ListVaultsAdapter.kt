package com.tnibler.cryptomator_android.listVaults

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tnibler.cryptomator_android.R
import kotlinx.android.synthetic.main.list_vault_item.view.*

class ListVaultsAdapter : RecyclerView.Adapter<ListVaultsAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameView: TextView = view.listVaultItemNameView
    }

    var items: List<VaultDisplay> = listOf()
        set(value) { field = value; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.list_vault_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.nameView.text = items[position].displayName
    }
}