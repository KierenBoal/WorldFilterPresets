package com.kierenboal.worldfilterpresets;

import java.util.Locale;
import java.util.function.IntConsumer;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.ItemComposition;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.game.chatbox.ChatboxTextInput;
import net.runelite.client.util.Text;

final class PresetItemSearch extends ChatboxTextInput
{
	private final ChatboxPanelManager chatbox;
	private final Client client;
	private final ItemManager items;
	private final IntConsumer selected;

	PresetItemSearch(ChatboxPanelManager chatbox, ClientThread thread, Client client, ItemManager items, IntConsumer selected)
	{
		super(chatbox, thread);
		this.chatbox = chatbox;
		this.client = client;
		this.items = items;
		this.selected = selected;
		prompt("Search item icons");
	}

	@Override
	protected void update()
	{
		Widget container = chatbox.getContainerWidget();
		container.deleteAllChildren();
		Widget heading = container.createChild(-1, WidgetType.TEXT);
		heading.setOriginalX(8).setOriginalY(3).setOriginalWidth(container.getWidth() - 16).setOriginalHeight(15);
		heading.setText("Search item icons").setFontId(FontID.PLAIN_11).setTextColor(0x800000)
			.setXTextAlignment(WidgetTextAlignment.CENTER).revalidate();
		buildEdit(8, 18, container.getWidth() - 16, 16);
		String query = getValue().strip().toLowerCase(Locale.ROOT);
		if (query.isEmpty())
		{
			return;
		}
		int columns = Math.max(1, (container.getWidth() - 12) / 38);
		int limit = columns * Math.max(1, (container.getHeight() - 35) / 34);
		int count = 0;
		for (int id = 0; id < client.getItemCount() && count < limit; id++)
		{
			ItemComposition item = items.getItemComposition(id);
			if (!PresetItemIcons.searchable(item, query))
			{
				continue;
			}
			Widget icon = container.createChild(-1, WidgetType.GRAPHIC);
			icon.setOriginalX(6 + count % columns * 38).setOriginalY(35 + count / columns * 34)
				.setOriginalWidth(36).setOriginalHeight(32);
			PresetItemIcons.configure(icon, id, 32);
			icon.setName(Text.escapeJagex(item.getName()));
			icon.setAction(0, "Use icon");
			icon.setHasListener(true);
			int itemId = id;
			icon.setOnOpListener((JavaScriptCallback) event ->
			{
				chatbox.close();
				selected.accept(itemId);
			});
			icon.revalidate();
			count++;
		}
	}
}
