package com.scrapider.finance.androidapp.profile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class PsychQuestion {
    public final String code;
    public final String title;
    public final List<String> optionCodes = new ArrayList<>();
    public final List<String> optionLabels = new ArrayList<>();

    private PsychQuestion(String code, String title) {
        this.code = code == null ? "" : code;
        this.title = title == null ? "" : title;
    }

    static PsychQuestion from(JSONObject item) {
        PsychQuestion question = new PsychQuestion(
                item.optString("code", ""),
                item.optString("title", ""));
        JSONArray options = item.optJSONArray("options");
        if (options == null) {
            return question;
        }
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.optJSONObject(i);
            if (option != null) {
                question.optionCodes.add(option.optString("code", ""));
                question.optionLabels.add(option.optString("label", ""));
            }
        }
        return question;
    }

    public String optionLabel(int index) {
        return index >= 0 && index < optionLabels.size() ? optionLabels.get(index) : "";
    }
}
