// Content script with Workday-safe field detection
console.log('Job Autofill content script loaded on:', window.location.href);

let fieldCounter = 0;

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    console.log('Message received in content script:', message);

    if (message.action === 'detectFields') {
        console.log('Detecting form fields...');
        const fields = detectFormFields();
        console.log('Found', fields.length, 'safe fields');
        sendResponse({ success: true, fields: fields });
        return true;
    }

    if (message.action === 'fillFields') {
        console.log('Filling fields:', message.fields.length, 'fields');
        fillSelectedFields(message.fields)
            .then(result => sendResponse(result))
            .catch(error => sendResponse({ success: false, error: error.message }));
        return true;
    }
});

/**
 * CRITICAL: Determine if element is safe to autofill
 */
function isSafeToAutofill(element) {
    const tagName = element.tagName.toLowerCase();
    const type = element.type ? element.type.toLowerCase() : '';
    
    // Rule 1: Only fill safe element types
    const safeTextInputs = ['text', 'email', 'tel', 'url'];
    const isSafeInput = tagName === 'input' && safeTextInputs.includes(type);
    const isSafeTextarea = tagName === 'textarea';
    
    if (!isSafeInput && !isSafeTextarea) {
        return false;
    }
    
    // Rule 2: Avoid Workday's controlled dropdowns/comboboxes
    if (element.closest('[role="combobox"]')) {
        return false;
    }
    
    // Rule 3: Avoid autocomplete fields
    const ariaAutocomplete = element.getAttribute('aria-autocomplete');
    if (ariaAutocomplete === 'list' || ariaAutocomplete === 'both') {
        return false;
    }
    
    // Rule 4: Avoid country/state fields
    const automationId = element.getAttribute('data-automation-id') || '';
    if (automationId.includes('country') || automationId.includes('state')) {
        return false;
    }
    
    // Rule 5: Avoid hidden or disabled fields
    if (element.disabled || element.readOnly) {
        return false;
    }
    
    // Rule 6: Avoid Workday selector patterns
    const workdaySelectors = ['select', 'dropdown', 'picker', 'lookup'];
    const elementClasses = element.className || '';
    const hasWorkdaySelector = workdaySelectors.some(pattern => 
        elementClasses.includes(pattern) || automationId.includes(pattern)
    );
    
    if (hasWorkdaySelector) {
        return false;
    }
    
    return true;
}

function detectFormFields() {
    const fields = [];
    const inputs = document.querySelectorAll('input, textarea');
    fieldCounter = 0;

    console.log('Total elements found:', inputs.length);

    inputs.forEach(element => {
        // Skip unwanted types
        const skipTypes = ['hidden', 'password', 'submit', 'button', 'file', 'image', 'reset', 'checkbox', 'radio'];
        if (skipTypes.includes(element.type)) {
            return;
        }

        // Only detect safe fields
        if (!isSafeToAutofill(element)) {
            return;
        }

        const fieldData = {
            id: `field_${fieldCounter++}`,
            label: getFieldLabel(element),
            name: element.name || element.id || '',
            placeholder: element.placeholder || '',
            type: element.type || element.tagName.toLowerCase(),
            currentValue: element.value || '',
            tagName: element.tagName.toLowerCase()
        };

        // Store reference
        element.dataset.autofillId = fieldData.id;

        if (fieldData.label || fieldData.name || fieldData.placeholder) {
            fields.push(fieldData);
            console.log('✓ Added safe field:', fieldData.label || fieldData.name);
        }
    });

    console.log('Final safe field count:', fields.length);
    return fields;
}

function getFieldLabel(element) {
    // Check for <label> by 'for' attribute
    if (element.id) {
        const label = document.querySelector(`label[for="${element.id}"]`);
        if (label) return cleanLabel(label.textContent);
    }

    // Check parent label
    const parentLabel = element.closest('label');
    if (parentLabel) {
        return cleanLabel(parentLabel.textContent);
    }

    // Check aria-label
    const ariaLabel = element.getAttribute('aria-label');
    if (ariaLabel) return cleanLabel(ariaLabel);

    // Check data-automation-label (Workday specific)
    const automationLabel = element.getAttribute('data-automation-label');
    if (automationLabel) return cleanLabel(automationLabel);

    // Check placeholder
    if (element.placeholder) return cleanLabel(element.placeholder);

    // Check previous sibling
    const prevSibling = element.previousElementSibling;
    if (prevSibling && prevSibling.textContent) {
        return cleanLabel(prevSibling.textContent);
    }

    return element.name || element.id || 'Unknown field';
}

function cleanLabel(text) {
    if (!text) return '';
    return text.trim()
        .replace(/\s+/g, ' ')
        .replace(/\*$/, '')
        .replace(/\(optional\)/i, '')
        .trim();
}

async function fillSelectedFields(fields) {
    let filledCount = 0;
    let skippedCount = 0;

    for (const fieldData of fields) {
        try {
            const element = document.querySelector(`[data-autofill-id="${fieldData.id}"]`);
            
            if (!element) {
                console.warn('Element not found for:', fieldData.id);
                skippedCount++;
                continue;
            }

            // Double-check safety
            if (!isSafeToAutofill(element)) {
                console.warn('Skipping unsafe element:', fieldData.id);
                skippedCount++;
                continue;
            }

            if (fieldData.value && fieldData.value.trim() !== '') {
                await fillField(element, fieldData.value);
                filledCount++;
                console.log('✓ Filled:', fieldData.label || fieldData.name);
            } else {
                skippedCount++;
            }
        } catch (error) {
            console.error('Error filling field:', fieldData.id, error);
            skippedCount++;
        }
    }

    console.log(`Fill complete: ${filledCount} filled, ${skippedCount} skipped`);
    
    return { 
        success: true, 
        filledCount: filledCount,
        skippedCount: skippedCount
    };
}

async function fillField(element, value) {
    const tagName = element.tagName.toLowerCase();
    
    // Set value using native setters (React compatibility)
    if (tagName === 'input') {
        const descriptor = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
        if (descriptor && descriptor.set) {
            descriptor.set.call(element, value);
        } else {
            element.value = value;
        }
    } else if (tagName === 'textarea') {
        const descriptor = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value');
        if (descriptor && descriptor.set) {
            descriptor.set.call(element, value);
        } else {
            element.value = value;
        }
    }

    // Trigger events with delays for React
    element.dispatchEvent(new Event('input', { bubbles: true }));
    await new Promise(resolve => setTimeout(resolve, 100));
    
    element.dispatchEvent(new Event('change', { bubbles: true }));
    await new Promise(resolve => setTimeout(resolve, 100));
    
    element.dispatchEvent(new Event('blur', { bubbles: true }));
    await new Promise(resolve => setTimeout(resolve, 50));

    // Visual feedback
    const originalBg = element.style.backgroundColor;
    element.style.backgroundColor = '#d1fae5';
    element.style.transition = 'background-color 0.3s';
    
    setTimeout(() => {
        element.style.backgroundColor = originalBg;
    }, 2000);
}

console.log('Content script ready and listening');
