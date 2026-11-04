package com.richstr.pubtracker.ui.map

import android.graphics.RectF
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.richstr.pubtracker.domain.Geo
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon

// Free OSM-based vector tiles, no API key required.
const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

private const val PINS_SOURCE = "pins-source"
private const val PINS_LAYER = "pins-layer"
private const val LABELS_LAYER = "labels-layer"
private const val CIRCLE_SOURCE = "circle-source"
private const val CIRCLE_FILL = "circle-fill"
private const val CIRCLE_LINE = "circle-line"
private const val CIRCLE_COLOR = 0xFF8D4F00.toInt()

data class MapPin(val id: String, val name: String, val lat: Double, val lon: Double, val colorHex: String)

data class MapCircle(val lat: Double, val lon: Double, val radiusMetres: Double)

data class CameraRequest(val lat: Double, val lon: Double, val zoom: Double, val animate: Boolean = true)

@Composable
fun PubMap(
    initialCamera: CameraRequest,
    modifier: Modifier = Modifier,
    pins: List<MapPin> = emptyList(),
    circle: MapCircle? = null,
    cameraRequest: CameraRequest? = null,
    onPinClick: (String) -> Unit = {},
    onMapClick: () -> Unit = {},
    onCameraMove: ((Double, Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val touchTolerancePx = with(LocalDensity.current) { 16.dp.toPx() }
    val mapView = remember {
        MapLibre.getInstance(context)
        // Texture mode composes correctly with overlays and navigation transitions.
        MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))
            .also { it.onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val currentOnPinClick by rememberUpdatedState(onPinClick)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentOnCameraMove by rememberUpdatedState(onCameraMove)

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            val state = lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(initialCamera.lat, initialCamera.lon))
                .zoom(initialCamera.zoom)
                .build()
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { loaded ->
                addOverlayLayers(loaded)
                style = loaded
            }
            m.addOnMapClickListener { latLng ->
                val p = m.projection.toScreenLocation(latLng)
                val box = RectF(p.x - touchTolerancePx, p.y - touchTolerancePx, p.x + touchTolerancePx, p.y + touchTolerancePx)
                val hit = m.queryRenderedFeatures(box, PINS_LAYER).firstOrNull()?.getStringProperty("id")
                if (hit != null) currentOnPinClick(hit) else currentOnMapClick()
                true
            }
            m.addOnCameraMoveListener {
                m.cameraPosition.target?.let { currentOnCameraMove?.invoke(it.latitude, it.longitude) }
            }
            map = m
        }
    }

    LaunchedEffect(style, pins) {
        val source = style?.getSourceAs<GeoJsonSource>(PINS_SOURCE) ?: return@LaunchedEffect
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                pins.map { pin ->
                    Feature.fromGeometry(Point.fromLngLat(pin.lon, pin.lat)).apply {
                        addStringProperty("id", pin.id)
                        addStringProperty("name", pin.name)
                        addStringProperty("color", pin.colorHex)
                    }
                }
            )
        )
    }

    LaunchedEffect(style, circle) {
        val source = style?.getSourceAs<GeoJsonSource>(CIRCLE_SOURCE) ?: return@LaunchedEffect
        if (circle == null) {
            source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        } else {
            val ring = Geo.circle(circle.lat, circle.lon, circle.radiusMetres)
                .map { (lat, lon) -> Point.fromLngLat(lon, lat) }
            source.setGeoJson(Feature.fromGeometry(Polygon.fromLngLats(listOf(ring))))
        }
    }

    LaunchedEffect(map, cameraRequest) {
        val m = map ?: return@LaunchedEffect
        val request = cameraRequest ?: return@LaunchedEffect
        val update = CameraUpdateFactory.newLatLngZoom(LatLng(request.lat, request.lon), request.zoom)
        if (request.animate) m.animateCamera(update) else m.moveCamera(update)
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        ZoomControls(
            onZoomIn = { map?.animateCamera(CameraUpdateFactory.zoomIn()) },
            onZoomOut = { map?.animateCamera(CameraUpdateFactory.zoomOut()) },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        )
    }
}

@Composable
private fun ZoomControls(onZoomIn: () -> Unit, onZoomOut: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(8.dp), shadowElevation = 4.dp) {
        Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onZoomIn, modifier = Modifier.semantics { contentDescription = "Zoom in" }) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
            HorizontalDivider()
            IconButton(onClick = onZoomOut, modifier = Modifier.semantics { contentDescription = "Zoom out" }) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

private fun addOverlayLayers(style: Style) {
    style.addSource(GeoJsonSource(CIRCLE_SOURCE))
    style.addLayer(
        FillLayer(CIRCLE_FILL, CIRCLE_SOURCE).withProperties(
            PropertyFactory.fillColor(CIRCLE_COLOR),
            PropertyFactory.fillOpacity(0.08f),
        )
    )
    style.addLayer(
        LineLayer(CIRCLE_LINE, CIRCLE_SOURCE).withProperties(
            PropertyFactory.lineColor(CIRCLE_COLOR),
            PropertyFactory.lineWidth(2f),
        )
    )
    style.addSource(GeoJsonSource(PINS_SOURCE))
    style.addLayer(
        CircleLayer(PINS_LAYER, PINS_SOURCE).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            PropertyFactory.circleStrokeWidth(2f),
        )
    )
    style.addLayer(
        SymbolLayer(LABELS_LAYER, PINS_SOURCE).withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
            PropertyFactory.textSize(12f),
            PropertyFactory.textOffset(arrayOf(0f, 1.2f)),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
            PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textOptional(true),
        ).apply { minZoom = 14.5f }
    )
}
