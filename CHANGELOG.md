# Loki changelog

Loki is a fork of [NewPipe](https://github.com/TeamNewPipe/NewPipe), licensed under GPL-3.0.
This file lists only what Loki changes on top of NewPipe.

## 0.29.3-loki.4 (unreleased)

### Added
- **Download playlist**: a download button at the top of every playlist screen (online
  playlists and the ones saved in the app). It asks "Audio only" or "Video", then downloads every
  item of the playlist, not only the part loaded on screen.
  - Uses the default format and quality from the download settings.
  - Skips songs whose file is already in the download folder, so running it again later only
    fetches what was added to the playlist since. A video listed twice is downloaded once.
  - Deleted, private or blocked videos are skipped without stopping the rest; a message at the
    end says how many were added, already saved and unavailable.
  - Downloads appear one by one on the normal Downloads screen, where they can be paused or
    retried as usual.

### Changed
- The playlist screen's **"Play all" button is now "Driving mode"**. It plays the whole list in
  the background in shuffle mode, starting from a random song, and opens driving mode.
  The same button appears on channel video lists and on the history screen.
- The About page links to Loki's source code: https://github.com/ZenFrogStudio/loki
- When a list starts already shuffled, the shuffle buttons now show "on" right away, and the
  first tap turns shuffle off (it used to reshuffle instead).

## 0.29.3-loki.3

Full rebrand: nothing a user sees says NewPipe any more, except the credit that the licence
requires.

### Changed
- **App text** says Loki in English and in every translation (130 text files). The one
  exception is the settings-import warning, which names a real NewPipe version number.
- **About page** now shows one link, "Based on NewPipe", crediting the original app.
  NewPipe's FAQ, website, donation and privacy policy entries are gone.
- **Side menu**: the "Donate" entry is removed, and the "pipes" header picture is replaced by
  a soft green glow.
- **Crash reports** can no longer be emailed to the NewPipe team or opened on NewPipe's issue
  page. Both buttons stay hidden until Loki has its own addresses.
- **File and folder names**: backups are saved as `LokiData-<date>.zip`, subscription exports
  as `loki_subscriptions_<date>.json`, and the default download folder is called `Loki`.
  Backups made by NewPipe can still be imported.
- **Images**: new Android TV banner and update-notification icon.
- **README and store listing** are rewritten for Loki. NewPipe's listing in 91 other
  languages, its per-release changelogs and its screenshots are removed.
- NewPipe's donation config, issue forms, contributor guide, translated READMEs and logo
  source files are removed from the project.

### Added
- `assets/loki_rebrand.mjs`: redoes the renaming and clean-up in one step. Run it after
  merging a new NewPipe release.
- Store artwork: `icon.png` and `featureGraphic.png`, drawn from the logo files.

### Left empty on purpose (fill in before publishing, see the README)
- Address of Loki's public source code, crash report addresses, privacy policy page.

## 0.29.3-loki.2

### Added
- **Loki logo and app icon**: an "L" with a play triangle in neon green on charcoal. It replaces
  NewPipe's logo on the launcher icon, splash screen, menu header, notifications and About page.
  The master files are `assets/loki_icon.svg` and `assets/loki_logo.svg`; every copy is drawn
  by `assets/loki_logo_generator.mjs`.
- A unit test for the driving mode tap/swipe rule (`DrivingGestureTest`).

### Changed
- App renamed to **Loki** (earlier builds were called Thor). The app ID is now `com.loki.app`,
  so it installs as a new app next to any Thor build.
- **Colours**: NewPipe's red is replaced by charcoal grey with a neon green gradient.
  - Toolbars, status bar and splash screen are charcoal.
  - Switches, checkboxes and sliders are green (neon on dark themes, deep green on the light one).
  - Progress bars fade from deep green to neon green, including the one in driving mode.
  - The video player's seek bar is neon green.
  - In driving mode, shuffle turns neon green when it is on.
- SoundCloud, PeerTube, Bandcamp and media.ccc.de keep their own toolbar colours.
- The red "LIVE" badge stays red on purpose, because red is what people expect for live.

## 0.29.3-loki.1

Based on NewPipe 0.29.3. This build was installed under the working name Thor.

### Added
- **Driving mode**: a full-screen, audio-only control screen with very large touch targets.
  - Three giant tap zones: previous, play/pause, next.
  - Large shuffle button (dim = off, bright = on).
  - Blurred cover image behind the controls; the screen stays awake.
  - Open it from the car icon in the video player's top bar, or from the play queue screen.
  - Swipe right/left anywhere on the zones to skip forward/back by the seek duration from
    the settings (10 seconds by default). Unclear movements are ignored on purpose.

### Changed
- App name is now Loki, with its own app ID (`com.loki.app`), so it installs next to NewPipe.
