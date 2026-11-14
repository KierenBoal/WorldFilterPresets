package com.kierenboal.worldfilterpresets;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.SpriteID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.*;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.client.util.Text;

/** All geometry is in game-canvas coordinates, so native UI stretching also scales hit targets. */
final class PresetDropdown
{
	interface Actions
	{
		void select(String id);
		void add();
		void manage(String id, int op);
		void bulk(int op);
	}

	private static final int TARGET_WIDTH = 210;
	private static final int ROW_HEIGHT = 16;
	private final PresetLabelMarquee marquee = new PresetLabelMarquee();
	private String selectedName = "";
	private final Client client;
	private final Widget frame;
	private final Widget root;
	private final Widget title;
	private final int titleX;
	private final int titleWidth;
	private final int titlePositionMode;
	private final int titleWidthMode;
	private final int titleAlignment;
	private final String titleText;
	private final Actions actions;
	private final List<Widget> header = new ArrayList<>();
	private final List<Widget> popup = new ArrayList<>();
	private final List<Row> rows = new ArrayList<>();
	private final List<Hover> hovers = new ArrayList<>();
	private Widget button;
	private Widget caption;
	private Widget arrow;
	private Widget buttonFace;
	private int buttonWidth;
	private int lastFrameWidth;
	private Widget selectedIcon;
	private PresetRepository repository;
	private boolean hideClient;
	private boolean hideNone;
	private volatile boolean open;
	private boolean removed;
	private int offset;
	private int visibleRows;
	private Rectangle popupBounds;
	private Rectangle lastAnchor;
	private Rectangle lastRoot;
	private long lastPress;

	PresetDropdown(Client client, Widget frame, Actions actions)
	{
		this.client = client;
		this.frame = frame;
		this.actions = actions;
		root = client.getWidget(InterfaceID.WorldswitcherOptions.UNIVERSE);
		title = Arrays.stream(frame.getDynamicChildren())
			.filter(w -> "Configure World Switcher".equals(Text.removeTags(w.getText())))
			.findFirst().orElse(null);
		titleX = title == null ? 0 : title.getOriginalX();
		titleWidth = title == null ? 0 : title.getOriginalWidth();
		titlePositionMode = title == null ? 0 : title.getXPositionMode();
		titleWidthMode = title == null ? 0 : title.getWidthMode();
		titleAlignment = title == null ? 0 : title.getXTextAlignment();
		titleText = title == null ? "" : title.getText();
		buttonWidth = TARGET_WIDTH;
		button = rectangle(frame, header, 7, 7, buttonWidth, 21, 0x73664e);
		buttonFace = rectangle(frame, header, 8, 8, buttonWidth - 2, 19, 0x3e382b);
		button.setName("World presets");
		button.setNoClickThrough(true);
		button.setHasListener(true);
		button.setOnOpListener((JavaScriptCallback) event ->
		{
			if (!alive()) { return; }
			int op = event.getOp();
			if (op == 1) { if (open) { close(); } else { expand(); } }
			else { close(); actions.bulk(op); }
		});
		button.setOnMouseOverListener((JavaScriptCallback) event -> buttonFace.setTextColor(0x514830));
		button.setOnMouseLeaveListener((JavaScriptCallback) event -> buttonFace.setTextColor(0x3e382b));
		selectedIcon = child(frame, header, WidgetType.GRAPHIC, 12, 8, 20, 18);
		caption = text(frame, header, 15, 8, buttonWidth - 34, 19, "");
		arrow = sprite(frame, header, 7 + buttonWidth - 21, 10, 16, 16, SpriteID.SCROLLBAR_ARROW_DOWN);
	}

	boolean isOpen() { return open; }
	boolean belongsTo(Widget current)
	{
		return current == frame && alive() && client.getWidget(InterfaceID.WorldswitcherOptions.UNIVERSE) == root;
	}
	private boolean alive()
	{
		return !removed && frame.getChild(button.getIndex()) == button;
	}

	void update(PresetRepository repository, boolean hideClient, boolean hideNone)
	{
		this.repository = repository;
		this.hideClient = hideClient;
		this.hideNone = hideNone;
		String name = repository.selectedName() + (repository.dirty() ? " *" : "");
		Preset selected = repository.find(repository.selected());
		int icon = selected == null || !validIcon(selected.icon) ? -1 : selected.icon;
		selectedIcon.setHidden(icon < 0);
		if (icon >= 0) { PresetItemIcons.configure(selectedIcon, icon, 18); }
		layoutHeader();
		caption.setOriginalX(icon >= 0 ? 38 : 15).setOriginalWidth(icon >= 0 ? buttonWidth - 59 : buttonWidth - 36).revalidate();
		selectedName = name;
		updateCaption();
		button.setName(Text.escapeJagex(name));
		button.setAction(0, open ? "Close presets" : "Choose preset");
		button.setAction(1, "Import from clipboard");
		button.setAction(2, repository.presets().isEmpty() ? null : "Export all presets");
		button.setAction(3, repository.presets().isEmpty() ? null : "Delete all presets");
		arrow.setSpriteId(open ? SpriteID.SCROLLBAR_ARROW_UP : SpriteID.SCROLLBAR_ARROW_DOWN);
		if (open) { buildRows(); drawPopup(); }
	}

