# Design system

## Direction: Forest worktable

Svetlanas Finanzkumpel should feel like a calm, well-kept worktable in daylight: grounded, legible, and reassuring without becoming rustic decoration. Forest Canopy supplies the palette and the contrast between a characterful serif heading voice and a plain sans-serif working voice. Material 3 owns navigation, controls, dialogs, spacing behavior, and touch targets.

The memorable move is the welcoming greeting and illustrated companion above the daily task panel. The missing-day panel uses a light tonal surface in both light and dark mode. The owl supports the task state without replacing its explicit wording.

## Hierarchy

- Every screen has one obvious task or status.
- Headings are left-aligned. Serif is reserved for greetings, screen introductions, and major state titles.
- Body, labels, controls, dates, amounts, and dense records use the sans-serif Material scale.
- Filled buttons are primary. Outlined and text buttons are secondary. One filled action per decision area.
- Lists use rows and dividers before separate cards. Cards group genuinely related information only.

## Material expression

- Compact Android navigation uses four Material bottom destinations: Start, Erfassen, Einträge, and Optionen. It remains visible on child screens. The selected icon is larger and shows its label; one tonal selected surface surrounds both icon and label. All destinations keep accessible names, including when their visible labels are hidden.
- Erfassen offers every supported document kind and own kinds. Einträge is the grouped, searchable collection of daily closes, invoice/order cases and general files, including orders without attachments. Quartalspaket is a child preparation workflow using the same records, not a second collection or root destination.
- The bottom bar is a continuous, subtly translucent tonal surface across the screen, without a floating outline or outer gutters. Selected and keyboard-focused states cover the whole tab; no separate ripple capsule surrounds the icon.
- Home leaves the greeting and owl free of controls. Major home groups are separated by 28 dp; related heading/content pairs stay close.
- Home welcomes the user with the serif displaySmall role (36/42 sp), intentionally distinct from the 28 sp page titles, beside a prominent 188 dp companion in a more generous header. The greeting and companion contract continuously toward a compact headline-and-companion header while Home scrolls and expand again on return. Five short, warm supporting lines vary on a new Home visit or return from the background, not during reading or recomposition. They never imply that unfinished tasks are complete. At enlarged font sizes the composition stacks.
- The daily turnover action follows the greeting directly. Quick capture comes next, with Rechnung and Bestellung using larger, centered icons, so frequent capture stays near the top. Alle Arten is right-aligned alongside Schnell erfassen, above the tiles, and opens the same Erfassen destination. The following overview is titled Aktuelles when open invoice/order cases exist and integrates up to three of them, linking additional cases to Entries; without such cases it remains Auf einen Blick. It uses quiet divided rows and includes preparation of the quarterly package. Content scrolls naturally to the bottom bar, with no separate continuation banner occupying the viewport. Background tint changes only very subtly toward the bottom.
- Capture actions share one tonal tile treatment and titleMedium typography. Daily close and adding to an existing entry use full width for their explanatory text, but the same icon scale, shape and padding as the other types.
- Capture and Entries share the prototype's Lucide document-type symbols at 1.8 stroke width: flame for gas, music for GEMA, landmark for bank statements, and corresponding distinct symbols for the other kinds. Custom kinds use the file fallback. Home quick capture uses the same receipt and package symbols.
- The daily task explicitly describes today, followed by its date and the daily turnover explanation. A missing close carries an explicit warning triangle beside the status. A completed close uses a quieter secondary container, an explicit check beside the status and a large amount on its own line. History is a secondary action next to the primary task action, stacking at enlarged font scales.
- Every secondary screen has one serif page title, followed by content with a consistent 16 dp top inset. Section headings use the smaller sans-serif title role.
- System Back and the standard child-screen back arrow return to the preceding screen; root pages use System Back to return to Home. The back arrow shares the title row, never adding a row above it. Leaving edited, unsaved forms asks before discarding input, and confirmation removes the saved draft too.
- When the software keyboard opens, the bottom navigation is hidden instead of moving above the keyboard. Screen content alone receives IME insets so the focused field remains reachable and the compact phone viewport stays useful.
- Invoice/order editing is divided into Grundangaben, Rechnungs- or Bestellangaben, document attachment, and optional follow-up details. Required fields are explained once and identified at the control; currently only the supplier and the mode's primary date are required.
- Automatic folder/Google Drive backup updates one `Quartalsmappe_Aktuell.zip` through a completed temporary archive, while an explicit action creates a separate timestamped snapshot. Restore remains conflict-safe and additive.
- Minimum touch target: 48 dp; primary actions: 56 dp.
- Corners use a restrained 10/14/18/24 dp scale. Buttons are rounded rectangles, not pills.
- Status always combines wording with shape or icon. Color never carries meaning alone.
- Critical states (overdue payment or a missing original) include a warning triangle. Attachment counts use a paperclip, and originals open from their owning entry. A present Z-Abschlag needs no extra success badge; a missing one remains actionable.

## Forest Canopy adaptation

- Forest green anchors primary actions and the daily focus surface.
- Ivory is the working ground; near-white surfaces separate content without drop-shadow stacks.
- Sage and olive support secondary containers, selection, and calm completed states.
- Coral is reserved for warnings and attention, never decorative emphasis.
- Dark mode retains the forest character with muted green surfaces and accessible foregrounds.
- Cards use tonal surface separation instead of perimeter borders. Dark surfaces use softer charcoal greens; quick actions and navigation use the stronger container tones.

## Personal appearance

- Settings offers compact Farbschema and Begleiter rows, with native selection sheets and labeled, radio-selected previews.
- All ten Theme Factory palettes have separately authored light and dark Material roles. Darstellung offers System, Hell and Dunkel independently of the palette. System is the backward-compatible default; a manual choice overrides device appearance, including previews and system-bar icon contrast. The serif/sans typography remains the same.
- Eule, Biene, Stachelschwein, Schaf, and Nacktkatze are independent of the palette. New animals use transparent, naturally colored illustrations, without task-completion symbols.
- Appearance changes save immediately on this device in separate preferences, without modifying financial records or creating business backups. The existing reminder draft survives selection; its save button explicitly says Erinnerungen speichern.

## Motion

Use Material state feedback only. No decorative entrance sequences. State changes may use a single fade-through or container transition when added later, and must respect the system animation setting.
