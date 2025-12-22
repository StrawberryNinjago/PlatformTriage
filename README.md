# PlatformTriage - DB Doctor (MVP)

A developer-first, read-only diagnostic tool that centralizes common database environment checks into a single, consistent interface.

## What It Does

PlatformTriage helps developers quickly diagnose database configuration issues:

1. **Confirm DB Identity**: Verify instance, database, schema, and current role
2. **Detect Privilege/Ownership Mismatches**: Check table ownership and CRUD grants per table
3. **Inspect Flyway State**: View latest applied migrations, pending changes, and failures
4. **Inventory Tables, Indexes, Constraints**: List and search database objects
5. **Verify Schema Rules**: Validate required uniqueness and schema correctness

## Why It Works

- **Reduces MTTR**: Diagnose issues in minutes, not hours
- **Encodes Expert Knowledge**: Senior and DevOps best practices, reusable across teams
- **Shifts Triage Left**: Catch environment issues before escalation
- **Improves Velocity**: Faster delivery by catching problems early

## Architecture

```
┌─────────────┐         REST API          ┌──────────────────┐
│   React     │ ──────────────────────> │  Spring Boot     │
│   (Port     │   HTTP Requests         │  Backend         │
│   3000)     │ <────────────────────── │  (Port 8081)     │
└─────────────┘         JSON             └──────────────────┘
                                                   │
                                                   │ JDBC
                                                   ▼
                                         ┌──────────────────┐
                                         │   PostgreSQL     │
                                         │   Database       │
                                         └──────────────────┘
```

## Project Structure

```
Triage/
├── apps/
│   └── dbtriage/          # Spring Boot backend (port 8081)
│       ├── src/main/java/com/example/Triage/
│       │   ├── config/    # CORS configuration
│       │   ├── controller/# REST API endpoints
│       │   ├── core/      # Service layer
│       │   └── model/     # Request/Response models
│       └── pom.xml
└── frontend/              # React frontend (port 3000)
    ├── src/
    │   ├── components/    # React components
    │   ├── services/      # API service (Axios)
    │   └── App.jsx        # Main app
    └── package.json
```

## Getting Started

### Prerequisites

- Java 21+
- Node.js 21+ (or 20.19+/22.12+)
- Maven 3.8+
- PostgreSQL database (for testing)

### Backend Setup

```bash
cd apps/dbtriage
mvn clean install -DskipTests
mvn spring-boot:run
```

Backend will start on **http://localhost:8081**

### Frontend Setup

```bash
cd frontend
npm install
npm run dev
```

Frontend will start on **http://localhost:3000**

## API Endpoints

### Connection Management
- `POST /api/db/connections` - Create database connection
- `GET /api/db/summary?connectionId=...` - Get database summary

### Diagnostic Endpoints
- `GET /api/db/identity?connectionId=...` - Get database identity
- `GET /api/db/flyway/health?connectionId=...` - Check Flyway health
- `GET /api/db/tables?connectionId=...&schema=public` - List tables
- `GET /api/db/tables/search?connectionId=...&schema=public&q=...` - Search tables
- `GET /api/db/privileges?connectionId=...&schema=public&table=...` - Check privileges
- `GET /api/db/tables/indexes?connectionId=...&schema=...&table=...` - List indexes
- `GET /api/db/tables/introspect?connectionId=...&schema=...&table=...` - Table details

## Using the UI

1. **Connect**: Enter your PostgreSQL connection details and click "Connect"
2. **Load Summary**: Click "Load Summary" to get an overview
3. **Run Diagnostics**: Use the 7 ready-to-use action buttons:
   - Verify Connection (DB Identity)
   - Flyway Health
   - List App Tables
   - Find Table (search)
   - Check Ownership & Grants
   - List Indexes (table)
   - Table Details (columns+constraints+indexes)

## Features

### 7 Ready-to-Use Actions

1. **Verify Connection (DB Identity)**
   - Shows: database, current user, server version, schema
   - Use: Confirm you're connected to the right environment

2. **Flyway Health**
   - Shows: latest migration, failed count, status
   - Use: Check if migrations are up-to-date

3. **List App Tables**
   - Shows: all non-system tables with row counts
   - Use: Inventory application tables

4. **Find Table**
   - Shows: tables matching search query
   - Use: Quickly locate tables by name

5. **Check Ownership & Grants**
   - Shows: table owner, current user privileges, missing grants
   - Use: Validate permissions (SELECT, INSERT, UPDATE, DELETE)

6. **List Indexes**
   - Shows: all indexes for a table
   - Use: Check index coverage

7. **Table Details**
   - Shows: columns, constraints, and indexes
   - Use: Complete table introspection

## Development

### Backend Development

```bash
cd apps/dbtriage
mvn spring-boot:run
```

Hot reload is enabled with Spring Boot DevTools.

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
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
```

## Security Notes

- This tool is **read-only** by design
- Credentials are stored in-memory only (15-minute TTL)
- No credentials are logged or persisted
- CORS is configured for localhost development
- For production, update CORS configuration in `CorsConfig.java`

## Troubleshooting

### Backend won't start
- Check Java version: `java -version` (need 21+)
- Check port 8081 is available: `lsof -i :8081`

### Frontend won't start
- Check Node version: `node -v` (need 21+ or 20.19+/22.12+)
- Check port 3000 is available: `lsof -i :3000`
- Clear npm cache: `npm cache clean --force`

### Connection fails
- Verify PostgreSQL is running
- Check SSL mode (use `require` for Azure, `disable` for local)
- Verify credentials and network access

### CORS errors
- Ensure backend is running on port 8081
- Check CORS configuration in `CorsConfig.java`
- Verify frontend is accessing `http://localhost:8081`

## License

This is an MVP/demonstration project.

## Contributing

This is an MVP. For production use, consider:
- Authentication/authorization
- Connection pooling
- Audit logging
- Role-based access control
- Multi-database support (MySQL, SQL Server, etc.)

