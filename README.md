# Lite Launcher

A fast, clean, ad-free Android home screen. Pure Android Java — no libraries, no ads, no trackers, **no internet permission**.

## Features
- Free-placement home grid with folders (custom name, icon, folder & label color, detailed color picker)
- Search all apps + built-in calculator in the search bar
- Quick panel with your most-used apps
- Widgets page (resize, reorder, configure)
- Daily page: tasks that carry over, repeating daily tasks, free notes
- Photos page: crop pictures to any ratio and size and lay them out
- Pages can be turned on/off and reordered
- English, Azerbaijani and Russian (follows phone language)

## Permissions
- `Query installed apps (launcher intent)` — to show and open your apps
- `REQUEST_DELETE_PACKAGES` — to open the system uninstall dialog when you choose "Uninstall"

## Build
Open the folder in Android Studio (AGP 9.2.1, Gradle 9.4.1, compileSdk 35) and press **Run**.
Release builds are minified with R8. To sign a release, put a `keystore.properties` file
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) in the project root — it is git-ignored.

## Privacy
Lite Launcher collects no data. See [PRIVACY.md](PRIVACY.md) or the
[online privacy policy](https://aliaghayev132.github.io/lite-launcher/).

---
**AZ:** Lite Launcher — reklamsız, internetsiz, yüngül Android tətbiqi. Heç bir məlumat toplamır.
**RU:** Lite Launcher — лёгкое приложение для Android без рекламы и без доступа к интернету. Не собирает данные.
