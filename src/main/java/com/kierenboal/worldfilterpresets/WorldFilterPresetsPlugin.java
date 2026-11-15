package com.kierenboal.worldfilterpresets;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import com.google.gson.Gson;
import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.KeyListener;
import java.awt.event.KeyEvent;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.Menu;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

@Slf4j
@PluginDescriptor(name = "World Filter Presets")
public class WorldFilterPresetsPlugin extends Plugin
{
	private static final String GROUP = "worldfilterpresets";
	private static final int[] MASKS = {14967, 14968, 14969};
	// The sixth character uses legacy varc 212 in the native search scripts.
	private static final int[] SEARCH = {1157, 1158, 1159, 1160, 1161, 212, 1162, 1163};
	private static final int[] ALL = new int[11];
	private static final int REBUILD = 892;
	private static final int REFRESH = 891;
	private static final int CHECKBOX = 7292;
	private static final int COMMIT_SEARCH = 7330;
	private static final int SELECTOR = 7288;
	private static final int FILTER_BUTTON = 7283;
	private static final int WORLD_SWITCHER_FILTERS_INIT_BUTTON = 7279;
	private static final int WORLD_SWITCHER_FILTER_SEARCH_REFRESH = 7280;
	private static final int WORLD_SWITCHER_LIST_SEARCH_REFRESH = 7272;
	// Cache signatures for scripts whose arguments or filter semantics this plugin relies on.
	private static final int[][] SCRIPT_SIGNATURES = {
		{REBUILD, 0xec9da9f3}, {REFRESH, 0x1929244a}, {CHECKBOX, 0xd97607c5},
		{COMMIT_SEARCH, 0x150f610e}, {SELECTOR, 0xeb371911}, {FILTER_BUTTON, 0x93e47447},
		{7282, 0x2d339af9}, {7287, 0x5ea87359},
		{WORLD_SWITCHER_FILTERS_INIT_BUTTON, 0x3ac93dce},
		{WORLD_SWITCHER_FILTER_SEARCH_REFRESH, 0xb858bfe8},
		{WORLD_SWITCHER_LIST_SEARCH_REFRESH, 0xb887ad54}
	};

	@Inject private Client client;
	@Inject private Gson gson;
	@Inject private ClientThread clientThread;
	@Inject private ConfigManager config;
	@Inject private ChatboxPanelManager chatbox;
	@Inject private ItemManager items;

	@Inject private WorldFilterPresetsConfig settings;
	@Inject private KeyManager keyManager;
	@Inject private TooltipManager tooltipManager;
	private PresetCodec codec;
	private final PresetClipboard clipboard = new PresetClipboard();
	private final PresetPrompts prompts = new PresetPrompts();
	private final List<Runnable> nextTickActions = new ArrayList<>();
	private PresetRepository repository;
	private volatile PresetDropdown dropdown;
	private final KeyListener escapeListener = new KeyListener()
	{
		public void keyTyped(KeyEvent event) { }
		public void keyReleased(KeyEvent event) { }
		public void keyPressed(KeyEvent event)
		{
			PresetDropdown current = dropdown;
			if (event.getKeyCode() == KeyEvent.VK_ESCAPE && current != null && current.isOpen())
			{
				event.consume();
				dispatch(session, () -> { if (dropdown == current) { current.close(); } });
			}
		}
	};
	private volatile boolean running;
	private boolean supported;
	private boolean refreshQueued;
	private boolean previewingInGameFilters;
	private long previewTicket;
	private int selectionRequest;
	private volatile int session;
	// Unlike prompt sessions, startup notifications survive the initial LOGIN_SCREEN transition.
	private volatile int messageSession;
	private int scopedScript = -1;
	private int[] originalMasks;
	private final Map<Integer, Object> originalSearch = new HashMap<>();

	@Provides
	WorldFilterPresetsConfig provideConfig(ConfigManager manager)
	{
		return manager.getConfig(WorldFilterPresetsConfig.class);
	}

