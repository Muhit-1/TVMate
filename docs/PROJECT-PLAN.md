# TV Remote + Free TV & Radio — Project Plan

Android phone app that works as a Wi-Fi TV remote and lets users pick free live TV channels (iptv-org) and internet radio (Radio Browser) to play on the TV.

## Scope
- Android first (phone app + Android TV / Google TV app). Wi-Fi only. No IR, iOS or Apple TV in v1.
- Route: **companion TV app** — phone finds TV, user picks channel/station, phone sends it, TV app plays it. Remote keys use Android TV Remote v2 (PIN pairing).
- Other brands (Samsung, LG, Roku): remote-only, later.

## Architecture
| Module | Purpose |
|---|---|
| `phone/` | Compose UI: discovery, pairing, remote, browse, favorites, now-playing control |
| `tv/` | Compose for TV: command receiver, full-screen player, radio now-playing, D-pad channel list |
| `shared/` | Models, command protocol, iptv-org + Radio Browser clients, Room cache, DataStore |

Phone ↔ TV: NSD (mDNS) discovery, then WebSocket/HTTP on LAN for play/pause/next/volume/play-URL commands. Remote keys: Android TV Remote v2 (TLS + protobuf) ported to Kotlin; pairing saved so PIN is entered once.

## Tech stack
Kotlin · Jetpack Compose · Compose for TV · Media3/ExoPlayer · OkHttp + Retrofit + kotlinx.serialization · Room · DataStore · Android NSD · WorkManager · Media3 session + foreground service (radio).
Skipped for now: backend, accounts, Firebase, libVLC, Flutter/RN, iOS, ads/analytics, casting.

## MVP features
**Phone:** auto-find TVs, PIN pairing, remote (D-pad, OK, Back, Home, Vol, Mute, Power), browse/search/filter TV (country/category/language) and radio (country/language/genre), favorites + recents, send to TV, playback control, settings.
**TV:** receive commands, full-screen live player, radio now-playing, D-pad channel list, keeps playing without phone.
**Data:** iptv-org API + Radio Browser, cached locally, daily refresh.

## Must-handle checklist
- [ ] Stream `referrer` / `user_agent` headers
- [ ] Geo-blocked / not-24/7 labels (show/hide)
- [ ] Hide NSFW, closed, blocklisted
- [ ] Dead streams: detect, retry, fallback stream, user can hide
- [ ] Cleartext HTTP allowed
- [ ] Radio: `hidebroken=true`, working-server selection, background play, lock-screen controls
- [ ] TV off / Wi-Fi lost / reconnect; persistent pairing
- [ ] Local network / nearby devices permission
- [ ] Low-RAM TV (~2 GB)
- [ ] Clear errors, no crashes on bad streams
- [ ] No bundled playlist, disclaimer, takedown contact, neutral store listing

## Phases
| # | Scope | Estimate |
|---|---|---|
| Setup | Plan, toolchain, project skeleton | now |
| Design | Mock design (phone + TV screens) — approved: "Tuner Dial", `design/b-tuner-dial-v2.html` | done |
| Skeleton | Android project: `phone/`, `tv/`, `shared/`, Gradle version catalog, theme from the design | done |
| 0 + data | PoC playback + data layer (iptv-org, Radio Browser, Room cache, filters) | done, phone-tested |
| 1 | TV player + phone app: discovery, pairing, browse, send to TV, playback control, real design screens | built, needs device test |
| 0 | Proof of concept: load iptv-org, play one channel + one station | 2–3 days |
| 1 | TV player + phone app: discovery, browse, send to TV | 3–4 weeks |
| 2 | Full Android TV remote on phone (Remote v2: pairing, keys, volume, power) | built, needs TV test |
| 3 | Polish: favorites, errors, dead streams, radio background | 1–2 weeks |
| 4 | Samsung / LG / Roku remote-only | 2–4 days each |
| 5 | Cast / DLNA, EPG, backend mirror, extras | as needed |

MVP = phases 0–3, about 5–8 weeks.

## Later
Samsung/LG/Roku remotes, Cast & DLNA, EPG (XMLTV), backend mirror with health checks, report-broken button, TV text input, deep links, voice search, M3U import, multiple TVs, sleep timer, PiP, widget, themes, iOS.

## Data sources
- https://iptv-org.github.io/api/ · https://iptv-org.github.io/iptv/index.m3u · https://github.com/iptv-org/iptv
- https://www.radio-browser.info/
- Android TV Remote v2 references: `androidtvremote2` (Python), `androidtv-remote` (Node)

## Open questions
Final app name · personal vs Play Store release · which brands after Android TV · confirm companion-app route.

## Setup checklist (this step)
- [x] Plan read and written up
- [x] JDK 17 (Temurin), Android SDK at `%LOCALAPPDATA%\Android\Sdk` (platform-tools, platform 35, build-tools 35.0.0), `ANDROID_HOME` set
- [x] Android Studio installed
- [ ] Project skeleton + Gradle version catalog with the dependencies above (after mock design, when module names are final)
