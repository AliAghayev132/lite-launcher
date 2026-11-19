package az.lite.launcher;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sadə tərcümə cədvəli (az / en / ru). Mənbə mətn azərbaycancadır:
 * L.t("Bağla") → seçilmiş dildə qarşılığı. Tapılmasa elə mənbə qaytarılır.
 */
final class L {

    static final String[] CODES = {"az", "en", "ru"};
    static final String[] NAMES = {"Azərbaycanca", "English", "Русский"};

    private static String lang = "en";
    private static final Map<String, String[]> T = new HashMap<>();

    private static void p(String az, String en, String ru) { T.put(az, new String[]{en, ru}); }

    static {
        // ümumi
        p("Bağla", "Close", "Закрыть");
        p("Ləğv et", "Cancel", "Отмена");
        p("Hazır", "Done", "Готово");
        p("Seç", "Choose", "Выбрать");
        p("Sil", "Delete", "Удалить");
        p("!Sil", "!Delete", "!Удалить");
        p("Geri", "Back", "Назад");
        p("Yadda saxla", "Save", "Сохранить");
        p("Redaktə", "Edit", "Изменить");
        p("Redaktə et", "Edit", "Изменить");
        p("Yuxarı", "Move up", "Вверх");
        p("Aşağı", "Move down", "Вниз");
        p("Əlavə et", "Add", "Добавить");
        p("Açmaq olmadı", "Couldn't open", "Не удалось открыть");
        p("Kopyalandı: ", "Copied: ", "Скопировано: ");
        p("Axtar…", "Search…", "Поиск…");
        p("Tətbiq", "App", "Приложение");
        p("Tətbiq axtar…", "Search apps…", "Поиск приложений…");
        p("Tətbiq məlumatı", "App info", "О приложении");
        p("Tətbiq seç", "Choose app", "Выберите приложение");
        p("Tətbiq qoy", "Place an app", "Поставить приложение");
        p("Ən çox ", "At most ", "Не более ");
        p(" tətbiq", " apps", " прил.");
        p("ƏN ÇOX AÇILANLAR", "MOST USED", "ЧАСТО ИСПОЛЬЗУЕМЫЕ");

        // ana ekran
        p("Boş xana", "Empty cell", "Пустая ячейка");
        p("Yeni qovluq", "New folder", "Новая папка");
        p("Vidcetlər", "Widgets", "Виджеты");
        p("Hamısını qovluqlara böl", "Sort all into folders", "Разложить по папкам");
        p("Parametrlər", "Settings", "Настройки");
        p("Kömək", "Help", "Справка");
        p("Qovluğa köçür", "Move to folder", "Переместить в папку");
        p("!Ana ekrandan götür", "!Remove from home", "!Убрать с экрана");
        p("Tətbiq əlavə et", "Add apps", "Добавить приложения");
        p("!Qovluğu sil", "!Delete folder", "!Удалить папку");
        p(" silinsin?", " — delete?", " — удалить?");
        p("Tətbiqlər telefondan silinmir, yalnız qovluq yığışdırılır.",
                "Apps stay on the phone, only the folder is removed.",
                "Приложения не удаляются, убирается только папка.");
        p("Boş xanaya uzun bas → qovluq və ya tətbiq\nSağa sürüşdür → vidcetlər\nSola sürüşdür → bütün tətbiqlər",
                "Long-press an empty cell → folder or app\nSwipe right → widgets\nSwipe left → all apps",
                "Долгое нажатие на пустую ячейку → папка или приложение\nСвайп вправо → виджеты\nСвайп влево → все приложения");

        // qovluq
        p("Qovluq boşdur. \"Tətbiq əlavə et\" ilə doldur.", "Folder is empty. Use \"Add apps\".", "Папка пуста. Нажмите «Добавить приложения».");
        p("+ Tətbiq", "+ Apps", "+ Прил.");
        p("Başqa qovluğa köçür", "Move to another folder", "В другую папку");
        p("Ana ekrana çıxar", "Move to home screen", "На главный экран");
        p("!Qovluqdan götür", "!Remove from folder", "!Убрать из папки");
        p("Sola çək", "Move left", "Сдвинуть влево");
        p("Sağa çək", "Move right", "Сдвинуть вправо");
        p(" — tətbiqlər", " — apps", " — приложения");
        p("Yeni qovluq", "New folder", "Новая папка");
        p("Qovluğu redaktə et", "Edit folder", "Изменить папку");
        p("Qovluğun adı", "Folder name", "Название папки");
        p("Qovluq", "Folder", "Папка");
        p("İkon", "Icon", "Значок");
        p("Rəng", "Color", "Цвет");
        p("Yazı", "Label", "Подпись");
        p("Qovluq rəngi", "Folder color", "Цвет папки");
        p("İkon və ad rəngi", "Icon & label color", "Цвет значка и подписи");
        p("Mini ikonlar", "Mini icons", "Мини-значки");
        p("Birinci xana: içindəki tətbiqlərin kiçik ikonları", "First cell: small icons of the apps inside",
                "Первая ячейка: мини-значки приложений внутри");
        p("Qovluq seç", "Choose folder", "Выберите папку");
        p("Qovluq axtar…", "Search folders…", "Поиск папок…");
        p("+  Yeni qovluq", "+  New folder", "+  Новая папка");
        p("+  Yeni qovluq: ", "+  New folder: ", "+  Новая папка: ");
        p("Əlavə olundu: ", "Added: ", "Добавлено: ");

        // rəng seçici
        p("Şəffaf", "Transparent", "Прозрачный");
        p("Palitra", "Palette", "Палитра");
        p("Dəqiq rəng", "Custom color", "Точный цвет");
        p("Son istifadə", "Recent", "Недавние");

        // sürətli
        p("Sürətli", "Quick", "Быстрые");
        p("Sürətli tətbiq yoxdur.\n\"Seç\" ilə 9-a qədər tətbiq əlavə et.", "No quick apps.\nUse \"Choose\" to add up to 9 apps.",
                "Нет быстрых приложений.\nНажмите «Выбрать», чтобы добавить до 9.");
        p("Avtomatik: ən çox açdıqların", "Automatic: your most used", "Автоматически: самые используемые");
        p("Sürətli tətbiqlər (maks 9)", "Quick apps (max 9)", "Быстрые приложения (макс. 9)");
        p(" sürətli tətbiq", " quick apps", " быстрых прил.");

        // avtomatik qovluqlar
        p("Bütün tətbiqlər artıq yerləşdirilib", "All apps are already placed", "Все приложения уже размещены");
        p("  (yeni)", "  (new)", "  (новая)");
        p("\nAna ekrandakı ayrıca tətbiqlərə toxunulmur.", "\nSeparate apps on the home screen are not touched.",
                "\nОтдельные приложения на экране не трогаются.");
        p(" tətbiq → ", " apps → ", " прил. → ");
        p(" qovluq", " folders", " папок");
        p("Böl", "Sort", "Разложить");
        p("Qovluqlara bölündü", "Sorted into folders", "Разложено по папкам");
        p("Alış-veriş", "Shopping", "Покупки");
        p("Alətlər", "Tools", "Инструменты");
        p("Digər", "Other", "Другое");
        p("Dövlət", "Government", "Госуслуги");
        p("Oyun", "Games", "Игры");
        p("Pul", "Finance", "Финансы");
        p("Sosial", "Social", "Соцсети");
        p("Səyahət", "Travel", "Путешествия");
        p("Taksi", "Taxi", "Такси");
        p("Təhsil", "Education", "Обучение");
        p("Xəbər", "News", "Новости");
        p("Yemək", "Food", "Еда");
        p("İdman", "Sport", "Спорт");
        p("İş", "Work", "Работа");

        // tətbiqlər səhifəsi
        p("Qovluğa əlavə et", "Add to folder", "Добавить в папку");
        p("Ana ekrana əlavə et", "Add to home screen", "На главный экран");
        p("!Telefondan sil", "!Uninstall", "!Удалить с телефона");
        p("Ana ekrana əlavə olundu", "Added to home screen", "Добавлено на экран");

        // vidcetlər
        p("Vidcet", "Widget", "Виджет");
        p("Vidcet əlavə et", "Add widget", "Добавить виджет");
        p("+  VİDCET ƏLAVƏ ET", "+  ADD WIDGET", "+  ДОБАВИТЬ ВИДЖЕТ");
        p(" vidcet", " widgets", " виджет.");
        p("Vidcet əlavə olunmadı", "Widget wasn't added", "Виджет не добавлен");
        p("Hələ vidcet yoxdur. Vidcetə uzun bas → sil və ya yerini dəyiş.",
                "No widgets yet. Long-press a widget → remove or move.",
                "Виджетов пока нет. Долгое нажатие → удалить или переместить.");
        p("Hündürlüyü artır", "Taller", "Выше");
        p("Hündürlüyü azalt", "Shorter", "Ниже");
        p("Ayarla", "Configure", "Настроить");
        p("Vidcet icazəsi verilmədi", "Widget permission not granted", "Разрешение на виджет не дано");

        // parametrlər / səhifələr
        p("Dil", "Language", "Язык");
        p("Səhifələr", "Pages", "Страницы");
        p("Səhifələri aç/bağla və sırasını dəyiş. Ana ekran həmişə açıqdır.",
                "Turn pages on/off and change their order. Home is always on.",
                "Включайте/выключайте страницы и меняйте порядок. Главный экран всегда включён.");
        p("Ana ekran", "Home", "Главный экран");
        p("Bütün tətbiqlər", "All apps", "Все приложения");
        p("Gündəlik", "Daily", "Ежедневник");
        p("Şəkillər", "Photos", "Фото");
        p("Ən sola", "Far left", "В начало");
        p("Ən sağa", "Far right", "В конец");

        // gündəlik
        p("Bu gün", "Today", "Сегодня");
        p("Yeni tapşırıq…", "New task…", "Новая задача…");
        p("Tapşırıqlar", "Tasks", "Задачи");
        p("Qeydlər", "Notes", "Заметки");
        p("Bura yaz…", "Write here…", "Пишите здесь…");
        p("Hələ tapşırıq yoxdur", "No tasks yet", "Задач пока нет");
        p("dünəndən", "from yesterday", "со вчера");
        p("köhnə", "earlier", "ранее");
        p("Tamamlananları təmizlə", "Clear completed", "Убрать выполненные");
        p("Hamısı tamamlandı 🎉", "All done 🎉", "Всё выполнено 🎉");

        // şəkillər
        p("+  ŞƏKİL ƏLAVƏ ET", "+  ADD PHOTO", "+  ДОБАВИТЬ ФОТО");
        p("Hələ şəkil yoxdur. Əlavə et, istədiyin ölçüdə kəs, yerləşdir.",
                "No photos yet. Add one, crop it to any size and place it.",
                "Фото пока нет. Добавьте, обрежьте как нужно и разместите.");
        p("Kəs", "Crop", "Обрезка");
        p("Sərbəst", "Free", "Свободно");
        p("Ölçü", "Size", "Размер");
        p("Tam en", "Full width", "Во всю ширину");
        p("Yarım", "Half", "Половина");
        p("Üçdə bir", "Third", "Треть");
        p("Üçdə iki", "Two thirds", "Две трети");
        p("Yenidən kəs", "Re-crop", "Обрезать заново");
        p("Əvvələ", "Move earlier", "Раньше");
        p("Sonraya", "Move later", "Позже");
        p("Şəkil", "Photo", "Фото");
        p("Şəkil açılmadı", "Couldn't open the image", "Не удалось открыть изображение");
        p("Şəkil seçmək üçün tətbiq yoxdur", "No app to pick images", "Нет приложения для выбора фото");
        p("Fırlat", "Rotate", "Повернуть");
        p("Təkrarı söndür", "Stop repeating", "Не повторять");
        p("Hər gün təkrarla", "Repeat every day", "Повторять каждый день");
        p("hər gün", "daily", "ежедневно");
    }

