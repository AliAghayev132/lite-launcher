package az.lite.launcher;

/** Lite Launcher bələdçisi (az / en / ru). Hər element: {başlıq, mətn}. */
final class Help {

    static String[][] sections() {
        switch (L.lang()) {
            case "en": return EN;
            case "ru": return RU;
            default: return AZ;
        }
    }

    private static final String[][] AZ = {
            {"Ana ekran",
                    "• Boş xanaya uzun bas → yeni qovluq, tətbiq qoy, vidcetlər, avtomatik qovluqlar, parametrlər.\n"
                            + "• Tətbiqə və ya qovluğa uzun bas və sürüşdür → istədiyin xanaya köçür (dolu xanada yerlər dəyişir).\n"
                            + "• Uzun bas və tərpətmədən burax → menyu: qovluğa köçür, məlumat, ana ekrandan götür, telefondan sil.\n"
                            + "• ⚡ Sürətli → ən çox açdığın (və ya seçdiyin) 9 tətbiq. Uzun bas → özün seç."},
            {"Qovluqlar",
                    "• Qovluğa bas → aç. \"Redaktə\" ilə ad, ikon, qovluq rəngi və yazı rəngini dəyiş.\n"
                            + "• Rəng seçicidə palitra, dəqiq rəng kvadratı, HEX kod və son rənglər var.\n"
                            + "• Qovluğun içində tətbiqə uzun bas → sola/sağa çək, başqa qovluğa köçür, ana ekrana çıxar, telefondan sil.\n"
                            + "• Yeni quraşdırılan Lite tətbiqləri avtomatik \"Lite\" qovluğuna düşür."},
            {"Səhifələr",
                    "• Sağa/sola sürüşdür: Gündəlik, Şəkillər, Vidcetlər, Ana ekran, Bütün tətbiqlər.\n"
                            + "• Parametrlər → Səhifələr: istənilən səhifəni bağla/aç, « ‹ › » ilə sırasını dəyiş.\n"
                            + "• Home düyməsi həmişə ana ekrana qaytarır."},
            {"Axtarış və tətbiqlər",
                    "• Bütün tətbiqlər səhifəsində yaz → tətbiq tapılır, Enter → birincisini açır.\n"
                            + "• Riyazi ifadə yaz (12*7+3) → nəticə çıxır, basanda kopyalanır.\n"
                            + "• Tətbiqə uzun bas → ana ekrana, qovluğa, məlumat, telefondan sil."},
            {"Gündəlik",
                    "• Bu günün tapşırıqlarını yaz, basanda tamamlanır.\n"
                            + "• Bitməyənlər sabaha keçir. Uzun bas → redaktə, \"hər gün təkrarla\", sıra, sil.\n"
                            + "• Aşağıda sərbəst qeydlər — avtomatik yadda qalır."},
            {"Şəkillər",
                    "• Şəkil əlavə et → istədiyin nisbətdə kəs (sərbəst, 1:1, 4:3, 16:9…), fırlat.\n"
                            + "• Ölçü: tam en, 2/3, yarım, 1/3 — şəkillər sətir-sətir düzülür, aşağı sürüşür.\n"
                            + "• Uzun bas → ölçü, yenidən kəs, yerini dəyiş, sil."},
            {"Vidcetlər",
                    "• \"Vidcet əlavə et\" → tətbiqi seç → vidceti seç.\n"
                            + "• Vidcetə uzun bas → hündürlüyü artır/azalt, yuxarı/aşağı, ayarla, sil."},
            {"Məxfilik",
                    "Lite Launcher-in internet icazəsi yoxdur, reklam və izləmə yoxdur. Bütün məlumatlar yalnız telefonda saxlanır."},
    };

