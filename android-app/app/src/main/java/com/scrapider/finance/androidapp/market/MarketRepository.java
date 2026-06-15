package com.scrapider.finance.androidapp.market;

import com.scrapider.finance.androidapp.network.ApiClient;
import com.scrapider.finance.androidapp.network.ApiConfig;
import com.scrapider.finance.androidapp.network.ApiResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class MarketRepository {
    public interface Callback {
        void onComplete(ApiResult result, MarketSummary summary);
    }

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA);

    private final ApiClient apiClient;

    public MarketRepository(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    public void load(Callback callback) {
        MarketSummary summary = new MarketSummary();
        Pending pending = new Pending(callback, summary);
        apiClient.get(ApiConfig.STOCK_QUOTES_PATH, result -> {
            if (result.success) {
                readStocks(result.body, summary);
            } else {
                pending.captureFailure(result, "股票行情同步失败。");
            }
            pending.done();
        });
        apiClient.get(ApiConfig.INDEX_QUOTES_PATH, result -> {
            if (result.success) {
                readIndices(result.body, summary);
            } else {
                pending.captureFailure(result, "指数行情同步失败。");
            }
            pending.done();
        });
        apiClient.get(ApiConfig.BOND_QUOTES_PATH, result -> {
            if (result.success) {
                readBonds(result.body, summary);
            } else {
                pending.captureFailure(result, "可转债行情同步失败。");
            }
            pending.done();
        });
    }

    private void readStocks(String body, MarketSummary summary) {
        JSONArray array = array(body);
        for (int i = 0; i < array.length() && i < 2; i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                summary.stocks.add(MarketQuote.stock(item));
            }
        }
    }

    private void readIndices(String body, MarketSummary summary) {
        JSONArray array = array(body);
        JSONObject item = array.optJSONObject(0);
        if (item != null) {
            summary.indices.add(MarketQuote.index(item));
        }
    }

    private void readBonds(String body, MarketSummary summary) {
        JSONArray array = array(body);
        JSONObject item = array.optJSONObject(0);
        if (item != null) {
            summary.bonds.add(MarketQuote.bond(item));
        }
    }

    private JSONArray array(String body) {
        try {
            return new JSONArray(body == null ? "" : body);
        } catch (JSONException exception) {
            return new JSONArray();
        }
    }

    private static final class Pending {
        private final Callback callback;
        private final MarketSummary summary;
        private int remaining = 3;
        private ApiResult failure;

        private Pending(Callback callback, MarketSummary summary) {
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
                callback.onComplete(ApiResult.success(200, "", "行情中心后端数据已同步。"), summary);
            }
        }
    }
}
