package com.mahou.textbox;

import android.content.SharedPreferences;
import android.graphics.Color;

public final class ThemeChrome {
    public static final class Palette {
        public final int windowBg;
        public final int statusBar;
        public final int panel;
        public final int webBg;
        public final int titleText;
        public final int panelButtonText;
        public final boolean lightStatusBar;

        Palette(
                int windowBg,
                int statusBar,
                int panel,
                int webBg,
                int titleText,
                int panelButtonText,
                boolean lightStatusBar
        ) {
            this.windowBg = windowBg;
            this.statusBar = statusBar;
            this.panel = panel;
            this.webBg = webBg;
            this.titleText = titleText;
            this.panelButtonText = panelButtonText;
            this.lightStatusBar = lightStatusBar;
        }
    }

    private ThemeChrome() {
    }

    public static String normalizeKey(String themeKey) {
        if ("night".equals(themeKey) || "light".equals(themeKey) || "mono".equals(themeKey)) {
            return themeKey;
        }
        return "dark";
    }

    public static String loadThemeKey(SharedPreferences prefs) {
        return normalizeKey(prefs.getString("uiTheme", "dark"));
    }

    public static Palette resolve(String themeKey) {
        switch (normalizeKey(themeKey)) {
            case "night":
                return new Palette(
                        0xff000000,
                        0xff000000,
                        0xff111111,
                        0xff000000,
                        Color.WHITE,
                        Color.WHITE,
                        false
                );
            case "light":
                return new Palette(
                        0xfff4f4f6,
                        0xfff4f4f6,
                        0xffffffff,
                        0xfff4f4f6,
                        Color.BLACK,
                        Color.BLACK,
                        true
                );
            case "mono":
                return new Palette(
                        0xff0a0a0a,
                        0xff0a0a0a,
                        0xff1a1a1a,
                        0xff0a0a0a,
                        Color.WHITE,
                        Color.WHITE,
                        false
                );
            case "dark":
            default:
                return new Palette(
                        0xff17181d,
                        0xff17181d,
                        0xff242631,
                        0xff17181d,
                        Color.WHITE,
                        Color.WHITE,
                        false
                );
        }
    }
}
