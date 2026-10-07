# pmndrs/drei: issue #2680 (аудит примеров) и баг в Outlines

Файлы:

- `COMMENT_2680.md` — комментарий в issue #2680: итог аудита, ссылка на PR в pmndrs/examples,
  что осталось, вопрос по формату ссылок в доках. Подставить номера PR и issue.
- `ISSUE_OUTLINES.md` — новый issue: `screenspace` у `Outlines` перепутан с 9.109.2.
  Приложить `outlines-10.7.8-before-fix.png` и `outlines-after-fix.png`.
- `0001-fix-Outlines-restore-screenspace-semantics.patch` — фикс в одну строку на `master`.
- `PR_BODY_OUTLINES.md` — описание PR с фиксом.

## Порядок

1. Открыть PR в pmndrs/examples (см. `../pmndrs-examples/README.md`), запомнить номер.
2. Создать issue из `ISSUE_OUTLINES.md`, запомнить номер.
3. Форк drei: https://github.com/pmndrs/drei/fork, затем

```bash
git clone https://github.com/sanyaragora24-oss/drei
cd drei
git checkout -b fix/outlines-screenspace
git am /path/to/0001-fix-Outlines-restore-screenspace-semantics.patch
git push -u origin fix/outlines-screenspace
```

4. PR на `pmndrs/drei` `master` из `PR_BODY_OUTLINES.md`, подставив номер issue.
5. Комментарий в #2680 из `COMMENT_2680.md`, подставив оба номера.
