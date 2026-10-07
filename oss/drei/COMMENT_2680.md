Audit done against the current `master` docs and `pmndrs/examples` `main`, matching each `<Codesandbox id>` to an example's `pmndrs.json` `source`. Numbers: 107 sandbox references in `docs/**/*.mdx`, 72 already have an example, 36 do not (30 unique sandboxes, some pages share one). Full table below.

First batch of the missing ones is in pmndrs/examples#<PR number>: ten examples written from scratch (no external assets), one per docs page: `ascii-renderer`, `selection-edges` (edges + select), `gradient-distort` (gradient-texture + mesh-distort-material), `inverted-hull-outlines`, `camera-controls`, `infinite-grid`, `pivot-controls`, `surface-sampling`, `round-points`, `studio-backdrop`.

Still missing after that batch, and why I left them for a second round: `decal`, `splat`, `text3d`/`center`, `gltf-use-gltf`, `trail-texture`, `video-texture`, `face-controls` need an asset (a texture, a font, a model, a video, a webcam) and therefore an `assets` entry with a license; `caustics`, `stage`, `environment`, `mesh-transmission-material` lean on an HDRI; `html`, `view`, `resize`, `presentation-controls`, `scroll-controls`, `sprite-animator` are interaction pages where the sandbox does something specific I would rather look at before rewriting.

On the third step, pointing the docs at the examples: the docs currently render `<Codesandbox id>` through pmndrs/docs. r3f's `getting-started/examples.mdx` links `pmndrs.github.io/examples/<name>` with the `thumbnail.webp` as the image, in plain markdown. If that is the shape you want for drei too, I can send that PR page by page once the examples land; if you would rather have a docs component that reads the examples catalog, that is a pmndrs/docs change first.

