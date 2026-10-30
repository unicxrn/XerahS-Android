package com.xerahs.android.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.xerahs.android.R
import com.xerahs.android.core.domain.model.HistoryItem
import com.xerahs.android.core.domain.model.UploadProfile
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import com.xerahs.android.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Pure rules for the per-profile sharing shortcuts. */
object ProfileShortcuts {
    const val SHARE_CATEGORY = "com.xerahs.android.SHARE_TARGET"
    const val MAX = 4
    private const val PREFIX = "profile:"

    fun shortcutId(profileId: String): String = PREFIX + profileId

    /** The profile id behind a sharing shortcut id, or null for any other shortcut. */
    fun profileIdOf(shortcutId: String?): String? =
        shortcutId?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { it.isNotBlank() }

    /** When each profile was last used, from upload history. */
    fun lastUsed(history: List<HistoryItem>): Map<String, Long> =
        history.mapNotNull { item -> item.profileId?.let { it to item.timestamp } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, times) -> times.max() }

    /** Up to [max] profiles, most recently used first; unused ones keep their list order. */
    fun choose(profiles: List<UploadProfile>, lastUsed: Map<String, Long>, max: Int = MAX): List<UploadProfile> =
        profiles.sortedByDescending { lastUsed[it.id] ?: Long.MIN_VALUE }.take(max)

    fun shortLabel(profile: UploadProfile): String = profile.name.trim().ifEmpty { profile.destination.displayName }
}

/** Keeps one sharing shortcut per profile (up to four) in step with the saved profiles. */
@Singleton
class ProfileShortcutPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: UploadProfileRepository,
    private val historyRepository: HistoryRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                profileRepository.getAllProfiles(),
                historyRepository.getAllHistory().map(ProfileShortcuts::lastUsed).distinctUntilChanged()
            ) { profiles, used -> ProfileShortcuts.choose(profiles, used) }
                .distinctUntilChanged()
                .collect { chosen ->
                    try {
                        publish(chosen)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Rate limited or launcher refused; the next profile change tries again.
                    }
                }
        }
    }

    private fun publish(chosen: List<UploadProfile>) {
        val keep = chosen.map { ProfileShortcuts.shortcutId(it.id) }.toSet()
        val stale = ShortcutManagerCompat.getShortcuts(
            context,
            ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED
        ).map { it.id }.filter { ProfileShortcuts.profileIdOf(it) != null && it !in keep }.distinct()
        if (stale.isNotEmpty()) ShortcutManagerCompat.removeLongLivedShortcuts(context, stale)

        chosen.forEachIndexed { rank, profile ->
            val label = ProfileShortcuts.shortLabel(profile)
            val shortcut = ShortcutInfoCompat.Builder(context, ProfileShortcuts.shortcutId(profile.id))
                .setShortLabel(label)
                .setLongLabel("Upload to $label")
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(Intent(Intent.ACTION_MAIN, null, context, MainActivity::class.java))
                .setCategories(setOf(ProfileShortcuts.SHARE_CATEGORY))
                .setLongLived(true)
                // Share sheet only, so the launcher menu keeps room for the static shortcuts.
                .setExcludedFromSurfaces(ShortcutInfoCompat.SURFACE_LAUNCHER)
                .setRank(rank)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
    }
}
