package com.kierenboal.worldfilterpresets;

import java.util.function.ToIntFunction;
import net.runelite.client.util.Text;

/** Keep the initial ellipsis for three seconds, then reveal an overflowing name from left to right. */
final class PresetLabelMarquee
{
	private static final long PAUSE_MS = 3000;
	private static final long STEP_MS = 180;
	private String value;
	private int width;
	private long started;

	String text(String name, int available, ToIntFunction<String> measure, long now)
	{
		if (!name.equals(value) || available != width)
		{
			value = name;
			width = available;
			started = now;
		}
		String escaped = Text.escapeJagex(name);
		if (measure.applyAsInt(escaped) <= available) { return escaped; }
		int last = 0;
		while (last < name.length() && measure.applyAsInt(Text.escapeJagex(name.substring(last))) > available)
		{ last = name.offsetByCodePoints(last, 1); }
		int steps = name.codePointCount(0, last);
		long elapsed = Math.max(0, now - started) % (PAUSE_MS * 2 + steps * STEP_MS);
		int step = elapsed < PAUSE_MS ? 0 : (int) Math.min(steps, 1 + (elapsed - PAUSE_MS) / STEP_MS);
		return ellipsize(name.substring(name.offsetByCodePoints(0, step)), available, measure);
	}

	static String ellipsize(String name, int width, ToIntFunction<String> measure)
	{
		String escaped = Text.escapeJagex(name);
		if (measure.applyAsInt(escaped) <= width) { return escaped; }
		int end = name.length();
		while (end > 0)
		{
			end = name.offsetByCodePoints(end, -1);
			String result = Text.escapeJagex(name.substring(0, end)) + "...";
			if (measure.applyAsInt(result) <= width) { return result; }
		}
		return measure.applyAsInt("...") <= width ? "..." : "";
	}
}
