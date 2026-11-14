package com.kierenboal.worldfilterpresets;

import com.google.gson.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PresetCodecTest
{
	private final PresetCodec codec = new PresetCodec();
	private String wrap(String presets)
	{
		return "{\"format\":\"world-filter-presets\",\"version\":1,\"presets\":" + presets + "}";
	}
	private JsonObject json(String text) { return new JsonParser().parse(text).getAsJsonObject(); }

	@Test
	public void roundTripsSingleAndBulkWithoutInternalState()
	{
		PresetRepository state = new PresetRepository(codec, null, ignored -> { });
		int[] values = {3, 4, 7, 65, 66, 67, 68, 69, 70, 71, 72};
		Preset first = state.add("A: <B>", values).withIcon(4151);
		Preset second = state.add("Second", new int[11]);
		String export = codec.export(Arrays.asList(first, second));
		assertFalse(export.contains("\"selected\""));
		assertFalse(export.contains("\"id\""));
		List<Preset> restored = codec.importPresets(export);
		assertArrayEquals(values, restored.get(0).filters());
		assertEquals(4151, restored.get(0).icon);
		assertEquals("A: <B>", restored.get(0).name);
		assertEquals(-1, restored.get(1).icon);
		assertEquals(1, codec.importPresets(codec.export(Collections.singletonList(first))).size());
		assertFalse(json(export).getAsJsonArray("presets").get(1).getAsJsonObject().has("icon"));
	}

	@Test
	public void defaultsMissingFiltersAndSearchCharactersToZero()
	{
		assertArrayEquals(new int[11], codec.importPresets(wrap("[{\"name\":\"Empty\"}]")).get(0).filters());
		int[] expected = new int[11];
		expected[1] = 4; expected[3] = 65;
		assertArrayEquals(expected, codec.importPresets(wrap("[{\"name\":\"Partial\",\"filters\":{\"country\":4,\"search\":[65]}}]")).get(0).filters());
	}

	@Test
	public void retainsFutureFieldsThroughEditsStorageAndExport()
	{
		Preset original = codec.importPresets(wrap("[{\"name\":\"Future\",\"future\":{\"enabled\":true},\"filters\":{\"population\":[1,2],\"search\":[0,0,0,0,0,0,0,0,123]}}]")).get(0);
		int[] values = original.filters(); values[2] = 4;
		Preset changed = original.withName("Renamed").withIcon(42).withFilters(values);
		Preset loaded = codec.load(codec.store(Collections.singletonList(changed), changed.id)).presets.get(0);
		JsonObject exported = json(codec.export(Collections.singletonList(loaded))).getAsJsonArray("presets").get(0).getAsJsonObject();
		assertTrue(exported.getAsJsonObject("future").get("enabled").getAsBoolean());
		assertEquals(2, exported.getAsJsonObject("filters").getAsJsonArray("population").size());
		assertEquals(123, exported.getAsJsonObject("filters").getAsJsonArray("search").get(8).getAsInt());
		assertEquals(4, loaded.filters()[2]);
		assertEquals("Renamed", loaded.name);
	}

	@Test
	public void rejectsMalformedPayloadsAndWrongTypes()
	{
		for (String value : Arrays.asList("", "garbage", "{}", "null", "[]", wrap("[]"), wrap("null"),
			wrap("[null]"), wrap("[{\"name\":3}]"), wrap("[{\"name\":\" \"}]"), wrap("[{\"name\":\"X\",\"icon\":-1}]"),
			wrap("[{\"name\":\"X\",\"icon\":1.5}]"), wrap("[{\"name\":\"X\",\"filters\":null}]"),
			wrap("[{\"name\":\"X\",\"filters\":{\"member\":\"1\"}}]"), wrap("[{\"name\":\"X\",\"filters\":{\"country\":true}}]"),
			wrap("[{\"name\":\"X\",\"filters\":{\"theme\":2147483648}}]"), wrap("[{\"name\":\"X\",\"filters\":{\"search\":{}}}]"),
			wrap("[{\"name\":\"X\",\"filters\":{\"search\":[65536]}}]"), wrap("[{\"name\":\"X\",\"filters\":{\"search\":[null]}}]"),
			wrap("[{\"name\":\"" + "x".repeat(81) + "\"}]"), wrap("[{\"name\":\"line\\nfeed\"}]"),
			wrap("[{\"name\":\"Valid\"},{}]"), wrap("[{\"name\":\"X\"}]") + " trailing",
			"{format:'world-filter-presets',version:1,presets:[{name:'X'}]}"))
		{
			try { codec.importPresets(value); fail("Accepted invalid payload: " + value); }
			catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
		}
	}

	@Test
	public void omitsDefaultsAndTrimsSearchWithoutLosingInteriorZeros()
	{
		PresetRepository state = new PresetRepository(codec, null, ignored -> { });
		Preset preset = state.add("Minimal", new int[11]);
		JsonObject minimal = json(codec.export(Collections.singletonList(preset))).getAsJsonArray("presets").get(0).getAsJsonObject();
		assertEquals(1, minimal.size());
		assertEquals("Minimal", minimal.get("name").getAsString());
		int[] filters = new int[11]; filters[1] = 4; filters[5] = 65;
		JsonObject changed = json(codec.export(Collections.singletonList(preset.withFilters(filters).withCogVisibility(false))))
			.getAsJsonArray("presets").get(0).getAsJsonObject();
		assertFalse(changed.get("showInCogMenu").getAsBoolean());
		assertEquals(2, changed.getAsJsonObject("filters").size());
		assertEquals("[0,0,65]", changed.getAsJsonObject("filters").get("search").toString());
		assertArrayEquals(filters, codec.importPresets(wrap("[" + changed + "]")).get(0).filters());
	}

	@Test
	public void comparesDecodedContentRatherThanJsonFormattingOrExplicitDefaults()
	{
		Preset minimal = codec.importPresets(wrap("[{\"name\":\"Same\"}]")).get(0);
		Preset explicit = codec.importPresets(wrap("[{\"filters\":{\"country\":0,\"member\":0,\"theme\":0,\"search\":[0,0,0]},\"showInCogMenu\":true,\"name\":\"Same\"}]")).get(0);
		assertTrue(codec.sameContent(minimal, explicit));
		assertFalse(codec.sameContent(minimal, explicit.withCogVisibility(false)));
		assertFalse(codec.sameContent(minimal, explicit.withName("SAME")));
		assertFalse(codec.sameContent(minimal, explicit.withIcon(42)));
		Preset future = codec.importPresets(wrap("[{\"name\":\"Same\",\"future\":true}]")).get(0);
		assertFalse(codec.sameContent(minimal, future));
	}

	@Test
	public void rejectsRepeatedReservedNamesAndInvalidVisibility()
	{
		for (String content : Arrays.asList("[{\"name\":\"A\"},{\"name\":\" a \"}]", "[{\"name\":\"IN-GAME FILTER\"}]",
			"[{\"name\":\"No filters\"}]", "[{\"name\":\"A\",\"showInCogMenu\":1}]", "[{\"name\":\"A\",\"showInCogMenu\":null}]"))
		{
			try { codec.importPresets(wrap(content)); fail(content); }
			catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
		}
	}

	@Test
	public void rejectsUnsupportedVersionAndWrongFormat()
	{
		for (String value : Arrays.asList(wrap("[{\"name\":\"X\"}]").replace("\"version\":1", "\"version\":2"),
			wrap("[{\"name\":\"X\"}]").replace("world-filter-presets", "other-plugin")))
		{
			try { codec.importPresets(value); fail(); }
			catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
		}
	}
}