    private static final String[][] EN = {
            {"Home screen",
                    "• Long-press an empty cell → new folder, place an app, widgets, auto folders, settings.\n"
                            + "• Long-press an app or folder and drag → move it to any cell (swaps with an occupied one).\n"
                            + "• Long-press and release without moving → menu: move to folder, info, remove, uninstall.\n"
                            + "• ⚡ Quick → your 9 most used (or chosen) apps. Long-press to choose them."},
            {"Folders",
                    "• Tap a folder to open it. \"Edit\" changes name, icon, folder color and label color.\n"
                            + "• The color picker has a palette, a precise color square, HEX input and recent colors.\n"
                            + "• Long-press an app inside → move left/right, to another folder, to home, uninstall.\n"
                            + "• Newly installed Lite apps go into the \"Lite\" folder automatically."},
            {"Pages",
                    "• Swipe: Daily, Photos, Widgets, Home, All apps.\n"
                            + "• Settings → Pages: turn any page on/off, reorder with « ‹ › ».\n"
                            + "• The Home button always returns to the home screen."},
            {"Search & apps",
                    "• Type on the All apps page to find an app, Enter opens the first one.\n"
                            + "• Type math (12*7+3) to see the result, tap it to copy.\n"
                            + "• Long-press an app → home, folder, info, uninstall."},
            {"Daily",
                    "• Write today's tasks, tap to complete.\n"
                            + "• Unfinished ones carry over. Long-press → edit, repeat daily, reorder, delete.\n"
                            + "• Free notes below are saved automatically."},
            {"Photos",
                    "• Add a photo → crop to any ratio (free, 1:1, 4:3, 16:9…), rotate.\n"
                            + "• Size: full width, 2/3, half, 1/3 — photos flow in rows and scroll down.\n"
                            + "• Long-press → size, re-crop, move, delete."},
            {"Widgets",
                    "• \"Add widget\" → choose the app → choose the widget.\n"
                            + "• Long-press a widget → taller/shorter, up/down, configure, delete."},
            {"Privacy",
                    "Lite Launcher has no internet permission, no ads and no tracking. All data stays on the phone."},
    };

    private static final String[][] RU = {
            {"Главный экран",
                    "• Долгое нажатие на пустую ячейку → новая папка, приложение, виджеты, автопапки, настройки.\n"
                            + "• Долгое нажатие на приложение/папку и перетаскивание → в любую ячейку (занятые меняются местами).\n"
                            + "• Долгое нажатие без движения → меню: в папку, сведения, убрать, удалить с телефона.\n"
                            + "• ⚡ Быстрые → 9 самых используемых (или выбранных) приложений. Долгое нажатие — выбрать."},
            {"Папки",
                    "• Нажмите на папку, чтобы открыть. «Изменить» — название, значок, цвет папки и подписи.\n"
                            + "• В выборе цвета: палитра, точный квадрат цвета, HEX и недавние цвета.\n"
                            + "• Долгое нажатие на приложение внутри → влево/вправо, в другую папку, на экран, удалить.\n"
                            + "• Новые Lite-приложения автоматически попадают в папку «Lite»."},
            {"Страницы",
                    "• Свайп: Ежедневник, Фото, Виджеты, Главный экран, Все приложения.\n"
                            + "• Настройки → Страницы: включайте/выключайте, меняйте порядок « ‹ › ».\n"
                            + "• Кнопка «Домой» всегда возвращает на главный экран."},
            {"Поиск и приложения",
                    "• Пишите на странице «Все приложения» — поиск, Enter открывает первое.\n"
                            + "• Напишите пример (12*7+3) — будет ответ, нажатие копирует.\n"
                            + "• Долгое нажатие на приложение → на экран, в папку, сведения, удалить."},
            {"Ежедневник",
                    "• Записывайте задачи на сегодня, нажатие отмечает выполнение.\n"
                            + "• Невыполненные переносятся. Долгое нажатие → изменить, повторять каждый день, порядок, удалить.\n"
                            + "• Заметки внизу сохраняются автоматически."},
            {"Фото",
                    "• Добавьте фото → обрежьте в любом соотношении (свободно, 1:1, 4:3, 16:9…), поверните.\n"
                            + "• Размер: во всю ширину, 2/3, половина, 1/3 — фото идут рядами с прокруткой.\n"
                            + "• Долгое нажатие → размер, обрезать заново, переместить, удалить."},
            {"Виджеты",
                    "• «Добавить виджет» → приложение → виджет.\n"
                            + "• Долгое нажатие на виджет → выше/ниже, вверх/вниз, настроить, удалить."},
            {"Конфиденциальность",
                    "У Lite Launcher нет доступа к интернету, нет рекламы и слежки. Все данные остаются на телефоне."},
    };

    private Help() { }
}
