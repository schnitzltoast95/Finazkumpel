# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

The primary user is the owner of one small business. She records the daily Z-Abschlag, follows invoices from order through payment, collects recurring business documents, and prepares a complete quarterly handoff for her financial adviser. The app must be understandable without bookkeeping expertise or training.

## Product Purpose

Svetlanas Finanzkumpel turns a recurring pile of receipts, dates, and documents into a short sequence of clear daily and quarterly actions. Success means the owner can see what needs attention, capture it quickly, and trust that the original files and prior versions remain available.

## Positioning

The app is a private, local-first paperwork companion rather than accounting or tax software. It explains completeness in everyday German, preserves originals and revisions, and never implies that records are tax-correct.

## Operating Context

The app is used on an Android phone in portrait orientation, often while handling paper receipts, PDFs, emailed invoices, and photos. Core rhythms are the daily closing entry, invoice follow-up, document collection, backup, and quarterly completeness check.

## Capabilities and Constraints

- Kotlin and Jetpack Compose with Material Design 3; minimum Android API 26.
- One business and one user in the MVP; local storage without accounts, tracking, ELSTER, bank, or DATEV connections.
- Preserve daily closes, invoice/order states, document categories, quarterly checks and exports, reminders, version history, backup and conflict-safe restore.
- Existing workflows, wording, layout, and presentation may change when requirements continue to be met and usability improves.
- Imported originals are copied into private app storage. Existing entries are not overwritten during restore, and the UI currently offers no deletion.
- Tax calculation, tax advice, automatic categorisation, partial payments, and multi-user roles remain outside scope.

## Brand Commitments

- Product name: Svetlanas Finanzkumpel.
- German, direct, reassuring everyday language; avoid unnecessary bookkeeping jargon.
- The companion is selectable: owl, bee, porcupine, sheep, or hairless cat. The owl retains its calm/busy task states; alternative animals are friendly, neutral illustrations without completion claims.
- Forest Canopy remains the default, with all ten Theme Factory color schemes selectable independently of the companion. Typography and semantic status meanings stay consistent across themes.

## Evidence on Hand

- Requirements and acceptance criteria: `MVP_PLAN.md`.
- Current product behavior and build instructions: `README.md`.
- Existing owl assets: `app/src/main/res/drawable-nodpi/mascot_*.png`.
- Automated unit and Android instrumentation tests in `app/src/test` and `app/src/androidTest`.
- Test-only invoices, images, and PDFs are available; no commercial claims or external endorsements may be invented.

## Product Principles

1. Show the next useful action before background detail.
2. Make status understandable in words and symbols, never color alone.
3. Keep one primary task per screen and allow incomplete records to be finished later.
4. Preserve originals, history, and user control over backups and exports.
5. Prefer calm guidance over accounting terminology or alarming dashboard chrome.

## Accessibility & Inclusion

Use scalable Material typography, at least 48 dp touch targets, strong contrast, descriptive TalkBack labels, and layouts that remain usable at enlarged font scale. Important status and errors must not rely on color alone.
