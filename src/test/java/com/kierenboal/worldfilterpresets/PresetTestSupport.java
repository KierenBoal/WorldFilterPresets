package com.kierenboal.worldfilterpresets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

final class PresetTestSupport
{
	private static final Gson GSON = new GsonBuilder().create();

	private PresetTestSupport() { }

	static PresetCodec codec() { return new PresetCodec(GSON); }
}
