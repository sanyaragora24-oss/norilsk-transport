# blender-mcp issue #365: safe mode misses `script_directories`

Готовый вклад в [ahujasid/blender-mcp](https://github.com/ahujasid/blender-mcp) по issue
[#365](https://github.com/ahujasid/blender-mcp/issues/365).

Файлы:

- `0001-*.patch` — коммит в формате `git format-patch`, применяется на upstream `main` (`6f992ffbca3cb715d111fc640b737b808632273c`).
- `changes.diff` — тот же diff без заголовков коммита.
- `PR_BODY.md` — заголовок и описание pull request.
- `ISSUE_COMMENT.md` — комментарий в issue перед открытием PR.

## Как отправить

```bash
# 1. Форк через GitHub UI: https://github.com/ahujasid/blender-mcp/fork
git clone https://github.com/sanyaragora24-oss/blender-mcp
cd blender-mcp
git checkout -b fix/safe-mode-script-directories

# 2. Применить патч (путь подставьте свой)
git am /path/to/0001-fix-safe-mode-block-the-live-script_directories-coll.patch

# 3. Проверить
uv sync
uv run --with pytest pytest -q      # ожидается: 160 passed

# 4. Отправить
git push -u origin fix/safe-mode-script-directories
```

Затем:

1. Вставить текст из `ISSUE_COMMENT.md` комментарием в issue #365.
2. Открыть PR на `ahujasid/blender-mcp` из `fix/safe-mode-script-directories` в `main`,
   заголовок и тело взять из `PR_BODY.md`.

## Что проверено

- Baseline upstream `main`: 152 теста проходят.
- С патчем: 160 проходят. Без правки `safe_mode.py` пять новых тестов падают
  (три формы `script_directories` и оба оператора `bpy.ops.extensions`).
- Факты по API: `script_directories` появился в Blender 3.6 (release notes 3.6, Python API:
  «`PreferencesFilePaths.script_directory` is deprecated»), `bpy.ops.extensions.package_install_files`
  с параметром `enable_on_install` есть в текущем API.
