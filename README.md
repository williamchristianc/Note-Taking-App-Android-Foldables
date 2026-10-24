# Folio: foldable notebook for Android (Phase 1 / v0.1.0)

Kotlin + Jetpack Compose + Room + DataStore. Offline-first, no account, no network permission.

## Status, honestly
The source was written without access to an Android SDK, so **it has not been compiled or run yet**.
Expect to fix a few compile errors on the first build. Nothing here has been tested on a Honor Magic V6.

## Build the APK with GitHub (no Android Studio needed)
1. Create a new empty repository on github.com.
2. Upload this folder's contents, including the hidden `.github` folder (drag the whole folder in the web UI on a computer, or use git: `git init && git add . && git commit -m init && git branch -M main && git remote add origin <url> && git push -u origin main`).
3. Open the repo's **Actions** tab > **Build APK** (it also runs on every push; use "Run workflow" if needed).
4. When it is green, open the run and download the **folio-debug-apk** artifact (a zip containing `app-debug.apk`).
5. Copy the APK to the phone, open it, and allow "install unknown apps" for your file manager or browser.
If the run is red, open the failed step and send me the log.

## Build locally (Android Studio)
Open the folder in Android Studio (Ladybug or newer, JDK 17), let it sync, press Run. Or `gradle assembleDebug` with Gradle 8.9.

## Signed release APK
```
keytool -genkeypair -v -keystore folio.jks -alias folio -keyalg RSA -keysize 2048 -validity 10000
gradle assembleRelease
zipalign -p 4 app/build/outputs/apk/release/app-release-unsigned.apk folio-aligned.apk
apksigner sign --ks folio.jks --out folio-release.apk folio-aligned.apk
```
Keep `folio.jks` private and backed up. Never commit it.

## Implemented in this version
- Library: gallery shelf and list views, pin, duplicate, archive, trash/restore, manual reorder (move earlier/later), sort by title/activity/created, "continue writing", global text search.
- Notebook creation/editing: 5 cover materials, 8 colours, custom cover image (photo picker), title typeface, elastic band, bookmark ribbon, paper style.
- Full-screen cover, swipe/tap-to-open 3D animation with haptics, page-turn slide with tilt, reduced-motion option.
- Adaptive layout by window width: single page on narrow windows, two-page spread with spine on wide ones (decided from window size, so it also works in split-screen). Text, cursor and unsaved strokes live in state and the activity handles config changes itself, so folding/unfolding does not recreate it.
- Typed pages with paper backgrounds (blank, lined, dotted, graph, Cornell, daily journal); 4 page fonts.
- Vector ink: pen, marker, stroke eraser, undo/redo, stylus/finger, stylus-only mode, page swipe disabled while drawing. Strokes stored as normalised vector data, not images.
- Autosave (500 ms debounce plus save on leaving a page), Room database.
- PDF export, Markdown export, full JSON backup and restore (includes cover images and strokes) through the system file picker.
- Immersive mode, edge-to-edge, IME-aware layout, dark/light theme.

## Not implemented yet (limitations)
- Block editor (headings, checklists, toggles, tables, "/" menu), nested pages, tags, backlinks, internal links, tasks.
- Folders/collections and drag-and-drop reordering (reorder uses menu actions).
- Free-placed text boxes, image attachments inside pages, stroke selection/resize/move, zoom and pan.
- Jetpack Ink API (a custom Canvas renderer is used), pressure sensitivity, true page curl.
- Hinge/tabletop posture handling (WindowManager FoldingFeature), home-screen widgets.
- Handwriting font, recognition, sync. There is no cloud sync of any kind.
- Text on a page that overflows scrolls, but the ruled lines do not scroll with it.
- Paper stays light in dark mode; strokes keep their original colours.
