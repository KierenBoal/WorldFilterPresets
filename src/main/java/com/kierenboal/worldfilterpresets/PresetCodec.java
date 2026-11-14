package com.kierenboal.worldfilterpresets;

import com.google.gson.*;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Version 1 is additive: retain unknown preset/filter fields when editing known fields. */
final class PresetCodec
{
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FORMAT = "world-filter-presets";
	private static final String[] MASK_NAMES = {"member", "country", "theme"};
	// Character positions 0..7 map to varcs 1157..1161, 212, 1162, 1163.
	// Position 5 deliberately uses legacy varc 212, NOT varc 1162.
	static final int SEARCH_LENGTH = 8;

	String export(List<Preset> presets) { return JSON.toJson(envelope(presets, false)); }

	boolean sameContent(Preset first, Preset second)
	{
		return encodePreset(first, false).equals(encodePreset(second, false));
	}

	String store(List<Preset> presets, String selected)
	{
		return store(presets, selected, java.util.Collections.emptyList(), java.util.Collections.emptyList());
	}

	String store(List<Preset> presets, String selected, List<String> order, List<String> hiddenFromCog)
	{
		JsonObject root = envelope(presets, true);
		if (!PresetRepository.CLIENT.equals(selected)) { root.addProperty("selected", selected); }
		if (!order.isEmpty()) { root.add("order", JSON.toJsonTree(order)); }
		if (!hiddenFromCog.isEmpty()) { root.add("hiddenFromCog", JSON.toJsonTree(hiddenFromCog)); }
		return JSON.toJson(root);
	}

	List<Preset> importPresets(String text)
	{
		List<Preset> result = decode(root(text), false);
		if (result.isEmpty()) { throw invalid("The clipboard contains no presets."); }
		return result;
	}

	Stored load(String text)
	{
		if (text == null || text.isBlank()) { return new Stored(new ArrayList<>(), PresetRepository.CLIENT, new ArrayList<>(), new ArrayList<>()); }
		JsonObject root = root(text);
		String selected = root.has("selected") ? string(root.get("selected"), "selected") : PresetRepository.CLIENT;
		return new Stored(decode(root, true), selected, strings(root, "order"), strings(root, "hiddenFromCog"));
	}

	private JsonObject envelope(List<Preset> presets, boolean internal)
	{
		JsonObject root = new JsonObject();
		root.addProperty("format", FORMAT);
		root.addProperty("version", 1);
		JsonArray array = new JsonArray();
		for (Preset preset : presets) { array.add(encodePreset(preset, internal)); }
		root.add("presets", array);
		return root;
	}

	private JsonObject encodePreset(Preset preset, boolean internal)
	{
		JsonObject object = preset.extra();
		object.remove("id");
		if (internal) { object.addProperty("id", preset.id); }
		object.addProperty("name", preset.name);
		object.remove("icon");
		if (preset.icon >= 0) { object.addProperty("icon", preset.icon); }
		object.remove("showInCogMenu");
		if (!preset.showInCogMenu) { object.addProperty("showInCogMenu", false); }
		JsonObject filters = preset.filterExtra();
		int[] values = preset.filters();
		for (int i = 0; i < MASK_NAMES.length; i++)
		{
			filters.remove(MASK_NAMES[i]);
			if (values[i] != 0) { filters.addProperty(MASK_NAMES[i], values[i]); }
		}
		// Normalize known positions; retain all future positions without discarding unknown data.
		JsonArray oldSearch = filters.has("search") ? filters.getAsJsonArray("search") : new JsonArray();
		int length = Math.max(SEARCH_LENGTH, oldSearch.size());
		if (length == SEARCH_LENGTH)
		{
			while (length > 0 && values[length + 2] == 0) { length--; }
		}
		filters.remove("search");
		if (length > 0)
		{
			JsonArray search = new JsonArray();
			for (int i = 0; i < length; i++)
			{
				if (i < SEARCH_LENGTH) { search.add(values[i + 3]); }
				else { search.add(oldSearch.get(i).deepCopy()); }
			}
			filters.add("search", search);
		}
		object.remove("filters");
		if (filters.size() > 0) { object.add("filters", filters); }
		return object;
	}

