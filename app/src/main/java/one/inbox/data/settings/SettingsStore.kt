package one.inbox.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import one.inbox.BuildConfig
import one.inbox.core.ChatApps
import javax.inject.Inject

class SettingsStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val allowedKey = stringSetPreferencesKey("allowed_packages")
    private val onboardingKey = booleanPreferencesKey("onboarding_done")
    private val catchUpKey = stringPreferencesKey("last_catch_up_json")

    private val defaults get() = ChatApps.defaultAllowlist(BuildConfig.DEBUG)

    val allowedPackages: Flow<Set<String>> = dataStore.data.map { it[allowedKey] ?: defaults }

    suspend fun setAllowed(packageName: String, allowed: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[allowedKey] ?: defaults
            prefs[allowedKey] = if (allowed) current + packageName else current - packageName
        }
    }

    val onboardingDone: Flow<Boolean> = dataStore.data.map { it[onboardingKey] ?: false }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[onboardingKey] = done }
    }

    val lastCatchUpJson: Flow<String?> = dataStore.data.map { it[catchUpKey] }

    suspend fun setLastCatchUpJson(json: String) {
        dataStore.edit { it[catchUpKey] = json }
    }
}
