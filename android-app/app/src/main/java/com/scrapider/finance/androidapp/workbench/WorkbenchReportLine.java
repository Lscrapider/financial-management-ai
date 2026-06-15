package com.scrapider.finance.androidapp.workbench;

public final class WorkbenchReportLine {
    public final String text;
    public final String tone;

    public WorkbenchReportLine(String text, String tone) {
        this.text = text == null ? "" : text;
        this.tone = tone == null ? "muted" : tone;
    }
}
