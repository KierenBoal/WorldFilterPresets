package com.kierenboal.worldfilterpresets;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import org.junit.Test;
import static org.junit.Assert.*;

public class PresetTickSelectionTest
{
	@Test
	public void selectionWaitsForTickAndLatestClickWins() throws Exception
	{
		List<Integer> sounds = new ArrayList<>();
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(object, method, args) -> { if (method.getName().equals("playSoundEffect")) { sounds.add((int) args[0]); } return null; });
		WorldFilterPresetsPlugin plugin = new WorldFilterPresetsPlugin();
		PresetRepository repository = new PresetRepository(PresetTestSupport.codec(), null, value -> { });
		set(plugin, "client", client); set(plugin, "repository", repository);
		set(plugin, "running", true); set(plugin, "supported", true);
		Method select = WorldFilterPresetsPlugin.class.getDeclaredMethod("select", String.class);
		select.setAccessible(true);
		select.invoke(plugin, PresetRepository.NONE);
		assertEquals(PresetRepository.CLIENT, repository.selected());
		plugin.onGameTick(new GameTick());
		assertEquals(PresetRepository.NONE, repository.selected());
		select.invoke(plugin, PresetRepository.CLIENT);
		select.invoke(plugin, PresetRepository.NONE);
		plugin.onGameTick(new GameTick());
		assertEquals(PresetRepository.NONE, repository.selected());
		assertEquals(Arrays.asList(2266, 2266, 2266), sounds);
		select.invoke(plugin, PresetRepository.CLIENT);
		GameStateChanged logout = new GameStateChanged(); logout.setGameState(GameState.LOGIN_SCREEN);
		plugin.onGameStateChanged(logout);
		plugin.onGameTick(new GameTick());
		assertEquals(PresetRepository.NONE, repository.selected());
	}

	private static void set(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
	}
}
