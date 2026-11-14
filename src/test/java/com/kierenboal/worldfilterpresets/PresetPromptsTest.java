package com.kierenboal.worldfilterpresets;

import org.junit.Test;
import static org.junit.Assert.*;

public class PresetPromptsTest
{
	@Test
	public void callbackCanFollowChatboxCloseButOnlyRunOnce()
	{
		PresetPrompts prompts = new PresetPrompts();
		long ticket = prompts.begin();
		assertTrue(prompts.pending());
		prompts.close(ticket);
		assertFalse(prompts.pending());
		assertTrue(prompts.complete(ticket));
		assertFalse(prompts.complete(ticket));
	}

	@Test
	public void logoutProfileChangeAndShutdownInvalidateAnyPendingOperation()
	{
		for (String operation : new String[]{"name", "rename", "icon", "import", "delete", "bulk first", "bulk second"})
		{
			PresetPrompts prompts = new PresetPrompts();
			long old = prompts.begin();
			prompts.invalidate();
			assertFalse(operation, prompts.complete(old));
			assertFalse(prompts.pending());
		}
	}

	@Test
	public void cancelledOrOlderPromptCannotAffectNextPrompt()
	{
		PresetPrompts prompts = new PresetPrompts();
		long old = prompts.begin();
		prompts.close(old); // No callback is submitted by Escape/close.
		long current = prompts.begin();
		prompts.close(old);
		assertTrue(prompts.pending());
		assertFalse(prompts.complete(old));
		assertTrue(prompts.complete(current));
	}

	@Test
	public void bulkDeleteRequiresIndependentSecondTicketWithReversedButtons()
	{
		PresetRepository repository = new PresetRepository(new PresetCodec(), null, value -> { });
		repository.add("Keep until both confirmations", new int[11]);
		PresetPrompts prompts = new PresetPrompts();
		assertArrayEquals(new String[]{"Yes", "No"}, PresetPrompts.confirmationOptions(false));
		assertArrayEquals(new String[]{"No", "Yes"}, PresetPrompts.confirmationOptions(true));
		long first = prompts.begin();
		assertTrue(prompts.complete(first));
		long second = prompts.begin();
		assertEquals(1, repository.presets().size());
		assertFalse(prompts.complete(first));
		prompts.close(second); // Cancelling second stage never invokes deleteAll.
		assertEquals(1, repository.presets().size());
	}

	@Test
	public void unsavedDecisionSavesDiscardsOrCancelsTransition()
	{
		for (PresetDecision decision : PresetDecision.values())
		{
			StringBuilder calls = new StringBuilder();
			decision.apply(() -> calls.append("save "), () -> calls.append("switch"));
			assertEquals(decision == PresetDecision.SAVE ? "save switch" : decision == PresetDecision.DISCARD ? "switch" : "", calls.toString());
		}
	}
}
