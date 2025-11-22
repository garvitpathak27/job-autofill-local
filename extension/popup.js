// Configuration
const BACKEND_URL = 'http://localhost:8080';

// Global state
let selectedFile = null;
let detectedFields = [];
let fieldSuggestions = {};

// DOM Elements
let dropZone, fileInfo, fileName, resumeUploadInput, uploadBtn, uploadStatus;
let statusIndicator, statusText, resumeInfoSection;
let scanBtn, autofillStatus;
let previewModal, fieldsPreview, applyBtn, cancelBtn, closeModal;

// Initialize popup
document.addEventListener('DOMContentLoaded', () => {
    console.log('=== Job Autofill popup loaded ===');
    
    // Get DOM elements
    dropZone = document.getElementById('drop-zone');
    fileInfo = document.getElementById('file-info');
    fileName = document.getElementById('file-name');
    resumeUploadInput = document.getElementById('resume-upload');
    uploadBtn = document.getElementById('upload-btn');
    uploadStatus = document.getElementById('upload-status');
    statusIndicator = document.getElementById('status-indicator');
    statusText = document.getElementById('status-text');
    resumeInfoSection = document.getElementById('resume-info-section');
    scanBtn = document.getElementById('scan-btn');
    autofillStatus = document.getElementById('autofill-status');
    
    // Modal elements
    previewModal = document.getElementById('preview-modal');
    fieldsPreview = document.getElementById('fields-preview');
    applyBtn = document.getElementById('apply-btn');
    cancelBtn = document.getElementById('cancel-btn');
    closeModal = document.getElementById('close-modal');

    // Drag & Drop event listeners
    dropZone.addEventListener('click', () => resumeUploadInput.click());
    dropZone.addEventListener('dragover', handleDragOver);
    dropZone.addEventListener('dragleave', handleDragLeave);
    dropZone.addEventListener('drop', handleDrop);
    resumeUploadInput.addEventListener('change', handleFileSelect);

    // Button event listeners
    uploadBtn.addEventListener('click', handleUpload);
    scanBtn.addEventListener('click', handleScan);
    applyBtn.addEventListener('click', handleApply);
    cancelBtn.addEventListener('click', closePreviewModal);
    closeModal.addEventListener('click', closePreviewModal);

    // Check backend health and resume status
    checkBackendHealth();
    checkResumeStatus();
});

function handleDragOver(e) {
    e.preventDefault();
    e.stopPropagation();
    dropZone.classList.add('drag-over');
}

function handleDragLeave(e) {
    e.preventDefault();
    e.stopPropagation();
    dropZone.classList.remove('drag-over');
}

function handleDrop(e) {
    e.preventDefault();
    e.stopPropagation();
    dropZone.classList.remove('drag-over');
    const files = e.dataTransfer.files;
    if (files.length > 0) handleFileSelection(files[0]);
}

function handleFileSelect(e) {
    const files = e.target.files;
    if (files.length > 0) handleFileSelection(files[0]);
}

function handleFileSelection(file) {
    if (!file.name.toLowerCase().endsWith('.pdf')) {
        showMessage(uploadStatus, 'Only PDF files are supported', 'error');
        return;
    }
    selectedFile = file;
    dropZone.classList.add('has-file');
    fileName.textContent = file.name;
    uploadBtn.disabled = false;
    showMessage(uploadStatus, `Ready to upload: ${file.name}`, 'info');
}

async function checkBackendHealth() {
    try {
        const response = await fetch(`${BACKEND_URL}/api/health`, {
            signal: AbortSignal.timeout(3000)
        });
        if (response.ok) console.log('✓ Backend is healthy');
    } catch (error) {
        showMessage(uploadStatus, '⚠️ Backend not running', 'error');
        uploadBtn.disabled = true;
    }
}

