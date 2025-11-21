package com.jobautofill.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Structured resume data extracted by Ollama.
 * This matches the JSON format we'll prompt the LLM to produce.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StructuredResume {

    @JsonProperty("personal_info")
    private PersonalInfo personalInfo;

    private List<Education> education;

    private List<Experience> experience;

    private List<String> skills;

    // Getters and Setters
    public PersonalInfo getPersonalInfo() {
        return personalInfo;
    }

    public void setPersonalInfo(PersonalInfo personalInfo) {
        this.personalInfo = personalInfo;
    }

    public List<Education> getEducation() {
        return education;
    }

    public void setEducation(List<Education> education) {
        this.education = education;
    }

    public List<Experience> getExperience() {
        return experience;
    }

    public void setExperience(List<Experience> experience) {
        this.experience = experience;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    // Inner classes
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PersonalInfo {
        private String name;
        private String email;
        private String phone;
        private String linkedin;
        private String github;

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getLinkedin() { return linkedin; }
        public void setLinkedin(String linkedin) { this.linkedin = linkedin; }
        public String getGithub() { return github; }
        public void setGithub(String github) { this.github = github; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Education {
        private String degree;
        private String institution;
        private String year;
        private String score;      // NEW - handle CGPA/percentage
        private String location;   // NEW - handle location

        // Getters and Setters
        public String getDegree() { return degree; }
        public void setDegree(String degree) { this.degree = degree; }
        public String getInstitution() { return institution; }
        public void setInstitution(String institution) { this.institution = institution; }
        public String getYear() { return year; }
        public void setYear(String year) { this.year = year; }
        public String getScore() { return score; }
        public void setScore(String score) { this.score = score; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Experience {
        private String title;
        private String company;
        private String duration;
        private String description;
        private String location;   // NEW - handle location

        // Getters and Setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getCompany() { return company; }
        public void setCompany(String company) { this.company = company; }
        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
    }
}
