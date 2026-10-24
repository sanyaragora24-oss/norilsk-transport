# pmndrs/examples: ten drei examples (drei #2680, batch 1)

- `0001-*.patch` — коммит на `pmndrs/examples` `main` (6ea1379): 10 примеров + `pnpm-lock.yaml`.
- `PR_BODY.md` — заголовок и описание PR.
- `thumbnails/` — превью каждого примера, чтобы посмотреть без сборки.

## Как отправить

```bash
# 1. Форк: https://github.com/pmndrs/examples/fork  (под sanyaragora24-oss)
git clone https://github.com/sanyaragora24-oss/examples pmndrs-examples
cd pmndrs-examples
git checkout -b feat/drei-docs-examples-batch-1
git am /path/to/0001-Add-ten-drei-examples-for-docs-pages-that-had-only-a.patch
git push -u origin feat/drei-docs-examples-batch-1
```

Затем PR на `pmndrs/examples` из этой ветки в `main`, текст из `PR_BODY.md`.
В CI репозитория PR из форка не получает превью на Vercel и Chromatic, это нормально
и написано у них в README.
