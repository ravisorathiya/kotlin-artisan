# Skill: Ads (`:core:ads`): placements, More Apps fallback, interstitial pacing, app open, splash

All ads live in one Android library module, `core/ads/`, created by `artisan make:ads` (its `README.md` has the API).
It sits on the Firebase module:
- ad units and switches are keys in `AdsConstant`;
- every ad fires a `firebaseEvent`;
- errors go through `logging`.

Use this skill for any task that adds, moves, removes or debugs an ad.

**First check what this project has:**
- `core/ads/` present: use it.
- No ads module, but `core/firebase/`: run `artisan make:ads`.
- An older app with ad code copied per Activity (`googleBannerAd()`, `loadMoreAppBannerAd()` in every screen): don't add
  a second copy. Use the module for new placements, and migrate old ones only when the task says so (`ASK DEV`).

## 1. One rule for every placement

| `onlyShowMoreApp...` | `show...` | Google ad | Shows |
|---|---|---|---|
| `"yes"` (feed has apps) | any | not loaded | More Apps (forced) |
| `"no"` | `"yes"` | loads | Google |
| `"no"` | `"yes"` | fails, `showMoreApp...` = `"yes"` | More Apps (fallback) |
| otherwise | | | hidden (the container collapses) |

The rule lives in `Ads.decide` and every format uses it. Never re-implement it in a screen.

## 2. Adding a placement (banner or native)

1. **Keys, in `AdsConstant`** (the Firebase module), under the screen's section:
   ```kotlin
   // HOME: HomeActivity
   var homeNativeAd by remoteAdUnit("/21775744923/example/native")   // Google test id until release
   var showHomeNativeAd by remoteString("yes")
   // optional: this placement's own More Apps pair (else the app-wide showMoreAppNBanner / onlyShowMoreAppNBanner)
   var showMoreAppHome by remoteString("yes")
   var onlyShowMoreAppHome by remoteString("no")
   ```
2. **A container in the layout:** a `FrameLayout`, plus a shimmer if the screen has one. The module builds the ad views.
3. **One call in the screen:**
   ```kotlin
   NativeAds.show(this, "home", binding.flNative, AdsConstant.homeNativeAd, AdsConstant.showHomeNativeAd, big = false,
       moreAppFallback = AdsConstant.showMoreAppHome, onlyMoreApp = AdsConstant.onlyShowMoreAppHome, loadingView = binding.shimmer)
   ```
   Banner: `BannerAds.show(this, "home", binding.flBanner, AdsConstant.homeBannerAd, AdsConstant.showHomeBannerAd)`.
   Compose: `BannerAd("home", ...)` / `NativeAd("home", ...)`.
4. **Events in event.md**, under the screen's section: `home_nat_load`, `home_nat_show`, `home_nat_fail`,
   `home_nat_click`, `home_more_show` and `home_more_click`. Use `artisan make:event <Screen> <name>` for each.
   - Banner placements use `_banner_` instead of `_nat_`.
   - The placement name is short and snake_case (`main`, `lang`, `exit`, `home`), so every event stays under 30 characters.
5. **Tell the developer the console keys to add:** the same names, with their defaults.

## 3. Interstitial

```kotlin
Interstitial.show(this, from = "home_to_detail") { startActivity(detailIntent) }
```

- **Put the next step inside the callback.** It always runs exactly once: after the ad closes, or right away when no ad
  shows. Never start the next screen outside it.
- **Pacing comes from `AdsConstant`:**
  - `googleInterMaxInterAdsShow`: at most this many per session;
  - `googleInterGapBetweenTwoInter`: calls skipped between two ads;
  - `googleInterCountDownTimer`: ms after one closes.

  `force = true` ignores the gap and the cooldown (never the session limit). Use it only where the developer asked.
- **`from` names the place** (bundle param of `inter_show` / `inter_skip`). Keep it stable across releases.

## 4. App open and splash

- **The app-open ad** shows when the app comes back to the front. To keep it off a screen:
  - add the screen to `Ads.init(this, excluded = listOf(...))`: the splash, payment and call screens;
  - or call `Ads.suppressAppOpen()` before leaving the app on purpose (share sheet, camera, file picker, a browser).

  Ad clicks and More Apps clicks already suppress it.
- **Splash:** `SplashAd.show(this) { goNext() }`, the only way the splash continues. With `onlineSplashAppOpen` = `"yes"`
  it waits for Remote Config first (inside the timeout).

## 5. More Apps

- **The feed:** `moreAppUrl` + `moreAccountName` (keys in `AdsConstant`). It loads after Remote Config answers and
  reloads when either key changes.
- **Its events:** `more_apps_load`, `more_apps_success` (with `count`) and `more_apps_fail`.
- **Restyle once** in `MoreAppsViews` (`accent`, `installText`) and `NativeAds.Style`. Never per screen.
- **Plain `http://` URL:** the app needs a network security config entry for that host. Suggest `https://`.

## 6. Checklist (any ad change)

1. Keys are in `AdsConstant` with their switch, and the screen makes one `show` call. No ad SDK calls in screens.
2. The placement's events are in event.md (`artisan check:events`).
3. The ad ids in code are Google's test ids. Real ids come from the console or the developer (`ASK DEV`).
4. Build: `gradlew :core:ads:compileDebugKotlin`, then the app.
5. **VERIFY ON DEVICE** with the event box or `artisan app:logcat GlobalFirebaseEventNameTag`. Check four cases:
   - Google shown;
   - Google failing to More Apps (turn off the network after the feed loads);
   - forced More Apps (`onlyShowMoreApp... = "yes"` in the console);
   - hidden (`show... = "no"`).

## 7. Gotchas

- **`AdsConstant.APP_ID`** is Google's test app id until release. The SDK won't serve real ads with a wrong pairing.
- **Classic and next-gen SDKs clash.** Other SDKs can pull in `com.google.android.gms:play-services-ads`, the classic
  SDK, which conflicts with the next-gen one. If a build reports duplicate ad classes, exclude it in the app:
  `configurations.all { exclude(group = "com.google.android.gms", module = "play-services-ads") }`, and
  `play-services-ads-lite` too.
- **Native ads are destroyed with their activity.** A Fragment placement should use `requireActivity()` and a container
  that lives as long as the view.
- **Never click your own live ads** while testing.