async function checkResumeStatus() {
    try {
        const response = await fetch(`${BACKEND_URL}/api/extract/current`);
        if (response.ok) {
            const data = await response.json();
            if (data.success && data.structured_resume) {
                updateUIWithResumeData(data.structured_resume);
            }
        }
    } catch (error) {
        console.log('No resume uploaded yet');
    }
}

async function handleUpload() {
    if (!selectedFile) {
        showMessage(uploadStatus, 'Please select a PDF file first', 'error');
        return;
    }

    uploadBtn.disabled = true;
    uploadBtn.textContent = 'Uploading...';
    showMessage(uploadStatus, 'Uploading resume...', 'info');

    try {
        const formData = new FormData();
        formData.append('file', selectedFile);

        const uploadResponse = await fetch(`${BACKEND_URL}/api/resume/upload`, {
            method: 'POST',
            body: formData
        });

        if (!uploadResponse.ok) throw new Error('Upload failed');

        showMessage(uploadStatus, 'Extracting with AI... (10-15s)', 'info');
        uploadBtn.textContent = 'Extracting...';

        const extractResponse = await fetch(`${BACKEND_URL}/api/extract`, {
            method: 'POST'
        });

        if (!extractResponse.ok) throw new Error('Extraction failed');

        const extractData = await extractResponse.json();
        if (extractData.success) {
            showMessage(uploadStatus, '✓ Resume uploaded and analyzed!', 'success');
            updateUIWithResumeData(extractData.structured_resume);
        }
    } catch (error) {
        showMessage(uploadStatus, `Error: ${error.message}`, 'error');
    } finally {
        uploadBtn.disabled = false;
        uploadBtn.textContent = 'Upload & Extract';
    }
}

function updateUIWithResumeData(resumeData) {
    statusIndicator.classList.add('success');
    statusText.textContent = '✓ Resume loaded and ready';
    resumeInfoSection.classList.remove('hidden');

    if (resumeData.personal_info) {
        document.getElementById('resume-name').textContent = resumeData.personal_info.name || '-';
        document.getElementById('resume-email').textContent = resumeData.personal_info.email || '-';
        document.getElementById('resume-phone').textContent = resumeData.personal_info.phone || '-';
    }

    if (resumeData.skills && resumeData.skills.length > 0) {
        document.getElementById('resume-skills').textContent = resumeData.skills.join(', ');
    }

    scanBtn.disabled = false;
}

async function handleScan() {
    console.log('=== handleScan called ===');
    scanBtn.disabled = true;
    scanBtn.textContent = 'Scanning page...';
    showMessage(autofillStatus, 'Detecting form fields...', 'info');

    try {
        // Get current tab
        const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
        console.log('Current tab:', tab.id, tab.url);

        // Inject content script
        console.log('Injecting content script...');
        await chrome.scripting.executeScript({
            target: { tabId: tab.id },
            files: ['content_script.js']
        });

        // Wait for injection
        await new Promise(resolve => setTimeout(resolve, 1000));
        console.log('Content script injected');

        // Send detectFields message
        console.log('Sending detectFields message...');
        const response = await chrome.tabs.sendMessage(tab.id, {
            action: 'detectFields'
        });

        console.log('Response from content script:', response);

        if (!response || !response.success) {
            throw new Error('Failed to detect fields');
        }

        if (response.fields.length === 0) {
            showMessage(autofillStatus, 'No fillable fields found on this page', 'info');
            return;
        }

        detectedFields = response.fields;
        console.log(`Found ${detectedFields.length} fields`);
        showMessage(autofillStatus, `Found ${detectedFields.length} fields. Getting suggestions...`, 'info');

        // Get suggestions for all fields
        await getSuggestionsForFields();
        
        // Show preview modal
        showPreviewModal();

    } catch (error) {
        console.error('=== handleScan error ===', error);
        showMessage(autofillStatus, `Error: ${error.message}. Make sure you're on a page with forms.`, 'error');
    } finally {
        scanBtn.disabled = false;
        scanBtn.textContent = '🔍 Scan & Preview Fields';
    }
}

