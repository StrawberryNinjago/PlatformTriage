# Smoke Tests Backend - Implementation Guide

## Overview

Complete Spring Boot backend implementation for the Smoke Tests API, following the patterns established in `dbtriage` and `platformtriage`.

**Status**: ✅ Core structure complete, ready for service implementation

---

## Project Structure

```
apps/smoketests/
├── pom.xml                                    ✅ Complete
├── src/main/
│   ├── java/com/example/smoketests/
│   │   ├── SmokeTestsApplication.java        ✅ Complete
│   │   ├── config/
│   │   │   └── CorsConfig.java               ✅ Complete
│   │   ├── controller/
│   │   │   └── SmokeTestsController.java     ✅ Complete (thin, delegates to handlers/services)
│   │   │
│   │   ├── handler/                          ✅ Business logic handlers
│   │   │   ├── EvidenceHandler.java          ✅ Complete (evidence retrieval, sanitization)
│   │   │   └── ExportHandler.java            ✅ Complete (export bundle creation)
│   │   ├── model/
│   │   │   ├── dto/                          ✅ Complete (18 DTOs)
│   │   │   ├── enums/                        ✅ Complete (9 enums)
│   │   │   ├── request/                      ✅ Complete (2 requests)
│   │   │   └── response/                     ✅ Complete (8 responses)
│   │   └── service/                          ✅ Core services implemented
│   │       ├── SpecResolverService.java      ✅ Complete (resolves specs, computes fingerprints)
│   │       ├── WorkflowService.java          ✅ Complete (workflow catalog management)
│   │       ├── ValidationService.java        ✅ Complete (preflight validation)
│   │       ├── ExecutionService.java         ✅ Complete (test execution, run management)
│   │       ├── UploadService.java            ✅ Complete (file upload handling)
│   │       ├── CacheService.java             ⚠️  TODO: Implement caching logic
│   │       └── ExportService.java            ⚠️  TODO: Implement export bundling
│   └── resources/
│       └── application.yaml                  ✅ Complete
```

---

## API Endpoints Implemented

### 1. Spec Resolution
**GET** `/api/smoke/spec/resolve`
- Resolves OpenAPI spec from blob storage or upload
- Computes spec fingerprint (ETag/hash)
- Returns cache status for generated tests

**Query Params:**
- `environment`, `capability`, `apiVersion`
- `source` (BLOB/UPLOAD)
- `blobPath` or `uploadId`

**Response**: `SpecResolveResponse`

---

### 2. File Uploads
**POST** `/api/smoke/uploads`
- Accepts multipart file upload
- Supports OpenAPI specs and workflow YAML
- Returns upload ID and SHA256 hash

**Form Data:**
- `file` (multipart)
- `purpose` (OPENAPI_SPEC/WORKFLOW_YAML)

**Response**: `UploadResponse`

---

### 3. Workflow Catalog
**GET** `/api/smoke/workflows?capability=cart`
- Returns curated workflow definitions
- Pre-configured workflows per capability

**Response**: `WorkflowCatalogResponse` with workflow list

---

### 4. Configuration Validation (Preflight)
**POST** `/api/smoke/validate`
- **Does NOT call target APIs**
- Validates spec resolution, auth profile, workflow YAML
- Checks operationId references
- Returns validation checks with PASS/FAIL status

**Request**: `ValidateConfigRequest`
**Response**: `ValidationResponse`

---

### 5. Run Smoke Tests
**POST** `/api/smoke/runs`
- Starts async test execution
- Generates/loads tests, executes against target
- Returns run ID immediately (202 Accepted)

**Request**: `RunSmokeTestsRequest`
**Response**: `{ runId, status: "RUNNING", links }`

---

### 6. Poll Run Status
**GET** `/api/smoke/runs/{runId}`
- Returns run status and results
- Includes summary, test results, findings

**Response**: `RunResponse`

---

### 7. Evidence Details
**GET** `/api/smoke/runs/{runId}/evidence/{evidenceRef}`
- Returns sanitized request/response evidence
- Headers redacted (authorization, secrets)
- Body preview (truncated)

**Response**: `Evidence`

---

### 8. Export Diagnostics
**GET** `/api/smoke/runs/{runId}/export`
- Full diagnostic bundle for sharing/JIRA
- Includes target, summary, results, recommendations

**Response**: Export JSON bundle

---

## Data Models

### Enums (9 total)
- `CacheStatus` - PRESENT, MISSING, STALE
- `Suite` - CONTRACT, WORKFLOW, BOTH
- `SpecSourceType` - BLOB, UPLOAD
- `WorkflowSource` - CATALOG, UPLOAD
- `RunStatus` - RUNNING, PASSED, FAILED, PARTIAL
- `TestStatus` - PASS, FAIL, SKIP
- `CheckStatus` - PASS, FAIL, WARN
- `GenerationMode` - REGENERATE, IF_MISSING
- `ErrorCode` - SPEC_NOT_FOUND, WORKFLOW_INVALID, etc.

