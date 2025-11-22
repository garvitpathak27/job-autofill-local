package com.jobautofill.util;

import com.jobautofill.model.StructuredResume;

/**
 * Extracts resume data based on field type without using AI.
 * Ollama is good at complex matching, but bad at simple data extraction.
 * This utility handles common field types accurately.
 */
public class FieldExtractor {

    /**
     * Extract value from resume based on field label/name
     */
    public static ExtractedValue extractValue(String fieldLabel, String fieldName, String fieldType,
            StructuredResume resume) {
        if (resume == null) {
            return new ExtractedValue("", 0.0, "Resume data unavailable");
        }

        String label = (fieldLabel + " " + fieldName).toLowerCase();
        StructuredResume.PersonalInfo personalInfo = resume.getPersonalInfo();

        // LINK FIELDS
        if (label.contains("github")) {
            String github = personalInfo != null ? safeString(personalInfo.getGithub()) : "";
            double confidence = github.isEmpty() ? 0.0 : 0.95;
            String reasoning = github.isEmpty() ? "GitHub URL not available" : "Used personal_info.github";
            return new ExtractedValue(github, confidence, reasoning);
        }

        if (label.contains("linkedin")) {
            String linkedin = personalInfo != null ? safeString(personalInfo.getLinkedin()) : "";
            double confidence = linkedin.isEmpty() ? 0.0 : 0.95;
            String reasoning = linkedin.isEmpty() ? "LinkedIn URL not available" : "Used personal_info.linkedin";
            return new ExtractedValue(linkedin, confidence, reasoning);
        }

        if (label.contains("portfolio") || label.contains("website")
                || (label.contains("url") && (label.contains("portfolio") || label.contains("site")))) {
            String portfolio = personalInfo != null ? safeString(personalInfo.getLinkedin()) : "";
            double confidence = portfolio.isEmpty() ? 0.0 : 0.60;
            String reasoning = portfolio.isEmpty() ? "Portfolio URL not found"
                    : "Using LinkedIn as closest portfolio link";
            return new ExtractedValue(portfolio, confidence, reasoning);
        }

        // NAME FIELDS
        if (label.contains("first") && label.contains("name")) {
            String fullName = personalInfo != null ? personalInfo.getName() : "";
            String firstName = extractFirstName(fullName);
            return new ExtractedValue(firstName, 0.99, "Extracted first name from personal_info.name");
        }

        if (label.contains("last") && label.contains("name")) {
            String fullName = personalInfo != null ? personalInfo.getName() : "";
            String lastName = extractLastName(fullName);
            return new ExtractedValue(lastName, 0.99, "Extracted last name from personal_info.name");
        }

        if (label.contains("given") && label.contains("name")) {
            String fullName = personalInfo != null ? personalInfo.getName() : "";
            String firstName = extractFirstName(fullName);
            return new ExtractedValue(firstName, 0.99, "Extracted given name from personal_info.name");
        }

        if ((label.contains("family") || label.contains("surname")) && label.contains("name")) {
            String fullName = personalInfo != null ? personalInfo.getName() : "";
            String lastName = extractLastName(fullName);
            return new ExtractedValue(lastName, 0.99, "Extracted family name from personal_info.name");
        }

        if (label.contains("full") && label.contains("name") && !label.contains("first") && !label.contains("last")) {
            String fullName = personalInfo != null ? personalInfo.getName() : "";
            return new ExtractedValue(fullName, 0.99, "Used full name from personal_info.name");
        }

        // EMAIL FIELDS
        if (label.contains("email") || label.contains("e-mail")) {
            String email = personalInfo != null ? safeString(personalInfo.getEmail()) : "";
            double confidence = email.isEmpty() ? 0.0 : 0.99;
            String reasoning = email.isEmpty() ? "Email not available" : "Used personal_info.email";
            return new ExtractedValue(email, confidence, reasoning);
        }

        // PHONE FIELDS
        if (label.contains("phone") && !label.contains("code") && !label.contains("extension")) {
            String phone = personalInfo != null ? extractPhoneNumber(personalInfo.getPhone()) : "";
            double confidence = phone.isEmpty() ? 0.0 : 0.99;
            String reasoning = phone.isEmpty() ? "Phone number not available" : "Extracted phone number (digits only)";
            return new ExtractedValue(phone, confidence, reasoning);
        }

        if (label.contains("country") && label.contains("code")) {
            String code = personalInfo != null ? extractCountryCode(personalInfo.getPhone()) : "";
            double confidence = code.isEmpty() ? 0.0 : 0.99;
            String reasoning = code.isEmpty() ? "Country code not available" : "Extracted country code from phone";
            return new ExtractedValue(code, confidence, reasoning);
        }

        if (label.contains("extension")) {
            return new ExtractedValue("", 0.99, "Extension not available in resume");
        }

        // ADDRESS FIELDS
        if (label.contains("address") && label.contains("line")) {
            // For Workday: use first education location or experience location
            String address = extractAddressLine(resume);
            return new ExtractedValue(address, 0.85, "Extracted address from resume");
        }

        if (label.contains("city")) {
            String city = extractCity(resume);
            return new ExtractedValue(city, 0.95, "Extracted city from address");
        }

        if (label.contains("postal") || label.contains("zip") || label.contains("pin")) {
            return new ExtractedValue("", 0.85, "Postal code not consistently available");
        }

        if (label.contains("country")) {
            return new ExtractedValue("India", 0.75, "Assuming India (not in resume)");
        }

        if (label.contains("state") || label.contains("province") || label.contains("region")) {
            return new ExtractedValue("", 0.70, "State not clearly available");
        }

        // SKILLS FIELDS
        if (label.contains("skill") || label.contains("technical") || label.contains("competenc")
                || label.contains("expertise")) {
            String skills = resume.getSkills() != null ? String.join(", ", resume.getSkills()) : "";
            double confidence = skills.isEmpty() ? 0.0 : 0.95;
            String reasoning = skills.isEmpty() ? "Skills not captured" : "Joined skills array with commas";
            return new ExtractedValue(skills, confidence, reasoning);
        }

        // EXPERIENCE FIELDS
        if (label.contains("experience") || label.contains("work")) {
            if (resume.getExperience() != null && !resume.getExperience().isEmpty()) {
                String exp = resumeExperience(resume);
                return new ExtractedValue(exp, 0.85, "Summarized experience");
            }
        }

        // EDUCATION FIELDS
        if (label.contains("college") || label.contains("university") || label.contains("institution")
                || label.contains("school")) {
            if (resume.getEducation() != null && !resume.getEducation().isEmpty()) {
                var edu = resume.getEducation().get(0);
                String institution = safeString(edu.getInstitution());
                double confidence = institution.isEmpty() ? 0.0 : 0.95;
                String reasoning = institution.isEmpty() ? "Institution not found" : "Used most recent institution";
                return new ExtractedValue(institution, confidence, reasoning);
            }
        }

        if (label.contains("degree") || label.contains("course") || label.contains("program")
                || label.contains("major")) {
            if (resume.getEducation() != null && !resume.getEducation().isEmpty()) {
                var edu = resume.getEducation().get(0);
                String degree = safeString(edu.getDegree());
                double confidence = degree.isEmpty() ? 0.0 : 0.9;
                String reasoning = degree.isEmpty() ? "Degree not specified" : "Used most recent degree";
                return new ExtractedValue(degree, confidence, reasoning);
            }
        }

        if (label.contains("graduation") || label.contains("passing") || label.contains("passout")
                || (label.contains("year")
                        && (label.contains("grad") || label.contains("completion") || label.contains("passing")))) {
            if (resume.getEducation() != null && !resume.getEducation().isEmpty()) {
                var edu = resume.getEducation().get(0);
                String year = safeString(edu.getYear());
                double confidence = year.isEmpty() ? 0.0 : 0.9;
                String reasoning = year.isEmpty() ? "Graduation year not present" : "Used most recent graduation year";
                return new ExtractedValue(year, confidence, reasoning);
            }
        }

        if (label.contains("education") || label.contains("qualification") || label.contains("degree")) {
            if (resume.getEducation() != null && !resume.getEducation().isEmpty()) {
                String edu = resumeEducation(resume);
                return new ExtractedValue(edu, 0.85, "Used most recent education");
            }
        }

        // DEFAULT
        return new ExtractedValue("", 0.0, "No matching field detected");
    }

