# DB Doctor – Environment Comparison (Schema Drift Detection)

## Overview

The Environment Comparison feature helps answer the critical question: **"Why does this work in DEV but fail (or behave differently) in PROD?"**

This feature compares database schemas between two environments (e.g., DEV and PROD) to detect drift, even when PROD access is partial or restricted.

## Core Principles

1. **Partial comparison is still valuable** - The system explicitly supports:
   - ✅ **Full comparison** – ideal scenario with complete metadata access
   - ⚠️ **Partial comparison** – most common scenario with limited access
   - ❌ **Blocked comparison** – insufficient access, but still explainable

2. **Never fail hard** - Privilege limitations are treated as diagnostic outcomes, not errors

3. **Read-only and safe** - No SQL execution, no data diff, no DDL generation

4. **Clarity over promises** - Even "I cannot know because PROD blocks this" is a valid, useful diagnostic

## Architecture

### Backend Components

#### DTOs and Models
- `CapabilityStatus` - Status of a specific capability check
- `EnvironmentCapabilityMatrix` - Complete capability matrix for an environment
- `ComparisonMode` - FULL, PARTIAL, or BLOCKED comparison mode
- `DriftStatus` - Three-valued truth: MATCH, DIFFER, or UNKNOWN
- `DriftSeverity` - ERROR, WARN, or INFO
- `DriftItem` - Single drift item with category, object, attribute, and values
- `DriftSection` - Section of drift results (Tables, Columns, Constraints, Indexes)
- `SectionAvailability` - Availability status of a comparison section
- `FlywayComparisonDto` - Flyway comparison between environments
- `DiagnosticConclusion` - Human-readable diagnostic conclusion
- `EnvironmentComparisonResponse` - Complete comparison response

#### Services
- `EnvironmentCapabilityService` - Checks what capabilities are available for a connection
  - Tests: connect, identity, tables, columns, constraints, indexes, flyway history, grants
  - Returns capability matrix showing what's accessible

- `SchemaDriftService` - Detects schema drift between environments
  - Compares: tables, columns, constraints, indexes
  - Handles unavailable sections gracefully
  - Uses three-valued logic (MATCH, DIFFER, UNKNOWN)

#### Handler
- `EnvironmentComparisonHandler` - Orchestrates the comparison process
  1. Retrieves connection contexts
  2. Builds capability matrices for both environments
  3. Determines comparison mode (FULL, PARTIAL, BLOCKED)
  4. Runs drift detection section by section
  5. Compares Flyway history
  6. Generates diagnostic conclusions

#### Controller
- `EnvironmentComparisonController` - REST API endpoint
  - `POST /api/db/environments/compare` - Compare two environments

### Frontend Components

#### EnvironmentComparisonPanel
A comprehensive React component that provides:
- Configuration form for source/target environments
- Real-time comparison execution
- Capability matrix visualization
- Comparison mode banner
- Drift analysis by section (Tables, Columns, Constraints, Indexes)
- Flyway comparison
- Diagnostic conclusions
- Access requirements panel

## Usage

### API Request

```json
POST /api/db/environments/compare
{
  "sourceConnectionId": "pt-xxx-source",
  "targetConnectionId": "pt-xxx-target",
  "sourceEnvironmentName": "DEV",
  "targetEnvironmentName": "PROD",
  "schema": "public",
  "specificTables": ["users", "orders"]  // optional
}
```

### API Response

