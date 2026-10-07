## Title

`Outlines`: `screenspace` semantics are swapped since 9.109.2 (default `thickness` is now in pixels, so the documented default of 0.05 is invisible)

## Body

### Problem

The docs for `<Outlines>` say `thickness` defaults to `0.05` and that `screenspace` makes "line thickness independent of zoom". Since #2048 the two shader branches are the other way round:

```glsl
if (screenspace) {
  // world units: position + normal * thickness
} else {
  // clip space: thickness / size * w * 2  -> thickness in pixels
}
```

#2048 changed the uniform from `float` to `bool` and rewrote `if (screenspace == 0.0)` as `if (screenspace)`, which negates it. So with the default `screenspace={false}` the hull is offset by `thickness` **pixels** (`0.05px`, nothing visible), and with `screenspace` it is offset by `thickness` **world units** (a `thickness={4}` meant as 4px becomes a 4-unit black blob).

This is what #2072 reported ("Outlines stopped respecting the `screenspace` property") for 9.111.3; it was closed without a fix and `master` still has the flipped condition (`src/core/Outlines.tsx`, the `if (screenspace)` line in the vertex shader).

### Reproduction

drei 10.7.8, three 0.165, r3f 9.6.1, the docs snippet plus a `screenspace` variant:

```jsx
<mesh><boxGeometry /><meshToonMaterial /><Outlines color="black" /></mesh>                 // default 0.05: nothing drawn
<mesh><sphereGeometry /><meshToonMaterial /><Outlines thickness={4} screenspace /></mesh>  // 4 world units
```

Before (current `master` shader) and after negating the condition, same scene:

- before: `outlines-10.7.8-before-fix.png` (attach)
- after: `outlines-after-fix.png` (attach)

### Fix

`if (screenspace)` -> `if (!screenspace)`. One line; PR to follow.
