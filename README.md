# DB Doctor (PlatformTriage – DB Doctor MVP)

**A diagnosis-first, read-only web UI for fast, actionable database triage**

---

## Problem

Across capability teams, we repeatedly lose engineering time to database-related issues that are hard to diagnose quickly:

- **Wrong credential / role used** – migration run under unexpected user; drift between connected user and `installed_by`
- **Privilege gaps** – service can connect but fails later on read/write
- **Schema drift and missing DB objects** – different constraints/indexes across environments
- **Performance regressions** – index mismatch with real query predicates
- **Risky deletes** – cascade chains and recursive cascades discovered too late

**These issues are not "DB admin work"—they are engineering workflow problems.** The cost is delayed debugging, longer incident time, and repeated tribal knowledge.

---

## Solution

DB Doctor is a **diagnosis-first, read-only web UI** that provides fast, actionable database triage without requiring DB admin access or deep Postgres expertise.

It focuses on two things:

1. **Explain what is true** (identity, Flyway state, ownership, privileges, indexes, constraints)
2. **Explain what it means** (risk, drift, missing requirements, likely failure modes)

---

## What DB Doctor Provides (MVP)

### 1. Connection Identity
- Current user, server, DB version, server time
- Confirms you're connected to the right environment

### 2. Flyway Health
- History present, latest migration, failed count
- Who ran migrations (`installed_by` summary)
- Drift detection (expected user vs actual user)

### 3. Privileges & Access
- Schema privileges + table privilege checks
- Warnings for missing grants (SELECT, INSERT, UPDATE, DELETE)
- Ownership validation

### 4. Table Diagnostics
- Indexes and constraint breakdown
- Risk annotations:
  - Cascade FK marked as "high impact"
  - Recursive cascades highlighted
  - Missing indexes flagged

### 5. SQL Diagnostic Sandbox (Static Analysis)
**SQL is never executed** – pure static analysis of pasted statements:

- **SELECT**: Index coverage against WHERE predicates
- **INSERT**: Missing NOT NULL columns, unique constraint risks
- **UPDATE**: Constraint conflicts, multi-row impacts
- **DELETE**: Cascade delete blast radius, root entity warnings

Analyzes:
- Index coverage
- Missing NOT NULL columns for INSERT
- Unique constraint conflict risk
- FK presence risk
- Cascade delete blast radius
- Root entity detection (cart, account, user, etc.)

---

## Why This Matters (Impact)

DB Doctor shortens the path from **"something is wrong"** to **"here is the likely root cause"**:

✅ **Faster confirmation** of credential drift, privilege gaps, schema inconsistencies  
✅ **Earlier detection** of risky schema patterns (recursive cascades, missing unique constraints)  
✅ **Preventive debugging** – detect likely constraint failures or index gaps before running code  
✅ **Lower dependency** on DB admins and reduced back-and-forth during incidents

---

## Severity Taxonomy

DB Doctor uses a consistent severity system across all diagnostics:

### ❌ ERROR (Red)
**Definition**: Highly likely to fail immediately or already failing

**Examples:**
- Flyway failed migrations > 0
- Missing required NOT NULL columns in INSERT
- No privileges (SELECT missing on required table)
- History table missing when Flyway is expected

### ⚠️ WARN (Amber)
**Definition**: Will not always fail, but indicates high risk, drift, or hidden production issues

**Examples:**
- Credential drift: `installed_by` ≠ connected user
- Cascade delete detected from root entity
- Unique constraint conflict risk (depends on values)
- Schema CREATE missing when a migration run is expected under this credential

### ℹ️ INFO (Blue)
**Definition**: Not a failure; useful context, best-practice confirmation, or low-risk improvement

**Examples:**
- Optimal index coverage for predicates
- Identity information (server version, time)
- "Composite index exists for WHERE columns"

### ✅ OK / PASS (Green)
**Definition**: Explicitly validated and safe under current checks

