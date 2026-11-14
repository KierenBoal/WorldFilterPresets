package com.kierenboal.worldfilterpresets;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

final class PresetClipboard
{
	String read() throws Exception
	{
		Object value = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
		if (!(value instanceof String)) { throw new IllegalArgumentException("The clipboard does not contain text."); }
		return (String) value;
	}
	void write(String value)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
	}
}
