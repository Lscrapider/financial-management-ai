package com.scrapider.finance.androidapp.profile;

import com.scrapider.finance.androidapp.network.ApiClient;
import com.scrapider.finance.androidapp.network.ApiConfig;
import com.scrapider.finance.androidapp.network.ApiResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class PsychProfileRepository {
    public interface Callback {
        void onComplete(ApiResult result, PsychProfileSummary summary);
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA);

    private final ApiClient apiClient;

    public PsychProfileRepository(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void load(Callback callback) {
        PsychProfileSummary summary = new PsychProfileSummary();
        Pending pending = new Pending(callback, summary);
        apiClient.get(ApiConfig.PSYCH_PROFILE_QUESTIONNAIRE_PATH, result -> {
            if (result.success) {
                readQuestionnaire(result.body, summary);
            } else {
                pending.captureFailure(result, "心理画像问卷同步失败。");
            }
            pending.done();
        });
        apiClient.get(ApiConfig.PSYCH_PROFILE_PATH, result -> {
            if (result.success) {
                readProfile(result.body, summary);
            } else {
                pending.captureFailure(result, "心理画像结果同步失败。");
            }
            pending.done();
        });
    }

    private void readQuestionnaire(String body, PsychProfileSummary summary) {
        JSONArray questions = object(body).optJSONArray("questions");
        if (questions == null) {
            return;
        }
        for (int i = 0; i < questions.length(); i++) {
            JSONObject item = questions.optJSONObject(i);
            if (item != null) {
                summary.questions.add(PsychQuestion.from(item));
            }
        }
    }

    private void readProfile(String body, PsychProfileSummary summary) {
        JSONObject root = object(body);
        summary.completed = root.optBoolean("profileCompleted", false);
        summary.version = root.optLong("profileVersion", 0);
        summary.riskEmotion = root.optString("riskEmotion", "");
        summary.decisionStyle = root.optString("decisionStyle", "");
        summary.tradingTempo = root.optString("tradingTempo", "");
        summary.explanationPreference = root.optString("explanationPreference", "");
        summary.adviceStyle = root.optString("adviceStyle", "");
        summary.summary = root.optString("summary", "");
        JSONArray mindset = root.optJSONArray("holdingMindset");
        if (mindset != null) {
            for (int i = 0; i < mindset.length(); i++) {
                summary.holdingMindset.add(mindset.optString(i, ""));
            }
        }
    }

    private JSONObject object(String body) {
        try {
            return new JSONObject(body == null ? "" : body);
        } catch (JSONException exception) {
            return new JSONObject();
        }
    }

    private static final class Pending {
        private final Callback callback;
        private final PsychProfileSummary summary;
        private int remaining = 2;
        private ApiResult failure;

        private Pending(Callback callback, PsychProfileSummary summary) {
            this.callback = callback;
            this.summary = summary;
        }

        private void captureFailure(ApiResult result, String fallback) {
            if (failure == null) {
                String message = result.message == null || result.message.isEmpty() ? fallback : result.message;
                failure = ApiResult.failure(result.statusCode, result.body, message);
            }
        }

        private void done() {
            remaining--;
            if (remaining > 0) {
                return;
            }
            summary.updatedAt = LocalDateTime.now().format(TIME_FORMAT);
            if (failure != null) {
                callback.onComplete(failure, summary);
            } else {
                callback.onComplete(ApiResult.success(200, "", "投资心理画像已同步。"), summary);
            }
        }
    }
}
