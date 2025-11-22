// Service worker for Job Autofill extension
console.log('Job Autofill service worker loaded');

// Listen for extension installation
chrome.runtime.onInstalled.addListener((details) => {
    console.log('Extension installed:', details.reason);
    
    if (details.reason === 'install') {
        console.log('First time installation');
        // Could open onboarding page here
    }
});

// Listen for messages from popup or content scripts
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    console.log('Message received in background:', message);
    
    if (message.action === 'checkBackend') {
        // Check if backend is reachable
        fetch('http://localhost:8080/api/health')
            .then(response => response.json())
            .then(data => {
                sendResponse({ success: true, status: data.status });
            })
            .catch(error => {
                sendResponse({ success: false, error: error.message });
            });
        return true; // Keep channel open for async response
    }
});