**Examples:**
- Flyway healthy and latest applied known
- PK integrity OK, FK integrity OK
- Privileges match selected profile (read-only / read-write)

### Presentation Rules
- **Always sort findings**: ERROR → WARN → INFO → OK
- Each finding includes:
  - **Title** (human label)
  - **Description** (one sentence)
  - **Recommendation** (actionable next step)
  - **Why it matters** (context)

---

## Guardrails / Non-Goals

DB Doctor is intentionally **not** a DB admin tool:

### ✅ What DB Doctor Does
- Read-only checks only; safe for wider engineering adoption
- Diagnostics are deterministic and explainable (not guessy)
- Fast, focused triage for engineering workflow problems

### ❌ What DB Doctor Does NOT Do

#### 1. No Writes or Mutations
- No arbitrary SQL execution
- No `EXPLAIN ANALYZE`
- No DDL suggestions with "Apply" buttons
- No write capability, even "safe" ones

**Reason**: It breaks the "diagnostics-only" trust model and increases review/security friction.

#### 2. No Performance Claims with Fake Precision
Avoid:
- "This query will take X ms"
- "This index will reduce latency by Y%"
- Cost-based estimates without plans

Keep it to what we can assert confidently:
- Coverage exists vs not
- Composite vs partial
- Risk heuristics

#### 3. No Row Counts by Default
Row counts are tempting but dangerous:
- Expensive on large tables
- Can mislead if stats are stale

If ever added, make it clearly optional and "may be slow".

#### 4. No Overly Generic Rules That Spam Warnings
Only warn when:
- There is meaningful blast radius (CASCADE, recursion)
- There is drift (ownership/`installed_by` mismatch)
- There is strong likelihood of operational impact

#### 5. No Deep DBA Features
Avoid scope creep into:
- Vacuum/analyze tuning UI
- Replication/HA status dashboards
- Role management/grants editor

**DB Doctor wins by being fast, safe, and engineer-friendly, not by replacing pgAdmin.**

---

## Architecture

```
┌─────────────┐         REST API        ┌──────────────────┐
│   React     │ ──────────────────────> │  Spring Boot     │
│   Frontend  │   HTTP Requests         │  Backend         │
│ (Port 5173) │ <────────────────────── │  (Port 8081)     │
└─────────────┘         JSON            └──────────────────┘
                                                   │
                                                   │ JDBC (Read-Only)
                                                   ▼
                                         ┌──────────────────┐
                                         │   PostgreSQL     │
                                         │   Database       │
                                         └──────────────────┘
```

---

## Project Structure

```
Triage/
├── apps/
│   └── dbtriage/              # Spring Boot backend (port 8081)
│       ├── src/main/java/com/example/Triage/
│       │   ├── config/        # CORS configuration
│       │   ├── controller/    # REST API endpoints
│       │   ├── handler/       # Business logic handlers
│       │   ├── service/       # Analysis services
│       │   ├── dao/           # Database queries
│       │   ├── model/         # DTOs, enums, requests/responses
│       │   └── exception/     # Custom exceptions
│       └── pom.xml
├── frontend/                  # React frontend (port 5173)
│   ├── src/
│   │   ├── components/        # React components
│   │   ├── services/          # API service (Axios)
│   │   └── App.jsx            # Main app
│   └── package.json
└── Documentation/
    ├── README.md              # This file
    ├── SQL_SANDBOX_README.md  # SQL Sandbox detailed docs
    ├── SQL_SANDBOX_QUICKSTART.md
    └── TESTING_GUIDE.md
```

---

## Getting Started

### Prerequisites

- **Java 21+**
- **Node.js 18+**
- **Maven 3.6+**
- **PostgreSQL database** (for testing)

### Quick Start

#### 1. Start the Backend

```bash
cd apps/dbtriage
mvn spring-boot:run
```

Backend will start on **http://localhost:8081**

