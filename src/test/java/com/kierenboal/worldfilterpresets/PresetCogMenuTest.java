package com.kierenboal.worldfilterpresets;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.util.Text;
import org.junit.Test;
import static org.junit.Assert.*;

public class PresetCogMenuTest
{
	@Test
	public void selectedPresetIsHighlightedWhenMenuIsOpened() throws Exception
	{
		PresetRepository state = new PresetRepository(PresetTestSupport.codec(), null, value -> { });
		Preset preset = state.add("A", new int[11]);
		state.select(PresetRepository.CLIENT);
		assertEquals("<col=ffff00>In-game filter*</col>", populate(state).target("In-game filter"));
		state.select(preset.id);
		M menu = populate(state);
		assertEquals("<col=ffff00>A*</col>", menu.target("A"));
		assertEquals("In-game filter", menu.target("In-game filter"));
		state.select(PresetRepository.NONE);
		assertEquals("<col=ffff00>No filters*</col>", populate(state).target("No filters"));
	}

	@Test
	public void cogIncludesBuiltInsAndVisibleCustomsInSharedOrderAlongsideConfigure() throws Exception
	{
		PresetRepository state = new PresetRepository(PresetTestSupport.codec(), null, value -> { });
		Preset a = state.add("A", new int[11]);
		Preset b = state.add("B", new int[11]);
		state.move(b.id, -1, false, false);
		state.move(b.id, -1, false, false);
		state.toggleCog(a.id);
		M menu = populate(state);
		assertEquals(Arrays.asList("In-game filter", "B", "No filters"), menu.targets());
		assertEquals("Configure", menu.entries.get(menu.entries.size() - 1).getOption());
	}

	@Test
	public void moreThanFiveHundredPresetsRemainReachableThroughBoundedSubmenus() throws Exception
	{
		PresetRepository state = new PresetRepository(PresetTestSupport.codec(), null, value -> { });
		Preset seed = state.add("Seed", new int[11]);
		List<Preset> imported = new ArrayList<>();
		for (int i = 0; i < 500; i++) { imported.add(seed.withName("Preset " + i)); }
		state.applyImport(state.previewImport(imported));
		M menu = populate(state);
		assertTrue(menu.entries.size() < 500);
		List<String> targets = menu.targets();
		assertEquals(503, targets.size());
		assertEquals("In-game filter", targets.get(0));
		assertEquals("No filters", targets.get(1));
		assertEquals("Preset 499", targets.get(targets.size() - 1));
	}

	private M populate(PresetRepository repository) throws Exception
	{
		return populate(repository, new WorldFilterPresetsConfig() { });
	}

	@Test
	public void hidingCogPresetsLeavesNativeMenuAndPerPresetPreferencesIntact() throws Exception
	{
		PresetRepository state = new PresetRepository(PresetTestSupport.codec(), null, value -> { });
		Preset preset = state.add("A", new int[11]);
		assertFalse(new WorldFilterPresetsConfig() { }.hideCogPresetMenu());
		M hidden = populate(state, new WorldFilterPresetsConfig()
		{
			@Override public boolean hideCogPresetMenu() { return true; }
		});
		assertTrue(hidden.targets().isEmpty());
		assertEquals(2, hidden.entries.size());
		assertEquals("Configure", hidden.entries.get(1).getOption());
		assertEquals("Cancel", hidden.entries.get(0).getOption());
		assertTrue(state.showInCogMenu(preset.id));
		assertEquals(3, state.visibleEntries(false, false).size());
		assertEquals(3, populate(state).targets().size());
	}

	private M populate(PresetRepository repository, WorldFilterPresetsConfig settings) throws Exception
	{
		M menu = new M();
		menu.api.createMenuEntry(0).setOption("Cancel");
		menu.api.createMenuEntry(1).setOption("Configure").setParam1(InterfaceID.Worldswitcher.OPTIONS);
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(object, method, args) -> method.getName().equals("getMenu") ? menu.api : null);
		WorldFilterPresetsPlugin plugin = new WorldFilterPresetsPlugin();
		set(plugin, "repository", repository); set(plugin, "client", client);
		set(plugin, "settings", settings);
		set(plugin, "running", true); set(plugin, "supported", true);
		MenuOpened event = new MenuOpened(); event.setMenuEntries(menu.api.getMenuEntries());
		plugin.onMenuOpened(event);
		return menu;
	}

	private void set(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
	}

	private static final class M
	{
		final List<MenuEntry> entries = new ArrayList<>();
		final Map<MenuEntry, M> submenus = new IdentityHashMap<>();
		final Menu api = (Menu) Proxy.newProxyInstance(Menu.class.getClassLoader(), new Class<?>[]{Menu.class}, (object, method, args) ->
		{
			if (method.getName().equals("getMenuEntries")) { return entries.toArray(new MenuEntry[0]); }
			if (!method.getName().equals("createMenuEntry")) { return null; }
			Map<String, Object> values = new HashMap<>();
			MenuEntry entry = (MenuEntry) Proxy.newProxyInstance(MenuEntry.class.getClassLoader(), new Class<?>[]{MenuEntry.class}, (e, m, a) ->
			{
				if (m.getName().equals("createSubMenu"))
				{
					M submenu = new M(); submenus.put((MenuEntry) e, submenu); return submenu.api;
				}
				if (m.getName().startsWith("set")) { values.put(m.getName().substring(3), a[0]); return e; }
				if (m.getName().equals("onClick")) { if (a != null) { values.put("click", a[0]); return e; } return values.get("click"); }
				if (m.getName().startsWith("get"))
				{
					Object result = values.get(m.getName().substring(3));
					return result == null && m.getReturnType() == int.class ? 0 : result;
				}
				return null;
			});
			entries.add((int) args[0], entry);
			return entry;
		});
		List<String> targets()
		{
			List<String> result = new ArrayList<>();
			for (int i = entries.size() - 1; i >= 0; i--)
			{
				MenuEntry entry = entries.get(i);
				if ("Use".equals(entry.getOption())) { result.add(presetName(entry.getTarget())); }
				if (submenus.containsKey(entry)) { result.addAll(submenus.get(entry).targets()); }
			}
			return result;
		}
		String target(String name)
		{
			for (MenuEntry entry : entries)
			{
				if ("Use".equals(entry.getOption()) && name.equals(presetName(entry.getTarget())))
				{ return entry.getTarget(); }
				if (submenus.containsKey(entry))
				{
					String result = submenus.get(entry).target(name);
					if (result != null) { return result; }
				}
			}
			return null;
		}
		private String presetName(String target)
		{
			String name = Text.removeTags(target);
			return target.startsWith("<col=ffff00>") ? name.substring(0, name.length() - 1) : name;
		}
	}
}