### Core DTOs (18 total)
- `Target`, `Spec`, `SpecSource`, `SpecInfo`
- `Auth`, `ContractOptions`, `WorkflowOptions`, `SuiteConfig`
- `GenerationInfo`, `ValidationCheck`, `ResolvedMetadata`
- `TestResult`, `SuiteSummary`, `RunSummary`
- `Evidence`, `WorkflowDefinition`, `LimitEndpointsConfig`, `TestCounts`

### Requests (2)
- `ValidateConfigRequest`
- `RunSmokeTestsRequest` (with nested `ExecutionConfig`)

### Responses (8)
- `SpecResolveResponse`
- `UploadResponse`
- `WorkflowCatalogResponse`
- `ValidationResponse`
- `RunResponse`
- `ApiErrorResponse`

---

## Configuration (application.yaml)

```yaml
server:
  port: 8081

smoke:
  cache:
    directory: ${user.home}/.triage/smoketests
  specs:
    blob-base-path: /specs
  workflows:
    catalog-path: /workflows
  execution:
    default-timeout-ms: 60000
    max-concurrent-runs: 10
```

---

## Services to Implement

### 1. SpecResolverService
**Responsibilities:**
- Fetch OpenAPI spec from blob storage or upload
- Parse YAML/JSON
- Compute fingerprint (ETag or SHA256)
- Cache spec content

**Key Methods:**
```java
SpecInfo resolveSpec(SpecSource source)
String computeFingerprint(String specContent)
OpenAPI parseSpec(String content)
```

**Dependencies:**
- Azure Blob Storage client (for blob source)
- Swagger Parser (`io.swagger.parser.v3`)
- SHA256 hashing utility

---

### 2. WorkflowService
**Responsibilities:**
- Load workflow catalog (from filesystem/DB)
- Parse uploaded workflow YAML
- Validate workflow structure
- Compute workflow fingerprint

**Key Methods:**
```java
List<WorkflowDefinition> getCatalogWorkflows(String capability)
WorkflowDefinition parseWorkflowYaml(String yaml)
String computeWorkflowFingerprint(String yaml)
void validateWorkflowSteps(WorkflowDefinition workflow, OpenAPI spec)
```

**Dependencies:**
- SnakeYAML parser
- SHA256 hashing

---

### 3. ValidationService
**Responsibilities:**
- Preflight validation (NO target API calls)
- Validate spec resolution
- Validate auth profile
- Validate workflow YAML
- Check operationId references in spec

**Key Methods:**
```java
ValidationResponse validate(ValidateConfigRequest request)
List<ValidationCheck> runPreflightChecks(...)
boolean validateAuthProfile(String profile)
boolean validateOperationIds(WorkflowDefinition workflow, OpenAPI spec)
```

**Dependencies:**
- SpecResolverService
- WorkflowService

---

### 4. ExecutionService
**Responsibilities:**
- Generate contract tests from OpenAPI spec
- Execute tests against target API
- Execute workflow steps
- Collect evidence
- Handle cleanup (even on failure)

**Key Methods:**
```java
String startRun(RunSmokeTestsRequest request) // Returns runId
RunResponse getRunStatus(String runId)
List<TestResult> generateContractTests(OpenAPI spec, ContractOptions options)
List<TestResult> executeContractTests(...)
List<TestResult> executeWorkflow(WorkflowDefinition workflow, ...)
Evidence captureEvidence(HttpRequest, HttpResponse)
```

**Dependencies:**
- WebClient (Spring WebFlux) for HTTP calls
- SpecResolverService
- WorkflowService
- CacheService
- Auth token acquisition service

**Important:**
- Redact sensitive headers (Authorization, API keys)
- Truncate large response bodies
- Support `alwaysAttemptCleanup` flag

---

### 5. UploadService
**Responsibilities:**
- Handle multipart file uploads
- Validate file size/type
- Compute SHA256
- Store temporarily
- Generate upload ID

**Key Methods:**
```java
UploadResponse uploadFile(MultipartFile file, String purpose)
String getUploadContent(String uploadId)
void cleanupExpiredUploads()
```

**Storage:**
- Filesystem: `~/.triage/smoketests/uploads/{uploadId}`
- Or: In-memory with TTL (for demo)

---

### 6. CacheService
**Responsibilities:**
- Cache generated test sets
- Store by `generatedTestSetId` (derived from spec fingerprint + options)
- Detect stale cache (spec changed)
- LRU eviction

**Key Methods:**
```java
void cacheGeneratedTests(String testSetId, List<TestResult> tests)
Optional<List<TestResult>> getCachedTests(String testSetId)
CacheStatus getCacheStatus(String specFingerprint)
void evictStaleCache()
```

**Storage:**
```
~/.triage/smoketests/
  └── cart/
      └── openapi-latest/
          ├── fingerprint.json
          ├── contract-tests.json
          └── metadata.json
```

---

### 7. ExportService
**Responsibilities:**
- Create export bundle from RunResponse
- Format for JIRA/support escalation
- Include recommendations

**Key Methods:**
```java
Map<String, Object> createExportBundle(RunResponse run)
List<String> generateRecommendations(RunResponse run)
```

