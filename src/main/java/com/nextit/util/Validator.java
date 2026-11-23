package com.nextit.util;

import com.nextit.exception.AppException;

public final class Validator {

    private Validator() {}

    public static void notBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AppException(fieldName + " is required.");
        }
    }

    public static void email(String value) {
        if (value == null || !value.matches("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$")) {
            throw new AppException("A valid email address is required.");
        }
    }

    /** Rejects text that would not fit the matching database column. Null is allowed. */
    public static void maxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new AppException(fieldName + " must be " + maxLength + " characters or fewer.");
        }
    }

    /** Allows an empty value but rejects text that is not an http(s) URL. */
    public static void optionalWebUrl(String value, String fieldName) {
        if (value != null && !value.isBlank() && !value.trim().matches("^https?://\\S+$")) {
            throw new AppException(fieldName + " must start with http:// or https://.");
        }
    }

    /** Allows an empty value, web URL, or local file/upload path. */
    public static void optionalUrlOrFilePath(String value, String fieldName) {
        if (value == null || value.isBlank()) return;
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")
                || trimmed.startsWith("file:") || trimmed.startsWith("uploads/")
                || trimmed.startsWith("uploads\\") || trimmed.contains("/") || trimmed.contains("\\")) {
            return;
        }
        throw new AppException(fieldName + " must be a valid URL or image file path.");
    }

    /** Returns true if null, blank, or a valid phone number format without alphabetic characters. */
    public static boolean isValidPhoneNumber(String value) {
        if (value == null || value.isBlank()) return true;
        String trimmed = value.trim();
        if (trimmed.matches(".*[a-zA-Z].*")) {
            return false;
        }
        if (!trimmed.matches("^\\+?[0-9\\s\\-\\(\\).]{7,25}$")) {
            return false;
        }
        int digitCount = trimmed.replaceAll("[^0-9]", "").length();
        return digitCount >= 7 && digitCount <= 15;
    }

    /** Allows an empty value but rejects text that is not a valid phone number. */
    public static void optionalPhoneNumber(String value, String fieldName) {
        if (value != null && !value.isBlank() && !isValidPhoneNumber(value)) {
            throw new AppException(fieldName + " must be a valid phone number (e.g., +63 9XX XXX XXXX).");
        }
    }

    public static void nonNegative(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0) {
            throw new AppException(fieldName + " must be a finite non-negative number.");
        }
    }

    public static void score(double score, double maxScore) {
        nonNegative(score, "Score");
        nonNegative(maxScore, "Maximum score");
        if (maxScore == 0) throw new AppException("Maximum score cannot be zero.");
        if (score > maxScore) throw new AppException("Score cannot exceed the maximum score.");
    }
}
