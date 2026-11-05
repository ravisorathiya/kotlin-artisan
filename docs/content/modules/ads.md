# Ads module

Banner, native, interstitial, app open and splash ads, plus the **More Apps** fallback (your own cross-promotion). It
uses Google Mobile Ads (the next-gen SDK) and runs on the Firebase module (`:core:firebase`):
- every ad unit and switch is a key in `AdsConstant` there, so the console can change them;
- every ad logs a `firebaseEvent`;
- errors go through `logging`.

Create it with `artisan make:ads` (a walkthrough: [Add ads with a fallback](#/recipes/ads)). The [Firebase module](#/modules/firebase) comes first. Code: package `com.example.app.ads`.

```text
com.example.app.ads
├── Ads.kt                 Ads.init(app), suppressAppOpen(), onPaid, the placement rule
├── banner/BannerAds.kt    BannerAds.show(activity, placement, container, adUnit, show, ...)
├── nativead/NativeAds.kt  NativeAds.show(..., big = false/true): native banner or big native
├── inter/Interstitial.kt  Interstitial.show(activity, from) { next() }: max / gap / cooldown from AdsConstant
├── appopen/AppOpenAds.kt  the app-open ad when the app comes back to the front
├── appopen/SplashAd.kt    SplashAd.show(activity) { next() }
├── moreapps/              the More Apps feed (moreAppUrl + moreAccountName) and its views
└── compose/AdsCompose.kt  BannerAd(...), NativeAd(...) composables (when the module has Compose)
```

## Set up (once)

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Analytics.setIcon(R.mipmap.ic_launcher).init(this)   // the Firebase module first: Remote Config holds the ad keys
        Ads.init(this, excluded = listOf(SplashActivity::class.java))   // no app-open ad over the splash
        Ads.onPaid = { value -> /* send to your revenue SDK */ }
    }
}
```

`AdsConstant.APP_ID` is the AdMob app id; replace Google's test id before a release.

## Every placement follows one rule

| `onlyShowMoreApp...` | `show...` | Google ad | Result |
|---|---|---|---|
| `"yes"` (and the feed has apps) | any | not loaded | **More Apps** (forced) |
| `"no"` | `"yes"` | loads | **Google ad** |
| `"no"` | `"yes"` | fails, `showMoreApp...` = `"yes"` | **More Apps** (fallback) |
| `"no"` | `"yes"` | fails, `showMoreApp...` = `"no"` | hidden |
| `"no"` | `"no"` | not loaded | hidden |

Banner and native placements use the app-wide pair `showMoreAppNBanner` / `onlyShowMoreAppNBanner` unless you pass
the placement's own pair (e.g. `showMoreAppLanguage` / `onlyShowMoreAppLanguage`).

## Banner

```kotlin
BannerAds.show(this, "main", binding.flBanner, AdsConstant.mainBannerAd, AdsConstant.showMainBannerAd,
    loadingView = binding.shimmerBanner)                       // optional: your shimmer, hidden when done
```

- An adaptive banner, as wide as the container.
- The slot hides when nothing shows. `onResult = { source -> }` tells you GOOGLE / MORE_APPS / NONE.

## Native

```kotlin
NativeAds.show(this, "lang", binding.flNative, AdsConstant.languageSmallNativeAd, AdsConstant.showLanguageNativeAd,
    big = false, moreAppFallback = AdsConstant.showMoreAppLanguage, onlyMoreApp = AdsConstant.onlyShowMoreAppLanguage)
```

- `big = true` adds the media view. The native ad is destroyed with the activity.
- The views are built in code: restyle `NativeAds.Style` and `MoreAppsViews` (colors, button text) once for the whole app.

## Compose

```kotlin
BannerAd("main", AdsConstant.mainBannerAd, AdsConstant.showMainBannerAd)
NativeAd("home", AdsConstant.homeNativeAd, AdsConstant.showHomeNativeAd, big = true)
```

## Interstitial

```kotlin
Interstitial.show(this, from = "home_to_detail") { startActivity(detailIntent) }   // next step: always runs, once
```

| Key | Effect |
|---|---|
| `interstitialAds` | the ad unit |
| `googleInterMaxInterAdsShow` | at most this many per session |
| `googleInterGapBetweenTwoInter` | this many `show` calls skipped between two ads |
| `googleInterCountDownTimer` | no ad for this long (ms) after one closes (`force = true` ignores it, and the gap) |

The ad is preloaded after Remote Config answers, and again after each one.

## App open and splash

- **App open:** shows when the app comes back to the front. It's skipped:
  - over excluded activities;
  - while another full-screen ad shows;
  - right after an ad or More Apps click.
- **Leaving the app on purpose** (share, camera, browser): call `Ads.suppressAppOpen()` first.
- **Splash:** `SplashAd.show(this) { goNext() }`.
  - It shows `splashAppOpenAd` if `showSplashAppOpenAd` = `"yes"`, waiting at most 8 s.
  - With `onlineSplashAppOpen` = `"yes"`, it first waits for Remote Config, so the console's unit and switch apply.

## More Apps

- **The feed:** `POST <moreAppUrl>index.php` with `account_name = moreAccountName`.
  - It runs after Remote Config answers, and again if the URL or account changes in the console.
  - This app is left out of the list, and each placement takes the next app in turn.
- **On click:** it opens `app_link` and skips the next app-open ad.
- **Plain HTTP:** `moreAppUrl` over `http://` needs a network security config entry in the app. `https://` doesn't.

## Events

- Per placement:
  - `<placement>_banner_load / _show / _fail / _click`;
  - `<placement>_nat_load / _show / _fail / _click` (bundle `size`);
  - `<placement>_more_show / _more_click`.
- Keep placement names short and snake_case (`main`, `lang`, `exit`, `home`), so the events stay under 30 characters.
- Add each placement's rows to event.md with `artisan make:event`. The fixed events (`inter_*`, `appopen_*`,
  `splash_ad_*`, `more_apps_*`, `ads_init`) are already there.

## Rules

- Ads code lives here. A screen makes one call per placement and doesn't copy load or fallback code.
- Test ids until release. Never click your own live ads.
- A new format or placement type goes in its own folder here (e.g. `rewarded/`), following the same rule and events.

## Remove it

```sh
artisan purge ads
```

It removes the module, its include and dependency lines, the catalog entries nothing else uses and its events in event.md. Calls you added (`BannerAds.show(...)`) stop it, with each `file:line` listed. See [purge](#/commands/tools).
