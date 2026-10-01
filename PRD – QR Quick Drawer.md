# PRD – QR Quick Drawer

Sep 30, 2026 · @Akash

## Overview

QR Quick Drawer is an offline Android app that puts all of my payment QR codes one tap away, from any screen, via a floating bubble. Tapping the bubble opens a drawer listing my saved QRs; tapping one shows it full-screen and bright so the payer can scan it. No backend, no account, no network: everything is stored on the device.

**Problem.** I receive money into 4–5 different bank accounts (UPI QRs), and which account I want depends on the payment. Today, getting the right QR means opening a specific bank or UPI app, logging in, and finding its "Receive" screen, or scrolling the gallery for a screenshot. That takes 20–60 seconds while the payer waits, and it is easy to show the wrong account.

**Solution.** One always-available floating bubble → a drawer of labelled QR cards → one tap to show the right QR full-screen. Target: the right QR on screen in under 3 seconds, from anywhere on the phone.

## Goals, non-goals and success metrics

**Goals**

- Show any saved QR in 2 taps (bubble → QR) from any app or the home screen.
- Make it obvious which account a QR belongs to (label, bank, colour, last 4 digits of account/UPI ID).
- Work fully offline with zero backend and zero data leaving the phone.
- Keep the bubble unobtrusive: movable, hideable, never blocking what I am doing.

**Non-goals (v1)**

- Processing, verifying or tracking payments (no transaction history, no "payment received" check).
- Generating dynamic QRs with a pre-filled amount (candidate for v2, see open questions).
- Cloud sync, multiple devices, user accounts, login.
- iOS: iOS does not allow floating overlays over other apps; an iOS version would need a widget / Control Center approach instead.

**Success metrics**

| Metric | Target |
| --- | --- |
| Time from bubble tap to QR on screen | < 3 s (2 taps) |
| Cold start of drawer after bubble tap | < 300 ms |
| Wrong-account shows (self-reported) | 0 per month |
| Network permission requested | None |
| Crashes / overlay killed by the OS | 0 in a week of daily use |

## Target user and user stories

Primary user: me, a single person with 4–5 bank accounts and UPI QRs who routinely receives payments in person (friends splitting bills, freelance clients, sellers/buyers) and wants to choose the receiving account on the spot.

| # | As a user, I want to… | So that… | Priority |
| --- | --- | --- | --- |
| U1 | tap a floating bubble from any screen and see all my QRs | I never have to open a bank app to receive money | P0 |
| U2 | tap a QR card and see it full-screen at max brightness | the payer's phone scans it on the first try | P0 |
| U3 | add a QR by importing a screenshot or scanning a printed QR with the camera | setup takes under a minute per account | P0 |
| U4 | label each QR (e.g. "HDFC Salary", "SBI Savings") with a colour and UPI ID | I never show the wrong account | P0 |
| U5 | reorder, edit and delete QRs | the most-used account is always on top | P0 |
| U6 | drag the bubble anywhere and snap it to an edge | it doesn't cover what I'm doing | P0 |
| U7 | hide the bubble temporarily and bring it back from the notification | I can clear the screen during games, video or calls | P1 |
| U8 | swipe left/right in full-screen view between QRs | I can switch accounts without closing | P1 |
| U9 | lock the app with fingerprint/PIN for editing | nobody can swap my QR for theirs | P1 |
| U10 | export/import my QRs as a backup file | I don't lose setup when I change phones | P2 |

## Functional requirements

**F1. Floating bubble**

1. A circular bubble (\~56 dp) drawn over other apps using the Android "Display over other apps" permission.
2. Draggable; snaps to the nearest left/right edge on release; position persists across restarts.
3. Semi-transparent (e.g. 60% opacity) when idle; full opacity on touch.
4. Drag to a "×" target at the bottom of the screen to hide it; restore from the persistent notification or by opening the app.
5. Starts automatically on phone boot (optional toggle).

**F2. Drawer**

