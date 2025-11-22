// Configuration
const BACKEND_URL = 'http://localhost:8080';

// Global state
let selectedFile = null;

// DOM Elements
let dropZone;
let fileInfo;
let fileName;
let resumeUploadInput;
let uploadBtn;
let uploadStatus;
let statusIndicator;
let statusText;
let resumeInfoSection;
let autofillBtn;
let autofillStatus;

// Initialize popup
document.addEventListener('DOMContentLoaded', () => {
    console.log('Job Autofill popup loaded');
    
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
    autofillBtn = document.getElementById('autofill-btn');
    autofillStatus = document.getElementById('autofill-status');

    // Drag & Drop event listeners
    dropZone.addEventListener('click', () => resumeUploadInput.click());
    dropZone.addEventListener('dragover', handleDragOver);
    dropZone.addEventListener('dragleave', handleDragLeave);
    dropZone.addEventListener('drop', handleDrop);
    
    // File input change (for click to browse)
    resumeUploadInput.addEventListener('change', handleFileSelect);

    // Button event listeners
    uploadBtn.addEventListener('click', handleUpload);
    autofillBtn.addEventListener('click', handleAutofill);

    // Check backend health
    checkBackendHealth();
    
    // Check if resume already uploaded
    checkResumeStatus();
});

/**
 * Handle drag over event
 */
function handleDragOver(e) {
    e.preventDefault();
    e.stopPropagation();
    dropZone.classList.add('drag-over');
}

/**
 * Handle drag leave event
 */
function handleDragLeave(e) {
    e.preventDefault();
    e.stopPropagation();
    dropZone.classList.remove('drag-over');
}

/**
 * Handle file drop
 */
function handleDrop(e) {
    e.preventDefault();
    e.stopPropagation();
    dropZone.classList.remove('drag-over');
    
    const files = e.dataTransfer.files;
    if (files.length > 0) {
        handleFileSelection(files[0]);
    }
}

/**
 * Handle file selection from input
 */
function handleFileSelect(e) {
    const files = e.target.files;
    if (files.length > 0) {
        handleFileSelection(files[0]);
    }
}

/**
 * Handle file selection (common logic)
 */
function handleFileSelection(file) {
    console.log('File selected:', file.name);
    
    // Validate file type
    if (!file.name.toLowerCase().endsWith('.pdf')) {
        showMessage(uploadStatus, 'Only PDF files are supported', 'error');
        return;
    }
    
    // Store file
    selectedFile = file;
    
    // Update UI
    dropZone.classList.add('has-file');
    fileName.textContent = file.name;
    uploadBtn.disabled = false;
    showMessage(uploadStatus, `Ready to upload: ${file.name}`, 'info');
}

/**
 * Check if backend is running
 */
async function checkBackendHealth() {
    try {
        const response = await fetch(`${BACKEND_URL}/api/health`, {
            method: 'GET',
            signal: AbortSignal.timeout(3000)
        });
        
        if (response.ok) {
            console.log('✓ Backend is reachable');
        } else {
            showMessage(uploadStatus, '⚠️ Backend returned error', 'error');
        }
    } catch (error) {
        console.error('Backend not reachable:', error);
        showMessage(uploadStatus, '⚠️ Backend not running. Start: mvn spring-boot:run', 'error');
        uploadBtn.disabled = true;
    }
}

/**
 * Check if resume is already uploaded and extracted
 */
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

/**
 * Handle resume upload
 */
