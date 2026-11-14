package com.kierenboal.worldfilterpresets;

import java.awt.Rectangle;
import java.lang.reflect.Proxy;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.FontTypeFace;
import net.runelite.api.ItemComposition;
import net.runelite.api.Point;
import net.runelite.api.ScriptEvent;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercise real widget construction/listeners against a small in-memory native widget surface. */
public class PresetDropdownTest
{
	private final Surface surface = new Surface();
	private final PresetRepository repository = new PresetRepository(new PresetCodec(), null, value -> { });
	private final List<Integer> bulk = new ArrayList<>();
	private final List<String> selected = new ArrayList<>();
	private final PresetDropdown dropdown = new PresetDropdown(surface.client, surface.frame.api, new PresetDropdown.Actions()
	{
		public void select(String id) { selected.add(id); }
		public void add() { }
		public void manage(String id, int op)
		{
			if (op == 8 || op == 9) { repository.move(id, op == 8 ? -1 : 1, false, false); }
			if (op == 10) { repository.toggleCog(id); }
		}
		public void bulk(int op) { bulk.add(op); }
	});

	private W header()
	{
		return surface.frame.live().stream().filter(w -> "Choose preset".equals(w.actions.get(0)) || "Close presets".equals(w.actions.get(0))).findFirst().get();
	}
	private void open()
	{
		dropdown.update(repository, false, false);
		header().op(1);
		assertTrue(dropdown.isOpen());
	}
	private W row(String name)
	{
		return surface.root.live().stream().filter(w -> name.equals(w.props.get("Name"))).findFirst().get();
	}

	@Test
	public void expandedHeaderRetainsManagementActionsAndClose()
	{
		repository.add("A", new int[11]);
		open();
		assertEquals("Close presets", header().actions.get(0));
		assertEquals("Import from clipboard", header().actions.get(1));
		assertEquals("Export all presets", header().actions.get(2));
		assertEquals("Delete all presets", header().actions.get(3));
		header().op(2);
		assertFalse(dropdown.isOpen());
		assertEquals(Collections.singletonList(2), bulk);
	}

	@Test
	public void builtInsAndCustomsRespectTenNativeActionSlots()
	{
		Preset preset = repository.add("A", new int[11]);
		open();
		W builtIn = row("In-game filter");
		assertNull(builtIn.actions.get(1)); // No Save/Rename/Icon/Export/Delete for built-ins.
		assertNull(builtIn.actions.get(6));
		assertNull(builtIn.actions.get(7)); // First visible row cannot move up.
		assertEquals("Move down", builtIn.actions.get(8));
		assertTrue(builtIn.actions.get(9).contains("On"));
		W custom = row("A");
		assertEquals("Delete", custom.actions.get(6));
		assertEquals("Move up", custom.actions.get(7));
		assertNull(custom.actions.get(8));
		assertTrue(custom.actions.keySet().stream().allMatch(i -> i >= 0 && i < 10));
		custom.op(10);
		assertFalse(repository.showInCogMenu(preset.id));
		assertTrue(dropdown.isOpen());
		builtIn.op(1);
		assertEquals(Collections.singletonList(PresetRepository.CLIENT), selected);
		assertFalse(dropdown.isOpen());
	}

	@Test
	public void keepsCompactHeaderAndPopupTheSameWidthAndRestoresNativeTitleOnRemoval()
	{
		open();
		assertEquals(210, header().api.getWidth());
		assertEquals(7, header().api.getOriginalX());
		assertEquals(7, header().api.getOriginalY());
		assertEquals(21, header().api.getHeight());
		assertEquals("World filters", surface.title.api.getText());
		assertEquals(WidgetTextAlignment.CENTER, surface.title.api.getXTextAlignment());
		assertEquals(header().api.getOriginalX() + header().api.getWidth() + 10, surface.title.api.getOriginalX());
		assertEquals(surface.frame.api.getWidth() - 36,
			surface.title.api.getOriginalX() + surface.title.api.getOriginalWidth());
		W popup = surface.root.live().stream().filter(w -> w.api.getType() == WidgetType.RECTANGLE).findFirst().get();
		assertEquals(header().api.getWidth(), popup.api.getWidth());
		assertTrue(popup.api.getNoClickThrough());
		assertTrue(surface.root.api.getBounds().contains(popup.api.getBounds()));
		assertTrue(row("No filters").api.getNoClickThrough());
		assertEquals(15, row("No filters").api.getHeight());
		dropdown.remove();
		assertEquals("Configure World Switcher", surface.title.api.getText());
		assertEquals(480, surface.title.api.getOriginalWidth());
		assertEquals(0, surface.title.api.getOriginalX());
		assertEquals(1, surface.frame.live().size());
		assertEquals(1, surface.root.live().size()); // The native frame remains.
	}

