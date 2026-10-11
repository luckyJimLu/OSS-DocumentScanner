---
version: alpha
name: OSS Document Scanner
description: Native document editing with a quiet image-first workspace
omitted:
  - section: colors
    reason: Runtime Material theme owns dynamic light, dark and e-ink colors.
  - section: rounded
    reason: Existing NativeScript Material controls own component shapes.
  - section: spacing
    reason: Existing native styles and window insets own app geometry.
typography:
  sans:
    fontFamily: "Roboto, Noto Sans CJK SC, sans-serif"
---

## Overview

A native Android document workbench for scanning and cleaning paper. Preserve the
existing image-first editor, Material controls and icon language. Repair is a
precision tool, not a new visual identity. User-approved behavior is recorded in
UX-CONTRACT.md. Evidence: DocumentEdit.svelte, CropEditView.svelte and the supplied
Android screenshot; no separate maintained PRD was found for this feature.

## Colors

Runtime ownership: app/helpers/theme.ts and app/variables.ts re-export the shared
theme; consumers use the colors store and existing native CSS classes. Repair
uses colorPrimary with transparency for its temporary mask. Do not duplicate a
fixed light/dark palette. The original image is never tinted by the UI theme.

## Typography

Use the platform font and existing actionBarTitle, actionBarButton and icon-btn
styles. Use the locale provider for all visible copy. New repair copy is provided
in English and Simplified/Traditional Chinese; other locales use the established
English fallback. Do not introduce display typography into the editor.

## Layout

Image fills the available center. App-owned CActionBar above it; instructions,
brush size and actions below. Respect windowInset at both edges. The document
editor tool row scrolls horizontally on narrow devices. Primary save action stays
in the action bar. Brush actions do not shift while processing.

## Elevation & Depth

Follow existing app modal and Material dialog treatment. No new floating cards.

## Shapes

Reuse NativeScript Material controls and the existing MDI icon font.

## Components

Repair's signature feedback is the translucent brush mask directly over paper.
One finger paints; two fingers navigate. Use the native canvas for both image and
mask so their transform is identical. All icon buttons have localized accessible
names; a tap can repair a single spot without dragging. Use the shared Material
slider, confirmation dialog, showError and modal navigation.

## Do's and Don'ts

Keep original files untouched; do not bake screen zoom or rotation into masks.
Keep save disabled while drawing/processing. Do not discard edits silently.
Use existing semantic controls; do not replace app dialogs with browser dialogs.