	@Override
	protected void startUp()
	{
		codec = new PresetCodec(gson);
		running = true;
		keyManager.registerKeyListener(escapeListener);
		clientThread.invokeLater(() ->
		{
			if (!running)
			{
				return true;
			}
			if (client.getIndexScripts() == null)
			{
				return false;
			}
			for (int[] signature : SCRIPT_SIGNATURES)
			{
				byte[] script = client.getIndexScripts().loadData(signature[0], 0);
				if (script == null)
				{
					return false;
				}
				if (Arrays.hashCode(script) != signature[1])
				{
					log.warn("Unsupported world switcher script {}", signature[0]);
					return true;
				}
			}
			supported = true;
			restoreSelection();
			refresh();
			return true;
		});
	}

	@Override
	protected void shutDown()
	{
		running = false;
		keyManager.unregisterKeyListener(escapeListener);
		clientThread.invoke(() ->
		{
			restoreScope();
			reset();
			removeHeader();
			refresh();
			supported = false;
		});
	}

	private void reset()
	{
		previewingInGameFilters = false;
		nextTickActions.clear();
		messageSession++;
		prompts.invalidate();
		removeHeader();
		session++;
	}

	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		clientThread.invokeLater(() ->
		{
			reset();
			restoreSelection();
			refresh();
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			previewingInGameFilters = false;
			nextTickActions.clear();
			prompts.invalidate();
			session++;
			removeHeader();
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			queueRefresh();
		}
	}

	private void restoreSelection()
	{
		try
		{
			repository = new PresetRepository(codec, config.getConfiguration(GROUP, "presets"),
				value -> config.setConfiguration(GROUP, "presets", value));
		}
		catch (IllegalArgumentException ex)
		{
			// Keep the damaged document available for recovery; do not overwrite it on load.
			log.warn("Unable to read world presets", ex);
			repository = new PresetRepository(codec, null, value -> config.setConfiguration(GROUP, "presets", value));
			message("Unable to load saved presets: " + ex.getMessage());
		}
	}

	private int[] effectiveFilters()
	{
		int[] working = repository.working();
		return working == null ? readFilters() : working;
	}

	private void save(String id)
	{
		Preset preset = repository.find(id);
		if (preset != null) { repository.update(preset.withFilters(effectiveFilters())); updateHeader(); }
	}

	private void select(String id)
	{
		client.playSoundEffect(2266);
		if (prompts.pending()) { return; }
		int request = ++selectionRequest;
		if (repository.selected().equals(id)) { return; }
		PresetRepository current = repository;
		leave(() -> nextTick(() ->
		{
			if (selectionRequest == request && repository == current
				&& (PresetRepository.CLIENT.equals(id) || PresetRepository.NONE.equals(id) || repository.find(id) != null))
			{ repository.select(id); refresh(); }
		}));
	}

	private void nextTick(Runnable action) { nextTickActions.add(action); }

	@Subscribe
	public void onGameTick(GameTick event)
	{
		List<Runnable> actions = new ArrayList<>(nextTickActions);
		nextTickActions.clear();
		if (running && supported) { actions.forEach(Runnable::run); }
		if (running && supported && dropdown != null) { dropdown.gameTick(); }
	}

	private void leave(Runnable change)
	{
		if (prompts.pending()) { return; }
		if (!repository.dirty()) { change.run(); return; }
		String previous = repository.selected();
		long ticket = prompts.begin();
		chatbox.openTextMenuInput(escaped(repository.selectedName()) + " has unsaved changes.")
			.option("Save", () -> finish(ticket, () -> PresetDecision.SAVE.apply(() -> save(previous), change)))
			.option("Discard", () -> finish(ticket, () -> PresetDecision.DISCARD.apply(() -> save(previous), change)))
			.option("Cancel", () -> finish(ticket, () -> { }))
			.onClose(() -> prompts.close(ticket)).build();
	}

	private void confirm(String title, boolean secondStage, Runnable yes)
	{
		long ticket = prompts.begin();
		var input = chatbox.openTextMenuInput(title);
		for (String option : PresetPrompts.confirmationOptions(secondStage))
		{
			input.option(option, () -> finish(ticket, "Yes".equals(option) ? yes : () -> { }));
		}
		input.onClose(() -> prompts.close(ticket)).build();
	}

