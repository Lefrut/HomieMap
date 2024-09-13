package com.world.homiemap.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.location.Location
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import com.world.homiemap.R
import com.world.homiemap.databinding.FragmentMapBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.api.IGeoPoint
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration.getInstance
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay


class MapFragment : Fragment(), MapListener {


    private val viewModel: MapViewModel by viewModels()
    private lateinit var locationPermissionRequest: ActivityResultLauncher<Array<String>>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    private lateinit var map: MapView
    private lateinit var mapController: IMapController

    private val meOverlay by lazy { MeOverlay(resources) }
    private val homieOverlays by lazy {
        homiesList.map {
            HomieOverlay(resources, GeoPoint(it.lat, it.lon))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        getInstance().load(
            requireContext(),
            PreferenceManager.getDefaultSharedPreferences(requireContext())
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bindingMain = FragmentMapBinding.bind(view)
        map = bindingMain.map

        bindingMain.gps.run {
            txt.setText(R.string.gps)
            img.setImageResource(R.drawable.baseline_wifi_24)
        }
        bindingMain.date.run {
            txt.setText(R.string.random_date)
            img.setImageResource(R.drawable.baseline_calendar_today_24)
        }
        bindingMain.time.run {
            txt.setText(R.string.random_time)
            img.setImageResource(R.drawable.baseline_access_time_24)
        }
        bindingMain.zoomOutButton.setOnClickListener { mapController.zoomOut() }
        bindingMain.zoomInButton.setOnClickListener { mapController.zoomIn() }
        bindingMain.nearMeButton.setOnClickListener {
            goCurrentLocation()
        }
        bindingMain.nextFriendButton.setOnClickListener {
            val friend = viewModel.currentFriend.value ?: homiesList.firstOrNull() ?: return@setOnClickListener
            val index =
                homieOverlays.indexOfFirst { it.geoPoint == GeoPoint(friend.lat, friend.lon) }
            val homieOverlay = homieOverlays.getOrNull(index + 1) ?: homieOverlays.firstOrNull()
            ?: return@setOnClickListener
            val newHomie =
                homiesList.firstOrNull { GeoPoint(it.lat, it.lon) == homieOverlay.geoPoint }
                    ?: return@setOnClickListener
            viewModel.setFriend(newHomie)
        }

        with(map) {
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            getLocalVisibleRect(Rect())
            addMapListener(this@MapFragment)
            mapController = controller
        }



        locationPermissionRequest = permissionListener {
            goCurrentLocation()
        }

        checkLocationPermissions()


        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentFriend.collectLatest { friend ->
                    if (friend == null) return@collectLatest
                    mapController.animateTo(GeoPoint(friend.lat, friend.lon))
                    mapController.setZoom(17.0)
                    bindingMain.bottomSheet.visibility = View.VISIBLE
                    delay(4000L)
                    bindingMain.bottomSheet.visibility = View.INVISIBLE
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onScroll(event: ScrollEvent?): Boolean {
        return true
    }

    override fun onZoom(event: ZoomEvent?): Boolean {
        return false;
    }

    private fun goCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        fusedLocationClient.getCurrentLocation(
            LocationRequest.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { location: Location? ->
            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude

                val mapPoint = GeoPoint(lat, lon)
                mapController.setCenter(mapPoint)
                mapController.setZoom(17.0)

                drawObjectsOnMap(mapPoint)
            } else Toast.makeText(
                requireContext(),
                getString(R.string.failed_gps),
                Toast.LENGTH_SHORT
            ).show()
        }.addOnFailureListener {
            Toast.makeText(
                requireContext(),
                getString(R.string.failed_gps),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun permissionListener(onAccessLocation: () -> Unit) = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                onAccessLocation()
            }

            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                onAccessLocation()
            }

            else -> {
                Toast.makeText(requireContext(), getString(R.string.failed_gps), Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun checkLocationPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return false
        }
        goCurrentLocation()
        return true
    }


    private fun drawObjectsOnMap(meGeoPoint: GeoPoint) {
        map.overlays.clear()
        meOverlay.setGeopoint(meGeoPoint)
        map.overlays.add(meOverlay)
        map.overlays.addAll(homieOverlays)
    }
}

class MeOverlay(
    private val resources: Resources
) : Overlay() {

    private var geoPoint: IGeoPoint = GeoPoint(0.0, 0.0)

    fun setGeopoint(newGeoPoint: IGeoPoint) {
        geoPoint = newGeoPoint
    }

    private val strokePaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true

    }

    private val fillPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true

    }

    private val bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_mylocation_55dp)

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        super.draw(canvas, mapView, shadow)
        if (canvas == null || mapView == null) return


        val point = Point()
        mapView.projection.toPixels(geoPoint, point)


        val size = dpToPx(44, resources)


        val centerX = point.x.toFloat()
        val centerY = point.y.toFloat()
        val x = centerX - size * 0.5f
        val y = centerY - size * 0.5f


        val destRect = RectF(x, y, x + size, y + size)
        canvas.drawCircle(centerX, centerY, size / 2, fillPaint)
        canvas.drawBitmap(bitmap, null, destRect, null)
        canvas.drawCircle(centerX, centerY, size / 2, strokePaint)


    }
}

