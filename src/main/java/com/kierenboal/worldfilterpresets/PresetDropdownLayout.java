package com.kierenboal.worldfilterpresets;

final class PresetDropdownLayout
{
	static int headerWidth(int frameWidth, int titleTextWidth)
	{
		// Left inset, gap, measured title plus padding, and the native close-button area.
		return Math.max(80, Math.min(210, frameWidth - 7 - 10 - titleTextWidth - 8 - 36));
	}
}
