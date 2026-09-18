## Title

Add ten drei examples for docs pages that had only a sandbox

## Body

Part of pmndrs/drei#2680, which asks for an example in this catalog behind every drei docs page that still embeds a CodeSandbox. Auditing drei's `docs/**/*.mdx` against this repo (each `<Codesandbox id>` matched to a `pmndrs.json` `source`) finds 36 references without one. This is the first batch: ten examples written from scratch, one per docs page, no external assets.

| example | drei docs page(s) |
|---|---|
| `ascii-renderer` | abstractions/ascii-renderer |
| `selection-edges` | abstractions/edges, misc/select |
| `gradient-distort` | abstractions/gradient-texture, shaders/mesh-distort-material |
| `inverted-hull-outlines` | abstractions/outlines |
| `camera-controls` | controls/camera-controls |
| `infinite-grid` | gizmos/grid |
| `pivot-controls` | gizmos/pivot-controls |
| `surface-sampling` | abstractions/sampler |
| `round-points` | shaders/point-material |
| `studio-backdrop` | staging/backdrop |

Each one is the `basic-example` template with the pinned dependency set (`round-points` adds `maath`), a `pmndrs.json` that uses existing tags only, `source` pointing at the drei docs page, and a 1600x840 `thumbnail.webp` shot from the built example in headless Chromium. `pnpm-lock.yaml` has the ten new importers (`pnpm install --lockfile-only`).

Checked locally: `tsc --noEmit` and `vite build` per example, `eslint` with the repo config (clean), `prettier --check`, `syncpack lint`, `lint:metadata` (194 examples validated). I could not run the Playwright e2e here; every example mounts a single `<Canvas>` from `App.tsx` and needs no network, so the harness should have nothing special to wait on.

`inverted-hull-outlines` is written against how `Outlines` actually behaves in drei 10.7.8, where the default mode offsets in clip space (pixels) and `screenspace` in world units, the opposite of its docs; the branches were swapped by pmndrs/drei#2048 and a fix is proposed in pmndrs/drei#<PR number>. The comments in `App.tsx` say so; once the fix ships and the pin moves past it, the two `thickness` values swap.
