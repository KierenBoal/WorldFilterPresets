package com.kierenboal.worldfilterpresets;

import org.junit.Test;
import static org.junit.Assert.*;

public class PresetLabelMarqueeTest
{
	@Test
	public void startsEllipsizedAndScrollsOnlyAfterThreeSeconds()
	{
		PresetLabelMarquee label = new PresetLabelMarquee();
		assertEquals("AB...", label.text("ABCDEFGHIJ", 5, String::length, 100));
		assertEquals("AB...", label.text("ABCDEFGHIJ", 5, String::length, 3099));
		assertEquals("BC...", label.text("ABCDEFGHIJ", 5, String::length, 3100));
		assertEquals("CD...", label.text("ABCDEFGHIJ", 5, String::length, 3280));
		assertEquals("FGHIJ", label.text("ABCDEFGHIJ", 5, String::length, 3820));
		assertEquals("FGHIJ", label.text("ABCDEFGHIJ", 5, String::length, 6500));
		assertEquals("AB...", label.text("ABCDEFGHIJ", 5, String::length, 7000));
	}

	@Test
	public void selectingOrResizingRestartsTheDelayAndShortNamesStayStill()
	{
		PresetLabelMarquee label = new PresetLabelMarquee();
		label.text("ABCDEFGHIJ", 5, String::length, 0);
		assertEquals("FGHIJ", label.text("ABCDEFGHIJ", 5, String::length, 4000));
		assertEquals("12...", label.text("1234567890", 5, String::length, 4001));
		assertEquals("123...", label.text("1234567890", 6, String::length, 8000));
		assertEquals("123...", label.text("1234567890", 6, String::length, 10999));
		assertEquals("Short", label.text("Short", 6, String::length, 11000));
		assertEquals("Short", label.text("Short", 6, String::length, 50000));
	}

	@Test
	public void measuresPixelWidthsAndAlwaysKeepsTheVisibleTextWithinItsSpace()
	{
		PresetLabelMarquee label = new PresetLabelMarquee();
		for (long time = 0; time < 12000; time += 40)
		{
			String text = label.text("WWWWiiiiMMMM", 30, value -> value.chars().map(c -> c == 'i' ? 2 : 6).sum(), time);
			assertTrue(text.chars().map(c -> c == 'i' ? 2 : 6).sum() <= 30);
		}
	}

	@Test
	public void escapesMarkupAndDoesNotSplitUnicodeCharacters()
	{
		PresetLabelMarquee label = new PresetLabelMarquee();
		assertEquals("<lt>name<gt>", label.text("<name>", 50, String::length, 0));
		for (int time = 0; time < 10000; time += 180)
		{
			String text = label.text("A\uD83D\uDE00BCDEFGHIJ", 5, String::length, time);
			for (int i = 0; i < text.length(); i++)
			{
				if (Character.isHighSurrogate(text.charAt(i))) { assertTrue(Character.isLowSurrogate(text.charAt(++i))); }
				else { assertFalse(Character.isLowSurrogate(text.charAt(i))); }
			}
		}
	}
}
