package com.nakiri00.auralis;

import java.util.UUID;

public final class HistoryIdentity {

    private static final int MAX_ID_LENGTH = 200;

    private HistoryIdentity() {
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    public static String normalizeOrCreate(
            String historyId
    ) {
        if (isUsable(historyId)) {
            return historyId.trim();
        }

        return newId();
    }

    public static boolean isUsable(
            String historyId
    ) {
        if (historyId == null) {
            return false;
        }

        String value = historyId.trim();

        if (
                value.isEmpty()
                        || value.length() > MAX_ID_LENGTH
                        || value.contains("/")
                        || ".".equals(value)
                        || "..".equals(value)
        ) {
            return false;
        }

        return true;
    }
}