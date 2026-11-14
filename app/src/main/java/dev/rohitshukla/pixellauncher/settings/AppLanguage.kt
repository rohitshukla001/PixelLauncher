package dev.rohitshukla.pixellauncher.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList

/**
 * Yomo's "Change language": English, Hinglish or Hindi, for this app only. Android 13+ keeps the
 * choice itself (it also shows in the phone's App languages setting); older versions keep it here
 * and every activity applies it as it starts.
 */
object AppLanguage {

    enum class Option(val tag: String, val nativeName: String) {
        English("en", "English"),
        Hinglish("hi-Latn", "Hinglish"),
        Hindi("hi", "हिन्दी"),
    }

    private const val PREFS = "language"
    private const val KEY = "tag"

    /** The chosen language, or null while the app follows the phone's language. */
    fun current(context: Context): Option? {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        }
        // Match by language + script, not the raw tag, so a canonicalised tag (e.g. hi-Latn-IN) still maps.
        val locale = tag?.takeIf { it.isNotEmpty() }?.let { java.util.Locale.forLanguageTag(it) } ?: return null
        return Option.entries.firstOrNull {
            val want = java.util.Locale.forLanguageTag(it.tag)
            want.language == locale.language && want.script == locale.script
        }
    }

    fun set(activity: Activity, option: Option) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android restarts the app's screens in the new language.
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(option.tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, option.tag).apply()
            activity.recreate()
        }
    }

    /** For attachBaseContext before Android 13: the context in the chosen language. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
        return base.createConfigurationContext(config)
    }

    /** Before Android 13: true when the activity was created in an older choice and should restart. */
    fun isOutdated(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return false
        val tag = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return false
        return !activity.resources.configuration.locales[0].toLanguageTag().equals(tag, ignoreCase = true)
    }
}
