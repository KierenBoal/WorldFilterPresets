package com.kierenboal.worldfilterpresets;

enum PresetDecision
{
	SAVE, DISCARD, CANCEL;

	void apply(Runnable save, Runnable transition)
	{
		if (this == SAVE)
		{
			save.run();
		}
		if (this != CANCEL)
		{
			transition.run();
		}
	}
}
