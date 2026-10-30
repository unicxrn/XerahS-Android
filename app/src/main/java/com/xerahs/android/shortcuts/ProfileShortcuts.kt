package com.xerahs.android.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.xerahs.android.R
import com.xerahs.android.core.domain.model.UploadProfile
import com.xerahs.android.core.domain.repository.HistoryRepository
import com.xerahs.android.core.domain.repository.UploadProfileRepository
import com.xerahs.android.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Pure rules for the per-profile sharing shortcuts. */
object ProfileShortcuts {
    const val SHARE_CATEGORY = "com.xerahs.android.SHARE_TARGET"
    const val MAX = 4
    private const val PREFIX = "profile:"

    /** What a published shortcut shows; two equal entries need no republish. */
    data class Entry(val id: String, val label: String, val rank: Int)

    fun shortcutId(profileId: String): String = PREFIX + profileId

    /** The profile id behind a sharing shortcut id, or null for any other shortcut. */
    fun profileIdOf(shortcutId: String?): String? =
        shortcutId?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.takeIf { it.isNotBlank() }

    /** Up to [max] profiles, most recently used first; unused ones keep their list order. */
    fun choose(profiles: List<UploadProfile>, lastUsed: Map<String, Long>, max: Int = MAX): List<UploadProfile> =
        profiles.sortedByDescending { lastUsed[it.id] ?: Long.MIN_VALUE }.take(max)

    fun shortLabel(profile: UploadProfile): String = profile.name.trim().ifEmpty { profile.destination.displayName }

    fun entries(chosen: List<UploadProfile>): List<Entry> =
        chosen.mapIndexed { rank, profile -> Entry(shortcutId(profile.id), shortLabel(profile), rank) }

    /** The [desired] entries that are missing from, or differ in, the [published] ones. */
    fun changed(desired: List<Entry>, published: List<Entry>): List<Entry> {
        val current = published.associateBy { it.id }
        return desired.filter { current[it.id] != it }
    }

    /** Profile shortcut ids in [published] that are no longer [desired]. */
    fun stale(desired: List<Entry>, published: List<String>): List<String> {
        val keep = desired.map { it.id }.toSet()
        return published.filter { profileIdOf(it) != null && it !in keep }.distinct()
    }
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
                historyRepository.lastUsedByProfile()
            ) { profiles, used -> ProfileShortcuts.entries(ProfileShortcuts.choose(profiles, used)) }
                .distinctUntilChanged()
                .collect { desired ->
                    try {
                        publish(desired)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Rate limited or launcher refused; the next profile change tries again.
                    }
                }
        }
    }

    private fun publish(desired: List<ProfileShortcuts.Entry>) {
        val all = ShortcutManagerCompat.getShortcuts(
            context,
            ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED
        ).map { it.id }
        val stale = ProfileShortcuts.stale(desired, all)
        if (stale.isNotEmpty()) ShortcutManagerCompat.removeLongLivedShortcuts(context, stale)

        val published = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_DYNAMIC)
            .map { ProfileShortcuts.Entry(it.id, it.shortLabel.toString(), it.rank) }
        ProfileShortcuts.changed(desired, published).forEach { entry ->
            val shortcut = ShortcutInfoCompat.Builder(context, entry.id)
                .setShortLabel(entry.label)
                .setLongLabel("Upload to ${entry.label}")
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(Intent(Intent.ACTION_MAIN, null, context, MainActivity::class.java))
                .setCategories(setOf(ProfileShortcuts.SHARE_CATEGORY))
                .setLongLived(true)
                // Not excluded from the launcher: on API 34 an excluded shortcut was never published.
                .setRank(entry.rank)
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
    }
}