    static void init(Context c) {
        // standart: telefonun dili (az → az, ru → ru, qalan hamısı → en)
        String sys = Locale.getDefault().getLanguage();
        String def = "az".equals(sys) ? "az" : ("ru".equals(sys) || "uk".equals(sys) || "be".equals(sys)) ? "ru" : "en";
        lang = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("lang", def);
    }

    static void set(Context c, String code) {
        lang = code;
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("lang", code).commit();
    }

    static String lang() { return lang; }

    static Locale locale() {
        switch (lang) {
            case "en": return Locale.ENGLISH;
            case "ru": return new Locale("ru");
            default: return new Locale("az");
        }
    }

    static String t(String az) {
        if ("az".equals(lang) || az == null) return az;
        boolean bang = false;
        String[] v = T.get(az);
        if (v == null && az.startsWith("!")) {
            v = T.get(az.substring(1));
            bang = true;
        }
        if (v == null) return az;
        String r = "en".equals(lang) ? v[0] : v[1];
        return bang && !r.startsWith("!") ? "!" + r : r;
    }

    /** Massiv üçün. */
    static String[] arr(String... az) {
        String[] out = new String[az.length];
        for (int i = 0; i < az.length; i++) out[i] = t(az[i]);
        return out;
    }

    private L() { }
}
