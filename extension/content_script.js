// Content script - injected into all pages
console.log('Job Autofill content script loaded on:', window.location.href);

// Listen for messages from popup
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    console.log('Message received in content script:', message);

    if (message.action === 'detectFields') {
        console.log('Detecting form fields...');
        const fields = detectFormFields();

        // Add data attributes to elements for easier identification
        fields.forEach((field, index) => {
            const fieldId = generateFieldId(field.element);
            field.element.setAttribute('data-field-id', fieldId);
            field.element.setAttribute('data-field-index', index);
        });

        console.log(`Found ${fields.length} fields:`, fields.map(f => ({
            id: f.element.getAttribute('data-field-id'),
            label: f.label,
            name: f.name,
            type: f.type
        })));

        sendResponse({
            success: true,
            fields: fields.map(field => ({
                id: field.element.getAttribute('data-field-id'),
                label: field.label,
                name: field.name,
                placeholder: field.placeholder,
                type: field.type
            }))
        });
        return false; // Synchronous response
    }

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

    if (message.action === 'fillFields') {
        console.log('Filling fields:', message.fields);
        let filledCount = 0;
        message.fields.forEach(fieldData => {
            const element = document.querySelector(`[data-field-id="${fieldData.id}"]`);
            if (element) {
                fillField(element, fieldData.value);
                filledCount++;
                console.log(`✓ Filled field ${fieldData.id} with: ${fieldData.value}`);
            } else {
                console.error(`Could not find element with data-field-id: ${fieldData.id}`);
            }
        });
        sendResponse({ success: true, filledCount: filledCount });
        return false;
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
 * Generate unique ID for a field element
 */
function generateFieldId(element) {
    // Use existing id if available
    if (element.id) return element.id;

    // Generate unique id based on element properties
    const type = element.tagName.toLowerCase();
    const name = element.name || '';
    const className = element.className || '';
    const index = Array.from(document.querySelectorAll(`${type}[name="${name}"]`)).indexOf(element);

    return `${type}_${name}_${className}_${index}`.replace(/\s+/g, '_').replace(/[^\w]/g, '_');
}
