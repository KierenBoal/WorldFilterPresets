package com.kierenboal.worldfilterpresets;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("worldfilterpresets")
public interface WorldFilterPresetsConfig extends Config
{
	@ConfigItem(keyName = "hideNoFilters", name = "Hide No filters preset", description = "Hide the built-in No filters entry", position = 0)
	default boolean hideNoFilters() { return false; }

	@ConfigItem(keyName = "hideInGameFilter", name = "Hide In-game filter preset", description = "Hide the built-in In-game filter entry from the dropdown", position = 1)
	default boolean hideInGameFilter() { return false; }

	@ConfigItem(keyName = "hideCogPresetMenu", name = "Hide cog preset menu", description = "Hide preset shortcuts from the world-switcher cog's right-click menu", position = 2)
	default boolean hideCogPresetMenu() { return false; }
}
