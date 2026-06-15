package com.scrapider.finance.androidapp.profile;

import java.util.ArrayList;
import java.util.List;

public final class PsychProfileSummary {
    public final List<PsychQuestion> questions = new ArrayList<>();
    public boolean completed;
    public long version;
    public String riskEmotion = "";
    public String decisionStyle = "";
    public final List<String> holdingMindset = new ArrayList<>();
    public String tradingTempo = "";
    public String explanationPreference = "";
    public String adviceStyle = "";
    public String summary = "";
    public String updatedAt = "--:--";

    public boolean hasAnyData() {
        return !questions.isEmpty() || completed || version > 0;
    }

    public PsychQuestion currentQuestion() {
        if (questions.isEmpty()) {
            return null;
        }
        return completed ? questions.get(questions.size() - 1) : questions.get(0);
    }

    public int completedCount() {
        return completed ? questions.size() : 0;
    }
}
