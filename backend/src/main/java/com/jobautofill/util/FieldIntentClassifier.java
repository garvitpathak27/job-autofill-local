package com.jobautofill.util;

import com.jobautofill.model.AutofillRequest;

import java.util.Locale;

/**
 * Lightweight heuristic classifier that maps a form field to an intent bucket.
 * This is intentionally simple so it can run offline before delegating to an
 * LLM.
 */
public final class FieldIntentClassifier {

    private FieldIntentClassifier() {
        // Utility class
    }

    public enum IntentType {
        SKILL_LIST("skill_list"),
        EXPERIENCE_SUMMARY("experience_summary"),
        EDUCATION_INSTITUTION("education_institution"),
        EDUCATION_DEGREE("education_degree"),
        EDUCATION_YEAR("education_year"),
        MOTIVATION_STATEMENT("motivation"),
        AVAILABILITY_DATE("availability"),
        TIMELINE("timeline"),
        PORTFOLIO_URL("portfolio_url"),
        GITHUB_URL("github_url"),
        LINKEDIN_URL("linkedin_url"),
        GENERIC_URL("generic_url"),
        ACADEMIC_STATUS("academic_status"),
        HEAR_ABOUT("hear_about"),
        COVER_LETTER("cover_letter"),
        GENERIC_TEXT("text"),
        UNKNOWN("unknown");

        private final String displayName;

        IntentType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static IntentResult classify(AutofillRequest request) {
        if (request == null) {
            return new IntentResult(IntentType.UNKNOWN, 0.0, "No field metadata provided");
        }

        String label = safeLower(request.getFieldLabel());
        String name = safeLower(request.getFieldName());
        String placeholder = safeLower(request.getFieldPlaceholder());
        String combined = (label + " " + name + " " + placeholder).trim();

        if (containsAny(combined, "skill", "tech stack", "competenc", "expertise")) {
            return new IntentResult(IntentType.SKILL_LIST, 0.95, "Detected skill keywords");
        }

        if (containsAny(combined, "github")) {
            return new IntentResult(IntentType.GITHUB_URL, 0.95, "Detected GitHub keyword");
        }

        if (containsAny(combined, "linkedin")) {
            return new IntentResult(IntentType.LINKEDIN_URL, 0.95, "Detected LinkedIn keyword");
        }

        if (containsAny(combined, "portfolio", "website", "site url", "personal site")) {
            return new IntentResult(IntentType.PORTFOLIO_URL, 0.85, "Detected portfolio keyword");
        }

        if (containsAny(combined, "college", "university", "institution", "school")) {
            return new IntentResult(IntentType.EDUCATION_INSTITUTION, 0.85, "Detected education institution keyword");
        }

        if (containsAny(combined, "degree", "course", "program", "major")) {
            return new IntentResult(IntentType.EDUCATION_DEGREE, 0.8, "Detected education degree keyword");
        }

        if (containsAny(combined, "graduation", "grad year", "passing year", "passout", "year of completion")) {
            return new IntentResult(IntentType.EDUCATION_YEAR, 0.8, "Detected education year keyword");
        }

        if (containsAny(combined, "experience", "work history", "professional summary")) {
            return new IntentResult(IntentType.EXPERIENCE_SUMMARY, 0.75, "Detected experience keyword");
        }

        if (containsAny(combined, "why", "motivation", "excite", "cover letter", "statement of purpose")) {
            return new IntentResult(IntentType.MOTIVATION_STATEMENT, 0.85, "Detected motivation keyword");
        }

        if (containsAny(combined, "join", "availability", "notice period", "start date")) {
            return new IntentResult(IntentType.AVAILABILITY_DATE, 0.85, "Detected availability keyword");
        }

        if (containsAny(combined, "timeline", "time frame")) {
            return new IntentResult(IntentType.TIMELINE, 0.7, "Detected timeline keyword");
        }

        if (containsAny(combined, "how did you hear", "where did you hear")) {
            return new IntentResult(IntentType.HEAR_ABOUT, 0.85, "Detected referral keyword");
        }

        if (containsAny(combined, "are you currently in college", "which year are you", "current year",
                "still in college")) {
            return new IntentResult(IntentType.ACADEMIC_STATUS, 0.75, "Detected academic status keyword");
        }

        if (containsAny(combined, "cover letter")) {
            return new IntentResult(IntentType.COVER_LETTER, 0.8, "Detected cover letter keyword");
        }

        if ("url".equals(request.getFieldType())) {
            return new IntentResult(IntentType.GENERIC_URL, 0.5, "Field type is URL");
        }

        return new IntentResult(IntentType.GENERIC_TEXT, 0.2, "Defaulting to generic text intent");
    }

    public static final class IntentResult {
        private final IntentType type;
        private final double confidence;
        private final String rationale;

        public IntentResult(IntentType type, double confidence, String rationale) {
            this.type = type;
            this.confidence = confidence;
            this.rationale = rationale;
        }

        public IntentType getType() {
            return type;
        }

        public double getConfidence() {
            return confidence;
        }

        public String getRationale() {
            return rationale;
        }
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isEmpty()) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && haystack.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
