package com.scrapider.finance.androidapp.model;

public final class RowSpec {
    public final String label;
    public final String value;
    public final String tone;
    public final boolean adminOnly;
    public final String targetScreenTitle;
    public final boolean prominentAction;
    public final String actionKey;

    public RowSpec(String label, String value, String tone) {
        this(label, value, tone, false, null, false);
    }

    public RowSpec(String label, String value, String tone, boolean adminOnly) {
        this(label, value, tone, adminOnly, null, false);
    }

    public RowSpec(String label, String value, String tone, boolean adminOnly, String targetScreenTitle) {
        this(label, value, tone, adminOnly, targetScreenTitle, false);
    }

    public RowSpec(String label, String value, String tone, boolean adminOnly, String targetScreenTitle, boolean prominentAction) {
        this(label, value, tone, adminOnly, targetScreenTitle, prominentAction, null);
    }

    public RowSpec(String label, String value, String tone, boolean adminOnly, String targetScreenTitle, boolean prominentAction, String actionKey) {
        this.label = label;
        this.value = value;
        this.tone = tone;
        this.adminOnly = adminOnly;
        this.targetScreenTitle = targetScreenTitle;
        this.prominentAction = prominentAction;
        this.actionKey = actionKey;
    }
}
