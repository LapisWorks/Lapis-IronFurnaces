package top.mc506lw.rebar.ironfurnaces.furnace

/**
 * The classic mod's redstone setting, minus its config GUI: the mode is cycled with the button in
 * the bottom-right corner of the furnace GUI and shown in the WAILA overlay.
 *
 * [LOW] stops the furnace while it receives a signal, [HIGH] only runs it while it receives one.
 * Unlike the mod, a blocked furnace pauses instead of throwing away its progress and lit fuel.
 */
enum class RedstoneMode(val translationKey: String) {
    IGNORED("ironfurnaces.redstone.ignored"),
    LOW("ironfurnaces.redstone.low"),
    HIGH("ironfurnaces.redstone.high");

    fun next(): RedstoneMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromOrdinal(ordinal: Int): RedstoneMode =
            entries.getOrElse(ordinal) { IGNORED }
    }
}
