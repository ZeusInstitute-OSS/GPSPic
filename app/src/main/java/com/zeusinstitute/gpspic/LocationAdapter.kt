package com.zeusinstitute.gpspic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LocationAdapter(private val locationData: MutableList<MainActivity.LocationData>) :
    RecyclerView.Adapter<LocationAdapter.LocationViewHolder>() {

    class LocationViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        val tvLocationHeader: TextView = itemView.findViewById(R.id.tvLocationHeader)
        val tvDateTime: TextView = itemView.findViewById(R.id.tvDateTime)
        val tvCoordinates: TextView = itemView.findViewById(R.id.tvCoordinates)
        val tvFullAddress: TextView = itemView.findViewById(R.id.tvFullAddress)
        val ivMapPreview: ImageView = itemView.findViewById(R.id.ivMapPreview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.location_details, parent, false) // Use your item_location layout
        return LocationViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        val currentItem = locationData[position]
        holder.tvLocationHeader.text = currentItem.locationHeader
        holder.tvDateTime.text = currentItem.dateTime
        holder.tvCoordinates.text = currentItem.coordinates
        holder.tvFullAddress.text = currentItem.fullAddress
        // For map preview, you'd typically use a mapping library here
        holder.ivMapPreview.setImageResource(currentItem.mapPreview)
    }

    override fun getItemCount(): Int = locationData.size
}