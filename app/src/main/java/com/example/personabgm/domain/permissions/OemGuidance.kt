package com.example.personabgm.domain.permissions

import java.util.Locale

/** Brand-specific help for phones whose battery managers stop background apps (spec 5.7). */
data class OemTip(
    val brand: String,
    /** Menu names vary by model and software version, so the text says what to look for rather than exact paths. */
    val advice: String,
    val moreInfoUrl: String,
)

object OemGuidance {

    fun forManufacturer(manufacturer: String?): OemTip? {
        val name = manufacturer?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return when {
            name.isEmpty() -> null

            "samsung" in name -> OemTip(
                brand = "Samsung",
                advice = "In Settings, open Battery and look for background usage limits. Make sure Persona BGM is " +
                    "not in \"Sleeping apps\" or \"Deep sleeping apps\", and add it to \"Never sleeping apps\".",
                moreInfoUrl = "https://dontkillmyapp.com/samsung",
            )

            name == "xiaomi" || name == "redmi" || name == "poco" -> OemTip(
                brand = "Xiaomi",
                advice = "In Settings, open Apps, then Manage apps, then Persona BGM. Turn on Autostart and set " +
                    "Battery saver to \"No restrictions\".",
                moreInfoUrl = "https://dontkillmyapp.com/xiaomi",
            )

            "oneplus" in name -> OemTip(
                brand = "OnePlus",
                advice = "In Settings, open Battery, then Battery optimization, and set Persona BGM to \"Don't " +
                    "optimize\". Also allow background activity and auto-launch if the option is there.",
                moreInfoUrl = "https://dontkillmyapp.com/oneplus",
            )

            name == "oppo" || name == "realme" || name == "vivo" || name == "iqoo" -> OemTip(
                brand = name.replaceFirstChar { it.titlecase(Locale.ROOT) },
                advice = "In Settings, open Battery and allow background activity for Persona BGM. Turn on " +
                    "auto-launch (auto-start) for it if the option is there.",
                moreInfoUrl = "https://dontkillmyapp.com/$name",
            )

            name == "huawei" || name == "honor" -> OemTip(
                brand = name.replaceFirstChar { it.titlecase(Locale.ROOT) },
                advice = "In Settings, open Battery, then App launch, and set Persona BGM to \"Manage manually\". " +
                    "Turn on auto-launch, secondary launch, and run in background.",
                moreInfoUrl = "https://dontkillmyapp.com/$name",
            )

            else -> null
        }
    }
}