async function handleUpload() {
    if (!selectedFile) {
        showMessage(uploadStatus, 'Please select a PDF file first', 'error');
        return;
    }

    // Disable button during upload
    uploadBtn.disabled = true;
    uploadBtn.textContent = 'Uploading...';
    showMessage(uploadStatus, 'Uploading resume...', 'info');

    try {
        // Step 1: Upload PDF
        const formData = new FormData();
        formData.append('file', selectedFile);

        console.log('Uploading file:', selectedFile.name);

        const uploadResponse = await fetch(`${BACKEND_URL}/api/resume/upload`, {
            method: 'POST',
            body: formData
        });

        if (!uploadResponse.ok) {
            const errorData = await uploadResponse.json();
            throw new Error(errorData.error || 'Upload failed');
        }

        const uploadData = await uploadResponse.json();
        console.log('Upload success:', uploadData);

        showMessage(uploadStatus, 'Extracting data with AI... (10-15 seconds)', 'info');
        uploadBtn.textContent = 'Extracting...';

        // Step 2: Extract structured data
        const extractResponse = await fetch(`${BACKEND_URL}/api/extract`, {
            method: 'POST'
        });

        if (!extractResponse.ok) {
            throw new Error('Extraction failed');
        }

        const extractData = await extractResponse.json();
        console.log('Extraction success:', extractData);
        
        if (extractData.success) {
            showMessage(uploadStatus, '✓ Resume uploaded and analyzed!', 'success');
            updateUIWithResumeData(extractData.structured_resume);
        } else {
            throw new Error('Extraction returned no data');
        }

    } catch (error) {
        console.error('Upload error:', error);
        showMessage(uploadStatus, `Error: ${error.message}`, 'error');
    } finally {
        uploadBtn.disabled = false;
        uploadBtn.textContent = 'Upload & Extract';
    }
}

/**
 * Update UI with resume data
 */
function updateUIWithResumeData(resumeData) {
    console.log('Updating UI with resume data:', resumeData);
    
    // Update status indicator
    statusIndicator.classList.remove('error');
    statusIndicator.classList.add('success');
    statusText.textContent = '✓ Resume loaded and ready';

    // Show resume info section
    resumeInfoSection.classList.remove('hidden');

    // Populate resume details
    if (resumeData.personal_info) {
        document.getElementById('resume-name').textContent = resumeData.personal_info.name || '-';
        document.getElementById('resume-email').textContent = resumeData.personal_info.email || '-';
        document.getElementById('resume-phone').textContent = resumeData.personal_info.phone || '-';
    }

    if (resumeData.skills && resumeData.skills.length > 0) {
        document.getElementById('resume-skills').textContent = resumeData.skills.join(', ');
    }

    // Enable autofill button
    autofillBtn.disabled = false;
}

/**
 * Handle autofill button click
 */
async function handleAutofill() {
    autofillBtn.disabled = true;
    autofillBtn.textContent = 'Scanning page...';
    showMessage(autofillStatus, 'Detecting form fields...', 'info');

    try {
        // Get current tab
        const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });

        console.log('Sending autofill message to tab:', tab.id);

        // Try to send message to content script
        let response;
        try {
            response = await chrome.tabs.sendMessage(tab.id, {
                action: 'autofill',
                backendUrl: BACKEND_URL
            });
        } catch (error) {
            // Content script not loaded - inject it manually
            console.log('Content script not ready, injecting...');
            
            await chrome.scripting.executeScript({
                target: { tabId: tab.id },
                files: ['content_script.js']
            });

            // Wait for script to initialize
            await new Promise(resolve => setTimeout(resolve, 500));

            // Try again
            response = await chrome.tabs.sendMessage(tab.id, {
                action: 'autofill',
                backendUrl: BACKEND_URL
            });
        }

        console.log('Autofill response:', response);

        if (response && response.success) {
            showMessage(autofillStatus, `✓ Filled ${response.fieldsCount} of ${response.totalFields} fields!`, 'success');
        } else {
            showMessage(autofillStatus, response?.message || 'No fillable fields found', 'info');
        }

    } catch (error) {
        console.error('Autofill error:', error);
        showMessage(autofillStatus, `Error: ${error.message}`, 'error');
    } finally {
        autofillBtn.disabled = false;
        autofillBtn.textContent = '🚀 Autofill Current Page';
    }
}

/**
 * Show status message
 */
function showMessage(element, message, type) {
    element.textContent = message;
    element.className = `status-message ${type}`;
    element.style.display = 'block';
}