	private void confirmUpdateFromInGame(String id)
	{
		restoreScope();
		long ticket = prompts.begin();
		previewTicket = ticket;
		previewingInGameFilters = true;
		refresh();
		int generation = session;
		chatbox.openTextMenuInput("Update this preset with the current in-game settings?")
			.option("Yes", () -> finish(ticket, () ->
			{
				updateFromInGame(id);
				endInGamePreview(ticket);
			}))
			.option("No", () -> finish(ticket, () -> endInGamePreview(ticket)))
			.onClose(() -> { prompts.close(ticket); dispatch(generation, () -> endInGamePreview(ticket)); }).build();
	}

	private void updateFromInGame(String id)
	{
		Preset preset = repository.find(id);
		if (preset == null) { return; }
		int[] filters = readFilters();
		repository.update(preset.withFilters(filters));
		if (id.equals(repository.selected())) { repository.edit(filters); }
	}

	private void endInGamePreview(long ticket)
	{
		if (previewingInGameFilters && previewTicket == ticket)
		{
			previewingInGameFilters = false;
			refresh();
		}
	}

	private void nameInput(String title, String initial, String exceptId, java.util.function.Consumer<String> done)
	{
		long ticket = prompts.begin();
		chatbox.openTextInput(title).value(initial)
			.onDone((java.util.function.Predicate<String>) value ->
			{
				if (value.strip().length() > 80) { message("Names can contain up to 80 characters."); return false; }
				finish(ticket, () ->
				{
					String name = PresetRepository.normalizeName(value, initial);
					try { repository.validateName(name, exceptId); done.accept(name); }
					catch (IllegalArgumentException ex)
					{
						message(ex.getMessage());
						nameInput(title, name, exceptId, done);
					}
				});
				return true;
			}).onClose(() -> prompts.close(ticket)).build();
	}

	private void finish(long ticket, Runnable action)
	{
		clientThread.invokeLater(() ->
		{
			if (running && prompts.complete(ticket)) { action.run(); }
		});
	}

	private void dispatch(int generation, Runnable action)
	{
		clientThread.invokeLater(() -> { if (running && generation == session) { action.run(); } });
	}

	private static String escaped(String value) { return Text.escapeJagex(value); }
	private void message(String value)
	{
		message(value, false);
	}

