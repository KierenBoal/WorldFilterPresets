package com.kierenboal.worldfilterpresets;

import java.util.Arrays;
import com.google.gson.JsonObject;

final class Preset
{
	final String id;
	final String name;
	final int icon;
	final boolean showInCogMenu;
	private final int[] filters;
	private final JsonObject extra;
	private final JsonObject filterExtra;

	Preset(String id, String name, int icon, int[] filters, JsonObject extra, JsonObject filterExtra)
	{
		this(id, name, icon, true, filters, extra, filterExtra);
	}

	Preset(String id, String name, int icon, boolean showInCogMenu, int[] filters, JsonObject extra, JsonObject filterExtra)
	{
		this.id = id;
		this.name = name;
		this.icon = icon;
		this.showInCogMenu = showInCogMenu;
		this.filters = filters.clone();
		this.extra = extra.deepCopy();
		this.filterExtra = filterExtra.deepCopy();
	}

	int[] filters() { return filters.clone(); }
	JsonObject extra() { return extra.deepCopy(); }
	JsonObject filterExtra() { return filterExtra.deepCopy(); }
	Preset withId(String value) { return new Preset(value, name, icon, showInCogMenu, filters, extra, filterExtra); }
	Preset withName(String value) { return new Preset(id, value, icon, showInCogMenu, filters, extra, filterExtra); }
	Preset withIcon(int value) { return new Preset(id, name, value, showInCogMenu, filters, extra, filterExtra); }
	Preset withFilters(int[] value) { return new Preset(id, name, icon, showInCogMenu, value, extra, filterExtra); }
	Preset withCogVisibility(boolean value) { return new Preset(id, name, icon, value, filters, extra, filterExtra); }

	boolean isDirty(int[] working)
	{
		return !Arrays.equals(filters, working);
	}
}