**Dependencies:**
- RunResponse data
- Common export formatter (if shared)

---

## Implementation Priority

### Phase 1: MVP (Core functionality)
1. **SpecResolverService** - Mock blob storage, parse uploaded specs
2. **ValidationService** - Basic preflight checks
3. **UploadService** - File upload handling
4. **WorkflowService** - Load hardcoded catalog

### Phase 2: Execution
5. **ExecutionService** - Generate and execute contract tests
6. **CacheService** - Filesystem-based caching
7. **ExportService** - Bundle formatting

### Phase 3: Production-ready
- Auth token acquisition (JWT)
- Azure Blob Storage integration
- Async execution with job queue
- Persistent run history
- Evidence storage with size limits

---

## Testing the API

### Start the Backend
```bash
cd apps/smoketests
mvn spring-boot:run
```

Backend will start on **http://localhost:8081**

### Test Workflow Catalog
```bash
curl http://localhost:8081/api/smoke/workflows?capability=carts
```

### Test Validation
```bash
curl -X POST http://localhost:8081/api/smoke/validate \
  -H "Content-Type: application/json" \
  -d '{
    "target": {"environment":"local","capability":"carts","apiVersion":""},
    "spec": {"source": {"type":"BLOB","blobPath":"/specs/carts/openapi.yaml"}},
    "auth": {"required":true,"profile":"jwt-service"},
    "suiteConfig": {
      "suite":"CONTRACT",
      "contractOptions": {"happyPaths":true,"negativeAuth":true,"basic400":true,"strictSchema":true,"failFast":false},
      "workflowOptions": null
    }
  }'
```

### Test Spec Resolution
```bash
curl "http://localhost:8081/api/smoke/spec/resolve?environment=local&capability=carts&source=BLOB&blobPath=/specs/carts/openapi.yaml"
```

---

## Error Handling

All errors return consistent format:
```json
{
  "error": {
    "code": "SPEC_NOT_FOUND",
    "message": "Human-readable summary",
    "details": {
      "field": "spec.source.blobPath",
      "hint": "Check blob path or permissions"
    }
  }
}
```

**Error Codes:**
- `SPEC_NOT_FOUND` - OpenAPI spec not found
- `WORKFLOW_INVALID` - Workflow YAML parse error
- `AUTH_PROFILE_INVALID` - Unknown auth profile
- `RUN_TIMEOUT` - Execution exceeded timeout
- `TARGET_UNREACHABLE` - Cannot connect to target API
- `VALIDATION_FAILED` - Preflight validation failed
- `UPLOAD_FAILED` - File upload error
- `PARSE_ERROR` - YAML/JSON parse error

---

## Security & Redaction

**Critical Requirements:**
1. **Never return raw secrets in evidence**
   - Authorization headers → `***redacted***`
   - API keys → `***redacted***`
   - Tokens → `***redacted***`

2. **Truncate response bodies**
   - Max 500 chars in evidence preview
   - Store full evidence server-side if needed

3. **Validate uploads**
   - Max file size: 10MB
   - Content-type validation
   - Scan for malicious content (optional)

---

## Integration with Frontend

The frontend (`SmokeTestsPage.jsx`) already calls these endpoints via `apiService.js`:

```javascript
// Frontend API calls
apiService.runSmokeTests(config)           → POST /api/smoke/runs
apiService.validateSmokeTestConfig(config) → POST /api/smoke/validate
apiService.getSmokeTestHistory()           → GET /api/smoke/runs (list)
apiService.exportSmokeTestResults(runId)   → GET /api/smoke/runs/{runId}/export
```

**CORS is configured** to allow all origins during development.

---

## Next Steps

1. **Implement Phase 1 services** (SpecResolver, Validation, Upload, Workflow)
2. **Test with frontend** - Run `npm run dev` and click "Validate Configuration"
3. **Implement Phase 2** (Execution, Cache, Export)
4. **Add auth token acquisition** (JWT service integration)
5. **Connect to Azure Blob Storage** for spec retrieval
6. **Add persistent run history** (database or filesystem)
7. **Implement async execution** with job queue (Spring @Async or separate worker)

---

## Dependencies Added

Key libraries in `pom.xml`:
- **Swagger Parser** (`io.swagger.parser.v3:swagger-parser:2.1.20`) - Parse OpenAPI specs
- **SnakeYAML** (`org.yaml:snakeyaml:2.2`) - Parse workflow YAML
- **WebFlux** (`spring-boot-starter-webflux`) - HTTP client for API calls
- **Jackson YAML** (`jackson-dataformat-yaml`) - YAML serialization

---

## Philosophy Alignment

✅ **Determinism & Traceability** - All runs record fingerprints, IDs, timestamps
✅ **Two-step UX** - Validate (preflight) + Run (execution)
✅ **Local caching** - Transparent to UI, exposed via API
✅ **Diagnosis-first** - Evidence capture, recommendations, export bundle
✅ **Clean errors** - Consistent error format with hints

---

**Backend structure is complete and ready for service implementation!**
