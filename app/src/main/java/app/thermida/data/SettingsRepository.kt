package app.thermida.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.thermida.domain.UserProfile
import app.thermida.ui.theme.AccentColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Stored names (this file, "morsel.db", "morsel_api_keys") keep an early name: renaming them would
// lose the data on every phone that already has the app.
private val Context.dataStore by preferencesDataStore("morsel_settings")

/**
 * The settings a backup carries, so a restored phone looks and works like the old one.
 * API keys and the custom service address never leave the phone.
 */
@Serializable
data class SettingsBackup(
    val theme: String? = null,
    val accent: String? = null,
    val scaleEnabled: Boolean? = null,
    val haptics: Boolean? = null,
    val primary: String? = null,
    val fallback: Boolean? = null,
    val fallbackProvider: String? = null,
    val engines: String? = null,
    val models: Map<String, String> = emptyMap(),
    val coachSpeak: Boolean? = null,
    val voiceLanguage: String? = null,
    val coachHandsFree: Boolean? = null,
    val coachSpeechRate: Float? = null,
    val coachPauseMs: Int? = null,
    val coachDetailed: Boolean? = null,
    val coachFollowUps: Boolean? = null,
    /** The day and week AI token limits (0 is no limit). */
    val aiDailyLimit: Long? = null,
    val aiWeeklyLimit: Long? = null,
)

/** The coach voice's normal speed (Android's own) and the range the slider allows. */
const val DEFAULT_SPEECH_RATE = 1.0f
const val MIN_SPEECH_RATE = 0.8f
const val MAX_SPEECH_RATE = 1.8f

