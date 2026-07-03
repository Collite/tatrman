package com.alteryx.byx.language;

import com.intellij.lang.Language;

public class ByxLanguage extends Language {
    public static final ByxLanguage INSTANCE = new ByxLanguage();

    private ByxLanguage() {
        super("Byx");
    }
}

