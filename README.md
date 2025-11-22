# 🤖 Job Autofill Assistant

**AI-powered Chrome extension for autofilling job application forms using your resume.**

100% local processing with Ollama + Phi-3 Mini. No cloud services. Privacy-first.

---

## ✨ Features

- 📄 **PDF Resume Parsing** - Extracts structured data from your resume
- 🧠 **Local LLM Intelligence** - Uses Phi-3 Mini (via Ollama) for smart field mapping
- 🎯 **Smart Autofill** - Intelligently matches form fields to resume data
- 🔒 **Privacy-First** - Everything runs locally (no API calls to external services)
- ⚡ **Fast & Lightweight** - Processes forms in seconds

---

## 🏗️ Architecture

┌─────────────────────────────────────────────────────────┐
│ Chrome Extension (Manifest V3) │
│ ┌─────────────┐ ┌─────────────┐ ┌─────────────────┐ │
│ │ popup.js │ │ background │ │ content_script │ │
│ │ (upload UI) │ │ worker │ │ (form detection)│ │
│ └──────┬──────┘ └─────────────┘ └────────┬────────┘ │
└─────────┼────────────────────────────────────┼──────────┘
│ │
└──────────────┬─────────────────────┘
│ HTTP (localhost:8080)
▼
┌─────────────────────────────────────┐
│ Java Spring Boot Backend │
│ │
│ - PDF Parsing (Apache PDFBox) │
│ - REST APIs │
│ - Resume Storage (in-memory) │
└──────────────┬──────────────────────┘
│ HTTP (localhost:11434)
▼
┌─────────────────────────────────────┐
│ Ollama (Phi-3 Mini) │
│ │
│ - Resume → JSON extraction │
│ - Field mapping & autofill │
└─────────────────────────────────────┘

---

## 📋 Prerequisites

| Component | Version | Purpose |
|-----------|---------|---------|
| **Java** | 17+ | Backend runtime |
| **Maven** | 3.8+ | Build tool |
| **Node.js** | 20+ | Extension development |
| **Chrome** | Latest | Extension platform |
| **Ollama** | 0.12.3+ | Local LLM runtime |
| **Phi-3 Mini** | Latest | Small instruction-tuned LLM (~3.8 GB) |

---

## 🚀 Installation & Setup

### Step 1: Install Ollama

Download and install Ollama
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

Pull Phi-3 Mini model (~3.8 GB)
```bash
ollama pull phi3:mini
```

Start Ollama server (keep this running)
```bash
ollama serve
```

### Step 2: Set Up Backend

Clone repository
```bash
git clone <your-repo-url>
cd job-autofill-local/backend
```

Build project
```bash
mvn clean install
```

Run backend (keep this running)
```bash
mvn spring-boot:run
```

Backend will start on `http://localhost:8080`

### Step 3: Load Chrome Extension

1. Open Chrome and navigate to `chrome://extensions/`
2. Enable **Developer mode** (toggle in top-right)
3. Click **Load unpacked**
4. Select the `extension/` folder from this project
5. Pin the extension to toolbar (optional)

---

## 📖 Usage

### 1. Upload Your Resume

1. Click the extension icon in Chrome toolbar
2. **Drag & drop** your resume PDF onto the upload zone (or click to browse)
3. Click **"Upload & Extract"**
4. Wait 10-15 seconds for AI extraction
5. Your name, email, phone, and skills will appear

### 2. Autofill a Job Application

1. Navigate to any job application form
2. Click the extension icon
3. Click **"🚀 Autofill Current Page"**
4. Fields will flash green and auto-fill with your data

---

## 🧪 Testing

### Test with Sample Form

Create a test form
```bash
google-chrome ~/test-form.html
```

Then use the extension to autofill it.

### Backend API Testing

Health check
```bash
curl http://localhost:8080/api/health
```

Upload resume
```bash
curl -X POST http://localhost:8080/api/resume/upload \
-F "file=@/path/to/resume.pdf"
```

Extract structured data
```bash
curl -X POST http://localhost:8080/api/extract
```

Test autofill suggestion
```bash
curl -X POST http://localhost:8080/api/autofill \
-H "Content-Type: application/json" \
-d '{
"field_label": "Full Name",
"field_name": "name",
"field_type": "text"
}'
```

---

## 🔧 Configuration

### Backend Configuration (`backend/src/main/resources/application.yml`)

```yaml
ollama:
  base-url: http://localhost:11434
  model: phi3:mini
  timeout: 90000 # 90 seconds

server:
  port: 8080
```

### Extension Configuration (`extension/popup.js`)

```javascript
const BACKEND_URL = 'http://localhost:8080';
```

---

## 📁 Project Structure

