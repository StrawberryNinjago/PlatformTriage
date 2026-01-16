# Smoke Tests - Service Layer Architecture

## Overview

The Smoke Tests backend follows a clean separation of concerns with a proper service layer. The controller is now **thin** - it only handles HTTP routing and delegates all business logic to services.

---

## Architecture Pattern

```
Controller (Thin)
    ↓ delegates to
Services (Business Logic)
    ↓ uses
DAOs/Repositories (Data Access)
```

---

## Service Layer Components

### ✅ 1. SpecResolverService

**Responsibility**: OpenAPI spec resolution and fingerprinting

**Methods**:
- `resolveSpec(environment, capability, apiVersion, source)` - Main resolution logic
- `computeFingerprint(source)` - Generate ETag/SHA256 for spec
- `buildBaseUrl(environment, capability, apiVersion)` - Construct target base URL

**Dependencies**: None (could add BlobStorageClient later)

**Status**: ✅ Implemented with mock data

**TODO**:
- Fetch specs from Azure Blob Storage
- Parse YAML/JSON with Swagger Parser
- Cache parsed specs in memory

---

### ✅ 2. WorkflowService

**Responsibility**: Workflow catalog and definition management

**Methods**:
- `getWorkflowCatalog(capability)` - Get all workflows for a capability
- `getWorkflowById(workflowId)` - Get specific workflow definition
- `validateWorkflowYaml(yaml)` - Validate workflow YAML structure
- `parseWorkflowYaml(yaml)` - Parse YAML to WorkflowDefinition
- `loadCatalogForCapability(capability)` - Load workflows from catalog

**Dependencies**: None (could add WorkflowRepository later)

**Status**: ✅ Implemented with hardcoded catalog

**Catalog Structure** (hardcoded):
- `carts`: cart-lifecycle-smoke, cart-item-mutation-smoke
- `catalog`: catalog-search-smoke

**TODO**:
- Load workflows from filesystem (`~/.triage/smoketests/workflows/`)
- Parse workflow YAML with SnakeYAML
- Validate operationId references against OpenAPI spec

---

### ✅ 3. ValidationService

**Responsibility**: Preflight configuration validation (NO target API calls)

**Methods**:
- `validateConfig(request)` - Main validation orchestration
- `validateBaseUrl(request)` - Check URL resolution
- `validateSpec(request)` - Fetch and parse OpenAPI spec
- `validateAuthProfile(request)` - Check auth profile exists
- `validateWorkflow(request)` - Check workflow configuration

**Dependencies**:
- SpecResolverService (for spec resolution)
- WorkflowService (for workflow validation)

**Status**: ✅ Implemented with basic checks

**Validation Checks**:
1. ✅ Resolve baseUrl - Uses SpecResolverService
2. ✅ Fetch and parse OpenAPI spec - Mock check
3. ✅ Auth profile validation - Known profiles list
4. ✅ Workflow validation - Checks workflow required when WORKFLOW/BOTH

**TODO**:
- Actually fetch and parse specs
- Validate operationIds in workflow against spec
- Check auth profile against real auth service

---

### ✅ 4. ExecutionService

**Responsibility**: Test execution and run management

**Methods**:
- `startRun(request)` - Start async test execution
- `getRunStatus(runId)` - Get run status and results
- `executeTestsAsync(runId, request)` - Execute tests asynchronously
- `generateRunId()` - Generate unique run ID

**Dependencies**:
- SpecResolverService (for spec)
- WorkflowService (for workflow)
- WebClient (for API calls) - TODO
- CacheService (for test caching) - TODO

**Status**: ✅ Implemented with mock execution

**Current Behavior**:
- Stores runs in in-memory ConcurrentHashMap
- Immediately completes with mock "PASSED" results
- Returns proper RunResponse structure

**TODO**:
- Implement real async execution (@Async or ExecutorService)
- Generate contract tests from OpenAPI spec
- Execute tests against target API (WebClient)
- Execute workflow steps sequentially
- Capture evidence (request/response)
- Handle cleanup (even on failure)
- Store results in database or filesystem

---

### ✅ 5. UploadService

**Responsibility**: File upload handling (specs and workflows)

**Methods**:
- `uploadFile(file, purpose)` - Handle multipart upload
- `validateFile(file, purpose)` - Validate file size/type
- `generateUploadId()` - Generate unique upload ID
- `computeSha256(file)` - Compute file hash
- `getUploadContent(uploadId)` - Retrieve uploaded file content

**Dependencies**: None (filesystem or storage service)

**Status**: ✅ Implemented with validation and SHA256

**Current Behavior**:
- Validates file size (max 10MB)
- Validates content type (yaml/json)
- Computes SHA256 hash
- Generates upload ID
- Returns UploadResponse

**TODO**:
- Store files to filesystem (`~/.triage/smoketests/uploads/{uploadId}`)
- Implement TTL cleanup for expired uploads
- Optionally store in Azure Blob Storage

---

### ⚠️ 6. CacheService (TODO)

**Responsibility**: Cache generated test sets

