package com.scrapider.finance.androidapp.screens;

import com.scrapider.finance.androidapp.auth.AuthPageSpecFactory;
import com.scrapider.finance.androidapp.model.ScreenSpec;

import java.util.List;

final class AuthScreenData {
    private AuthScreenData() {
    }

    static List<ScreenSpec> create() {
        return AuthPageSpecFactory.create();
    }
}