```json
{
  "sourceEnvironment": "DEV",
  "targetEnvironment": "PROD",
  "schema": "public",
  "comparisonMode": "PARTIAL",
  "modeBanner": "⚠️ Partial Comparison: PROD metadata access is limited...",
  "sourceCapabilities": {
    "environmentName": "DEV",
    "connectionId": "pt-xxx-source",
    "connect": { "available": true, "message": "Available", "diagnosticCode": null },
    "identity": { "available": true, "message": "Available", "diagnosticCode": null },
    "tables": { "available": true, "message": "Available", "diagnosticCode": null },
    "columns": { "available": true, "message": "Available", "diagnosticCode": null },
    "constraints": { "available": true, "message": "Available", "diagnosticCode": null },
    "indexes": { "available": true, "message": "Available", "diagnosticCode": null },
    "flywayHistory": { "available": true, "message": "Available", "diagnosticCode": null },
    "grants": { "available": true, "message": "Available", "diagnosticCode": null }
  },
  "targetCapabilities": { /* similar structure */ },
  "driftSections": [
    {
      "sectionName": "Tables",
      "description": "Table existence and structure",
      "availability": {
        "available": true,
        "partial": false,
        "unavailabilityReason": null,
        "neededPrivilege": null,
        "impact": null
      },
      "driftItems": [
        {
          "category": "TABLE",
          "objectName": "new_feature_table",
          "attribute": "exists",
          "sourceValue": true,
          "targetValue": false,
          "status": "DIFFER",
          "severity": "ERROR",
          "message": "Table 'new_feature_table' exists in source but missing in target"
        }
      ],
      "matchCount": 10,
      "differCount": 1,
      "unknownCount": 0
    }
  ],
  "flywayComparison": {
    "available": true,
    "sourceLatestVersion": "1.5.0",
    "targetLatestVersion": "1.4.0",
    "versionMatch": false,
    "sourceFailedCount": 0,
    "targetFailedCount": 0,
    "sourceInstalledBy": "dev_user",
    "targetInstalledBy": "deploy_user",
    "message": "Flyway version mismatch: 1.5.0 vs 1.4.0"
  },
  "conclusions": [
    {
      "severity": "ERROR",
      "summary": "Schema drift detected: 1 critical differences found",
      "impact": "These differences may cause INSERT failures, constraint violations, or runtime errors in production",
      "recommendation": "Review structural differences (tables, columns, constraints) and apply missing migrations"
    }
  ],
  "timestamp": "2026-01-03T10:30:00Z"
}
```

## Drift Detection Levels

### Level 1 — Structural Drift (ERROR)
Hard-failure causes:
- Missing tables
- Missing columns
- Column type mismatch
- NOT NULL mismatch
- Default value mismatch

### Level 2 — Constraint Drift (WARN / ERROR)
Runtime correctness:
- PK / UNIQUE mismatch (ERROR)
- FK missing or different (WARN)
- CASCADE vs NO ACTION mismatch (WARN)
- CHECK constraint differences (WARN)

### Level 3 — Index Drift (WARN)
Performance & locking:
- Missing indexes
- Column order mismatch
- Unique vs non-unique mismatch
- Partial index mismatch

### Level 4 — Ownership & Privileges (WARN)
Operational drift:
- Owner differs
- Grants differ
- Schema owner mismatch

### Level 5 — Flyway Alignment (ERROR / WARN)
Root cause attribution:
- Latest version mismatch (ERROR)
- Failed migrations (ERROR)
- installed_by mismatch (WARN)
- Checksum mismatch (WARN)

## Section-Level Degradation

Each section independently supports three states:

### Available
- Full metadata access
- Complete drift detection
- Shows all differences

### Partial
- Limited metadata access
- Some drift detectable
- Shows "unknown" markers for inaccessible data

### Unavailable
- No metadata access
- Shows locked card with:
  - Reason for unavailability
  - Needed privilege
  - Impact of limitation
  - Next steps

## Three-Valued Truth Model

Every drift item must be one of:
- ✅ **MATCH** (green) - Values match between environments
- ⚠️ **DIFFER** (amber/red) - Values differ between environments
- 🔒 **UNKNOWN** (gray + lock) - Cannot determine due to privilege limitations

**Important:** Never treat "unknown" as "no difference"

## Flyway-Only Fallback Mode

