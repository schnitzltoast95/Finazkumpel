package de.quartalsmappe.app.model

enum class AppPalette(val stableId: String, val label: String) {
    FOREST("forest", "Waldgrün"),
    OCEAN("ocean", "Meeresblau"),
    SUNSET("sunset", "Abendrot"),
    MINIMAL("minimal", "Schiefergrau"),
    GOLDEN("golden", "Goldene Stunde"),
    ARCTIC("arctic", "Eisblau"),
    ROSE("rose", "Wüstenrose"),
    TECH("tech", "Klarblau"),
    BOTANICAL("botanical", "Botanischer Garten"),
    MIDNIGHT("midnight", "Nachtviolett");

    companion object {
        fun fromStableId(id: String?): AppPalette = entries.firstOrNull { it.stableId == id } ?: FOREST
    }
}

enum class CompanionAnimal(val stableId: String, val label: String) {
    OWL("owl", "Eule"),
    BEE("bee", "Biene"),
    PORCUPINE("porcupine", "Stachelschwein"),
    SHEEP("sheep", "Schaf"),
    SPHYNX("sphynx", "Nacktkatze");

    companion object {
        fun fromStableId(id: String?): CompanionAnimal = entries.firstOrNull { it.stableId == id } ?: OWL
    }
}

enum class AppThemeMode(val stableId: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Hell"),
    DARK("dark", "Dunkel");

    fun isDarkTheme(systemDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStableId(id: String?): AppThemeMode = entries.firstOrNull { it.stableId == id } ?: SYSTEM
    }
}

data class AppearanceSettings(
    val palette: AppPalette = AppPalette.FOREST,
    val companion: CompanionAnimal = CompanionAnimal.OWL,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
) {
    companion object {
        fun fromStableIds(
            paletteId: String?,
            companionId: String?,
            themeModeId: String? = null,
        ): AppearanceSettings = AppearanceSettings(
            palette = AppPalette.fromStableId(paletteId),
            companion = CompanionAnimal.fromStableId(companionId),
            themeMode = AppThemeMode.fromStableId(themeModeId),
        )
    }
}