async function getSuggestionsForFields() {
    console.log('Getting suggestions for', detectedFields.length, 'fields');
    fieldSuggestions = {};
    
    for (const field of detectedFields) {
        try {
            console.log('Getting suggestion for:', field.label || field.name);
            
            const response = await fetch(`${BACKEND_URL}/api/autofill`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    field_label: field.label,
                    field_name: field.name,
                    field_placeholder: field.placeholder,
                    field_type: field.type
                })
            });

            if (response.ok) {
                const suggestion = await response.json();
                fieldSuggestions[field.id] = suggestion;
                console.log('Suggestion:', suggestion.suggested_value, 'confidence:', suggestion.confidence);
            }
        } catch (error) {
            console.error(`Failed to get suggestion for field ${field.id}:`, error);
        }
    }
    
    console.log('All suggestions received:', Object.keys(fieldSuggestions).length);
}

function showPreviewModal() {
    console.log('Showing preview modal with', detectedFields.length, 'fields');
    fieldsPreview.innerHTML = '';

    detectedFields.forEach((field, index) => {
        const suggestion = fieldSuggestions[field.id] || {
            suggested_value: '',
            confidence: 0,
            reasoning: 'No suggestion available'
        };

        const fieldItem = document.createElement('div');
        fieldItem.className = 'field-item' + (suggestion.confidence > 0.5 ? ' selected' : '');
        fieldItem.dataset.fieldId = field.id;

        const confidenceClass = 
            suggestion.confidence > 0.8 ? 'high' :
            suggestion.confidence > 0.5 ? 'medium' : 'low';

        fieldItem.innerHTML = `
            <div class="field-header">
                <input type="checkbox" class="field-checkbox" 
                       data-field-id="${field.id}" 
                       ${suggestion.confidence > 0.5 ? 'checked' : ''}>
                <span class="field-label">${field.label || field.name}</span>
                <span class="field-type">${field.type}</span>
            </div>
            <input type="text" class="field-value" 
                   data-field-id="${field.id}"
                   value="${suggestion.suggested_value || ''}"
                   placeholder="Enter value...">
            <div class="field-confidence">
                <span>${Math.round(suggestion.confidence * 100)}%</span>
                <div class="confidence-bar">
                    <div class="confidence-fill ${confidenceClass}" 
                         style="width: ${suggestion.confidence * 100}%"></div>
                </div>
            </div>
        `;

        fieldsPreview.appendChild(fieldItem);
    });

    previewModal.classList.remove('hidden');
    console.log('Preview modal shown');
}

function closePreviewModal() {
    previewModal.classList.add('hidden');
}

async function handleApply() {
    console.log('=== handleApply called ===');
    const selectedFields = [];
    
    document.querySelectorAll('.field-checkbox:checked').forEach(checkbox => {
        const fieldId = checkbox.dataset.fieldId;
        const valueInput = document.querySelector(`.field-value[data-field-id="${fieldId}"]`);
        const field = detectedFields.find(f => f.id === fieldId);
        
        if (field && valueInput) {
            selectedFields.push({
                id: fieldId,
                value: valueInput.value
            });
        }
    });

    console.log('Selected fields:', selectedFields.length);

    if (selectedFields.length === 0) {
        alert('No fields selected');
        return;
    }

    try {
        const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
        
        console.log('Sending fillFields message...');
        const response = await chrome.tabs.sendMessage(tab.id, {
            action: 'fillFields',
            fields: selectedFields
        });

        console.log('Fill response:', response);

        if (response && response.success) {
            showMessage(autofillStatus, `✓ Filled ${response.filledCount} fields!`, 'success');
            closePreviewModal();
        }
    } catch (error) {
        console.error('Apply error:', error);
        alert('Error applying fields: ' + error.message);
    }
}

function showMessage(element, message, type) {
    element.textContent = message;
    element.className = `status-message ${type}`;
    element.style.display = 'block';
}
