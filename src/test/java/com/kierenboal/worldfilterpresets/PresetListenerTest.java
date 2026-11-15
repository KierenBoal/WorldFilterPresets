package com.kierenboal.worldfilterpresets;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptEventBuilder;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;

public class PresetListenerTest
{
	private static final int[] MASKS = {14967, 14968, 14969};
	private static final int[] SEARCH = {1157, 1158, 1159, 1160, 1161, 212, 1162, 1163};
	private final WorldFilterPresetsPlugin plugin = new WorldFilterPresetsPlugin();
	private final Map<Integer, Integer> masks = new HashMap<>();
	private final Map<Integer, Object> search = new HashMap<>();
	private final Widget widget = proxy(Widget.class, (object, method, args) -> null);
	private final Object[] arguments = {891, 123};
	private Widget source;
	private int operation = -1;
	private int executions;

	@Test
	public void refreshPreservesContextAndUsesDefaultScriptRestrictions() throws Exception
	{
		setup(() -> { });
		runListener(0);
		assertSame(widget, source);
		assertEquals(0, operation);
		assertEquals(1, executions);
	}

	@Test
	public void successfulScriptRestoresNativeFiltersAndSearch() throws Exception
	{
		verifyScopeRestored(false);
	}

	@Test
	public void failingScriptRestoresNativeFiltersAndSearch() throws Exception
	{
		verifyScopeRestored(true);
	}

	private void verifyScopeRestored(boolean fail) throws Exception
	{
		RuntimeException failure = new IllegalStateException("Script execution failed");
		setup(() ->
		{
			plugin.onScriptPreFired(new ScriptPreFired(892));
			for (int mask : MASKS) { assertEquals(Integer.valueOf(0), masks.get(mask)); }
			for (int varc : SEARCH) { assertEquals(0, search.get(varc)); }
			if (fail) { throw failure; }
			plugin.onScriptPostFired(new ScriptPostFired(892));
		});
		Map<Integer, Integer> nativeMasks = new HashMap<>(masks);
		Map<Integer, Object> nativeSearch = new HashMap<>(search);
		try
		{
			runListener(1);
			assertFalse("Expected the script failure to propagate", fail);
		}
		catch (InvocationTargetException ex)
		{
			assertTrue(fail);
			assertSame(failure, ex.getCause());
		}
		assertSame(widget, source);
		assertEquals(1, operation);
		assertEquals(1, executions);
		assertEquals(nativeMasks, masks);
		assertEquals(nativeSearch, search);
		assertEquals(-1, get("scopedScript"));
		assertTrue(((Map<?, ?>) get("originalSearch")).isEmpty());
	}

	private void setup(Runnable execute) throws Exception
	{
		masks.put(14967, 3); masks.put(14968, 12); masks.put(14969, 7);
		search.put(1157, 5);
		search.put(212, -1); // Preserve raw values and absent entries, not just normalized filters.
		ScriptEvent event = proxy(ScriptEvent.class, (object, method, args) ->
		{
			if (method.getName().equals("run")) { executions++; execute.run(); return null; }
			throw new AssertionError("Unexpected script event call: " + method.getName());
		});
		ScriptEventBuilder builder = proxy(ScriptEventBuilder.class, (object, method, args) ->
		{
			switch (method.getName())
			{
				case "setSource": source = (Widget) args[0]; return object;
				case "setOp": operation = (int) args[0]; return object;
				case "build": return event;
				default: throw new AssertionError("Unexpected script builder call: " + method.getName());
			}
		});
		Client client = proxy(Client.class, (object, method, args) ->
		{
			switch (method.getName())
			{
				case "createScriptEventBuilder": assertSame(arguments, args[0]); return builder;
				case "getVarbitValue": return masks.getOrDefault((int) args[0], 0);
				case "setVarbit": masks.put((int) args[0], (int) args[1]); return null;
				case "getVarcIntValue": return search.getOrDefault((int) args[0], 0);
				case "getVarcMap": return search;
				default: throw new AssertionError("Unexpected client call: " + method.getName());
			}
		});
		PresetRepository repository = new PresetRepository(PresetTestSupport.codec(), null, ignored -> { });
		repository.add("Preset", new int[11]);
		set("client", client); set("repository", repository);
		set("running", true); set("supported", true);
	}

	private void runListener(int op) throws Exception
	{
		Method method = plugin.getClass().getDeclaredMethod("runListener", Widget.class, Object[].class, int.class);
		method.setAccessible(true);
		method.invoke(plugin, widget, arguments, op);
	}

	private void set(String name, Object value) throws Exception
	{
		Field field = plugin.getClass().getDeclaredField(name);
		field.setAccessible(true); field.set(plugin, value);
	}

	private Object get(String name) throws Exception
	{
		Field field = plugin.getClass().getDeclaredField(name);
		field.setAccessible(true); return field.get(plugin);
	}

	private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler)
	{
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
	}
}