#### 2. Start the Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend will start on **http://localhost:5173**

#### 3. Connect to Your Database

1. Open **http://localhost:5173** in your browser
2. Enter your PostgreSQL connection details
3. Click **"Connect"**
4. Start diagnosing!

---

## Work Laptop Runbook (Deployment Doctor)

Use this when running Deployment Doctor on a work laptop against AKS.

### 1. Authenticate to Azure and target subscription

```bash
az login
az account set --subscription "<your-subscription-id>"
az aks get-credentials -g "<resource-group>" -n "<aks-cluster>" --overwrite-existing
```

### 2. Verify Kubernetes access

```bash
kubectl config current-context
kubectl get ns
```

### 3. Start the app locally

Backend (port 8082):

```bash
cd /Users/yanalbright/Downloads/Triage/apps/platformtriage
mvn spring-boot:run
```

Frontend (port 3000):

```bash
cd /Users/yanalbright/Downloads/Triage/frontend
npm install
npm run dev
```

### 4. Open Deployment Doctor UI

- Open `http://localhost:3000`
- Go to **Deployment Doctor**

### 5. Required query inputs in UI

- `namespace` is required
- You must provide either `Label Selector` or `Release`
- If you do not use label selectors directly, use `Release`
- `Release` maps to selector: `app.kubernetes.io/instance=<release>`
- Namespace-only is not enough. Use: `namespace + (selector or release)`

Examples:

- `namespace=cart`, `selector=app=cart-app`
- `namespace=cart`, `release=cart`

---

## Using DB Doctor

### Main Features

#### 1. Database Connection
- Enter host, port, database, username, password
- Optional: schema (defaults to `public`)
- SSL mode configuration

#### 2. Summary View
- Database identity (server, version, current user)
- Flyway health status
- Quick diagnostics overview

#### 3. Action Buttons
**General Database:**
- Verify Connection (DB Identity)
- Flyway Health
- List All Tables

**Inspect Specific Table:**
- Show Table Details (introspection)
- Check Ownership & Grants

**Search for Tables:**
- Find Table (fuzzy search)

#### 4. SQL Diagnostic Sandbox
- Paste INSERT / UPDATE / DELETE / SELECT SQL
- Click **"Analyze SQL"**
- Get instant static analysis:
  - Index coverage warnings
  - Constraint violation risks
  - Cascade delete impacts
  - Root entity detection

#### 5. Results Panel
- Color-coded findings (ERROR / WARN / INFO)
- Severity-sorted automatically
- Actionable recommendations
- Expandable details

#### 6. Console Panel
- Real-time activity log
- Success/warning/error messages
- API call tracking

---

## API Endpoints

### Connection Management
- `POST /api/db/connections` - Create database connection
- `GET /api/db/summary?connectionId=...` - Get database summary

### Diagnostic Endpoints
- `GET /api/db/identity?connectionId=...` - Get database identity
- `GET /api/db/flyway/health?connectionId=...` - Check Flyway health
- `GET /api/db/tables?connectionId=...&schema=public` - List tables
- `GET /api/db/tables/search?connectionId=...&schema=public&queryString=...` - Search tables
- `POST /api/db/privileges:check` - Check table privileges
- `GET /api/db/tables/indexes?connectionId=...&schema=...&table=...` - List indexes
- `GET /api/db/tables/introspect?connectionId=...&schema=...&table=...` - Full table introspection

### SQL Analysis
- `POST /api/sql/analyze` - Analyze SQL statement (static analysis only)

---

## Configuration

### Backend (application.yaml)

```yaml
spring:
  application:
    name: PlatformTriage

server:
  port: 8081
```

### Frontend (vite.config.js)

```javascript
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173
  }
})
```

### CORS Configuration
Edit `apps/dbtriage/src/main/java/com/example/Triage/config/CorsConfig.java` for production environments.

---

## Security Notes