class HomieOverlay(
    private val resources: Resources,
    val geoPoint: GeoPoint
) : Overlay() {

    private val backgroundBitmap =
        BitmapFactory.decodeResource(resources, R.drawable.ic_tracker_75dp)
    private val homieBitmap =
        BitmapFactory.decodeResource(resources, R.drawable.user)

    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        super.draw(canvas, mapView, shadow)
        if (canvas == null || mapView == null) return

        val point = Point()

        mapView.projection.toPixels(geoPoint, point)


        val centerX = point.x.toFloat()
        val centerY = point.y.toFloat()

        val backgroundSize = dpToPx(75, resources)
        val homieSize = backgroundSize * 0.6f
        val sizeDifferenceX2 = (backgroundSize - homieSize) / 2

        val x = centerX - backgroundSize / 2
        val y = centerY - backgroundSize / 2


        val destRect1 =
            RectF(
                x,
                y,
                x + backgroundSize,
                y + backgroundSize
            )
        val destRect2 =
            RectF(
                x + sizeDifferenceX2,
                y + sizeDifferenceX2 / 1.5f,
                x + homieSize + sizeDifferenceX2,
                y + homieSize + sizeDifferenceX2 / 1.5f
            )


        canvas.drawBitmap(backgroundBitmap, null, destRect1, null)
        canvas.drawBitmap(homieBitmap, null, destRect2, null)

    }
}

data class HomieAndLocation(
    val name: String,
    val lat: Double,
    val lon: Double
)

val homiesList = listOf(
    HomieAndLocation(name = "Илья", lat = 55.77195, lon = 38.44176), // New York, USA
    HomieAndLocation(name = "Дима", lat = 48.8566, lon = 2.3522),   // Paris, France
    HomieAndLocation(name = "Саша", lat = 35.6895, lon = 139.6917), // Tokyo, Japan
    HomieAndLocation(name = "Алексей", lat = 51.5074, lon = -0.1278),  // London, UK
    HomieAndLocation(name = "Никита", lat = 34.0522, lon = -118.2437), // Los Angeles, USA
    HomieAndLocation(name = "Артем", lat = 55.7558, lon = 37.6173),  // Moscow, Russia
    HomieAndLocation(name = "Камиль", lat = -33.8688, lon = 151.2093), // Sydney, Australia
    HomieAndLocation(name = "Рустам", lat = -23.5505, lon = -46.6333), // São Paulo, Brazil
    HomieAndLocation(name = "Иван", lat = 52.5200, lon = 13.4050),   // Berlin, Germany
    HomieAndLocation(name = "Вячеслав", lat = 19.4326, lon = -99.1332)  // Mexico City, Mexico
)

fun pxToDp(px: Int, resources: Resources): Float {
    val density = resources.displayMetrics.density
    return px / density
}

fun dpToPx(dp: Int, resources: Resources): Float {
    val density = resources.displayMetrics.density
    return dp * density
}