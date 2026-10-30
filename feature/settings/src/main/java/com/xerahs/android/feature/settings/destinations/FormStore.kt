package com.xerahs.android.feature.settings.destinations

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Form fields kept in a ViewModel so typed edits survive rotation. Nothing goes into saved
 * state, so secrets stay out of the Bundle; edits made before process death are lost.
 */
class FormStore {
    private val fields = HashMap<String, MutableState<*>>()

    /** True once the saved config has been copied into the fields; a rotation must not reload it. */
    var loaded = false

    @Suppress("UNCHECKED_CAST")
    fun <T> field(key: String, initial: T): MutableState<T> =
        fields.getOrPut(key) { mutableStateOf(initial) } as MutableState<T>
}
