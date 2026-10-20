package com.example.royalremotelab.model

/**
 * Command categories for grouping and UI rendering.
 */
enum class CommandCategory {
    NAVIGATION,
    SYSTEM,
    VOLUME,
    MEDIA
}

/**
 * Explicit model for all supported smart TV commands.
 */
enum class RemoteCommand(
    val label: String,
    val iconSymbol: String,
    val category: CommandCategory,
    val description: String
) {
    // Navigation
    UP("Up", "▲", CommandCategory.NAVIGATION, "Navigate cursor upward"),
    DOWN("Down", "▼", CommandCategory.NAVIGATION, "Navigate cursor downward"),
    LEFT("Left", "◀", CommandCategory.NAVIGATION, "Navigate cursor leftward"),
    RIGHT("Right", "▶", CommandCategory.NAVIGATION, "Navigate cursor rightward"),
    OK("OK / Enter", "OK", CommandCategory.NAVIGATION, "Select highlighted item / enter"),

    // System keys
    BACK("Back", "⮌", CommandCategory.SYSTEM, "Return to previous screen"),
    HOME("Home", "⌂", CommandCategory.SYSTEM, "Open smart TV home dashboard"),
    MENU("Menu", "☰", CommandCategory.SYSTEM, "Open TV settings / OSD menu"),
    INPUT_SOURCE("Source", "⇥", CommandCategory.SYSTEM, "Toggle input source (HDMI, TV, AV)"),

    // Audio controls
    VOLUME_UP("Vol +", "🔊+", CommandCategory.VOLUME, "Increase volume level"),
    VOLUME_DOWN("Vol -", "🔉-", CommandCategory.VOLUME, "Decrease volume level"),
    MUTE("Mute", "🔇", CommandCategory.VOLUME, "Toggle audio mute state"),

    // Media playback controls
    PLAY("Play", "▶", CommandCategory.MEDIA, "Start media playback"),
    PAUSE("Pause", "⏸", CommandCategory.MEDIA, "Pause active media playback"),
    STOP("Stop", "⏹", CommandCategory.MEDIA, "Stop media playback")
}
