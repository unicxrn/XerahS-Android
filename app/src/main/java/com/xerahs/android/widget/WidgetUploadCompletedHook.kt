package com.xerahs.android.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.xerahs.android.core.domain.UploadCompletedHook
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetUploadCompletedHook @Inject constructor(
    @ApplicationContext private val context: Context
) : UploadCompletedHook {
    override suspend fun onUploadCompleted() {
        LatestUploadWidget().updateAll(context)
    }
}
