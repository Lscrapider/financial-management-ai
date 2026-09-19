package com.scrapider.finance.androidapp.core.prefs

import android.content.Context

/**
 * 应用内字号档位。
 *
 * factor 与系统字体缩放相乘后生效，默认 MEDIUM 保持系统原样。
 */
enum class FontScaleMode(
    val label: String,
    val description: String,
    val factor: Float,
) {
    SMALL("小", "更紧凑，同屏可看更多内容", 0.85f),
    MEDIUM("中", "标准大小，跟随系统缩放", 1.0f),
    LARGE("大", "更大文字，阅读更轻松", 1.2f),
}

class DisplayPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadFontScaleMode(): FontScaleMode =
        preferences.getString(KEY_FONT_SCALE_MODE, null)
            ?.let { stored -> FontScaleMode.entries.firstOrNull { it.name == stored } }
            ?: FontScaleMode.MEDIUM

    fun saveFontScaleMode(mode: FontScaleMode) {
        preferences.edit().putString(KEY_FONT_SCALE_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "finance_android_display"
        const val KEY_FONT_SCALE_MODE = "font_scale_mode"
    }
}