	private void message(String value, boolean red)
	{
		int generation = messageSession;
		String text = "World Filter Presets: " + escaped(value);
		String formatted = red ? "<col=ff0000>" + text + "</col>" : text;
		clientThread.invokeLater(() ->
		{
			if (!running || generation != messageSession) { return true; }
			// addChatMessage synchronously notifies other plugins, some of which require a player.
			if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) { return false; }
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", formatted, null);
			return true;
		});
	}

	private int[] readFilters()
	{
		int[] values = new int[11];
		for (int i = 0; i < MASKS.length; i++)
		{
			values[i] = client.getVarbitValue(MASKS[i]);
		}
		for (int i = 0; i < SEARCH.length; i++)
		{
			values[i + MASKS.length] = Math.max(0, client.getVarcIntValue(SEARCH[i]));
		}
		return values;
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (!running || !supported || previewingInGameFilters || scopedScript != -1)
		{
			return;
		}
		int id = event.getScriptId();
		int[] values = null;
		if (id == REBUILD)
		{
			values = repository.working();
		}
		else if (repository.working() != null && (id == CHECKBOX || id == COMMIT_SEARCH || id == SELECTOR
			|| id == FILTER_BUTTON || id == WORLD_SWITCHER_FILTERS_INIT_BUTTON
			|| id == WORLD_SWITCHER_FILTER_SEARCH_REFRESH || id == WORLD_SWITCHER_LIST_SEARCH_REFRESH))
		{
			values = repository.working();
		}
		if (values != null)
		{
			scopedScript = id;
			originalMasks = Arrays.copyOf(readFilters(), MASKS.length);
			for (int i = 0; i < MASKS.length; i++)
			{
				client.setVarbit(MASKS[i], values[i]);
			}
			Map<Integer, Object> varcs = client.getVarcMap();
			for (int i = 0; i < SEARCH.length; i++)
			{
				originalSearch.put(SEARCH[i], varcs.put(SEARCH[i], values[i + MASKS.length]));
			}
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		int id = event.getScriptId();
		if (id == scopedScript)
		{
			boolean edited = id == CHECKBOX || id == COMMIT_SEARCH;
			if (edited)
			{
				repository.edit(readFilters());
			}
			restoreScope();
			if (edited)
			{
				queueRefresh();
			}
		}
		if (id == 227 || id == 747)
		{
			queueHeader();
		}
	}

	@Subscribe
	public void onPostClientTick(PostClientTick event)
	{
		if (scopedScript != -1) { restoreScope(); }
		if (dropdown != null)
		{
			Widget frame = client.getWidget(InterfaceID.WorldswitcherOptions.FRAME);
			if (frame == null || frame.isHidden() || !dropdown.belongsTo(frame))
			{
				removeHeader();
				if (frame != null && !frame.isHidden()) { updateHeader(); }
			}
			else { dropdown.tick(); }
		}
	}

	private void restoreScope()
	{
		if (scopedScript == -1)
		{
			return;
		}
		for (int i = 0; i < MASKS.length; i++)
		{
			client.setVarbit(MASKS[i], originalMasks[i]);
		}
		Map<Integer, Object> varcs = client.getVarcMap();
		originalSearch.forEach((key, value) ->
		{
			if (value == null)
			{
				varcs.remove(key);
			}
			else
			{
				varcs.put(key, value);
			}
		});
		originalSearch.clear();
		scopedScript = -1;
	}

	private void queueHeader()
	{
		if (running && supported)
		{
			clientThread.invokeLater(this::updateHeader);
		}
	}

	private void queueRefresh()
	{
		if (!refreshQueued)
		{
			refreshQueued = true;
			clientThread.invokeLater(() ->
			{
				refreshQueued = false;
				if (running)
				{
					refresh();
				}
			});
		}
	}

	private void refresh()
	{
		replayRefresh(InterfaceID.Worldswitcher.UNIVERSE, REFRESH, 21);
		replayRefresh(InterfaceID.WorldswitcherOptions.UNIVERSE, REFRESH, 21);
		if (running && supported)
		{
			for (int id = InterfaceID.WorldswitcherOptions.FILTER_MEMBER;
				id <= InterfaceID.WorldswitcherOptions.RESET; id++)
			{
				replayRefresh(id, 7282, 8);
			}
			updateHeader();
		}
	}

	private void replayRefresh(int widgetId, int script, int length)
	{
		Widget widget = client.getWidget(widgetId);
		Object[] args = widget == null ? null : widget.getOnVarTransmitListener();
		if (listener(args, script, length))
		{
			runListener(widget, args, 0);
		}
	}

	private static boolean listener(Object[] args, int script, int length)
	{
		return args != null && args.length == length && Integer.valueOf(script).equals(args[0]);
	}

	private void runListener(Widget widget, Object[] args, int op)
	{
		try
		{
			// Replayed listeners must work under RuneLite's default packet restrictions.
			client.createScriptEventBuilder(args).setSource(widget).setOp(op).build().run();
		}
		finally
		{
			restoreScope();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!running || !supported || previewingInGameFilters || repository.working() == null || event.getId() != 1
			|| event.getMenuAction() != MenuAction.CC_OP)
		{
			return;
		}
		Widget widget = event.getWidget();
		if (widget == null)
		{
			return;
		}
		int id = widget.getId();
		Object[] args = widget.getOnOpListener();
		if (id == InterfaceID.WorldswitcherFilter.OPTIONS && listener(args, CHECKBOX, 10))
		{
			event.consume();
			clientThread.invokeLater(() -> runListener(widget, args, 1));
		}
		else if (id == InterfaceID.WorldswitcherOptions.RESET && listener(args, 489, 3))
		{
			event.consume();
			repository.edit(ALL);
			queueRefresh();
		}
		else if (id == InterfaceID.WorldswitcherFilter.CLEAR && listener(args, 489, 3))
		{
			event.consume();
			int category = selectorCategory();
			if (category >= 1 && category <= MASKS.length)
			{
				int[] working = repository.working();
				working[category - 1] = 0;
				repository.edit(working);
				clientThread.invokeLater(() ->
				{
					replayRefresh(InterfaceID.WorldswitcherFilter.UNIVERSE, 7287, 7);
					refresh();
				});
			}
		}
	}

	private int selectorCategory()
	{
		Widget options = client.getWidget(InterfaceID.WorldswitcherFilter.OPTIONS);
		if (options != null)
		{
			for (Widget row : options.getDynamicChildren())
			{
				Object[] listener = row.getOnOpListener();
				if (listener(listener, CHECKBOX, 10) && listener[2] instanceof Integer)
				{
					// The native checkbox script maps enum IDs 4995–4997 to the three filter masks.
					return (Integer) listener[2] - 4994;
				}
			}
		}
		return -1;
	}

	@Subscribe
	public void onBeforeRender(BeforeRender event)
	{
		if (dropdown != null) { dropdown.tooltip(tooltipManager); }
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (GROUP.equals(event.getGroup()) && !"presets".equals(event.getKey()))
		{
			clientThread.invokeLater(() -> { if (dropdown != null) { dropdown.close(); updateHeader(); } });
		}
	}

	private void updateHeader()
	{
		if (!running || !supported || repository == null) { return; }
		Widget frame = client.getWidget(InterfaceID.WorldswitcherOptions.FRAME);
		if (frame == null || frame.isHidden()) { removeHeader(); return; }
		if (dropdown != null && !dropdown.belongsTo(frame)) { removeHeader(); }
		if (dropdown == null)
		{
			dropdown = new PresetDropdown(client, frame, new PresetDropdown.Actions()
			{
				public void select(String id) { WorldFilterPresetsPlugin.this.select(id); }
				public void add() { addPreset(); }
				public void manage(String id, int op) { managePreset(id, op); }
				public void bulk(int op) { bulkAction(op); }
				public void nextTick(Runnable action) { WorldFilterPresetsPlugin.this.nextTick(action); }
			});
		}
		dropdown.update(repository, settings.hideInGameFilter(), settings.hideNoFilters());
	}

	private void removeHeader()
	{
		if (dropdown != null) { dropdown.remove(); dropdown = null; }
	}

	private void addPreset()
	{
		if (prompts.pending()) { return; }
		int[] snapshot = effectiveFilters();
		nameInput("New preset name (up to 80 characters)", repository.defaultName(), null, name ->
			leave(() -> { repository.add(name, snapshot); refresh(); }));
	}

	private void managePreset(String id, int op)
	{
		if (prompts.pending()) { return; }
		if (op == 8 || op == 9)
		{
			repository.move(id, op == 8 ? -1 : 1, settings.hideInGameFilter(), settings.hideNoFilters());
			updateHeader();
			return;
		}
		if (op == 10) { repository.toggleCog(id); updateHeader(); return; }
		Preset preset = repository.find(id);
		if (preset == null) { return; }
		switch (op)
		{
			case 2: confirmUpdateFromInGame(id); break;
			case 3:
				nameInput("Preset name (up to 80 characters)", preset.name, id, value ->
				{ Preset current = repository.find(id); if (current != null) { repository.update(current.withName(value)); updateHeader(); } });
				break;
			case 4: useIcon(id); break;
			case 5: repository.update(preset.withIcon(-1)); updateHeader(); break;
			case 6: exportPresets(Collections.singletonList(preset)); break;
			case 7:
				confirm("Delete preset \"" + escaped(preset.name) + "\"?", false, () -> { repository.delete(id); refresh(); });
				break;
			default: break;
		}
	}

	private void bulkAction(int op)
	{
		if (prompts.pending()) { return; }
		if (op == 2) { importPresets(); }
		else if (!repository.presets().isEmpty())
		{
			if (op == 3) { exportPresets(repository.presets()); }
			else if (op == 4)
			{
				int count = repository.presets().size();
				confirm("Delete all " + count + " presets?", false, () ->
					confirm("Are you sure? " + count + " presets will be deleted.", true,
						() -> { repository.deleteAll(); refresh(); }));
			}
		}
	}

	private void exportPresets(List<Preset> presets)
	{
		try { clipboard.write(codec.export(presets)); }
		catch (RuntimeException ex) { message("Unable to access the clipboard. Please try again."); return; }
		String text = presets.size() == 1 ? "Copied '" + escaped(presets.get(0).name) + "' to clipboard"
			: "Copied " + presets.size() + " presets to clipboard";
		long ticket = prompts.begin();
		chatbox.openTextMenuInput(text)
			.option("Click to continue", () -> finish(ticket, () -> { }))
			.onClose(() -> prompts.close(ticket)).build();
	}

	private void importPresets()
	{
		try
		{
			List<Preset> imported = codec.importPresets(clipboard.read());
			PresetRepository.ImportPlan plan = repository.previewImport(imported);
			if (!plan.hasChanges()) { reportSkipped(plan); return; }
			String heading = imported.size() == 1 ? "You are about to import: " + escaped(imported.get(0).name)
				: "You are about to import " + imported.size() + " presets";
			StringBuilder summary = new StringBuilder(heading).append("<br>Add: ").append(plan.additions.size())
				.append("; overwrite: ").append(plan.overwrites.size()).append("; skip: ").append(plan.skipped.size());
			if (!plan.overwrites.isEmpty())
			{
				summary.append("<br>Overwrite: ");
				for (int i = 0; i < plan.overwrites.size(); i++)
				{
					if (i > 0) { summary.append(", "); }
					summary.append(escaped(plan.overwrites.get(i).name));
				}
			}
			if (plan.overwrites(repository.selected()) && repository.dirty())
			{ summary.append("<br><col=ff0000>Your selected preset's unsaved edits will be lost.</col>"); }
			confirm(summary.toString(), false, () ->
			{
				try
				{
					repository.applyImport(plan);
					refresh();
					message("Imported " + (plan.additions.size() + plan.overwrites.size()) + " preset(s).");
					reportSkipped(plan);
				}
				catch (IllegalArgumentException ex) { message("Import failed: " + ex.getMessage()); }
			});
		}
		catch (IllegalArgumentException ex) { message("Import failed: " + ex.getMessage()); }
		catch (Exception ex) { message("Unable to read text from the clipboard. Please try again."); }
	}

	private void reportSkipped(PresetRepository.ImportPlan plan)
	{
		if (plan.skipped.isEmpty()) { return; }
		String text = plan.total == 1
			? "Skipped importing '" + plan.skipped.get(0).name + "' because it already exists"
			: "Skipped importing '" + plan.skipped.size() + "' presets that already exist";
		message(text, true);
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!running || !supported || repository == null || settings.hideCogPresetMenu()) { return; }
		MenuEntry[] entries = event.getMenuEntries();
		for (int i = 0; i < entries.length; i++)
		{
			if (entries[i].getParam1() == InterfaceID.Worldswitcher.OPTIONS && "Configure".equals(entries[i].getOption()))
			{
				addCogEntries(client.getMenu(), i, repository.cogEntries(), 0);
				break;
			}
		}
	}

	private void addCogEntries(Menu menu, int index, List<String> ids, int start)
	{
		// Native menus are finite: overflow into submenus instead of imposing a preset limit.
		int end = Math.min(ids.size(), start + 200);
		int generation = session;
		for (int i = start; i < end; i++)
		{
			String id = ids.get(i);
			String target = escaped(repository.name(id));
			if (id.equals(repository.selected())) { target = "<col=ffff00>" + target + "*</col>"; }
			menu.createMenuEntry(index).setOption("Use").setTarget(target)
				.setType(MenuAction.RUNELITE).onClick(entry ->
				{
					if (running && generation == session)
					{ if (dropdown != null) { dropdown.close(); } select(id); }
				});
		}
		if (end < ids.size())
		{
			Menu next = menu.createMenuEntry(index).setOption("More presets").setType(MenuAction.RUNELITE).createSubMenu();
			addCogEntries(next, 0, ids, end);
		}
	}

	private void useIcon(String id)
	{
		PresetDropdown currentDropdown = dropdown;
		if (currentDropdown != null) { currentDropdown.holdOpen(); }
		long ticket = prompts.begin();
		new PresetItemSearch(chatbox, clientThread, client, items, icon -> finish(ticket, () ->
		{
			Preset current = repository.find(id);
			if (current != null) { repository.update(current.withIcon(icon)); updateHeader(); }
		})).onClose(() ->
		{
			prompts.close(ticket);
			if (currentDropdown != null) { currentDropdown.releaseOpen(); }
		}).build();
	}
}
