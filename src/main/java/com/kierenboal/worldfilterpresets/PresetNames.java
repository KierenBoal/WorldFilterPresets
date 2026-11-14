package com.kierenboal.worldfilterpresets;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class PresetNames
{
	static String key(String name) { return name.strip().toLowerCase(Locale.ROOT); }

	static void validate(List<Preset> presets)
	{
		Set<String> names = new HashSet<>();
		for (Preset preset : presets)
		{
			validateName(preset.name);
			if (!names.add(key(preset.name))) { throw new IllegalArgumentException("More than one preset is named '" + preset.name + "'. Names must be unique."); }
		}
	}

	static void validateName(String name)
	{
		if (name.isBlank() || name.strip().length() > 80 || name.chars().anyMatch(Character::isISOControl))
		{ throw new IllegalArgumentException("Names must contain 1–80 printable characters."); }
		if (key(name).equals(key("In-game filter")) || key(name).equals(key("No filters")))
		{ throw new IllegalArgumentException("That name belongs to a built-in filter."); }
	}
}