	private void layoutHeader()
	{
		lastFrameWidth = frame.getWidth();
		int textWidth = title != null && title.getFont() != null ? title.getFont().getTextWidth("World filters") : 95;
		buttonWidth = PresetDropdownLayout.headerWidth(frame.getWidth(), textWidth);
		button.setOriginalWidth(buttonWidth).revalidate();
		buttonFace.setOriginalWidth(buttonWidth - 2).revalidate();
		arrow.setOriginalX(7 + buttonWidth - 21).revalidate();
		if (title != null)
		{
			title.setText("World filters").setXPositionMode(0).setWidthMode(0)
				.setOriginalX(7 + buttonWidth + 10).setOriginalWidth(Math.max(0, frame.getWidth() - 36 - (7 + buttonWidth + 10)))
				.setXTextAlignment(WidgetTextAlignment.CENTER).revalidate();
		}
	}

	private void buildRows()
	{
		rows.clear();
		for (String id : repository.visibleEntries(hideClient, hideNone))
		{
			Preset preset = repository.find(id);
			rows.add(new Row(id, repository.name(id), preset == null ? -1 : preset.icon, preset != null));
		}
	}

	private void expand()
	{
		if (root == null || root.isHidden() || repository == null) { return; }
		open = true;
		offset = 0;
		lastPress = client.getMouseLastPressedMillis();
		buildRows();
		for (int i = 0; i < rows.size(); i++)
		{
			if (rows.get(i).id.equals(repository.selected())) { offset = Math.max(0, i - 3); break; }
		}
		update(repository, hideClient, hideNone);
	}

