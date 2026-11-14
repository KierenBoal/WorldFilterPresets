package com.kierenboal.worldfilterpresets;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Profile-scoped saved state, with independent working filters and one order for all entries. */
final class PresetRepository
{
	static final String CLIENT = "client";
	static final String NONE = "none";
	static final String CUSTOM = "custom";
	private final PresetCodec codec;
	private final Consumer<String> persist;
	private final List<Preset> presets;
	private final List<String> order = new ArrayList<>();
	private final List<String> hiddenFromCog = new ArrayList<>();
	private String selected;
	private int[] working;
	private long revision;

	PresetRepository(PresetCodec codec, String document, Consumer<String> persist)
	{
		this.codec = codec;
		this.persist = persist;
		PresetCodec.Stored stored = codec.load(document);
		presets = new ArrayList<>(stored.presets);
		for (String id : stored.order)
		{
			if (isEntry(id)) { order.add(id); }
		}
		if (!order.contains(CLIENT)) { order.add(CLIENT); }
		if (!order.contains(NONE)) { order.add(NONE); }
		for (Preset preset : presets) { if (!order.contains(preset.id)) { order.add(preset.id); } }
		for (String id : stored.hiddenFromCog)
		{
			if (!CLIENT.equals(id) && !NONE.equals(id)) { throw new IllegalArgumentException("Invalid built-in cog preference."); }
			hiddenFromCog.add(id);
		}
		sortPresets();
		selectInternal(stored.selected);
	}

	List<Preset> presets() { return Collections.unmodifiableList(presets); }
	String selected() { return selected; }
	int[] working() { return working == null ? null : working.clone(); }
	Preset find(String id) { return presets.stream().filter(p -> p.id.equals(id)).findFirst().orElse(null); }
	private boolean isEntry(String id) { return CLIENT.equals(id) || NONE.equals(id) || find(id) != null; }
	String name(String id)
	{
		Preset preset = find(id);
		return preset != null ? preset.name : NONE.equals(id) ? "No filters" : CUSTOM.equals(id) ? "Custom" : "In-game filter";
	}
	String selectedName() { return name(selected); }
	boolean dirty() { Preset preset = find(selected); return preset != null && preset.isDirty(working); }

	List<String> visibleEntries(boolean hideClient, boolean hideNone)
	{
		List<String> result = new ArrayList<>(order);
		result.removeIf(id -> hideClient && CLIENT.equals(id) || hideNone && NONE.equals(id));
		return result;
	}

	List<String> cogEntries()
	{
		List<String> result = new ArrayList<>(order);
		result.removeIf(id -> !showInCogMenu(id));
		return result;
	}

	boolean showInCogMenu(String id)
	{
		Preset preset = find(id);
		return preset != null ? preset.showInCogMenu : isEntry(id) && !hiddenFromCog.contains(id);
	}

	void toggleCog(String id)
	{
		Preset preset = find(id);
		if (preset != null) { update(preset.withCogVisibility(!preset.showInCogMenu)); }
		else if (isEntry(id))
		{
			if (!hiddenFromCog.remove(id)) { hiddenFromCog.add(id); }
			save();
		}
	}

	boolean canMove(String id, int direction, boolean hideClient, boolean hideNone)
	{
		List<String> visible = visibleEntries(hideClient, hideNone);
		int index = visible.indexOf(id);
		return index >= 0 && index + direction >= 0 && index + direction < visible.size();
	}

	void move(String id, int direction, boolean hideClient, boolean hideNone)
	{
		if (!canMove(id, direction, hideClient, hideNone)) { return; }
		List<String> visible = visibleEntries(hideClient, hideNone);
		String neighbor = visible.get(visible.indexOf(id) + direction);
		Collections.swap(order, order.indexOf(id), order.indexOf(neighbor));
		sortPresets();
		save();
	}

	private void sortPresets() { presets.sort(Comparator.comparingInt(p -> order.indexOf(p.id))); }

	String defaultName()
	{
		int number = presets.size() + 1;
		while (hasName("Preset " + number, null)) { number++; }
		return "Preset " + number;
	}

	void validateName(String name, String exceptId)
	{
		PresetNames.validateName(name);
		if (hasName(name, exceptId)) { throw new IllegalArgumentException("A preset named '" + name + "' already exists."); }
	}

