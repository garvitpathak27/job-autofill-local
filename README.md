cat > README.md << 'EOF'
# Job Application Autofill – Local LLM + Chrome Extension

A local, privacy-first Chrome extension that auto-fills job application forms using your resume and a small LLM (Phi-3 Mini via Ollama).

## Architecture

See `docs/ARCHITECTURE.md`.

## Quick Start

1. Start Ollama: `ollama serve`
2. Start backend: (Phase 1 onwards)
3. Load extension in Chrome: (Phase 6 onwards)

## Phases

- [ ] Phase 0: Environment setup (current)
- [ ] Phase 1: Spring Boot skeleton
- [ ] Phase 2: Resume parsing (PDFBox)
- [ ] Phase 3: Ollama integration
- [ ] Phase 4: Form field mapping
- [ ] Phase 5: Chrome extension structure
- [ ] Phase 6: Extension popup & upload
- [ ] Phase 7: Content script for form detection
- [ ] Phase 8: Wire extension to backend
- [ ] Phase 9: Testing & refinement

EOF
