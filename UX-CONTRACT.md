# Document repair interaction contract

Scope: Android manual spot repair. Source: user's explicit feature specification
in this task and the existing document/crop editor flow. No permissions, billing,
or deletion policy changes are introduced.

| Capability | Canonical owner | Behavior / verification |
|---|---|---|
| Modal navigation | CActionBar + shared showModal/closeModal | Return to same page on cancel/save; confirm dirty exit, including Android Back |
| Confirmation | NativeScript Material dialogs | Discard / keep editing, localized, no confirmation for normal save |
| Feedback | showError, shared loading semantics | Busy indicator; inline retry guidance; preserve edits after failure |
| Range input | NativeScript Material slider | Brush diameter, labeled and enabled only while editable |
| Image manipulation | Android RepairView | Single finger paint; two fingers cancel tentative stroke and pan/zoom; undo/redo; compare; fit |
| Persistence | OCRDocument.repairPage | Save new rendered image then update page; original untouched; source-coordinate repair regions in page.extra |
| Regeneration | cropDocumentFromFile / RepairProcessor | Reapply saved regions before cropping/enhancement; invalidate OCR/search on repair |
| Locale/theme | app/helpers/locale + colors store | Existing native theme and translation fallback |

Save is unavailable with zero applied strokes, while drawing, or during processing.
Cancel never mutates the document. Saving blocks duplicate taps and navigation.
Missing original image produces a localized explanation at entry. Save failure
retains strokes for retry. Original comparison means the image at session entry.

Repair targets small marks on paper. It does not infer missing printed text or
promise automatic handwriting removal. Preview is downsampled; save renders from
the full original. Stroke radius and coordinates are image-relative; crop quad
maps them to original image coordinates. Changing crop or enhancements must keep
saved repairs. Existing documents require no database migration (extra JSON).
