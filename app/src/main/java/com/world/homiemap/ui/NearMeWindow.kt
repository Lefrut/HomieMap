package com.world.homiemap.ui

import com.world.homiemap.R
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.infowindow.InfoWindow

class NearMeWindow(private val mapView: MapView) : InfoWindow(R.layout.icon_and_text, mapView) {
    override fun onOpen(item: Any?) {
        
    }

    override fun onClose() {

    }
}