```
job-autofill-local/
├── backend/ # Spring Boot backend
│ ├── src/main/java/
│ │ └── com/jobautofill/
│ │ ├── controller/ # REST endpoints
│ │ ├── service/ # Business logic
│ │ ├── model/ # Data models
│ │ ├── storage/ # In-memory storage
│ │ ├── config/ # Spring configuration
│ │ └── util/ # Utilities (JSON sanitizer)
│ ├── src/main/resources/
│ │ └── application.yml # Configuration
│ └── pom.xml # Maven dependencies
│
├── extension/ # Chrome Extension
│ ├── manifest.json # Extension configuration
│ ├── popup.html # UI
│ ├── popup.js # Upload & autofill logic
│ ├── styles.css # Styling
│ ├── content_script.js # Form detection & filling
│ ├── background.js # Service worker
│ └── icons/ # Extension icons
│
├── docs/ # Documentation
│ └── ARCHITECTURE.md # Detailed architecture
│
└── README.md # This file
```

---

## 🛠️ Tech Stack

### Backend
- **Java 17** - Language
- **Spring Boot 3.2** - Web framework
- **Apache PDFBox 3.0.1** - PDF parsing
- **Spring WebFlux** - HTTP client for Ollama
- **Jackson** - JSON processing

### Frontend
- **Chrome Extension API (Manifest V3)** - Extension platform
- **Vanilla JavaScript** - No frameworks
- **CSS3** - Modern styling

### AI/ML
- **Ollama** - Local LLM runtime
- **Phi-3 Mini (3.8B)** - Microsoft's small instruction-tuned model

---

## 🐛 Troubleshooting

| Problem | Solution |
|---------|----------|
| **"Backend not running"** | Start backend: `cd backend && mvn spring-boot:run` |
| **"Ollama not reachable"** | Start Ollama: `ollama serve` in separate terminal |
| **"Extraction takes >30 seconds"** | Normal for first run (model loading). Subsequent runs faster. |
| **"No fields found"** | Page might use dynamic forms. Try clicking refresh on page. |
| **"Low confidence autofill"** | Field labels unclear. Check console logs for field detection. |
| **CORS error** | Restart backend. Check `WebConfig.java` allows `*` origins. |

---

## 🎯 Supported Field Types

| Field Type | Examples | Autofill Capability |
|------------|----------|---------------------|
| **Name** | Full Name, First Name, Last Name | ✅ High accuracy |
| **Contact** | Email, Phone, Mobile | ✅ High accuracy |
| **Skills** | Technical Skills, Core Competencies | ✅ High accuracy |
| **Education** | Degree, University, Graduation Year | ✅ Good accuracy |
| **Experience** | Work Experience, Job Title, Company | ✅ Good accuracy |
| **Custom** | Cover Letter, Why Us?, etc. | ⚠️ May need review |

---

## 🔒 Privacy & Security

- ✅ **100% Local Processing** - No data sent to external servers
- ✅ **No Telemetry** - Extension doesn't track or log your activity
- ✅ **No Authentication** - No accounts or API keys needed
- ✅ **In-Memory Storage** - Resume data cleared on backend restart
- ✅ **Open Source** - Full code visibility

---

## 📊 Performance

| Metric | Value |
|--------|-------|
| **Resume Upload** | < 1 second |
| **AI Extraction** | 10-15 seconds (first run), 5-8 seconds (subsequent) |
| **Field Autofill** | 2-5 seconds per field |
| **Memory Usage** | ~500 MB (backend) + ~4 GB (Ollama/Phi-3) |

---

## 🚀 Future Enhancements

- [ ] Add DOCX resume support
- [ ] Implement SQLite persistence for resume storage
- [ ] Add batch autofill for multi-page forms
- [ ] Support for custom field mappings
- [ ] Browser extension for Firefox
- [ ] Dark mode for popup UI
- [ ] Keyboard shortcuts (Ctrl+Shift+A to autofill)
- [ ] Export/import resume data as JSON

---

## 🤝 Contributing

Built as a learning project. Feel free to fork and enhance!

---

## 📝 License

MIT License - See LICENSE file for details

---

## 👨‍💻 Author

**Garvit Pathak**

- 🎓 B.Tech Computer Science - VIT Vellore (Expected 2026)
- 💼 Aspiring DevOps Engineer & Full-Stack Developer
- 📧 garvitpathak2003@gmail.com
- 🔗 [GitHub](https://github.com/your-username)
- 🔗 [LinkedIn](https://linkedin.com/in/your-profile)

---

## 🙏 Acknowledgments

- **Ollama** - For making local LLM deployment easy
- **Microsoft** - For the Phi-3 Mini model
- **Spring Boot** - For the excellent framework
- **Apache PDFBox** - For robust PDF parsing

---

## 📸 Screenshots

### Extension Popup
![Extension Popup](docs/screenshots/popup.png)

### Autofill in Action
![Autofill Demo](docs/screenshots/autofill.png)

---

**Built with ❤️ using local AI. No cloud required.**