- **Read-only by design** – no writes, no DDL, no mutations
- **In-memory credentials only** – 15-minute TTL, no persistence
- **No credential logging** – passwords never logged
- **CORS configured** for localhost development
- **Static SQL analysis** – SQL never executed in Sandbox

### For Production
- Add authentication/authorization
- Configure CORS for your domain
- Consider connection pooling
- Add audit logging
- Implement role-based access control

---

## Development

### Backend Development

```bash
cd apps/dbtriage
mvn spring-boot:run
```

Hot reload enabled with Spring Boot DevTools.

### Frontend Development

```bash
cd frontend
npm run dev
```

Vite provides instant hot module replacement (HMR).

### Building for Production

**Backend:**
```bash
cd apps/dbtriage
mvn clean package
java -jar target/dbtriage-0.0.1-SNAPSHOT.jar
```

**Frontend:**
```bash
cd frontend
npm run build
# Output: frontend/dist/
```

Optional: Copy frontend build to Spring Boot static resources for single-JAR deployment:
```bash
cp -r frontend/dist/* apps/dbtriage/src/main/resources/static/
```

---

## Adoption Path

1. **Start with capability teams** and DevOps volunteers as primary users
2. **Integrate into onboarding** and "how to debug DB issues" playbooks
3. **Standardize a "Copy Diagnostics" artifact** for JIRA/Slack escalation

### Success Metrics

- ✅ Reduced average time-to-diagnosis for DB-related issues
- ✅ Fewer repeated incidents tied to credential drift / missing privileges / missing indexes
- ✅ Reduced reliance on DB admins for routine triage questions

---

## Troubleshooting

### Backend won't start
- Check Java version: `java -version` (need 21+)
- Check port 8081 is available: `lsof -i :8081`
- Check Maven installation: `mvn -version`

### Frontend won't start
- Check Node version: `node -v` (need 18+)
- Check port 5173 is available: `lsof -i :5173`
- Clear npm cache: `npm cache clean --force`
- Delete `node_modules` and reinstall: `rm -rf node_modules && npm install`

### Connection fails
- Verify PostgreSQL is running
- Check SSL mode (`disable` for local, `require` for Azure)
- Verify credentials and network access
- Check firewall rules

### CORS errors
- Ensure backend is running on port 8081
- Check CORS configuration in `CorsConfig.java`
- Verify frontend is accessing `http://localhost:8081`

### SQL Sandbox keyboard shortcuts not working
- Refresh the browser page
- Click inside the SQL text area before using shortcuts
- Check browser console for JavaScript errors

---

## Documentation

- **[SQL Sandbox README](./SQL_SANDBOX_README.md)** - Detailed SQL analysis documentation
- **[SQL Sandbox Quick Start](./SQL_SANDBOX_QUICKSTART.md)** - 5-minute getting started guide
- **[SQL Sandbox Visual Guide](./SQL_SANDBOX_VISUAL_GUIDE.md)** - UI layout and examples
- **[Testing Guide](./TESTING_GUIDE.md)** - How to test DB Doctor

---

## Contributing

This is an MVP/demonstration project. For production use, consider:

- Authentication/authorization
- Connection pooling with HikariCP
- Comprehensive audit logging
- Role-based access control (RBAC)
- Multi-database support (MySQL, SQL Server, Oracle)
- Metrics and monitoring integration
- Docker containerization
- Kubernetes deployment manifests

---

## License

This is an MVP/demonstration project for internal use.

---

## Philosophy

**DB Doctor wins by being fast, safe, and engineer-friendly.**

It's not a replacement for pgAdmin, DataGrip, or DBA tools. It's a focused diagnostic tool that helps engineers answer the question: **"Why is this failing?"** as quickly as possible.

By staying focused on diagnosis, maintaining read-only guarantees, and providing explainable results, DB Doctor builds trust and becomes a go-to tool for database triage across engineering teams.

---

**Built with ❤️ for engineers who just want their database to work**
