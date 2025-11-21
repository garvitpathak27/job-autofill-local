# Architecture Overview

[Reference the diagram from above]

## Data Models

### Resume (Structured)

{
"personal_info": {
"name": "John Doe",
"email": "john@example.com",
"phone": "+1-555-1234"
},
"education": [
{
"degree": "B.Tech Computer Science",
"institution": "VIT Vellore",
"graduation_year": 2025
}
],
"experience": [
{
"title": "Junior Backend Developer",
"company": "Acme Corp",
"duration": "2023–2025",
"description": "Developed Spring Boot microservices..."
}
],
"skills": ["Java", "Spring Boot", "Docker", "Kubernetes", ...]
}

text

### Autofill Request

{
"field_label": "Full Name",
"field_name": "applicant_name",
"field_placeholder": "e.g., John Smith",
"field_value_current": ""
}

text

### Autofill Response

{
"suggested_value": "John Doe",
"confidence": 0.95,
"reasoning": "Matches 'Full Name' field with resume personal_info.name"
}

text