	private boolean hasName(String name, String exceptId)
	{
		return presets.stream().anyMatch(p -> !p.id.equals(exceptId) && PresetNames.key(p.name).equals(PresetNames.key(name)));
	}

	void select(String id) { selectInternal(id); save(); }
	private void selectInternal(String id)
	{
		Preset preset = find(id);
		selected = preset != null || NONE.equals(id) ? id : CLIENT;
		working = preset != null ? preset.filters() : NONE.equals(selected) ? new int[11] : null;
	}

	void edit(int[] values)
	{
		if (working == null || Arrays.equals(working, values)) { return; }
		working = values.clone();
		revision++;
		if (NONE.equals(selected) && !Arrays.equals(working, new int[11])) { selected = CUSTOM; }
	}

	Preset add(String name, int[] filters)
	{
		String label = normalizeName(name, defaultName());
		validateName(label, null);
		Preset preset = new Preset(UUID.randomUUID().toString(), label, -1, filters, new JsonObject(), new JsonObject());
		presets.add(preset);
		order.add(preset.id);
		selectInternal(preset.id);
		save();
		return preset;
	}

	void update(Preset preset)
	{
		for (int i = 0; i < presets.size(); i++)
		{
			if (presets.get(i).id.equals(preset.id))
			{
				validateName(preset.name, preset.id);
				presets.set(i, preset.withName(preset.name.strip()));
				save();
				return;
			}
		}
	}

	ImportPlan previewImport(List<Preset> imported)
	{
		PresetNames.validate(presets);
		PresetNames.validate(imported);
		ImportPlan plan = new ImportPlan(revision, imported.size());
		for (Preset incoming : imported)
		{
			Preset existing = presets.stream().filter(p -> PresetNames.key(p.name).equals(PresetNames.key(incoming.name))).findFirst().orElse(null);
			if (existing == null) { plan.additions.add(incoming.withId(UUID.randomUUID().toString())); }
			else if (codec.sameContent(existing, incoming)) { plan.skipped.add(incoming); }
			else { plan.overwrites.add(incoming.withId(existing.id)); }
		}
		return plan;
	}

	void applyImport(ImportPlan plan)
	{
		if (revision != plan.revision) { throw new IllegalArgumentException("Presets changed while confirming. Please import again."); }
		if (!plan.hasChanges()) { return; }
		for (Preset replacement : plan.overwrites)
		{
			for (int i = 0; i < presets.size(); i++)
			{
				if (presets.get(i).id.equals(replacement.id)) { presets.set(i, replacement); break; }
			}
			if (selected.equals(replacement.id)) { working = replacement.filters(); }
		}
		for (Preset addition : plan.additions) { presets.add(addition); order.add(addition.id); }
		sortPresets();
		save();
	}

	void delete(String id)
	{
		if (presets.removeIf(p -> p.id.equals(id)))
		{
			order.remove(id);
			if (selected.equals(id)) { selectInternal(CLIENT); }
			save();
		}
	}

	void deleteAll()
	{
		boolean activeCustom = find(selected) != null;
		presets.clear();
		order.removeIf(id -> !CLIENT.equals(id) && !NONE.equals(id));
		if (activeCustom) { selectInternal(CLIENT); }
		save();
	}

	private void save()
	{
		revision++;
		persist.accept(codec.store(presets, CUSTOM.equals(selected) ? CLIENT : selected, order, hiddenFromCog));
	}

	static String normalizeName(String value, String fallback)
	{
		String name = value.strip();
		return name.isEmpty() ? fallback : name.substring(0, Math.min(80, name.length()));
	}

	static final class ImportPlan
	{
		private final long revision;
		final int total;
		final List<Preset> additions = new ArrayList<>();
		final List<Preset> overwrites = new ArrayList<>();
		final List<Preset> skipped = new ArrayList<>();
		ImportPlan(long revision, int total) { this.revision = revision; this.total = total; }
		boolean hasChanges() { return !additions.isEmpty() || !overwrites.isEmpty(); }
		boolean overwrites(String id) { return overwrites.stream().anyMatch(p -> p.id.equals(id)); }
	}
}
