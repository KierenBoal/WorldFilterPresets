package com.kierenboal.worldfilterpresets;

import net.runelite.api.ItemComposition;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

/** Native inventory sprites are 36x32. Never use MODEL widgets for item icons. */
final class PresetItemIcons
{
	static void configure(Widget widget, int id, int height)
	{
		widget.setType(WidgetType.GRAPHIC);
		widget.setOriginalWidth(36 * height / 32).setOriginalHeight(height);
		widget.setItemId(id).setItemQuantity(1).setItemQuantityMode(ItemQuantityMode.NEVER);
		widget.setBorderType(1);
		widget.revalidate();
	}

	static boolean searchable(ItemComposition item, String query)
	{
		String name = item.getName();
		return name != null && !name.isBlank() && !"null".equalsIgnoreCase(name)
			&& item.getNote() == -1 && item.getPlaceholderTemplateId() == -1
			&& name.toLowerCase(java.util.Locale.ROOT).contains(query);
	}
}
