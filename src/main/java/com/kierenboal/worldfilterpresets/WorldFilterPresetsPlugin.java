package com.kierenboal.worldfilterpresets;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(name = "World Filter Presets")
public class WorldFilterPresetsPlugin extends Plugin
{
	@Inject
	private WorldFilterPresetsConfig config;

	@Override
	protected void startUp() throws Exception
	{
		log.debug("World Filter Presets started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("World Filter Presets stopped");
	}

	@Provides
	WorldFilterPresetsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WorldFilterPresetsConfig.class);
	}
}