    private static String extractFirstName(String fullName) {
        if (fullName == null || fullName.isEmpty())
            return "";
        return fullName.split("\\s+")[0];
    }

    private static String extractLastName(String fullName) {
        if (fullName == null || fullName.isEmpty())
            return "";
        String[] parts = fullName.split("\\s+");
        return parts.length > 1 ? parts[parts.length - 1] : "";
    }

    private static String extractPhoneNumber(String phone) {
        if (phone == null)
            return "";
        // Remove +, -, spaces, and keep only digits
        return phone.replaceAll("[^0-9]", "");
    }

    private static String extractCountryCode(String phone) {
        if (phone == null || !phone.contains("+"))
            return "";
        // Extract +91 or +1 format
        String[] parts = phone.split(" ");
        if (parts[0].startsWith("+")) {
            return parts[0]; // Return "+91"
        }
        return "";
    }

    private static String extractCity(StructuredResume resume) {
        // Try to extract from experience location
        if (resume.getExperience() != null && !resume.getExperience().isEmpty()) {
            String location = resume.getExperience().get(0).getLocation();
            if (location != null) {
                // Extract first part before comma
                String[] parts = location.split(",");
                return parts[0].trim();
            }
        }
        // Try from education
        if (resume.getEducation() != null && !resume.getEducation().isEmpty()) {
            String location = resume.getEducation().get(0).getLocation();
            if (location != null) {
                String[] parts = location.split(",");
                return parts[0].trim();
            }
        }
        return "";
    }

    private static String extractAddressLine(StructuredResume resume) {
        // Use city as fallback
        return extractCity(resume);
    }

    private static String resumeExperience(StructuredResume resume) {
        if (resume.getExperience() == null || resume.getExperience().isEmpty())
            return "";
        var exp = resume.getExperience().get(0);
        return String.format("%s at %s (%s)", exp.getTitle(), exp.getCompany(), exp.getDuration());
    }

    private static String resumeEducation(StructuredResume resume) {
        if (resume.getEducation() == null || resume.getEducation().isEmpty())
            return "";
        var edu = resume.getEducation().get(0);
        return String.format("%s from %s (%s)", edu.getDegree(), edu.getInstitution(), edu.getYear());
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    public static class ExtractedValue {
        public String value;
        public double confidence;
        public String reasoning;

        public ExtractedValue(String value, double confidence, String reasoning) {
            this.value = value;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }
    }
}
