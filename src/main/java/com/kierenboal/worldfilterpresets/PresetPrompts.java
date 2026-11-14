package com.kierenboal.worldfilterpresets;

/** Tickets prevent duplicate callbacks and callbacks from an old profile/session. */
final class PresetPrompts
{
	private long ticket;
	private boolean pending;
	synchronized long begin() { pending = true; return ++ticket; }
	synchronized boolean pending() { return pending; }
	synchronized void close(long value) { if (value == ticket) { pending = false; } }
	synchronized void invalidate() { ++ticket; pending = false; }
	synchronized boolean complete(long value)
	{
		if (value != ticket) { return false; }
		invalidate();
		return true;
	}
	static String[] confirmationOptions(boolean secondStage)
	{
		return secondStage ? new String[]{"No", "Yes"} : new String[]{"Yes", "No"};
	}
}