One thing the audit turned up on the side: the `Outlines` docs and implementation disagree since 9.109.2 (`screenspace` branches swapped by #2048, reported in #2072). Filed separately as #<issue number> with a one-line fix.

<details>
<summary>Audit table</summary>

**Audit of `docs/**/*.mdx` on `master` (107 `<Codesandbox>` references) against `pmndrs/examples` `main` (194 examples, matched on each example's `pmndrs.json` `source`).**

### Already have an example (72)

| docs page | sandbox | example |
|---|---|---|
| `abstractions/clone` | `42glz0` | `clones` |
| `abstractions/image` | `9s2wd9` | `cards-with-border-radius` |
| `abstractions/image` | `l4klb` | `horizontal-tiles` |
| `abstractions/image` | `gsm1y` | `useintersect-and-scrollcontrols` |
| `abstractions/image` | `x8gvs` | `infinite-scroll` |
| `abstractions/image` | `yjhzv` | `scrollcontrols-with-minimap` |
| `abstractions/positional-audio` | `gkfhr` | `lulaby-city` |
| `abstractions/use-animations` | `pecl6` | `gltf-animations` |
| `controls/keyboard-controls` | `vkgi6` | `minecraft` |
| `controls/motion-path-controls` | `2y73c6` | `motionpathcontrols` |
| `controls/presentation-controls` | `qyz5r` | `bouncy-watch` |
| `controls/scroll-controls` | `l4klb` | `horizontal-tiles` |
| `controls/scroll-controls` | `gsm1y` | `useintersect-and-scrollcontrols` |
| `controls/scroll-controls` | `x8gvs` | `infinite-scroll` |
| `controls/scroll-controls` | `yjhzv` | `scrollcontrols-with-minimap` |
| `controls/scroll-controls` | `4jr4p` | `scrollcontrols-gltf` |
| `gizmos/transform-controls` | `btsbj` | `transformcontrols-and-makedefault` |
| `loaders/loader` | `0buje` | `viking-ship` |
| `loaders/video-texture-use-video-texture` | `39hg8` | `video-textures` |
| `misc/cycle-raycast` | `ls503` | `raycast-cycling` |
| `misc/html` | `0n9it` | `the-three-graces` |
| `misc/html` | `qyz5r` | `bouncy-watch` |
| `misc/html` | `9keg6` | `mixing-html-and-webgl-w-occlusion` |
| `misc/html` | `6oei7` | `html-markers` |
| `misc/use-box-projected-env` | `s006f` | `sport-hall` |
| `misc/use-camera` | `py4db` | `viewcube` |
| `misc/use-depth-buffer` | `tx1pq` | `volumetric-spotlight` |
| `misc/use-intersect` | `gsm1y` | `useintersect-and-scrollcontrols` |
| `misc/wireframe` | `2572o5` | `wireframes` |
| `performances/detailed` | `12nmp` | `re-using-geometry-and-level-of-detail` |
| `performances/instances` | `h8o2d` | `floating-instanced-shoes` |
| `performances/instances` | `i6t0j` | `hi-key-bubbles` |
| `performances/merged` | `l900i` | `night-train` |
| `portals/fisheye` | `7qytdw` | `threejs-journey-lv-1-fisheye` |
| `portals/hud` | `py4db` | `viewcube` |
| `portals/mask` | `7n2yru` | `inverted-stencil-buffer` |
| `portals/mask` | `z3f2mw` | `stencil-mask` |
| `portals/mesh-portal-material` | `9m4tpc` | `enter-portals` |
| `portals/mesh-portal-material` | `qvk72r` | `pass-through-portals` |
| `portals/mesh-portal-material` | `drc6qg` | `magic-box` |
| `portals/mesh-portal-material` | `ik11ln` | `portals` |
| `portals/render-texture` | `0z8i2c` | `drei-rendertexture` |
| `portals/view` | `r9w2ob` | `multiple-views-with-uniform-controls` |
| `portals/view` | `bp6tmc` | `view-tracking` |
| `shaders/mesh-reflector-material` | `lx2h8` | `image-gallery` |
| `shaders/mesh-reflector-material` | `l900i` | `night-train` |
| `shaders/mesh-refraction-material` | `zqrreo` | `diamond-refraction` |
| `shaders/shader-material` | `ni6v4` | `threejs-journey-portal` |
| `shaders/soft-shadows` | `ykfpwf` | `room-with-soft-shadows` |
| `shaders/soft-shadows` | `dh2jc` | `soft-shadows` |
| `shapes/quadratic-bezier-line` | `2ij9u` | `backdrop-and-cables` |
| `staging/accumulative-shadows` | `hxcc1x` | `baking-soft-shadows` |
| `staging/bounds` | `rz2g0` | `bounds-and-makedefault` |
| `staging/bounds` | `42glz0` | `clones` |
| `staging/camera-shake` | `t4l0f` | `camera-shake` |
| `staging/camera-shake` | `0ycwe` | `staging-and-camerashake` |
| `staging/caustics` | `szj6p7` | `caustics` |
| `staging/cloud` | `gwthnh` | `thunder-clouds` |
| `staging/cloud` | `mbfzf` | `clouds` |
| `staging/contact-shadows` | `qxjoj` | `shoe-configurator` |
| `staging/environment` | `t4l0f` | `camera-shake` |
| `staging/environment` | `e662p3` | `building-dynamic-envmaps` |
| `staging/environment` | `lwo219` | `building-live-envmaps` |
| `staging/environment` | `q48jgy` | `envmap-ground-projection` |
| `staging/environment` | `0c5hv9` | `ground-projected-envmaps-lamina` |
| `staging/float` | `2ij9u` | `backdrop-and-cables` |
| `staging/lightformer` | `lwo219` | `building-live-envmaps` |
| `staging/sky` | `vkgi6` | `minecraft` |
| `staging/sparkles` | `0c5hv9` | `ground-projected-envmaps-lamina` |
| `staging/spot-light-shadow` | `yyk6gv` | `spotlight-shadows` |
| `staging/spot-light` | `tx1pq` | `volumetric-spotlight` |
| `staging/spot-light` | `wdzv4` | `ragdoll-physics` |

### No example yet (35)

| docs page | sandbox | proposed |
|---|---|---|
| `abstractions/ascii-renderer` | `vq9wsl` | `ascii-renderer` (this PR) |
| `abstractions/decal` | `ymb5d9` |  |
| `abstractions/edges` | `ny3p4` | `selection-edges` (this PR) |
| `abstractions/gradient-texture` | `l03yb` | `gradient-distort` (this PR) |
| `abstractions/outlines` | `2gh6jf` | `inverted-hull-outlines` (this PR) |
| `abstractions/sampler` | `ehflx3` | `surface-sampling` (this PR) |
| `abstractions/sampler` | `k6rcp2` | `surface-sampling` (this PR) |
| `abstractions/splat` | `qp4jmf` |  |
| `abstractions/text` | `yup2o` |  |
| `abstractions/text3d` | `x6obrb` |  |
| `controls/camera-controls` | `sew669` | `camera-controls` (this PR) |
| `controls/face-controls` | `jfx2t6` |  |
| `controls/face-controls` | `zhjbhy` |  |
| `controls/presentation-controls` | `kheke` |  |
| `controls/scroll-controls` | `4m0d0` |  |
| `gizmos/grid` | `19uq2u` | `infinite-grid` (this PR) |
| `gizmos/pivot-controls` | `om2ff8` | `pivot-controls` (this PR) |
| `loaders/gltf-use-gltf` | `z3xdgr` |  |
| `loaders/trail-texture-use-trail-texture` | `fj1qlg` |  |
| `loaders/video-texture-use-video-texture` | `2cemck` |  |
| `misc/html` | `wp9mkp` |  |
| `misc/select` | `ny3p4` | `selection-edges` (this PR) |
| `misc/sprite-animator` | `r3f-sprite-animator-s12ijv` |  |
| `portals/view` | `v5i9wl` |  |
| `portals/view` | `1wmlew` |  |
| `shaders/mesh-distort-material` | `l03yb` | `gradient-distort` (this PR) |
| `shaders/mesh-transmission-material` | `hmgdjq` |  |
| `shaders/point-material` | `eq7sc` | `round-points` (this PR) |
| `staging/backdrop` | `8yfnd` | `studio-backdrop` (this PR) |
| `staging/caustics` | `g7wbe0` |  |
| `staging/center` | `x6obrb` |  |
| `staging/center` | `v8s9ij` |  |
| `staging/environment` | `mih0lx` |  |
| `staging/resize` | `6yg0i3` |  |
| `staging/stage` | `57iefg` |  |

</details>