	@Test
	public void addNewHasAlignedTextAndFullRowHoverHighlight()
	{
		open();
		W add = surface.root.live().stream().filter(w -> "Add new preset".equals(w.actions.get(0))).findFirst().get();
		W label = surface.root.live().stream().filter(w -> "+ Add new".equals(w.api.getText())).findFirst().get();
		W normalLabel = surface.root.live().stream().filter(w -> "In-game filter".equals(w.api.getText())).findFirst().get();
		assertEquals(4, label.api.getOriginalX() - add.api.getOriginalX());
		assertEquals(normalLabel.api.getOriginalX(), label.api.getOriginalX());
		assertEquals(0x302b22, add.api.getTextColor());
		add.listener("OnMouseOverListener", 0, 0);
		assertEquals(0x625539, add.api.getTextColor());
		add.listener("OnMouseLeaveListener", 0, 0);
		assertEquals(0x302b22, add.api.getTextColor());
	}

	@Test
	public void scrollingHundredsOfPresetsDoesNotGrowWidgetArrayOrMoveAddRow()
	{
		List<Preset> incoming = new ArrayList<>();
		Preset seed = repository.add("Seed", new int[11]);
		for (int i = 0; i < 500; i++) { incoming.add(seed.withName("Preset " + i)); }
		repository.applyImport(repository.previewImport(incoming));
		open();
		int length = surface.root.children.length;
		int addY = surface.root.live().stream().filter(w -> "+ Add new".equals(w.api.getText())).findFirst().get().api.getOriginalY();
		for (int i = 0; i < 100; i++)
		{
			W target = surface.root.live().stream().filter(w -> "Load preset".equals(w.actions.get(0))).findFirst().get();
			target.wheel(1);
		}
		assertEquals(length, surface.root.children.length);
		assertEquals(addY, surface.root.live().stream().filter(w -> "+ Add new".equals(w.api.getText())).findFirst().get().api.getOriginalY());
		assertTrue(surface.root.live().size() < 40);
		dropdown.close();
		assertEquals(1, surface.root.live().size());
	}

	@Test
	public void resizesClosedHeaderAndRejectsCallbacksAfterRemoval()
	{
		open();
		W old = row("No filters");
		dropdown.close();
		surface.frame.api.setOriginalWidth(300);
		dropdown.tick();
		assertTrue(header().api.getWidth() < 210);
		dropdown.remove();
		old.op(1);
		assertTrue(selected.isEmpty());
	}

	@Test
	public void itemIconsUseInventorySpriteDimensionsAndSearchNonTradeables()
	{
		W icon = new W(null);
		PresetItemIcons.configure(icon.api, 4151, 32);
		assertEquals(WidgetType.GRAPHIC, icon.api.getType());
		assertEquals(36, icon.api.getWidth());
		assertEquals(32, icon.api.getHeight());
		assertEquals(4151, icon.api.getItemId());
		assertEquals(ItemQuantityMode.NEVER, icon.api.getItemQuantityMode());
		PresetItemIcons.configure(icon.api, 4151, 20);
		assertEquals(22, icon.api.getWidth());
		ItemComposition item = proxy(ItemComposition.class, (method, args) ->
		{
			if (method.equals("getName")) { return "Fire cape"; }
			if (method.equals("getNote") || method.equals("getPlaceholderTemplateId")) { return -1; }
			return null; // isGeTradeable defaults false.
		});
		assertTrue(PresetItemIcons.searchable(item, "cape"));
		ItemComposition placeholder = proxy(ItemComposition.class, (method, args) -> method.equals("getName") ? "Fire cape" : method.equals("getNote") ? -1 : null);
		assertFalse(PresetItemIcons.searchable(placeholder, "cape"));
	}

