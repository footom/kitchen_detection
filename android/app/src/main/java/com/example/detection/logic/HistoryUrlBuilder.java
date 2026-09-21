package com.example.detection.logic;

/**
 * 組出 /api/sensors/history 查詢字串的邏輯，從 showhistory.fetchHistory()
 * 抽出來。原本這段跟 Volley 的 StringRequest、RequestQueue 混在一起，
 * 沒辦法在不啟動網路請求的情況下單獨驗證「網址到底組得對不對」。
 */
public final class HistoryUrlBuilder {

    private HistoryUrlBuilder() {
    }

    /**
     * @param baseUrl 不含查詢字串的完整網址，例如 BuildConfig.API_BASE_URL + "/api/sensors/history"
     * @param start   使用者選擇的起始日期（yyyy-MM-dd），可為 null 或空字串
     * @param end     使用者選擇的結束日期（yyyy-MM-dd），可為 null 或空字串
     */
    public static String build(String baseUrl, String start, String end) {
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        boolean hasParam = false;

        if (start != null && !start.trim().isEmpty()) {
            urlBuilder.append(hasParam ? '&' : '?').append("start=").append(start.trim());
            hasParam = true;
        }
        if (end != null && !end.trim().isEmpty()) {
            urlBuilder.append(hasParam ? '&' : '?').append("end=").append(end.trim());
            hasParam = true;
        }

        return urlBuilder.toString();
    }
}
