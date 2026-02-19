package com.comprint.wakenear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.comprint.wakenear.data.FavoriteLocation
import com.comprint.wakenear.data.WakeNearDatabase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

data class LocationUiState(
    val destination: GeoPoint? = null,
    val destinationName: String = "",
    val radiusMeters: Float = 500f,
    val isMonitoring: Boolean = false,
    val currentLocation: GeoPoint? = null,
    val distanceRemaining: Float? = null,
    val favorites: List<FavoriteLocation> = emptyList()
)

class LocationViewModel(application: Application) : AndroidViewModel(application) {

    private val db = WakeNearDatabase.getInstance(application)
    private val dao = db.favoriteLocationDao()

    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            dao.getAll().collect { favs ->
                _uiState.update { it.copy(favorites = favs) }
            }
        }
    }

    fun setDestination(point: GeoPoint, name: String = "") {
        _uiState.update { it.copy(destination = point, destinationName = name) }
    }

    fun setRadius(radius: Float) {
        _uiState.update { it.copy(radiusMeters = radius) }
    }

    fun setMonitoring(monitoring: Boolean) {
        _uiState.update { it.copy(isMonitoring = monitoring) }
    }

    fun updateCurrentLocation(location: GeoPoint) {
        val dest = _uiState.value.destination
        val distance = if (dest != null) {
            Utils.haversineDistance(
                location.latitude, location.longitude,
                dest.latitude, dest.longitude
            ).toFloat()
        } else null
        _uiState.update { it.copy(currentLocation = location, distanceRemaining = distance) }
    }

    fun saveFavorite(name: String) {
        val dest = _uiState.value.destination ?: return
        viewModelScope.launch {
            dao.insert(
                FavoriteLocation(
                    name = name,
                    latitude = dest.latitude,
                    longitude = dest.longitude
                )
            )
        }
    }

    fun deleteFavorite(favorite: FavoriteLocation) {
        viewModelScope.launch {
            dao.delete(favorite)
        }
    }
}