If PROD catalog access is blocked but Flyway history is readable:
- Show latest migration (DEV vs PROD)
- Show failed migration count
- Show installed_by summary
- Generate diagnostic conclusion

This alone is often enough to debug incidents.

## Diagnostic Conclusions

The system generates human-readable insights, not just diffs:

Examples:
- "Schema drift detected that may cause INSERT failures in PROD."
- "Index mismatch likely explains PROD-only performance regression."
- "Flyway alignment mismatch indicates migrations not consistently applied."

This is where DB Doctor behaves like a senior engineer.

## Privilege Limitation Handling

When metadata access fails:
- Show stable diagnostic code
- Explain impact
- Suggest next steps
- **No stack traces, no 500s**

Example:
```
🔒 Index Drift (Unavailable)
Reason: permission denied on pg_catalog
Impact: index drift unknown
Next: request read-only metadata role
```

## Access Requirements

For full comparison, the following read-only access is required:
- ✅ Read access to `information_schema`
- ✅ Read access to `pg_catalog` metadata
- ✅ Optional: Flyway history table if Flyway is in use

## Guardrails

### What DB Doctor Does NOT Do:
- ❌ No SQL execution
- ❌ No data diff
- ❌ No EXPLAIN / cost estimates
- ❌ No auto-fix / DDL generation

### What DB Doctor DOES Do:
- ✅ Read-only operations
- ✅ Deterministic results
- ✅ Explainable diagnostics
- ✅ Copy-friendly reports

## Future Enhancements

### Copy Drift Report (Coming Soon)
A single structured drift report suitable for:
- JIRA tickets
- Postmortem documentation
- Team communication
- Audit trails

## Testing

### Test Scenarios

1. **Full Access Scenario**
   - Both environments have complete metadata access
   - All sections available
   - Complete drift detection

2. **Partial Access Scenario**
   - PROD has limited metadata access
   - Some sections unavailable
   - Graceful degradation

3. **Blocked Access Scenario**
   - PROD has minimal access
   - Only Flyway comparison available
   - Clear explanation of limitations

4. **Flyway-Only Scenario**
   - Catalog access blocked
   - Flyway history readable
   - Version mismatch detected

## Mental Model

> DB Doctor does not promise answers.
> It promises clarity.

Even "I cannot know because PROD blocks this" is a valid, useful diagnostic.

## Implementation Notes

### High-ROI Implementation Order
1. ✅ Capability matrix pre-flight
2. ✅ Comparison mode banner
3. ✅ Section-level unavailable/partial handling
4. ✅ Flyway-only fallback
5. ✅ Derived conclusions
6. 🔜 Copy Drift Report

### Key Design Decisions

1. **Static factory methods** - Used `createAvailable()` instead of `available()` to avoid conflicts with record accessor methods
2. **DataSource factory** - Uses `build()` method for consistency with existing codebase
3. **Flyway integration** - Extracts `FlywaySummaryDto` from `DbFlywayHealthResponse`
4. **Three-valued logic** - Explicit handling of UNKNOWN state throughout
5. **Graceful degradation** - Each section independently handles unavailability

## Troubleshooting

### Common Issues

**Issue:** Comparison shows "BLOCKED"
- **Cause:** Target environment lacks basic metadata access
- **Solution:** Grant read access to `information_schema.tables`

**Issue:** Index section shows "Unavailable"
- **Cause:** No access to `pg_catalog.pg_indexes`
- **Solution:** Grant read access to `pg_catalog` or accept partial comparison

**Issue:** Flyway comparison unavailable
- **Cause:** `flyway_schema_history` table not accessible
- **Solution:** Grant read access to Flyway history table or skip Flyway comparison

## Support

For questions or issues:
1. Check capability matrices to understand access limitations
2. Review diagnostic conclusions for actionable recommendations
3. Consult access requirements panel for needed privileges
4. Contact your DBA for privilege escalation if needed

