package com.kierenboal.worldfilterpresets;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class WorldFilterPresetsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(WorldFilterPresetsPlugin.class);
		RuneLite.main(args);
	}
}
