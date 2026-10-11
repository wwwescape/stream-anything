<p align="center">
  <img src="assets/logo.svg" alt="Stream Anything logo" width="120" />
</p>

<h1 align="center">Stream Anything</h1>

<p align="center">
  Paste a video link, press play. A clean, minimal video player for Android phones, tablets, and
  Google TV, built with Material 3 to feel at home alongside Google's own apps. No backend, no
  accounts, no analytics.
</p>

<p align="center">
  <a href="https://github.com/wwwescape/stream-anything/releases"><img src="https://img.shields.io/github/v/release/wwwescape/stream-anything.svg?style=flat-square" alt="GitHub release" /></a>
  <a href="https://github.com/wwwescape/stream-anything/commits/master"><img src="https://img.shields.io/github/last-commit/wwwescape/stream-anything.svg?style=flat-square" alt="GitHub last commit" /></a>
  <a href="https://github.com/wwwescape/stream-anything"><img src="https://img.shields.io/github/languages/code-size/wwwescape/stream-anything.svg?color=red&style=flat-square" alt="GitHub code size" /></a>
</p>

## Features

- **One field, one button** — paste a link and the video starts playing immediately. See
  [Supported services](#supported-services) for what it can play.
- **Share or "Open with"** — share a link from any app, or open a supported link with Stream
  Anything, and it goes straight to the player.
- **A minimal player** — auto-hiding controls, a slim seek bar with buffered progress, playback
  speed (0.5×–2×), Fit / Zoom / Stretch scaling, double-tap to skip, and the video's own
  thumbnail while it buffers. Orientation follows the video.
- **Smooth seeking** — dragging the seek bar or pressing left/right previews the target time in
  a bubble above the thumb (with how far you're jumping) and only seeks once you let go or stop
  pressing, so a long scrub costs one rebuffer instead of dozens. Holding left/right on a remote
  speeds up the longer you hold; Back cancels a scrub. Phones tick gently at every tenth of the
  bar.
- **Made for Google TV too** — D-pad first: OK plays/pauses, left/right scrub, Back hides the
  controls; bold focus highlights readable from the couch; remote media keys and Google
  Assistant work through a media session.
- **Resume where you left off** — reopening a video you didn't finish picks up where you stopped,
  with a quick **Start over** if you'd rather not. Remembers your last 50 videos; can be turned
  off or cleared in **Settings → Playback**.
- **Recent history (experimental)** — turn on **Settings → Advanced → Experimental → Show
  History** to list your last 20 links under the link field, with watched / total time and a
  **Resume** or **Rewatch** label. Tap one to play it again, or remove it with the delete button
  beside it.
- **Play on TV (experimental)** — turn on **Settings → Advanced → Experimental → Play on TV** on
  your phone and your Google TV, keep Stream Anything open on the TV, and a TV button appears
  next to Play: the video starts on the TV, picking up where you stopped on the phone. Already
  watching on the phone? Tap **Continue on TV** in the player and it carries on from the same
  spot. With a paired TV around, tapping a Recent history entry asks whether to watch on the
  phone or the TV. It works the other way too: with Stream Anything open on your phone,
  **Continue on phone** in the TV's player (or **Play on phone** next to Play, or from Recent history on the TV) carries the video on to your paired phone. It works
  directly over your Wi-Fi (no Chromecast, no account, no server). The first time, the TV shows
  a 6-digit code to type on the phone; after that, only paired phones can send to it. If your
  router stops the phone from finding the TV on its own, pair from **Pair with a TV** on the
  phone by typing the address the TV shows in its Experimental settings; both devices remember
  each other's address from then on.
- **Recovers on its own** — if a link expires mid-video (e.g. after a long pause), a fresh one
  is fetched and playback resumes where it stopped.
- **Material 3 design** — light, dark, and system themes, dynamic color (Material You), 16+
  curated color themes, adjustable contrast, and Pure Black / Absolute Black modes.
- **Languages** — English, Spanish, French, Hindi, and Portuguese.

## Supported services

| Service | Link formats | Status | Restrictions / notes |
|---|---|---|---|
| **Streamtape** | `streamtape.com/v/{id}`, `/v/{id}/`, `/v/{id}/{filename}`, `/e/{id}` and its mirrors (`advtpe.com`, `strtape.cloud`, `streamta.pe`, …) | ✅ Tested | The video link is tied to your device's IP and expires; the app fetches a fresh one automatically if playback stops. |
| **Direct links** | Any `https://…` URL ending in `.mp4`, `.m4v`, `.webm`, `.mkv`, `.mov`, `.3gp`, `.avi`, `.ts`, `.mpg`, `.flv`, or `.m3u8` (HLS) | ✅ Tested | The server must allow plain HTTPS downloads (no login or cookies). Plain `http://` works only for home-network IP addresses (e.g. `http://192.168.0.20/…`). MKV/HEVC playback depends on the device's decoders. |
| **[Stream Anything Server](https://github.com/wwwescape/stream-anything-server)** | `http://<computer's IP>:<port>/media/<library>/<file>?key=…`, as handed out by the server's browse page | 🧪 Untested | Your own videos shared from a computer on the same home network. Plain HTTP, only to home-network IP addresses; the link carries the server's access key. |
| **BigWarp** | `bigwarp.io/{id}`, `/e/{id}`, `/embed-{id}.html` (+ `bgwp.cc`) | 🧪 Untested | — |
| **DoodStream** | `dood.to/d/{id}`, `/e/{id}` and its mirrors (`dood.la`, `ds2play.com`, `myvidplay.com`, …) | 🧪 Untested | Every Dood link is fetched through its current mirror (playmogo.com). Dood's domains are blocked by some ISPs and sometimes sit behind a browser check; when that happens the video can't be loaded. |
| **LuluStream** | `lulustream.com/{id}`, `/e/{id}`, `/d/{id}` (+ `luluvdo.com`, `lulu.st`, …) | 🧪 Untested | — |
| **Mixdrop** | `mixdrop.ag/f/{id}`, `/e/{id}` and its numbered mirrors | 🧪 Untested | Links expire quickly; the app re-fetches if needed. |
| **StreamWish** | `streamwish.to/e/{id}` and its many `*wish*` mirrors (`embedwish.com`, `hglink.to`, …) | 🧪 Untested | Fetched through StreamWish's current mirror whatever domain the link uses. |
| **Veev** | `veev.to/e/{id}`, `/d/{id}` | 🧪 Untested | Veev changes its link encoding often; expect occasional breakage until the app is updated. |
| **VidHide** | `vidhide.com/v/{id}`, `/e/{id}`, `/f/{id}` (+ old FileLions domains) | 🧪 Untested | Links on retired domains are fetched from a current mirror. |
| **VidNest** | `vidnest.io/{id}`, `/e/{id}`, `/embed-{id}.html` | 🧪 Untested | — |
| **VOE** | `voe.sx/{id}`, `/e/{id}` (+ known mirrors) | 🧪 Untested | VOE redirects through ever-changing random-name domains; only the listed ones are recognized. If a link isn't accepted, use its `voe.sx` form. |
| **Filemoon** | — | ❌ Not supported | Playback now requires passing a captcha / proof-of-work bot check. The app doesn't bypass bot protection. |
| **Netu** (hqq / waaw) | — | ❌ Not supported | Every video requires solving an image captcha, and its main domains currently redirect elsewhere. |
| **VidGuard** | — | ❌ Not supported | Its domains no longer resolve, and there's no maintained reference for its player. |

✅ verified end-to-end on a device · 🧪 implemented from the host's current page format and
covered by unit tests, but not yet played on a device with a real link · ❌ deliberately not
supported.

Video hosts change their pages without notice; when one does, Stream Anything shows "The video
site sent back something unexpected" rather than guessing. See
[Adding a video service](#adding-a-video-service) for how resolvers work.

## Installation

Download the APK from the [latest release](https://github.com/wwwescape/stream-anything/releases/latest)
and install it on your phone, tablet, or Google TV / Android TV device.

Requires Android 8.0 (API 26) or newer.

## Privacy

Stream Anything has no backend, no analytics, and no crash-reporting SDKs. A link you play is
only sent to the site hosting the video, to fetch the page and stream the video.

To resume videos, it remembers where you stopped in your last 50 — stored as one-way hashes
with a position, never the links or titles, so nothing can list what you watched. The one
exception is the opt-in, experimental **Show History**: while it's on, the links are saved too
so they can be listed, and turning it off removes them again. Either way the history stays on
the device (it's excluded from Android backups), and turning **Resume Playback** off or tapping
**Clear Watch History** erases it. Only your preferences are
included in backups.

Internet connections are HTTPS-only. Plain `http://` is allowed only to private home-network IP
addresses (for example a computer running Stream Anything Server); every request and redirect
is checked, so a link can never fall back to plain HTTP on the internet.

**Play on TV** (opt-in, experimental) never leaves your home network: the phone finds the TV
with local network discovery (mDNS) and sends it the link directly. Pairing uses a one-time code
shown on the TV; each pairing's key is kept in the Android Keystore on both devices, every
request is signed with it, and each device only listens while Stream Anything is open on it (a phone only once it's
paired with a TV). See the in-app Privacy statement (**Settings → About**) for details.

Stream Anything doesn't host, index, or provide any content — it only plays links you supply.

## Development

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (Narwhal or newer)
- JDK 17+ (bundled with Android Studio)
- An Android device or emulator running Android 8.0 (API 26) or newer — a Google TV emulator
  image for testing the TV experience

### Build & run

```bash
git clone https://github.com/wwwescape/stream-anything.git
cd stream-anything
./gradlew installDebug
```

Or open the project in Android Studio and run the `app` configuration.

### Adding a video service

Each service is a `StreamResolver` in `data/streams/`: `canResolve(url)` recognizes its links
(offline), `resolve(url)` turns one into a playable `ResolvedStream` (media URL plus any
Referer/Origin headers its CDN requires). Implement one, register it in `StreamResolvers.all`
(before `DirectLinkResolver`, the catch-all), and add its main domains to the `VIEW` intent
filter in `AndroidManifest.xml` if it should appear under "Open with".

Shared building blocks: `JsUnpacker` (P.A.C.K.E.R.-packed scripts), `PageScraper` (JW Player
`sources` / `links` configs), and `JwPlayerHostResolver` (most XFileSharing-style hosts are a
one-object configuration of it). The host-specific decoders are ported from the open-source
[ResolveURL](https://github.com/Gujal00/ResolveURL) project and unit-tested against vectors
produced by its reference implementations.

### Test

```bash
./gradlew lint testDebugUnitTest
```

### Regenerate icons

```bash
python scripts/generate_icons.py
```

### Release a new version

Bump `versionCode` and `versionName` in `app/build.gradle.kts`, commit, then:

```bash
git tag v0.1.0
git push origin v0.1.0
```

The tag push builds a signed release APK and AAB and attaches them to a new GitHub Release (see
`.github/workflows/release.yml`). It needs the `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, and `KEY_PASSWORD` repository secrets, which are kept locally in the gitignored
`keystore.properties`.

### Project layout

```
app/       Kotlin, Jetpack Compose (Material 3), single module
  data/        Settings and resume history (DataStore), stream resolvers for each service,
               Play on TV (tvlink: NSD discovery, pairing, signed local requests)
  ui/player/   Full-screen player Activity, Media3/ExoPlayer, custom TV-friendly controls
  ui/          Compose screens, navigation, and theme
design/    Play Store icon and TV banner assets
scripts/   Icon generator
assets/    README assets
```

## License

GPL-3.0 — see [LICENSE](LICENSE).

## Support

If you find Stream Anything useful, consider buying me a coffee:

[<img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" height="40" />](https://buymeacoffee.com/wwwescape)
