Reproduced on `main` (commit 6f992ff): all three shapes of the `script_directories` payload pass `validate_code`, while only the pre-3.6 `script_directory` spelling is blocked.

One more miss of the same kind while auditing the tables: `bpy.ops.extensions.*` (the 4.2+ replacement for `bpy.ops.preferences.addon_install`, with `enable_on_install=True`) is not in `_FORBIDDEN_OPS_PREFIXES`, so `package_install_files(filepath=..., enable_on_install=True)` also passes.

I have a fix ready (policy tables only, plus regression tests for `.new()`, `.directory` assignment via an alias, iteration, the legacy spelling, and both extension install operators). Opening a PR now.