	private List<String> strings(JsonObject root, String field)
	{
		List<String> result = new ArrayList<>();
		if (!root.has(field)) { return result; }
		if (!root.get(field).isJsonArray()) { throw invalid(field + " must be an array."); }
		for (JsonElement value : root.getAsJsonArray(field))
		{
			String id = string(value, field);
			if (result.contains(id)) { throw invalid("Duplicate ID in " + field + "."); }
			result.add(id);
		}
		return result;
	}

	private JsonObject root(String text)
	{
		try
		{
			JsonReader reader = new JsonReader(new StringReader(text));
			reader.setLenient(false);
			JsonObject root = object(Streams.parse(reader), "document");
			if (reader.peek() != JsonToken.END_DOCUMENT) { throw invalid("Unexpected content after preset JSON."); }
			if (!FORMAT.equals(string(root.get("format"), "format"))) { throw invalid("This is not a World Filter Presets export."); }
			if (integer(root.get("version"), "version") != 1) { throw invalid("Unsupported preset format version."); }
			return root;
		}
		catch (JsonParseException | IOException | NullPointerException ex) { throw invalid("Invalid preset JSON."); }
	}

	private List<Preset> decode(JsonObject root, boolean internal)
	{
		if (!root.has("presets") || !root.get("presets").isJsonArray()) { throw invalid("presets must be an array."); }
		List<Preset> result = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (JsonElement element : root.getAsJsonArray("presets"))
		{
			JsonObject data = object(element, "preset").deepCopy();
			String name = string(data.remove("name"), "name").strip();
			if (name.isEmpty() || name.length() > 80 || name.chars().anyMatch(Character::isISOControl))
			{ throw invalid("Preset names must contain 1–80 printable characters."); }
			int icon = data.has("icon") ? integer(data.remove("icon"), "icon") : -1;
			boolean showInCog = true;
			if (data.has("showInCogMenu"))
			{
				JsonElement value = data.remove("showInCogMenu");
				if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) { throw invalid("showInCogMenu must be a boolean."); }
				showInCog = value.getAsBoolean();
			}
			JsonElement idValue = data.remove("id");
			String id = internal ? string(idValue, "id") : UUID.randomUUID().toString();
			if (internal)
			{
				try { UUID.fromString(id); } catch (IllegalArgumentException ex) { throw invalid("Invalid preset ID."); }
			}
			if (!ids.add(id)) { throw invalid("Duplicate preset ID."); }
			JsonObject filters = data.has("filters") ? object(data.remove("filters"), "filters").deepCopy() : new JsonObject();
			int[] values = new int[11];
			for (int i = 0; i < MASK_NAMES.length; i++)
			{
				String key = MASK_NAMES[i];
				if (filters.has(key)) { values[i] = integer(filters.remove(key), key); }
			}
			if (filters.has("search"))
			{
				if (!filters.get("search").isJsonArray()) { throw invalid("search must be an array."); }
				JsonArray search = filters.getAsJsonArray("search");
				for (int i = 0; i < search.size(); i++)
				{
					int value = integer(search.get(i), "search");
					if (i < SEARCH_LENGTH)
					{
						if (value > Character.MAX_VALUE) { throw invalid("Invalid search character."); }
						values[i + 3] = value;
					}
				}
			}
			result.add(new Preset(id, name, icon, showInCog, values, data, filters));
		}
		PresetNames.validate(result);
		return result;
	}

	private static JsonObject object(JsonElement value, String field)
	{
		if (value == null || !value.isJsonObject()) { throw invalid(field + " must be an object."); }
		return value.getAsJsonObject();
	}

	private static String string(JsonElement value, String field)
	{
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
		{ throw invalid(field + " must be text."); }
		return value.getAsString();
	}

	private static int integer(JsonElement value, String field)
	{
		try
		{
			if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) { throw new ArithmeticException(); }
			int number = new BigDecimal(value.getAsString()).intValueExact();
			if (number < 0) { throw new ArithmeticException(); }
			return number;
		}
		catch (ArithmeticException | NumberFormatException ex) { throw invalid(field + " must be a non-negative integer."); }
	}

	private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }

	static final class Stored
	{
		final List<Preset> presets;
		final String selected;
		final List<String> order;
		final List<String> hiddenFromCog;
		Stored(List<Preset> presets, String selected, List<String> order, List<String> hiddenFromCog)
		{
			this.presets = presets; this.selected = selected; this.order = order; this.hiddenFromCog = hiddenFromCog;
		}
	}
}
