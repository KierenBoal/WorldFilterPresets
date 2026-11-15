package com.kierenboal.worldfilterpresets;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.events.ScriptPreFired;
import org.junit.Test;
import static org.junit.Assert.*;

public class PresetInGamePreviewTest
{
	@Test
	public void previewDoesNotSubstitutePresetFiltersAndUpdateUsesNativeValues() throws Exception
	{
		Map<Integer, Integer> masks = new HashMap<>();
		masks.put(14967, 3); masks.put(14968, 12); masks.put(14969, 7);
		Map<Integer, Object> varcs = new HashMap<>();
		varcs.put(1157, 65);
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(object, method, args) ->
			{
				switch (method.getName())
				{
					case "getVarbitValue": return masks.getOrDefault((int) args[0], 0);
					case "setVarbit": masks.put((int) args[0], (int) args[1]); return null;
					case "getVarcIntValue": return varcs.getOrDefault((int) args[0], 0);
					case "getVarcMap": return varcs;
					default: return null;
				}
			});
		WorldFilterPresetsPlugin plugin = new WorldFilterPresetsPlugin();
		PresetRepository repository = new PresetRepository(new PresetCodec(), null, ignored -> { });
		Preset preset = repository.add("A", new int[11]);
		set(plugin, "client", client); set(plugin, "repository", repository);
		set(plugin, "running", true); set(plugin, "supported", true);
		set(plugin, "previewingInGameFilters", true);
		set(plugin, "previewTicket", 3L);
		ScriptPreFired script = new ScriptPreFired(892);
		plugin.onScriptPreFired(script);
		assertEquals(Integer.valueOf(12), masks.get(14968));
		assertEquals(65, varcs.get(1157));
		assertArrayEquals(new int[11], repository.find(preset.id).filters());
		Method end = WorldFilterPresetsPlugin.class.getDeclaredMethod("endInGamePreview", long.class);
		end.setAccessible(true); end.invoke(plugin, 2L);
		assertTrue((boolean) get(plugin, "previewingInGameFilters"));
		end.invoke(plugin, 3L);
		assertFalse((boolean) get(plugin, "previewingInGameFilters"));
		assertArrayEquals(new int[11], repository.find(preset.id).filters());
		Method update = WorldFilterPresetsPlugin.class.getDeclaredMethod("updateFromInGame", String.class);
		update.setAccessible(true); update.invoke(plugin, preset.id);
		int[] expected = {3, 12, 7, 65, 0, 0, 0, 0, 0, 0, 0};
		assertArrayEquals(expected, repository.find(preset.id).filters());
		assertArrayEquals(expected, repository.working());
		assertFalse(repository.dirty());
	}

	private static void set(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
	}
	private static Object get(Object target, String name) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
	}
}
