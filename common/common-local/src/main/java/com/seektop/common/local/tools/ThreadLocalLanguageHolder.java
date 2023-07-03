package com.seektop.common.local.tools;

import com.seektop.enumerate.Language;

import java.util.Optional;

public class ThreadLocalLanguageHolder {

    private static ThreadLocal<Language> val = new ThreadLocal();

    public static void language(Language language){
        val.set(language);
    }

    public static Language get(){
        return Optional.ofNullable(val.get()).orElse(Language.ZH_CN);
    }
}