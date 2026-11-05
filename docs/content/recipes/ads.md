# Add ads with a fallback

`make:ads` adds a `:core:ads` module: banner, native, interstitial, app open and splash ads. When the Google ad fails, your own **More Apps** cross-promotion ad shows instead. Every ad unit and switch is a Remote Config key, so the console controls them.

**Before you start:** the [Firebase module](#/recipes/firebase). The ads read their keys from its `AdsConstant` and log through it. If it's missing, `make:ads` offers to create it first.

::: steps
### Create the module

```sh
artisan make:ads
```

It asks which UI you use, with your current setup pre-selected:

```text
Ad UI for this project (detected: views; 1 = recommended)
  1  views     XML views only (AdView / NativeAdView in a FrameLayout)
  2  both      XML views + Compose wrappers
  3  compose   Compose wrappers (+ the views they use)
```

`--ui views|compose|both` skips the question. Then it checks that the app still builds. If a library needs a newer compileSdk than the app has, it steps down to the newest version that fits.

### Start the SDK

In `Application.onCreate()`, after `Analytics...init(this)`:

```kotlin
Ads.init(this, excluded = listOf(SplashActivity::class.java))   // no app-open ad over the splash
```

### Show a banner

Add a container to the layout:

```xml
<FrameLayout
    android:id="@+id/flBanner"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
```

Then make one call:

```kotlin
BannerAds.show(this, "main", binding.flBanner, AdsConstant.mainBannerAd, AdsConstant.showMainBannerAd)
```

In Compose:

```kotlin
BannerAd("main", AdsConstant.mainBannerAd, AdsConstant.showMainBannerAd)
```

The slot hides itself when nothing shows.

### Show an interstitial between screens

```kotlin
Interstitial.show(this, from = "home_to_detail") { startActivity(detailIntent) }
```

The next step always runs exactly once, whether or not an ad showed. How often ads show comes from `AdsConstant` (`googleInterMaxInterAdsShow`, `googleInterGapBetweenTwoInter`, `googleInterCountDownTimer`).

### The splash

```kotlin
SplashAd.show(this) { goNext() }
```

### Control it from the console

Every placement follows one rule:

| `onlyShowMoreApp...` | `show...` | Google ad | Result |
|---|---|---|---|
| `"yes"` | any | not loaded | **More Apps** (forced) |
| `"no"` | `"yes"` | loads | **Google ad** |
| `"no"` | `"yes"` | fails, `showMoreApp...` = `"yes"` | **More Apps** (fallback) |
| `"no"` | `"yes"` | fails, `showMoreApp...` = `"no"` | hidden |
| `"no"` | `"no"` | not loaded | hidden |
:::

::: warning Test ids until release
`AdsConstant.APP_ID` and every ad unit start as Google's test ids. Set the real ones, in the code or in the console, before a release. Never click your own live ads.
:::

## Next

- Name each placement's events: [Track a new screen](#/recipes/events)
- All formats and options: [Ads module](#/modules/ads)
