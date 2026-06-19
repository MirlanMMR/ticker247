package com.mirlanmamytov.ticker247.ui.screens

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mirlanmamytov.ticker247.data.datastore.AppSettings
import com.mirlanmamytov.ticker247.service.TickerForegroundService
import com.mirlanmamytov.ticker247.workmanager.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    application: Application,
    private val appSettings: AppSettings
) : AndroidViewModel(application) {

    val isOnboardingDone: StateFlow<Boolean?> = appSettings.onboardingDoneFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val selectedCategories = mutableStateListOf<Category>()

    fun toggleCategory(category: Category) {
        if (selectedCategories.contains(category)) selectedCategories.remove(category)
        else selectedCategories.add(category)
    }

    fun saveAndFinish(region: String) {
        viewModelScope.launch {
            appSettings.saveContentRegion(region)
            appSettings.setOnboardingDone(true)
            TickerForegroundService.startService(getApplication())
        }
    }

    /** Пропустить онбординг — сразу открыть главный экран */
    fun completeOnboarding() {
        viewModelScope.launch {
            appSettings.setOnboardingDone(true)
        }
    }
}
