package com.bailitop.gallery.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bailitop.gallery.R
import com.bailitop.gallery.ext.display
import com.bailitop.gallery.ext.externalUri
import com.bailitop.gallery.scan.ScanEntity

class PreviewAdapter(val galleryList: ArrayList<ScanEntity>): RecyclerView.Adapter<PreviewAdapter.PreviewHolder>(){


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_preview, parent, false)
        return PreviewHolder(itemView)
    }

    override fun getItemCount(): Int = galleryList.size

    override fun onBindViewHolder(holder: PreviewHolder, position: Int) {
        holder.ivPreview.display(galleryList[position].externalUri())
    }


    class PreviewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val ivPreview: ImageView = itemView.findViewById(R.id.ivPreview)
    }

}