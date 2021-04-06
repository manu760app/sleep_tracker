/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import android.provider.SyncStateContract.Helpers.insert
import android.provider.SyncStateContract.Helpers.update
import androidx.lifecycle.*
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.Sleepnight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

    private var viewModelJob = Job()

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    private var tonight = MutableLiveData<Sleepnight?>()
    private val nights = database.getAllNights()
    val nightString = Transformations.map(nights) {nights ->
        formatNights(nights, application.resources)
    }

    private val _navigateToSleepQuality = MutableLiveData<Sleepnight>()
    val navigateToSleepQualityFragment: LiveData<Sleepnight>
        get() = _navigateToSleepQuality

    val startButtonVisible = Transformations.map(tonight){
        null == it
    }
    val stopButtonVisible = Transformations.map(tonight) {
        null != it
    }
    val clearButtonVisible = Transformations.map(nights) {
        it?.isNotEmpty()
    }

    private var _showSnackbarEvent = MutableLiveData<Boolean>()
    val showSnackbarEvent: MutableLiveData<Boolean>
    get() = _showSnackbarEvent

    init {
        initializeTonight()

    }

    fun doneShowingSnackbar() {
        _showSnackbarEvent.value = false
    }

    private fun initializeTonight() {
        uiScope.launch {
            tonight.value = getTonightFromDatabase()
        }

    }

    private suspend fun getTonightFromDatabase(): Sleepnight? {
        return withContext(Dispatchers.IO) {
            var night = database.getTonight()
            if (night?.endTimeMilli != night?.startTimeMilli) {
                night = null
            }
            night
        }

    }

    fun onStartTracking() {
        uiScope.launch {
            val newNight = Sleepnight()
            insert(newNight)
            tonight.value = getTonightFromDatabase()

        }

    }

    fun doneNavigating(){
        _navigateToSleepQuality.value = null
    }

    private suspend fun insert(night: Sleepnight) {
        withContext(Dispatchers.IO){
            database.insert(night)
        }
    }

    fun onStopTracking() {
        uiScope.launch {
            val oldNight = tonight.value ?: return@launch
            oldNight.endTimeMilli = System.currentTimeMillis()
            update(oldNight)
            _navigateToSleepQuality.value = oldNight

        }

    }

    private suspend fun update(night: Sleepnight) {
        withContext(Dispatchers.IO) {
            database.update(night)
        }
    }

    fun onClear(){
        uiScope.launch {
            clear()
            tonight.value = null
            _showSnackbarEvent.value = true
        }
    }

    suspend fun clear(){
        withContext(Dispatchers.IO) {
            database.clear()
        }

    }
}

