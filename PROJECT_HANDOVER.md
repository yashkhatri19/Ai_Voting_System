# VOTE INDIA - Project Handover & Claude Prompt Context

This file serves as a comprehensive system summary and handover guide. You can copy the content below or send this file directly to Claude (or any other assistant) to explain the project architecture, implemented features, and next steps/changes.

---

## 🏛️ Project Summary & Tech Stack
**VOTE INDIA** is a secure, state-of-the-art online voting portal with integrated AI assistance.

- **Backend**: Spring Boot 3.5.4 (Java 21)
- **Database**: MongoDB (via Spring Data MongoDB)
- **Security**: Spring Security 6.x (Stateless JWT token cookies, BCrypt, Brute-Force lockout, Cache protection, CSRF support)
- **Frontend**: SSR Thymeleaf + HTML5 + CSS App Shell Layout + Bootstrap 5.3.3
- **AI Integrations**: Google Gemini API (`gemini-1.5-flash` model)
- **Email Delivery**: JavaMailSender (SMTP) for OTP and notifications
- **Containers**: Multi-stage Dockerfile and Docker Compose setup

---

## 📂 Core Directory Structure

```
src/main/java/com/chhavi/
├── pojo/
│   ├── User.java               # Auth properties, lockout trackers, verified status
│   ├── Candidate.java          # Manifesto details and Base64 graphic symbols
│   ├── Election.java           # Boundaries and candidateIds list
│   ├── VoterRecord.java        # Decoupled voter metadata (Voter privacy)
│   └── Vote.java               # Decoupled anonymous ballot
│
├── repository/                 # UserRepository, ElectionRepository, etc.
├── config/
│   └── SecurityConfig.java     # CORS, CORS policy, security filters, CSRF config
│
├── security/
│   ├── JwtUtils.java           # Creates/validates Access & Refresh JWTs
│   ├── JwtAuthenticationFilter. # Intercepts calls, handles token renewal
│   ├── AuthenticationSuccessListener.java
│   └── AuthenticationFailureListener.java
│
├── controller/                 # AuthController, AdminController, VoterController, etc.
└── utils/
    └── EmailService.java       # Mail triggers (OTP, signup validation)
```

---

## 🔑 Core Features & Internal Logic

1. **JWT & Session Hybrid Auth**:
   - Authentication is performed stateless on every request using HttpOnly cookies `accessToken` (1 hour) and `refreshToken` (7 days).
   - `SessionCreationPolicy.IF_REQUIRED` is kept active to preserve standard Thymeleaf container features such as CSRF tokens and HttpSession logs (e.g. Chat history).
2. **Double-Voting Prevention & Privacy**:
   - `Vote` contains no reference to the User.
   - `VoterRecord` stores `(userId, electionId)` to verify if the user has voted.
3. **Smart AI Assistant (Gemini)**:
   - Uses `HttpSession` to pass the last 10 conversational turns (user/model roles) to Gemini's API.
   - Manifesto summary prompt translates the candidate's manifestos into Markdown showing Short, Detailed, and Bullet point promises in both English and Regional script formats.
   - Auto-fallback to offline responses if `GEMINI_API_KEY` is equal to `"your_gemini_api_key"`.
4. **Docker & Compose**:
   - Production multi-stage caching Dockerfile.
   - Multi-container local Compose setups (Java Server + local MongoDB instance) with health check parameters mapping to `/health`.

---

## 🎯 Next Steps / Suggested Changes (Copy this to Claude)

Here are the potential areas for next-step enhancements. You can ask Claude to implement any of these:

```markdown
Hello Claude! I am working on a Spring Boot + Thymeleaf + MongoDB Voting System.
The system is fully documented in PROJECT_HANDOVER.md.
Please help me implement the following next features/changes:

1. [Option A - Admin Enhancements]
   - Add search and pagination features to the Admin's Users / Voters list page (templates/admin/users.html).
   - Add sorting capabilities to the Elections list table.

2. [Option B - Voter Dashboard Enhancements]
   - Implement an interactive profile photo upload using Base64 format (similar to Candidate symbol upload).
   - Add a download option (e.g., generate PDF) for the voter's voting success receipt (voter/vote-success.html) using a library like OpenPDF/iText.

3. [Option C - AI Improvements]
   - Build a comparison screen where voters can select two candidates, send their manifestos to Gemini, and generate a side-by-side neutral comparison matrix.

4. [Option D - OTP Enhancement]
   - Integrate an SMS Gateway provider fallback (e.g., Twilio API) if SMTP mail delivery fails or times out.
```
