package com.kierenboal.worldfilterpresets;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import org.junit.Test;
import static org.junit.Assert.*;

public class PresetMessageTest
{
	private GameState gameState = GameState.STARTING;
	private Player player;
	private final List<String> messages = new ArrayList<>();
	private final DeferredThread thread = new DeferredThread();
	private final WorldFilterPresetsPlugin plugin = new WorldFilterPresetsPlugin();

	private void setup() throws Exception
	{
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (object, method, args) ->
		{
			if (method.getName().equals("getGameState")) { return gameState; }
			if (method.getName().equals("getLocalPlayer")) { return player; }
			if (method.getName().equals("addChatMessage"))
			{
				assertEquals(GameState.LOGGED_IN, gameState);
				assertNotNull(player);
				messages.add((String) args[2]);
			}
			return null;
		});
		set("client", client); set("clientThread", thread); set("running", true);
	}

	private void set(String name, Object value) throws Exception
	{
		Field field = plugin.getClass().getDeclaredField(name); field.setAccessible(true); field.set(plugin, value);
	}
	private void message(String value, boolean red) throws Exception
	{
		Method method = plugin.getClass().getDeclaredMethod("message", String.class, boolean.class);
		method.setAccessible(true); method.invoke(plugin, value, red);
	}
	private void ready()
	{
		gameState = GameState.LOGGED_IN;
		player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (object, method, args) -> null);
	}

	@Test
	public void startupWarningWaitsForLoginAndPlayerAndDeliversOnce() throws Exception
	{
		setup(); message("Unable to load saved presets: duplicate names", false);
		thread.tick(); assertTrue(messages.isEmpty());
		GameStateChanged event = new GameStateChanged(); event.setGameState(GameState.LOGIN_SCREEN);
		plugin.onGameStateChanged(event);
		gameState = GameState.LOGIN_SCREEN;
		thread.tick(); assertTrue(messages.isEmpty());
		gameState = GameState.LOGGED_IN;
		thread.tick(); assertTrue(messages.isEmpty()); // Local player can still be null during login.
		ready(); thread.tick(); thread.tick();
		assertEquals(1, messages.size());
		assertEquals("World Filter Presets: Unable to load saved presets: duplicate names", messages.get(0));
	}

	@Test
	public void profileResetAndShutdownDiscardPendingMessages() throws Exception
	{
		setup(); message("Old profile", false);
		Method reset = plugin.getClass().getDeclaredMethod("reset"); reset.setAccessible(true); reset.invoke(plugin);
		ready(); thread.tick(); assertTrue(messages.isEmpty());
		message("After shutdown", false); set("running", false);
		thread.tick(); assertTrue(messages.isEmpty());
		assertTrue(thread.pending.isEmpty());
	}

	@Test
	public void redMessagesUseTheSameGuardAndEscapePresetText() throws Exception
	{
		setup(); message("Skipped importing '<name>' because it already exists", true);
		thread.tick(); assertTrue(messages.isEmpty());
		ready(); thread.tick();
		assertEquals("<col=ff0000>World Filter Presets: Skipped importing '<lt>name<gt>' because it already exists</col>", messages.get(0));
	}

	private static final class DeferredThread extends ClientThread
	{
		final List<BooleanSupplier> pending = new ArrayList<>();
		@Override public void invokeLater(BooleanSupplier action) { pending.add(action); }
		void tick() { pending.removeIf(BooleanSupplier::getAsBoolean); }
	}
}