	private interface Call { Object invoke(String method, Object[] args); }
	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, Call call)
	{
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (object, method, args) ->
		{
			if (method.getName().equals("equals")) { return object == args[0]; }
			if (method.getName().equals("hashCode")) { return System.identityHashCode(object); }
			Object value = call.invoke(method.getName(), args == null ? new Object[0] : args);
			if (method.getReturnType() == void.class) { return null; }
			if (value != null) { return value; }
			if (method.getReturnType() == boolean.class) { return false; }
			if (method.getReturnType() == int.class) { return 0; }
			if (method.getReturnType() == long.class) { return 0L; }
			return null;
		});
	}

	private static final class Surface
	{
		final W root = new W(null);
		final W frame;
		final W title;
		final Client client;
		Surface()
		{
			root.api.setOriginalX(20).setOriginalY(30).setOriginalWidth(480).setOriginalHeight(300);
			frame = root.create(0, WidgetType.LAYER);
			frame.api.setOriginalWidth(480).setOriginalHeight(300);
			title = frame.create(0, WidgetType.TEXT);
			title.api.setText("Configure World Switcher").setOriginalWidth(480).setOriginalHeight(32);
			client = proxy(Client.class, (method, args) ->
			{
				if (method.equals("getWidget")) { return (int) args[0] == InterfaceID.WorldswitcherOptions.FRAME ? frame.api : root.api; }
				if (method.equals("getCanvasWidth")) { return 1000; }
				if (method.equals("getCanvasHeight")) { return 800; }
				if (method.equals("getItemCount")) { return 40000; }
				if (method.equals("getMouseCanvasPosition")) { return new Point(0, 0); }
				return null;
			});
		}
	}

	private static final class W
	{
		final Widget api;
		final W parent;
		final Map<String, Object> props = new HashMap<>();
		final Map<Integer, String> actions = new HashMap<>();
		Widget[] children = new Widget[0];
		final List<W> created = new ArrayList<>();
		int index;
		W(W parent)
		{
			this.parent = parent;
			api = proxy(Widget.class, this::call);
		}
		W create(int index, int type)
		{
			if (index < 0) { index = children.length; }
			if (index >= children.length) { children = Arrays.copyOf(children, index + 1); }
			W child = new W(this); child.index = index; child.props.put("Type", type);
			children[index] = child.api; created.add(child); return child;
		}
		List<W> live()
		{
			List<W> result = new ArrayList<>();
			for (W child : created) { if (child.index < children.length && children[child.index] == child.api) { result.add(child); } }
			return result;
		}
		void op(int op) { listener("OnOpListener", op, 0); }
		void wheel(int delta) { listener("OnScrollWheelListener", 0, delta); }
		void listener(String name, int op, int delta)
		{
			Object[] args = (Object[]) props.get(name);
			((JavaScriptCallback) args[0]).run(proxy(ScriptEvent.class, (method, values) -> method.equals("getOp") ? op : method.equals("getMouseY") ? delta : null));
		}
		private Object call(String method, Object[] args)
		{
			if (method.equals("createChild")) { return create((int) args[0], (int) args[1]).api; }
			if (method.equals("getChildren")) { return children; }
			if (method.equals("getDynamicChildren")) { return Arrays.stream(children).filter(Objects::nonNull).toArray(Widget[]::new); }
			if (method.equals("getChild")) { int i = (int) args[0]; return i < children.length ? children[i] : null; }
			if (method.equals("getIndex")) { return index; }
			if (method.equals("setAction")) { actions.put((int) args[0], (String) args[1]); return api; }
			if (method.startsWith("set") && args.length == 1) { props.put(method.substring(3), args[0]); return api; }
			if (method.equals("getWidth")) { return props.get("OriginalWidth"); }
			if (method.equals("getHeight")) { return props.get("OriginalHeight"); }
			if (method.equals("getText")) { return props.getOrDefault("Text", ""); }
			if (method.equals("getCanvasLocation"))
			{
				Point origin = parent == null ? new Point(0, 0) : parent.api.getCanvasLocation();
				return new Point(origin.getX() + (int) props.getOrDefault("OriginalX", 0), origin.getY() + (int) props.getOrDefault("OriginalY", 0));
			}
			if (method.equals("getBounds")) { Point p = api.getCanvasLocation(); return new Rectangle(p.getX(), p.getY(), api.getWidth(), api.getHeight()); }
			if (method.equals("contains")) { Point p = (Point) args[0]; return api.getBounds().contains(p.getX(), p.getY()); }
			if (method.equals("getFont")) { return proxy(FontTypeFace.class, (m, a) -> m.equals("getTextWidth") ? ((String) a[0]).length() * 6 : null); }
			if (method.startsWith("get")) { return props.get(method.substring(3)); }
			return null;
		}
	}
}