1. Tapping the bubble opens a compact drawer panel anchored next to it (slides in from the bubble's edge).
2. Lists QR cards in the user's order: colour stripe, label, bank name, masked UPI ID (e.g. akash•••@okhdfc), small QR thumbnail.
3. Shows up to 5 cards without scrolling; scrolls beyond that.
4. Tap outside, press Back, or tap the bubble again to close.
5. Optional "default" QR pinned first; long-press the bubble to open the default QR directly (1 tap).

**F3. Full-screen QR view**

1. Tapping a card opens the QR full-screen, centred, on a white background, sized to fill the width with a quiet zone.
2. Screen brightness forced to 100% while shown, restored on close; screen kept awake.
3. Large label above the QR (account name + bank) so both I and the payer can confirm the account.
4. Swipe left/right to move to the next/previous QR (P1).
5. Close via Back, tap on ×, or swipe down.

**F4. Adding and managing QRs**

1. Add from image: pick a screenshot/photo from the gallery; the app detects and decodes the QR (ML Kit barcode scanning, on-device).
2. Add from camera: scan a printed or on-screen QR live.
3. On decode, parse the UPI string (upi://pay?pa=…&pn=…) and pre-fill the label and UPI ID; user can edit.
4. Store the decoded payload and re-render it as a clean, crisp QR (not the original blurry screenshot). Keep the original image as a fallback if decoding fails.
5. Fields per QR: label (required), bank name, UPI ID, colour, note, is-default.
6. Edit, delete (with confirm), drag-to-reorder.
7. Soft limit of 20 QRs; no hard limit needed.

**F5. Settings**

- Bubble on/off, opacity, size (S/M/L), start on boot.
- App lock (biometric / device PIN) for opening the manager screen; showing QRs from the drawer stays unlocked for speed (configurable).
- Backup: export all QRs to a single JSON file (payloads + labels) and import it back (P2).

## UX flow and screens

&#91;embedded content: user flow · daily use and one-time setup\]

Daily use is two taps: bubble, then the card; a long-press on the bubble skips straight to the default QR. Setup happens once per account.

**Screens:** (1) floating bubble, (2) drawer panel of QR cards, (3) full-screen QR viewer, (4) QR manager list, (5) add/edit QR form, (6) settings, (7) onboarding for the overlay and notification permissions.

## Non-functional requirements and tech approach

**Platform.** Android 8.0+ (API 26+), built natively in Kotlin + Jetpack Compose. Android is required because only Android lets an app draw a floating bubble over other apps.

**Storage (no backend).**

- Room (SQLite) table `qr_codes`: id, label, bank, upi\_id, payload, color, note, is\_default, sort\_order, original\_image\_path, created\_at.
- Images, if kept, in app-private internal storage (not the gallery).
- Preferences (bubble position, opacity, toggles) in DataStore.
- Android Auto Backup disabled by default so QR data is not silently copied to Google Drive; export is explicit (F5).

**Key components.**

| Component | Responsibility |
| --- | --- |
| `BubbleService` (foreground service) | Draws the bubble and drawer via WindowManager (`TYPE_APPLICATION_OVERLAY`); holds the persistent notification |
| `QrViewerActivity` | Full-screen QR, brightness and keep-awake flags |
| `ManagerActivity` | Add, edit, reorder, delete, settings |
| QR decode | Google ML Kit Barcode Scanning (on-device, bundled model) |
| QR render | ZXing core, generating a bitmap from the stored payload |
| Boot start | `BOOT_COMPLETED` receiver (optional) |

**Permissions.** `SYSTEM_ALERT_WINDOW` (overlay), `FOREGROUND_SERVICE` (+ `specialUse` type on Android 14+), `POST_NOTIFICATIONS` (Android 13+), `CAMERA` (only when scanning), `RECEIVE_BOOT_COMPLETED` (optional). **No `INTERNET` permission**, which is itself a privacy guarantee users can verify.

**Performance.** Drawer opens in < 300 ms; QR bitmaps cached in memory; service RAM < 40 MB; negligible battery (no polling, no wakelocks except while a QR is shown).

**Security and privacy.** All data stays on the device. Optional biometric lock on the manager screen prevents someone swapping a QR. `FLAG_SECURE` is optional for the manager screen (off for the viewer, since showing it is the point).

## Edge cases and risks

| Case / risk | Handling |
| --- | --- |
| Overlay permission denied | Onboarding explains why, deep-links to the setting; app still works as a normal app without the bubble |
| OEM battery savers (Xiaomi, Oppo, Vivo, Samsung) kill the service | Guide user to "No restrictions" / autostart settings on first run; restart service on app open |
| Screenshot has no decodable QR or several QRs | Show error, or let user pick which QR; fall back to storing the cropped image |
| QR is not UPI (e.g. a bank-specific or merchant QR) | Store and show as-is; UPI fields left blank |
| Bubble covers a button in another app | Drag to move, or hide via × target / notification |
| Someone swaps my QR while phone is unlocked | Biometric lock on the manager screen (F5) |
| Empty state (no QRs yet) | Drawer shows one "Add your first QR" card |
| Phone change / reinstall | Export/import JSON (P2); data otherwise lost by design |

## Open questions

- [ ] v2: dynamic QRs with a pre-filled amount (upi://pay?…&am=500)? Easy to generate offline from the stored UPI ID.
- [ ] Home-screen widget and Quick Settings tile as extra entry points?
- [ ] Personal use only, or publish on Play Store? Play policy scrutinises overlay + foreground "specialUse" apps, so a listing needs a clear justification.

## Milestones

1. **M1: Core** — manager screen, add from gallery/camera, local storage, full-screen viewer.
2. **M2: Bubble** — overlay service, draggable bubble, drawer, notification, hide/restore.
3. **M3: Polish** — reorder, colours, swipe between QRs, default QR long-press, OEM battery guidance.
4. **M4: Safety** — biometric lock, export/import backup.