**Planned Methods**:
```java
void cacheGeneratedTests(String testSetId, List<TestResult> tests)
Optional<List<TestResult>> getCachedTests(String testSetId)
CacheStatus getCacheStatus(String specFingerprint)
void evictStaleCache()
```

**Storage Location**:
```
~/.triage/smoketests/cache/
  └── {capability}/
      └── {spec-fingerprint}/
          ├── metadata.json
          └── tests.json
```

---

### ⚠️ 7. ExportService (TODO)

**Responsibility**: Create export diagnostic bundles

**Planned Methods**:
```java
Map<String, Object> createExportBundle(RunResponse run)
List<String> generateRecommendations(RunResponse run)
```

**Export Bundle Structure**:
```json
{
  "type": "smoke-test-diagnostics",
  "runId": "run_xxx",
  "generatedAt": "2026-01-16T...",
  "target": {...},
  "summary": {...},
  "results": [...],
  "recommendations": [...]
}
```

---

## Controller Refactoring Results

### Before (Messy)
```java
@GetMapping("/workflows")
public ResponseEntity<WorkflowCatalogResponse> getWorkflowCatalog(...) {
    // 30+ lines of business logic in controller
    List<WorkflowDefinition> workflows = List.of(...);
    WorkflowCatalogResponse response = WorkflowCatalogResponse.builder()...
    return ResponseEntity.ok(response);
}
```

### After (Clean)
```java
@GetMapping("/workflows")
public ResponseEntity<WorkflowCatalogResponse> getWorkflowCatalog(
        @RequestParam String capability) {
    WorkflowCatalogResponse response = workflowService.getWorkflowCatalog(capability);
    return ResponseEntity.ok(response);
}
```

**Result**: Controller is now 150 lines (down from 396 lines)

---

## Service Interaction Flow

### Example: Validate Configuration

```
Frontend
   ↓
Controller.validateConfig(request)
   ↓
ValidationService.validateConfig(request)
   ├─→ SpecResolverService.resolveSpec()
   ├─→ WorkflowService.validateWorkflow()
   └─→ Returns ValidationResponse
   ↓
Controller returns ResponseEntity
   ↓
Frontend
```

### Example: Run Smoke Tests

```
Frontend
   ↓
Controller.startRun(request)
   ↓
ExecutionService.startRun(request)
   ├─→ SpecResolverService.resolveSpec()
   ├─→ WorkflowService.getWorkflowById()
   ├─→ CacheService.getCachedTests() [TODO]
   ├─→ Execute tests async
   └─→ Returns runId
   ↓
Controller returns 202 Accepted
   ↓
Frontend polls getRunStatus(runId)
```

---

## Testing Services

### Unit Testing Pattern

```java
@ExtendWith(MockitoExtension.class)
class ValidationServiceTest {
    
    @Mock
    private SpecResolverService specResolverService;
    
    @Mock
    private WorkflowService workflowService;
    
    @InjectMocks
    private ValidationService validationService;
    
    @Test
    void shouldValidateConfig() {
        // Given
        ValidateConfigRequest request = ...;
        when(specResolverService.resolveSpec(...)).thenReturn(...);
        
        // When
        ValidationResponse response = validationService.validateConfig(request);
        
        // Then
        assertThat(response.isOk()).isTrue();
    }
}
```

### Integration Testing

```bash
# Start backend
cd apps/smoketests
mvn spring-boot:run

# Test validation endpoint
curl -X POST http://localhost:8081/api/smoke/validate \
  -H "Content-Type: application/json" \
  -d @test-config.json
```

---

## Benefits of Service Layer

✅ **Separation of Concerns**
- Controller: HTTP routing only
- Service: Business logic
- DAO: Data access

✅ **Testability**
- Services can be unit tested in isolation
- Mock dependencies easily
- Controller becomes trivial to test

✅ **Reusability**
- Services can be used by multiple controllers
- Services can call other services
- Logic not tied to HTTP layer

✅ **Maintainability**
- Changes to business logic isolated to services
- Controller changes minimal (routing only)
- Clear boundaries between layers

---

## What's Implemented vs TODO

### ✅ Implemented (5 services)
1. **SpecResolverService** - Spec resolution and fingerprinting
2. **WorkflowService** - Workflow catalog management
3. **ValidationService** - Preflight validation
4. **ExecutionService** - Test execution orchestration
5. **UploadService** - File upload handling

### ⚠️ TODO (2 services)
6. **CacheService** - Test result caching
7. **ExportService** - Export bundle generation

### ✅ Controller
- **SmokeTestsController** - Now thin and clean (150 lines, down from 396)
- All business logic delegated to services
- Proper dependency injection
- Exception handling centralized

---

## Next Steps

1. ✅ Service layer implemented and controller refactored
2. ⚠️ Implement CacheService for test caching
3. ⚠️ Implement ExportService for diagnostics export
4. ⚠️ Add Azure Blob Storage integration to SpecResolverService
5. ⚠️ Add real async execution to ExecutionService (@Async)
6. ⚠️ Add database persistence for run history
7. ⚠️ Implement actual spec parsing with Swagger Parser
8. ⚠️ Implement workflow YAML parsing with SnakeYAML

---

**Clean architecture = maintainable code!** ✓
