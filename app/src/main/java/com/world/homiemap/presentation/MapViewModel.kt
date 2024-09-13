package com.world.homiemap.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MapViewModel : ViewModel() {
    fun setFriend(newHomie: HomieAndLocation) {
        _currentFriend.value = newHomie
    }


    private val _currentFriend: MutableStateFlow<HomieAndLocation?> = MutableStateFlow(null)
    val currentFriend: StateFlow<HomieAndLocation?> = _currentFriend
}