/** The pause that ends a spoken question, and the choices offered. */
const val DEFAULT_PAUSE_MS = 1500
const val MIN_PAUSE_MS = 1000
const val MAX_PAUSE_MS = 2000

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore
    private val cipher = KeyCipher()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val profileKey = stringPreferencesKey("profile")
    private val themeKey = stringPreferencesKey("theme")
    private val accentKey = stringPreferencesKey("accent")
    private val scaleKey = booleanPreferencesKey("scale_enabled")
    private val primaryKey = stringPreferencesKey("ai_primary")
    private val fallbackKey = booleanPreferencesKey("ai_fallback")
    private val fallbackProviderKey = stringPreferencesKey("ai_fallback_provider")
    private val customUrlKey = stringPreferencesKey("custom_base_url")
    private val enginesKey = stringPreferencesKey("ai_engines")
    private val thinkingKey = stringPreferencesKey("deepseek_thinking")
    private val foodFactsKey = booleanPreferencesKey("use_food_facts")
    private val coachKey = booleanPreferencesKey("coach_enabled")
    private val greekKey = booleanPreferencesKey("region_greece")
    private val usageKey = stringPreferencesKey("ai_usage")
    private val dailyLimitKey = longPreferencesKey("ai_daily_limit")
    private val weeklyLimitKey = longPreferencesKey("ai_weekly_limit")
    private val hapticsKey = booleanPreferencesKey("haptics_enabled")
    private val coachTutorialKey = booleanPreferencesKey("coach_tutorial_done")
    private val coachSpeakKey = booleanPreferencesKey("coach_speak")
    private val voiceLanguageKey = stringPreferencesKey("voice_language")
    private val handsFreeKey = booleanPreferencesKey("coach_hands_free")
    private val speechRateKey = floatPreferencesKey("coach_speech_rate")
    private val pauseKey = intPreferencesKey("coach_pause_ms")
    private val detailedKey = booleanPreferencesKey("coach_detailed")
    private val followUpsKey = booleanPreferencesKey("coach_follow_ups")

    private fun keyPref(provider: Provider) = stringPreferencesKey("${provider.prefix}_key")

    private fun modelPref(provider: Provider) = stringPreferencesKey("${provider.prefix}_model")

    val profile: Flow<UserProfile?> = store.data.map { prefs ->
        prefs[profileKey]?.let { runCatching { json.decodeFromString<UserProfile>(it) }.getOrNull() }?.agedTo(LocalDate.now())
    }

    val theme: Flow<ThemeMode> = store.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[themeKey] } ?: ThemeMode.DARK
    }

    val accent: Flow<AccentColor> = store.data.map { prefs -> AccentColor.fromName(prefs[accentKey]) }

    /** The Xiaomi scale integration is opt-in; off until the user turns it on in Settings. */
    val scaleEnabled: Flow<Boolean> = store.data.map { prefs -> prefs[scaleKey] ?: false }

    /** Vibration feedback on taps, switches and gestures: on unless the user turns it off. */
    val hapticsEnabled: Flow<Boolean> = store.data.map { prefs -> prefs[hapticsKey] ?: true }.distinctUntilChanged()

    /** Open Food Facts lookups: on unless the user turns them off (since 1.1.7). */
    val useFoodFacts: Flow<Boolean> = store.data.map { prefs -> prefs[foodFactsKey] ?: true }

    /**
     * The AI coach (beta) sends a summary of the user's data to their AI engine, so it stays off
     * until they turn it on. Deliberately not carried in backups: a restored phone asks again.
     */
    val coachEnabled: Flow<Boolean> = store.data.map { prefs -> prefs[coachKey] ?: false }.distinctUntilChanged()

    /** The coach reads its answers aloud; off until the user turns on the speaker in the chat. */
    val coachSpeak: Flow<Boolean> = store.data.map { prefs -> prefs[coachSpeakKey] ?: false }.distinctUntilChanged()

    /** The language the coach's mic listens for (a VoiceLanguage name); null until the user picks one. */
    val voiceLanguage: Flow<String?> = store.data.map { prefs -> prefs[voiceLanguageKey] }.distinctUntilChanged()

    /** After a spoken question the mic opens again once the coach has finished talking; on by default. */
    val coachHandsFree: Flow<Boolean> = store.data.map { prefs -> prefs[handsFreeKey] ?: true }.distinctUntilChanged()

    /** How fast the coach's voice talks; 1.0 is Android's normal speed. */
    val coachSpeechRate: Flow<Float> = store.data.map { prefs -> prefs[speechRateKey] ?: DEFAULT_SPEECH_RATE }.distinctUntilChanged()

    /** How long a pause ends a spoken question, in milliseconds. */
    val coachPauseMs: Flow<Int> = store.data.map { prefs -> prefs[pauseKey] ?: DEFAULT_PAUSE_MS }.distinctUntilChanged()

    /** Fuller coach answers instead of the short default. */
    val coachDetailed: Flow<Boolean> = store.data.map { prefs -> prefs[detailedKey] ?: false }.distinctUntilChanged()

    /** Suggested next questions under coach answers; on by default. */
    val coachFollowUps: Flow<Boolean> = store.data.map { prefs -> prefs[followUpsKey] ?: true }.distinctUntilChanged()

    /** Greek foods and brands tuning for scans, the coach and food search; on unless turned off. */
    val regionGreece: Flow<Boolean> = store.data.map { prefs -> prefs[greekKey] ?: true }.distinctUntilChanged()

    /** Tokens used per AI request, as "time:tokens" pairs (see TokenBudget). */
    val aiUsageLog: Flow<String> = store.data.map { prefs -> prefs[usageKey].orEmpty() }.distinctUntilChanged()

    /** The day limit and the week limit in tokens; 0 means no limit. */
    val aiDailyLimit: Flow<Long> = store.data.map { prefs -> prefs[dailyLimitKey] ?: 0L }.distinctUntilChanged()
    val aiWeeklyLimit: Flow<Long> = store.data.map { prefs -> prefs[weeklyLimitKey] ?: 0L }.distinctUntilChanged()

    /** False until the user has pulled the coach down once after turning it on (the hint shows until then). */
    val coachTutorialDone: Flow<Boolean> = store.data.map { prefs -> prefs[coachTutorialKey] ?: false }.distinctUntilChanged()

    val ai: Flow<AiSettings> = store.data.map { prefs ->
        AiSettings(
            primary = Provider.entries.firstOrNull { it.name == prefs[primaryKey] } ?: Provider.DEEPSEEK,
            keys = Provider.entries.associateWith { cipher.decrypt(prefs[keyPref(it)]) },
            models = Provider.entries.associateWith { prefs[modelPref(it)].orEmpty() },
            customBaseUrl = prefs[customUrlKey].orEmpty(),
            fallback = prefs[fallbackKey] ?: false,
            fallbackProvider = Provider.entries.firstOrNull { it.name == prefs[fallbackProviderKey] },
            deepseekThinking = ThinkingLevel.entries.firstOrNull { it.name == prefs[thinkingKey] } ?: ThinkingLevel.HIGH,
            engines = prefs[enginesKey].orEmpty().split(',').mapNotNull { name -> Provider.entries.firstOrNull { it.name == name } },
        )
    }

    private fun backupOf(prefs: Preferences) = SettingsBackup(
        theme = prefs[themeKey],
        accent = prefs[accentKey],
        scaleEnabled = prefs[scaleKey],
        haptics = prefs[hapticsKey],
        primary = prefs[primaryKey],
        fallback = prefs[fallbackKey],
        fallbackProvider = prefs[fallbackProviderKey],
        engines = prefs[enginesKey],
        models = Provider.entries.mapNotNull { p -> prefs[modelPref(p)]?.takeIf { it.isNotBlank() }?.let { p.name to it } }.toMap(),
        coachSpeak = prefs[coachSpeakKey],
        voiceLanguage = prefs[voiceLanguageKey],
        coachHandsFree = prefs[handsFreeKey],
        coachSpeechRate = prefs[speechRateKey],
        coachPauseMs = prefs[pauseKey],
        coachDetailed = prefs[detailedKey],
        coachFollowUps = prefs[followUpsKey],
        aiDailyLimit = prefs[dailyLimitKey],
        aiWeeklyLimit = prefs[weeklyLimitKey],
    )

    suspend fun backup(): SettingsBackup = backupOf(store.data.first())

    /** Puts restored settings in place. Missing values (older backups) leave the current ones alone. */
    suspend fun restore(backup: SettingsBackup) {
        store.edit { prefs ->
            backup.theme?.let { prefs[themeKey] = it }
            backup.accent?.let { prefs[accentKey] = it }
            backup.scaleEnabled?.let { prefs[scaleKey] = it }
            backup.haptics?.let { prefs[hapticsKey] = it }
            backup.primary?.let { prefs[primaryKey] = it }
            backup.fallback?.let { prefs[fallbackKey] = it }
            backup.fallbackProvider?.let { prefs[fallbackProviderKey] = it }
            backup.engines?.let { prefs[enginesKey] = it }
            backup.coachSpeak?.let { prefs[coachSpeakKey] = it }
            backup.voiceLanguage?.let { prefs[voiceLanguageKey] = it }
            backup.coachHandsFree?.let { prefs[handsFreeKey] = it }
            backup.coachSpeechRate?.let { prefs[speechRateKey] = it.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE) }
            // Values from a file are checked like the app's own controls would allow.
            backup.coachPauseMs?.let { prefs[pauseKey] = it.coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS) }
            backup.aiDailyLimit?.let { prefs[dailyLimitKey] = it.coerceAtLeast(0L) }
            backup.aiWeeklyLimit?.let { prefs[weeklyLimitKey] = it.coerceAtLeast(0L) }
            backup.coachDetailed?.let { prefs[detailedKey] = it }
            backup.coachFollowUps?.let { prefs[followUpsKey] = it }
            backup.models.forEach { (name, model) ->
                Provider.entries.firstOrNull { it.name == name }?.let { prefs[modelPref(it)] = model }
            }
        }
    }

    /** Adds one reply's tokens to the usage log the day and week limits are checked against. */
    suspend fun recordAiTokens(tokens: Long, now: Long) {
        store.edit { prefs -> prefs[usageKey] = TokenBudget.add(prefs[usageKey].orEmpty(), now, tokens, now) }
    }

    suspend fun setDailyLimit(tokens: Long) {
        store.edit { it[dailyLimitKey] = tokens }
    }

    suspend fun setWeeklyLimit(tokens: Long) {
        store.edit { it[weeklyLimitKey] = tokens }
    }

    suspend fun setRegionGreece(on: Boolean) {
        store.edit { it[greekKey] = on }
    }

    suspend fun setFallback(enabled: Boolean) {
        store.edit { it[fallbackKey] = enabled }
    }

    suspend fun setFallbackProvider(provider: Provider) {
        store.edit { it[fallbackProviderKey] = provider.name }
    }

    suspend fun setCustomBaseUrl(url: String) {
        store.edit { it[customUrlKey] = url.trim() }
    }

    /** Wipes every setting. API keys (and the custom address that goes with them) survive when [keepKeys] is true. */
    suspend fun clear(keepKeys: Boolean) {
        store.edit { prefs ->
            val keys = Provider.entries.mapNotNull { p -> prefs[keyPref(p)]?.let { p to it } }
            val customUrl = prefs[customUrlKey]
            prefs.clear()
            if (keepKeys) {
                keys.forEach { (p, key) -> prefs[keyPref(p)] = key }
                customUrl?.let { prefs[customUrlKey] = it }
            }
        }
    }

    suspend fun saveProfile(profile: UserProfile) {
        store.edit { it[profileKey] = json.encodeToString(UserProfile.serializer(), profile) }
    }

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[themeKey] = mode.name }
    }

    suspend fun setAccent(accent: AccentColor) {
        store.edit { it[accentKey] = accent.name }
    }

    suspend fun setScaleEnabled(enabled: Boolean) {
        store.edit { it[scaleKey] = enabled }
    }

    /** Only the list of engines shown; saved keys are never touched here. */
    suspend fun setEngines(engines: List<Provider>) {
        store.edit { it[enginesKey] = engines.distinct().joinToString(",") { p -> p.name } }
    }

    suspend fun setDeepseekThinking(level: ThinkingLevel) {
        store.edit { it[thinkingKey] = level.name }
    }

    suspend fun setUseFoodFacts(enabled: Boolean) {
        store.edit { it[foodFactsKey] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        store.edit { it[hapticsKey] = enabled }
    }

    suspend fun setCoachSpeak(enabled: Boolean) {
        store.edit { it[coachSpeakKey] = enabled }
    }

    suspend fun setVoiceLanguage(name: String) {
        store.edit { it[voiceLanguageKey] = name }
    }

    suspend fun setCoachHandsFree(on: Boolean) {
        store.edit { it[handsFreeKey] = on }
    }

    suspend fun setCoachSpeechRate(rate: Float) {
        store.edit { it[speechRateKey] = rate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE) }
    }

    suspend fun setCoachPauseMs(ms: Int) {
        store.edit { it[pauseKey] = ms.coerceIn(MIN_PAUSE_MS, MAX_PAUSE_MS) }
    }

    suspend fun setCoachDetailed(on: Boolean) {
        store.edit { it[detailedKey] = on }
    }

    suspend fun setCoachFollowUps(on: Boolean) {
        store.edit { it[followUpsKey] = on }
    }

    /** Brings back the one-time "pull down" hint. */
    suspend fun resetCoachTutorial() {
        store.edit { it[coachTutorialKey] = false }
    }

    /** Turning the coach on also brings back its one-time "pull down" hint. */
    suspend fun setCoachEnabled(enabled: Boolean) {
        store.edit {
            it[coachKey] = enabled
            if (enabled) it[coachTutorialKey] = false
        }
    }

    suspend fun setCoachTutorialDone() {
        store.edit { it[coachTutorialKey] = true }
    }

    suspend fun setPrimary(provider: Provider) {
        store.edit { it[primaryKey] = provider.name }
    }

    suspend fun setKey(provider: Provider, key: String) {
        val encrypted = cipher.encrypt(key.trim())
        store.edit { it[keyPref(provider)] = encrypted }
    }

    suspend fun setModel(provider: Provider, model: String) {
        store.edit { it[modelPref(provider)] = model.trim() }
    }
}

/** Encrypts API keys with an AES key that never leaves the Android Keystore. */
private class KeyCipher {
    // Kept as is: a new alias would make the saved keys unreadable.
    private val alias = "morsel_api_keys"

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    /** Decrypted keys by ciphertext; settings re-emit often, and unlocking the Keystore each time is slow. */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun decrypt(encoded: String?): String {
        if (encoded.isNullOrEmpty()) return ""
        return cache.getOrPut(encoded) { decryptUncached(encoded) }
    }

    private fun decryptUncached(encoded: String): String {
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            String(cipher.doFinal(bytes, 12, bytes.size - 12))
        }.getOrDefault("")
    }
}
