# World Filter Presets

A RuneLite plugin for creating and reusing presets for world-list filters.

Open the world-switcher configuration to use the preset dropdown. **In-game filter** uses your
normal client settings; **No filters** temporarily shows all worlds. **+ Add new** saves the
current filters and searches under a unique name. Names are matched without regard to letter case.

Right-click a preset to save, rename, choose an inventory item icon, export, or delete it.
Every entry, including the protected built-ins, supports **Move up**, **Move down**, and
**Show in cog menu**. The cog shortcuts follow that same order. Hiding a built-in from the
dropdown in plugin configuration does not hide its cog shortcut.

Right-click the dropdown header, even while expanded, to import from the clipboard,
export all custom presets, or delete all custom presets. Bulk deletion requires two confirmations.
When switching away from an edited preset, choose Save, Discard, or Cancel.

## Sharing presets

Exports contain custom presets in their relative order, without local IDs or absolute positions.
Imports append new names and ask before overwriting existing names with changed content.
An overwrite retains the existing position and selection and applies the imported filters.
Identical presets are skipped with a red chat message.

The readable version 1 JSON format omits defaults. A minimal export is:

```json
{
  "format": "world-filter-presets",
  "version": 1,
  "presets": [{ "name": "All worlds" }]
}
```

Optional preset fields are `icon` (item ID), `showInCogMenu` (defaults to true), and `filters`.
Filter fields `member`, `country`, and `theme` default to zero. `search` contains up to eight
character values with omitted trailing positions defaulting to zero. Unknown preset and filter
fields are retained for forward compatibility. Built-ins and their preferences stay local.

## Development

Requires JDK 11 or newer.

Run the development client on Windows with `.\gradlew.bat run`, or on macOS/Linux with `./gradlew run`.

Run the tests and build with `.\gradlew.bat test build`. Tests cover storage, import reconciliation,
ordering, cog menus, and native widget construction. Live fixed/resizable layout and Stretched Mode
checks require a logged-in client.
