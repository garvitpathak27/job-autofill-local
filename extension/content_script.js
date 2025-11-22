// Content script - injected into all pages
console.log('Job Autofill content script loaded on:', window.location.href);

// Listen for messages from popup
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    console.log('Message received in content script:', message);

    if (message.action === 'autofill') {
        handleAutofill(message.backendUrl)
            .then(result => {
                console.log('Autofill result:', result);
                sendResponse(result);
            })
            .catch(error => {
                console.error('Autofill error:', error);
                sendResponse({ success: false, message: error.message });
            });
        return true; // Keep channel open for async response
    }
});

/**
 * Main autofill logic - detects and fills form fields
 */
async function handleAutofill(backendUrl) {
    console.log('Starting autofill process...');

    // Find all form inputs on the page
    const fields = detectFormFields();
    
    if (fields.length === 0) {
        console.log('No form fields detected');
        return { 
            success: false, 
            message: 'No fillable form fields found on this page',
            fieldsCount: 0,
            totalFields: 0
        };
    }

    console.log(`Found ${fields.length} form fields:`, fields.map(f => ({
        label: f.label,
        name: f.name,
        type: f.type
    })));

    let filledCount = 0;
    const errors = [];

    // Process each field
    for (const field of fields) {
        try {
            console.log(`Processing field: ${field.label || field.name}`);
            
            const suggestion = await getAutofillSuggestion(backendUrl, field);
            console.log('Suggestion:', suggestion);
            
            if (suggestion.suggested_value && suggestion.confidence > 0.5) {
                fillField(field.element, suggestion.suggested_value);
                filledCount++;
                console.log(`✓ Filled: ${field.label || field.name} = ${suggestion.suggested_value}`);
            } else {
                console.log(`✗ Skipped (low confidence): ${field.label || field.name}`);
            }
        } catch (error) {
            console.error(`Failed to autofill field ${field.label}:`, error);
            errors.push(`${field.label}: ${error.message}`);
        }
    }

    return {
        success: true,
        fieldsCount: filledCount,
        totalFields: fields.length,
        errors: errors.length > 0 ? errors : undefined
    };
}

/**
 * Detect form fields on the page
 */
function detectFormFields() {
    const fields = [];
    const inputs = document.querySelectorAll('input, textarea, select');

    inputs.forEach(element => {
        // Skip hidden fields, passwords, submit buttons, checkboxes, radio
        if (element.type === 'hidden' || 
            element.type === 'password' || 
            element.type === 'submit' || 
            element.type === 'button' ||
            element.type === 'checkbox' ||
            element.type === 'radio' ||
            element.type === 'file') {
            return;
        }

        // Skip if already filled (optional - remove if you want to overwrite)
        // if (element.value && element.value.trim() !== '') {
        //     return;
        // }

        // Get field metadata
        const fieldData = {
            element: element,
            label: getFieldLabel(element),
            name: element.name || element.id || '',
            placeholder: element.placeholder || '',
            type: element.type || 'text',
            currentValue: element.value || ''
        };

        // Only add if we have some identifying information
        if (fieldData.label || fieldData.name || fieldData.placeholder) {
            fields.push(fieldData);
        }
    });

    return fields;
}

/**
 * Get label for a form field
 */
function getFieldLabel(element) {
    // Check for <label> element by 'for' attribute
    if (element.id) {
        const label = document.querySelector(`label[for="${element.id}"]`);
        if (label) return label.textContent.trim();
    }

    // Check for parent label
    const parentLabel = element.closest('label');
    if (parentLabel) {
        // Get text content but exclude the input itself
        let text = parentLabel.textContent.trim();
        return text;
    }

    // Check for nearby text (previous sibling)
    let prevSibling = element.previousElementSibling;
    if (prevSibling) {
        if (prevSibling.textContent && prevSibling.textContent.trim()) {
            return prevSibling.textContent.trim();
        }
    }

    // Check aria-label
    const ariaLabel = element.getAttribute('aria-label');
    if (ariaLabel) return ariaLabel.trim();

    // Check for nearby span/div with text
    const parent = element.parentElement;
    if (parent) {
        const spans = parent.querySelectorAll('span, div');
        for (const span of spans) {
            if (span.textContent && span.textContent.trim() && !span.contains(element)) {
                return span.textContent.trim();
            }
        }
    }

    // Fallback to name or placeholder
    return element.name || element.placeholder || 'Unknown field';
}

/**
 * Get autofill suggestion from backend
 */
async function getAutofillSuggestion(backendUrl, field) {
    const requestBody = {
        field_label: field.label,
        field_name: field.name,
        field_placeholder: field.placeholder,
        field_type: field.type,
        field_value_current: field.currentValue
    };

    console.log('Requesting autofill for:', requestBody);

    const response = await fetch(`${backendUrl}/api/autofill`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(requestBody)
    });

    if (!response.ok) {
        throw new Error(`Autofill API returned ${response.status}`);
    }

    return await response.json();
}

/**
 * Fill a form field with a value
 */
function fillField(element, value) {
    // Set the value
    element.value = value;
    
    // Visual feedback - green highlight
    const originalBackground = element.style.backgroundColor;
    element.style.backgroundColor = '#d1fae5';
    element.style.transition = 'background-color 0.3s';
    
    // Trigger events to notify page of change
    element.dispatchEvent(new Event('input', { bubbles: true }));
    element.dispatchEvent(new Event('change', { bubbles: true }));
    element.dispatchEvent(new Event('blur', { bubbles: true }));
    
    // Remove highlight after 3 seconds
    setTimeout(() => {
        element.style.backgroundColor = originalBackground;
    }, 3000);
}
