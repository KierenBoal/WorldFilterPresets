package com.kierenboal.worldfilterpresets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PresetRepositoryTest
{
	private final PresetCodec codec = PresetTestSupport.codec();
	private final List<String> writes = new ArrayList<>();
	private PresetRepository state() { return new PresetRepository(codec, null, writes::add); }

	@Test
	public void newProfileDelegatesToClientAndProtectsBuiltIns()
	{
		PresetRepository state = state();
		assertEquals(PresetRepository.CLIENT, state.selected());
		assertNull(state.working());
		assertEquals(0, writes.size());
		state.edit(new int[11]);
		assertNull(state.working());
		state.delete(PresetRepository.CLIENT);
		state.delete(PresetRepository.NONE);
		assertEquals(0, writes.size());
		assertTrue(state.presets().isEmpty());
		state.select(PresetRepository.NONE);
		assertArrayEquals(new int[11], state.working());
		state.deleteAll();
		assertEquals(PresetRepository.NONE, state.selected());
	}

	@Test
	public void builtInEditsBecomeUnsavedCustomWithoutModifyingDefaults()
	{
		PresetRepository state = state();
		state.select(PresetRepository.NONE);
		int[] values = new int[11]; values[1] = 4;
		int count = writes.size();
		state.edit(values);
		assertEquals(PresetRepository.CUSTOM, state.selected());
		assertEquals("Custom", state.selectedName());
		assertEquals(count, writes.size());
		state.select(PresetRepository.NONE);
		assertArrayEquals(new int[11], state.working());
	}

	@Test
	public void maintainsStableSelectionAcrossDeletionAndReload()
	{
		PresetRepository state = state();
		Preset first = state.add("First", new int[11]);
		Preset second = state.add("Second", new int[11]);
		state.delete(first.id);
		assertEquals(second.id, state.selected());
		PresetRepository loaded = new PresetRepository(codec, writes.get(writes.size() - 1), writes::add);
		assertEquals(second.id, loaded.selected());
		assertEquals("Second", loaded.selectedName());
		loaded.delete(second.id);
		assertEquals(PresetRepository.CLIENT, loaded.selected());
		assertNull(loaded.working());
	}

	@Test
	public void defaultsNamesUsingCustomCountAndAvoidsCollisions()
	{
		PresetRepository state = state();
		assertEquals("Preset 1", state.defaultName());
		Preset first = state.add("   ", new int[11]);
		assertEquals("Preset 1", first.name);
		assertEquals("Preset 2", state.defaultName());
		state.add("Preset 2", new int[11]);
		assertEquals(2, state.presets().size());
		state.delete(first.id);
		assertEquals("Preset 3", state.defaultName());
		assertEquals(80, PresetRepository.normalizeName("x".repeat(81), "Fallback").length());
	}

	@Test
	public void keepsSavedAndWorkingArraysIndependent()
	{
		PresetRepository state = state();
		int[] filters = new int[11];
		Preset preset = state.add("Saved", filters);
		filters[0] = 3;
		assertFalse(state.dirty());
		int[] working = state.working(); working[1] = 4;
		assertFalse(state.dirty());
		state.edit(working);
		assertTrue(state.dirty());
		assertEquals(0, preset.filters()[1]);
		state.update(preset.withFilters(state.working()));
		assertFalse(state.dirty());
		state.edit(new int[11]);
		assertTrue(state.dirty());
		state.select(preset.id);
		assertFalse(state.dirty());
		assertEquals(4, state.working()[1]);
	}

	@Test
	public void importsAppendWithFreshIdsAndPreserveSelectionAndEditsInOneWrite()
	{
		PresetRepository state = state();
		Preset preset = state.add("Duplicate", new int[11]);
		int[] changed = new int[11]; changed[2] = 7;
		state.edit(changed);
		int count = writes.size();
		Preset incoming = preset.withName("New one");
		Preset incoming2 = preset.withName("New two");
		state.applyImport(state.previewImport(java.util.Arrays.asList(incoming, incoming2)));
		assertEquals(count + 1, writes.size());
		assertEquals(preset.id, state.selected());
		assertArrayEquals(changed, state.working());
		assertTrue(state.dirty());
		assertEquals(3, state.presets().size());
		assertNotEquals(preset.id, state.presets().get(1).id);
		assertNotEquals(state.presets().get(1).id, state.presets().get(2).id);
		PresetRepository loaded = new PresetRepository(codec, writes.get(writes.size() - 1), writes::add);
		assertEquals(preset.id, loaded.selected());
		assertFalse(loaded.dirty());
	}

	@Test
	public void deletesAllCustomPresetsInOneWriteAndReturnsActiveCustomToClient()
	{
		PresetRepository state = state();
		state.add("One", new int[11]); state.add("Two", new int[11]);
		int count = writes.size();
		state.deleteAll();
		assertEquals(count + 1, writes.size());
		assertTrue(state.presets().isEmpty());
		assertEquals(PresetRepository.CLIENT, state.selected());
		assertNull(state.working());
	}

	@Test
	public void hasNoFiveSlotLimitAndFallsBackFromMissingSelection()
	{
		PresetRepository state = state();
		Preset preset = state.add("Many", new int[11]);
		List<Preset> incoming = new ArrayList<>();
		for (int i = 0; i < 500; i++) { incoming.add(preset.withName("Imported " + i)); }
		state.applyImport(state.previewImport(incoming));
		assertEquals(501, state.presets().size());
		state.select("missing");
		assertEquals(PresetRepository.CLIENT, state.selected());
		assertNull(state.working());
	}

	@Test
	public void enforcesUniqueNamesAndReservedNamesWithoutMutatingState()
	{
		PresetRepository state = state();
		Preset first = state.add("Aussie worlds", new int[11]);
		Preset other = state.add("Other", new int[11]);
		for (String name : new String[]{" AUSSIE WORLDS ", "In-game filter", "NO FILTERS"})
		{
			int count = writes.size();
			try { state.add(name, new int[11]); fail(name); }
			catch (IllegalArgumentException expected) { assertEquals(2, state.presets().size()); }
			try { state.update(other.withName(name)); fail(name); }
			catch (IllegalArgumentException expected) { assertEquals("Other", state.find(other.id).name); }
			assertEquals(count, writes.size());
		}
		state.update(first.withName("AUSSIE WORLDS"));
		assertEquals("AUSSIE WORLDS", state.find(first.id).name);
	}

	@Test
	public void persistsSharedOrderAndIndependentCogVisibilityForBuiltInsAndCustoms()
	{
		PresetRepository state = state();
		Preset a = state.add("A", new int[11]);
		Preset b = state.add("B", new int[11]);
		state.move(a.id, -1, false, false);
		state.move(PresetRepository.CLIENT, 1, false, false);
		assertEquals(java.util.Arrays.asList(a.id, PresetRepository.CLIENT, PresetRepository.NONE, b.id), state.visibleEntries(false, false));
		state.toggleCog(PresetRepository.NONE);
		state.toggleCog(a.id);
		assertEquals(java.util.Arrays.asList(PresetRepository.CLIENT, b.id), state.cogEntries());
		assertEquals(java.util.Arrays.asList(a.id, b.id), state.visibleEntries(true, true));
		PresetRepository loaded = new PresetRepository(codec, writes.get(writes.size() - 1), writes::add);
		assertEquals(state.visibleEntries(false, false), loaded.visibleEntries(false, false));
		assertEquals(state.cogEntries(), loaded.cogEntries());
		assertEquals("In-game filter", loaded.name(PresetRepository.CLIENT));
	}

	@Test
	public void movementSkipsHiddenEntriesAndKeepsTheirPositions()
	{
		PresetRepository state = state();
		Preset a = state.add("A", new int[11]);
		state.move(a.id, -1, false, true);
		assertEquals(java.util.Arrays.asList(a.id, PresetRepository.NONE, PresetRepository.CLIENT), state.visibleEntries(false, false));
		assertFalse(state.canMove(a.id, -1, false, true));
		assertFalse(state.canMove(PresetRepository.CLIENT, 1, false, true));
		assertFalse(state.canMove(PresetRepository.NONE, 1, false, true));
	}

	@Test
	public void importsPreviewSkipsIdenticalAndOverwritesChangedNamesKeepingPositionAndSelection()
	{
		PresetRepository state = state();
		Preset a = state.add("A", new int[11]);
		Preset b = state.add("B", new int[11]);
		state.move(b.id, -1, false, false);
		state.select(a.id);
		int[] unsaved = new int[11]; unsaved[0] = 1; state.edit(unsaved);
		int[] replacement = new int[11]; replacement[1] = 4;
		Preset incoming = a.withName("a").withFilters(replacement).withCogVisibility(false);
		List<String> order = state.visibleEntries(false, false);
		int count = writes.size();
		PresetRepository.ImportPlan preview = state.previewImport(java.util.Arrays.asList(b, incoming, a.withName("New")));
		assertEquals(1, preview.additions.size());
		assertEquals(1, preview.overwrites.size());
		assertEquals(1, preview.skipped.size());
		assertEquals(count, writes.size()); // Merely previewing (or cancelling) changes nothing.
		assertArrayEquals(unsaved, state.working());
		assertTrue(preview.overwrites(a.id));
		state.applyImport(preview);
		assertEquals(count + 1, writes.size());
		assertEquals(a.id, state.selected());
		assertEquals("a", state.find(a.id).name);
		assertFalse(state.showInCogMenu(a.id));
		assertArrayEquals(replacement, state.working());
		assertFalse(state.dirty());
		assertEquals(order, state.visibleEntries(false, false).subList(0, order.size()));
	}

	@Test
	public void allIdenticalImportsDoNotWriteAndStalePlansCannotApply()
	{
		PresetRepository state = state();
		Preset a = state.add("A", new int[11]);
		int count = writes.size();
		PresetRepository.ImportPlan skip = state.previewImport(Collections.singletonList(a));
		assertFalse(skip.hasChanges());
		state.applyImport(skip);
		assertEquals(count, writes.size());
		PresetRepository.ImportPlan plan = state.previewImport(Collections.singletonList(a.withIcon(42)));
		int[] working = new int[11]; working[2] = 4; state.edit(working);
		try { state.applyImport(plan); fail(); }
		catch (IllegalArgumentException expected) { assertEquals(-1, state.find(a.id).icon); }
	}

	@Test
	public void exportUsesRelativeCustomOrderWithoutBuiltInsOrPositions()
	{
		PresetRepository state = state();
		Preset a = state.add("A", new int[11]);
		Preset b = state.add("B", new int[11]);
		state.move(b.id, -1, false, false);
		List<Preset> bulk = codec.importPresets(codec.export(state.presets()));
		assertEquals("B", bulk.get(0).name);
		assertEquals("A", bulk.get(1).name);
		String single = codec.export(Collections.singletonList(a));
		assertFalse(single.contains("order"));
		PresetRepository target = new PresetRepository(codec, null, value -> { });
		target.applyImport(target.previewImport(codec.importPresets(single)));
		assertEquals("A", target.presets().get(0).name);
	}
}
