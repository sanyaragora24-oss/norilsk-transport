## Title

fix(safe-mode): block the live `script_directories` collection and `bpy.ops.extensions.*`

## Body

Fixes #365

### Problem

Safe mode's script-search-path rule only blocked assignment to `script_directory`, the pre-3.6 string property. Blender 3.6 deprecated it in favour of the `script_directories` collection (see the 3.6 Python API release notes) and 4.x no longer has the old name, so on every supported build this passed validation:

```python
import bpy
sd = bpy.context.preferences.filepaths.script_directories.new()
sd.directory = '/tmp/evil_scripts'
```

A new entry there is a persistent code-loading location: Blender imports `startup/`, `addons/` and `modules/` from it on the next launch, which is exactly what safe mode says it prevents.

While auditing for the same class of miss (a rule written against a name that later moved), `bpy.ops.extensions.*` was also unblocked. It is the 4.2+ successor to `bpy.ops.preferences.addon_install` and has `enable_on_install`, so `package_install_files(filepath=..., enable_on_install=True)` installs and executes code in one call.

### Fix

- `_FORBIDDEN_BPY_PATHS`: add `bpy.context.preferences.filepaths.script_directories`.
- `_FORBIDDEN_BARE_ATTRS`: add `script_directories`, so the aliased (`prefs = bpy.context.preferences; prefs.filepaths.script_directories...`) and iterated shapes are caught, matching how `texts` / `handlers` are handled.
- `_FORBIDDEN_OPS_PREFIXES`: add `bpy.ops.extensions`; `_MODULE_NAVIGATION`: add `extensions` so the container-wrap backstop covers it.
- Keep the `script_directory` assignment rule for builds older than 3.6, with a comment saying which spelling is which.
- Module docstring updated to list both.

No changes to the validator mechanics; policy tables only.

### Tests

New `BLOCKED_SCRIPTS` entries: `script_directories_new`, `script_directories_via_alias`, `script_directories_iterate`, `script_directory_legacy`, `scripts_auto_execute`, `extension_install_files`, `extension_install_remote`. New `ALLOWED_SCRIPTS` entry `read_preferences` guards against over-blocking `preferences.filepaths` as a whole.

Before the fix, five of the new cases fail (the three `script_directories` shapes and both extension operators); after it the full suite is green:

```
uv run --with pytest pytest -q
160 passed
```