	private void drawPopup()
	{
		deleteWidgets(root, popup);
		hovers.clear();
		Rectangle anchor = button.getBounds();
		Rectangle bounds = root.getBounds().intersection(new Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight()));
		lastAnchor = new Rectangle(anchor);
		lastRoot = new Rectangle(bounds);
		int below = bounds.y + bounds.height - (anchor.y + anchor.height);
		int above = anchor.y - bounds.y;
		boolean down = below >= ROW_HEIGHT * 3 || below >= above;
		int space = Math.max(ROW_HEIGHT * 2, down ? below : above);
		visibleRows = Math.max(1, Math.min(8, space / ROW_HEIGHT - 1));
		visibleRows = Math.min(visibleRows, Math.max(1, rows.size()));
		offset = Math.max(0, Math.min(offset, Math.max(0, rows.size() - visibleRows)));
		int width = Math.min(buttonWidth, bounds.width);
		int height = (visibleRows + 1) * ROW_HEIGHT;
		int x = Math.max(bounds.x, Math.min(anchor.x, bounds.x + bounds.width - width));
		int y = down ? anchor.y + anchor.height : anchor.y - height;
		y = Math.max(bounds.y, Math.min(y, bounds.y + bounds.height - height));
		popupBounds = new Rectangle(x, y, width, height);
		int localX = x - root.getCanvasLocation().getX();
		int localY = y - root.getCanvasLocation().getY();
		// Attach AFTER the root's native contents, rather than inside the frame behind the filters.
		Widget background = rectangle(root, popup, localX, localY, width, height, 0x73664e);
		background.setNoClickThrough(true);
		wheel(background);
		rectangle(root, popup, localX + 1, localY + 1, width - 2, height - 2, 0x302b22);
		boolean scroll = rows.size() > visibleRows;
		int rowWidth = width - (scroll ? 18 : 2);
		for (int i = 0; i < visibleRows && i + offset < rows.size(); i++)
		{
			Row row = rows.get(i + offset);
			int rowY = localY + i * ROW_HEIGHT;
			Widget target = rectangle(root, popup, localX + 1, rowY + 1, rowWidth - 1, ROW_HEIGHT - 1,
				row.id.equals(repository.selected()) ? 0x514830 : 0x302b22);
			target.setNoClickThrough(true);
			target.setName(Text.escapeJagex(row.name));
			target.setAction(0, "Load preset");
			if (row.custom)
			{
				String[] options = {"Save current filters", "Rename", "Change icon", "Remove icon", "Export", "Delete"};
				for (int j = 0; j < options.length; j++) { target.setAction(j + 1, options[j]); }
			}
			target.setAction(7, repository.canMove(row.id, -1, hideClient, hideNone) ? "Move up" : null);
			target.setAction(8, repository.canMove(row.id, 1, hideClient, hideNone) ? "Move down" : null);
			target.setAction(9, "Show in cog menu: " + (repository.showInCogMenu(row.id) ? "<col=00ff00>On</col>" : "Off"));
			target.setHasListener(true);
			int baseColor = row.id.equals(repository.selected()) ? 0x514830 : 0x302b22;
			target.setOnMouseOverListener((JavaScriptCallback) event -> target.setTextColor(0x625539));
			target.setOnMouseLeaveListener((JavaScriptCallback) event -> target.setTextColor(baseColor));
			target.setOnOpListener((JavaScriptCallback) event ->
			{
				if (!alive() || !open || !popup.contains(target)) { return; }
				if (event.getOp() >= 8) { actions.manage(row.id, event.getOp()); return; }
				close();
				if (event.getOp() == 1) { actions.select(row.id); }
				else { actions.manage(row.id, event.getOp()); }
			});
			wheel(target);
			boolean hasIcon = validIcon(row.icon);
			if (hasIcon) { PresetItemIcons.configure(child(root, popup, WidgetType.GRAPHIC, localX + 5, rowY + 2, 13, 12), row.icon, 12); }
			Widget label = text(root, popup, localX + (hasIcon ? 22 : 5), rowY, rowWidth - (hasIcon ? 26 : 9), ROW_HEIGHT, "");
			label.setText(ellipsize(label, row.name));
			hovers.add(new Hover(target, row.name));
		}
		int addY = localY + visibleRows * ROW_HEIGHT;
		Widget add = rectangle(root, popup, localX + 1, addY, width - 2, ROW_HEIGHT - 1, 0x302b22);
		add.setNoClickThrough(true);
		add.setOnMouseOverListener((JavaScriptCallback) event -> add.setTextColor(0x625539));
		add.setOnMouseLeaveListener((JavaScriptCallback) event -> add.setTextColor(0x302b22));
		add.setAction(0, "Add new preset");
		add.setHasListener(true);
		add.setOnOpListener((JavaScriptCallback) event -> { if (alive() && open) { close(); actions.add(); } });
		wheel(add);
		text(root, popup, localX + 5, addY, width - 10, ROW_HEIGHT, "+ Add new");
		if (scroll && visibleRows * ROW_HEIGHT >= 48)
		{
			int sx = localX + width - 17;
			Widget track = rectangle(root, popup, sx, localY, 16, visibleRows * ROW_HEIGHT, 0x14120f);
			wheel(track);
			int trackHeight = visibleRows * ROW_HEIGHT - 32;
			int thumbHeight = Math.max(12, trackHeight * visibleRows / rows.size());
			int thumbY = localY + 16 + (trackHeight - thumbHeight) * offset / (rows.size() - visibleRows);
			wheel(sprite(root, popup, sx, thumbY, 16, 5, SpriteID.SCROLLBAR_THUMB_TOP));
			Widget middle = sprite(root, popup, sx, thumbY + 5, 16, Math.max(1, thumbHeight - 10), SpriteID.SCROLLBAR_THUMB_MIDDLE);
			middle.setSpriteTiling(true);
			wheel(middle);
			wheel(sprite(root, popup, sx, thumbY + Math.max(5, thumbHeight - 5), 16, 5, SpriteID.SCROLLBAR_THUMB_BOTTOM));
			scrollButton(sx, localY, -1);
			scrollButton(sx, localY + visibleRows * ROW_HEIGHT - 16, 1);
		}
	}

	private void scrollButton(int x, int y, int delta)
	{
		Widget arrow = sprite(root, popup, x, y, 16, 16, delta < 0 ? SpriteID.SCROLLBAR_ARROW_UP : SpriteID.SCROLLBAR_ARROW_DOWN);
		arrow.setAction(0, delta < 0 ? "Scroll up" : "Scroll down");
		arrow.setHasListener(true);
		arrow.setOnOpListener((JavaScriptCallback) event -> scroll(delta));
		wheel(arrow);
	}

	private void wheel(Widget widget)
	{
		widget.setNoScrollThrough(true);
		widget.setHasListener(true);
		widget.setOnScrollWheelListener((JavaScriptCallback) event -> scroll(event.getMouseY()));
	}

	private void scroll(int delta)
	{
		if (!open || !alive()) { return; }
		int next = Math.max(0, Math.min(offset + delta, Math.max(0, rows.size() - visibleRows)));
		if (next != offset) { offset = next; drawPopup(); }
	}

	void tick()
	{
		if (!alive() || frame.isHidden() || client.getWidget(InterfaceID.WorldswitcherOptions.FRAME) != frame)
		{ close(); return; }
		if (frame.getWidth() != lastFrameWidth) { update(repository, hideClient, hideNone); }
		updateCaption();
		if (!open) { return; }
		long press = client.getMouseLastPressedMillis();
		if (press != lastPress)
		{
			lastPress = press;
			net.runelite.api.Point point = client.getMouseCanvasPosition();
			if (!client.isMenuOpen() && !popupBounds.contains(point.getX(), point.getY()) && !button.contains(point)) { close(); }
		}
		// Reanchor after a native resize, without rebuilding rows on every client tick.
		Rectangle anchor = button.getBounds();
		Rectangle bounds = root.getBounds().intersection(new Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight()));
		if (!anchor.equals(lastAnchor) || !bounds.equals(lastRoot))
		{ update(repository, hideClient, hideNone); }
	}

	private boolean validIcon(int id) { return id >= 0 && id < client.getItemCount(); }

	private void updateCaption()
	{
		if (caption.getFont() == null) { caption.setText(Text.escapeJagex(selectedName)); return; }
		caption.setText(marquee.text(selectedName, caption.getWidth(), caption.getFont()::getTextWidth,
			System.nanoTime() / 1_000_000L));
	}

	void tooltip(TooltipManager manager)
	{
		if (!alive() || client.isMenuOpen()) { return; }
		net.runelite.api.Point point = client.getMouseCanvasPosition();
		if (!open && button.contains(point)) { manager.add(new Tooltip(Text.escapeJagex(repository.selectedName()))); }
		else if (open)
		{
			for (Hover hover : hovers)
			{
				if (hover.widget.contains(point)) { manager.add(new Tooltip(Text.escapeJagex(hover.name))); break; }
			}
		}
	}

	void close()
	{
		open = false;
		deleteWidgets(root, popup);
		hovers.clear();
		if (!removed && repository != null && alive()) { update(repository, hideClient, hideNone); }
	}

	void remove()
	{
		close();
		removed = true;
		if (title != null && frame.getChild(title.getIndex()) == title)
		{
			title.setText(titleText).setOriginalX(titleX).setOriginalWidth(titleWidth)
				.setXPositionMode(titlePositionMode).setWidthMode(titleWidthMode).setXTextAlignment(titleAlignment).revalidate();
		}
		deleteWidgets(frame, header);
	}

	private static Widget child(Widget parent, List<Widget> owned, int type, int x, int y, int width, int height)
	{
		// Reuse holes left by a previous popup instead of growing the parent's array on every scroll.
		Widget[] children = parent.getChildren();
		int index = 0;
		while (children != null && index < children.length && children[index] != null) { index++; }
		Widget widget = parent.createChild(index, type);
		widget.setOriginalX(x).setOriginalY(y).setOriginalWidth(width).setOriginalHeight(height).revalidate();
		owned.add(widget);
		return widget;
	}

	private static Widget rectangle(Widget parent, List<Widget> owned, int x, int y, int width, int height, int color)
	{
		return child(parent, owned, WidgetType.RECTANGLE, x, y, width, height).setFilled(true).setTextColor(color);
	}

	private static Widget text(Widget parent, List<Widget> owned, int x, int y, int width, int height, String value)
	{
		return child(parent, owned, WidgetType.TEXT, x, y, width, height).setFontId(FontID.PLAIN_11)
			.setTextColor(0xffdf70).setTextShadowed(true).setYTextAlignment(WidgetTextAlignment.CENTER).setText(value);
	}

	private static Widget sprite(Widget parent, List<Widget> owned, int x, int y, int width, int height, int spriteId)
	{
		return child(parent, owned, WidgetType.GRAPHIC, x, y, width, height).setSpriteId(spriteId);
	}

	private static String ellipsize(Widget widget, String value)
	{
		return widget.getFont() == null ? Text.escapeJagex(value)
			: PresetLabelMarquee.ellipsize(value, widget.getWidth(), widget.getFont()::getTextWidth);
	}

	private static void deleteWidgets(Widget parent, List<Widget> owned)
	{
		Widget[] children = parent == null ? null : parent.getChildren();
		if (children != null)
		{
			for (Widget widget : owned)
			{
				if (widget.getIndex() < children.length && children[widget.getIndex()] == widget) { children[widget.getIndex()] = null; }
			}
		}
		owned.clear();
	}

	private static final class Row
	{
		final String id;
		final String name;
		final int icon;
		final boolean custom;
		Row(String id, String name, int icon, boolean custom) { this.id = id; this.name = name; this.icon = icon; this.custom = custom; }
	}
	private static final class Hover
	{
		final Widget widget;
		final String name;
		Hover(Widget widget, String name) { this.widget = widget; this.name = name; }
	}
}
