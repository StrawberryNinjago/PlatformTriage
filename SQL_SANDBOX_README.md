# SQL Diagnostic Sandbox - Feature Documentation

## Overview

The **SQL Diagnostic Sandbox** is a powerful static analysis tool that helps developers identify potential issues in SQL statements **before** they are executed. This feature analyzes INSERT, UPDATE, DELETE, and SELECT statements to provide actionable insights about:

- Missing or suboptimal indexes
- Constraint violation risks
- Dangerous cascade delete operations
- Performance hot-spots

## Key Features

### 🔒 Safety First
- **Static Analysis Only**: SQL is NEVER executed
- **Parse-Only Mode**: Validates and analyzes without touching the database
- **Single Statement**: Only one SQL statement allowed at a time
- **Max Length**: 50,000 characters limit

### 🔍 What Gets Analyzed

#### 1. **Index Coverage Analysis** (High Value)
Detects missing or suboptimal indexes that could impact query performance.

**Example:**
```sql
SELECT * FROM cart_item
WHERE cart_id = ?
  AND los_id = ?
  AND product_code = ?
```

**DB Doctor Analysis:**
- ✅ Composite index exists covering all WHERE columns
- ⚠️ Only single-column indexes exist → potential inefficiency
- ❌ No index found → significant performance risk

**Why This Matters:**
"Why is this query slow in prod but fine in dev?" - This analysis helps answer that question by identifying index gaps before deployment.

---

#### 2. **Constraint Violation Detection** (Critical Value)
For INSERT/UPDATE operations, identifies potential constraint violations.

**Example:**
```sql
INSERT INTO cart_item (cart_id, los_id, product_code)
VALUES (?, ?, ?)
```

**DB Doctor Warnings:**
- ❌ Missing NOT NULL columns: `quantity`, `unit_price_cents`
- ⚠️ UNIQUE constraint columns: `cart_id`, `product_code`
- ⚠️ Foreign key columns: `cart_id` (must reference valid cart)

**Why This Matters:**
Catches the "it worked in dev but failed in prod" bugs caused by missing required fields or constraint violations.

---

#### 3. **Dangerous DELETE Detection** (Safety Critical)
Analyzes DELETE statements for cascading impacts.

**Example:**
```sql
DELETE FROM cart WHERE cart_id = ?
```

**DB Doctor Analysis:**
- ⚠️ 3 cascading foreign keys detected
- 🔥 Recursive delete via `cart_item.parent_item_id`
- 📊 Estimated cascade depth: HIGH
- 📋 Affected tables:
  - `cart_item` (CASCADE)
  - `cart_history` (SET NULL)
  - `cart_audit` (CASCADE)

**Why This Matters:**
Prevents accidental data loss by making cascade impacts visible before execution.

---

#### 4. **Update Hot-Spot Detection**
Warns about updates that may affect many rows or trigger extensive constraint checks.

**Example:**
```sql
UPDATE cart_item SET quantity = ?
WHERE cart_id = ?
```

**DB Doctor Warnings:**
- ⚠️ Updating rows under a single cart
- ⚠️ Triggers FK checks + unique constraints
- ℹ️ Consider more specific WHERE clause

---

## Architecture

### Backend Components

#### DTOs and Enums
- `SqlOperationType` - Enum for SQL operation types (SELECT, INSERT, UPDATE, DELETE)
- `SqlAnalysisRequest` - Request payload with SQL, operation type, and connection ID
- `SqlAnalysisResponse` - Complete analysis results
- `SqlAnalysisFinding` - Individual finding with severity, category, and recommendation
- `IndexMatchResult` - Index coverage analysis results
- `ConstraintViolationRisk` - Constraint validation results
- `CascadeAnalysisResult` - Cascade delete impact analysis

#### Services

**SqlParserService**
- Parses SQL statements to extract table names, columns, WHERE clauses
- Detects operation type (SELECT, INSERT, UPDATE, DELETE)
- Validates SQL structure
- Guards against multiple statements

**SqlIndexAnalysisService**
- Analyzes index coverage for WHERE clause columns
- Detects composite indexes, partial coverage, prefix matches
- Generates index recommendations

**SqlConstraintAnalysisService**
- Identifies missing NOT NULL columns in INSERT statements
- Detects unique constraint columns being inserted/updated
- Checks foreign key column references

**SqlCascadeAnalysisService**
- Maps cascading foreign key relationships
- Detects recursive cascade patterns
- Calculates cascade depth and impact

**SqlAnalysisService** (Orchestrator)
- Coordinates all analysis services
- Generates comprehensive findings
- Assigns severity levels (INFO, WARN, ERROR)

#### Controller & Handler
- `SqlAnalysisController` - REST endpoint at `/api/sql/analyze`
- `SqlAnalysisHandler` - Business logic coordination
- `SqlAnalysisException` - Custom exception handling

### Frontend Components

#### SqlSandboxPanel
- Collapsible panel with expand/collapse functionality
- Large SQL text area with monospace font
- Operation type dropdown (auto-detect or manual override)
- "Analyze SQL" button with loading state
- Clear button to reset form

#### Results Display
- **Findings Section**: Categorized list of issues with severity badges
- **Index Analysis Section**: Shows matched/missing indexes with suggestions
- **Constraint Risks Section**: Highlights potential constraint violations
- **Cascade Analysis Section**: Visualizes delete cascade impacts

#### API Integration
- New `analyzeSql()` method in `apiService.js`
- Console message integration for user feedback
- Error handling and display

---

## API Specification

### Endpoint
```
POST /api/sql/analyze
```

### Request
```json
{
  "connectionId": "conn-123",
  "sql": "SELECT * FROM cart_item WHERE cart_id = ? AND los_id = ?",
  "operationType": "SELECT"  // Optional - auto-detected if null
}
```

### Response
```json
{
  "detectedOperation": "SELECT",
  "analyzedSql": "SELECT * FROM...",
  "isValid": true,
  "parseError": null,
  "findings": [
    {
      "severity": "WARN",
      "category": "Index Coverage",
      "title": "Partial Index Coverage",
      "description": "Only partial index coverage found...",
      "recommendation": "Consider creating composite index: CREATE INDEX..."
    }
  ],
  "indexAnalysis": {
    "tableName": "cart_item",
    "queryColumns": ["cart_id", "los_id"],
    "hasCompositeIndex": false,
    "hasPartialCoverage": true,
    "matchedIndexes": ["idx_cart_item_cart_id (prefix match)"],
    "suggestedIndexes": ["CREATE INDEX idx_cart_item_composite ON cart_item (cart_id, los_id);"]
  },
  "constraintRisks": null,
  "cascadeAnalysis": null
}
```

---

## Security Guardrails

### Input Validation
- ✅ Max SQL length: 50,000 characters
- ✅ Single statement only (no semicolon chaining)
- ✅ No execution - parse-only mode
- ✅ Connection ID required (existing connection validation)

### Clear User Communication
- 🔒 Prominent "This analysis is static. SQL is NEVER executed." banner
- 🛡️ Non-executable by design - no prepared statement execution
- 📋 All analysis done via metadata queries only

---

## Usage Guide

### Step 1: Connect to Database
Use the connection form to establish a database connection.

### Step 2: Expand SQL Sandbox
Click on "🧪 SQL Diagnostic Sandbox (Static Analysis)" to expand the panel.

### Step 3: Paste SQL
Paste your SQL statement into the text area. The statement can include parameter placeholders (`?`).

### Step 4: (Optional) Override Operation Type
If auto-detection is incorrect, manually select the operation type from the dropdown.

### Step 5: Analyze
Click "Analyze SQL" to run the static analysis.

### Step 6: Review Results
Review the findings, index analysis, constraint risks, and cascade impacts.

---

## Example Use Cases

### Use Case 1: Pre-Deployment Query Review
**Scenario:** Developer writes a new query for a hot-path API endpoint.

**Action:** Paste query into SQL Sandbox before deploying.

**Result:** Discovers missing composite index. Adds index before deployment. Avoids production performance issue.

---

### Use Case 2: Debugging Slow Queries
**Scenario:** Query is slow in production but fast in dev.

**Action:** Analyze the query in SQL Sandbox.

**Result:** Identifies that dev has a composite index but production only has single-column indexes. DevOps adds missing index.

---

### Use Case 3: Preventing Data Loss
**Scenario:** Developer needs to delete a cart record.

**Action:** Tests DELETE statement in SQL Sandbox.

**Result:** Discovers recursive cascade will delete all cart items, history, and audit records. Developer adjusts approach to soft-delete instead.

---

### Use Case 4: Constraint Violation Prevention
**Scenario:** New INSERT statement for cart_item.

**Action:** Analyzes INSERT in SQL Sandbox.

**Result:** Identifies missing NOT NULL columns (`quantity`, `unit_price_cents`). Developer adds them before deployment. Avoids runtime errors.

---

## Implementation Highlights

### What Makes This Feature Special

1. **Zero-Execution Safety**: Never touches production data
2. **Tribal Knowledge Codification**: Encodes best practices into automated checks
3. **Bridge FE ↔ BE ↔ DB**: Helps frontend developers understand database constraints
4. **Proactive vs Reactive**: Catches issues before deployment, not after

### ROI Metrics
- **Time Saved**: Prevents "it worked in dev" debugging cycles
- **Risk Reduction**: Avoids constraint violations and cascading deletes
- **Knowledge Transfer**: Junior developers learn database patterns
- **Production Stability**: Fewer post-deploy hotfixes

---

## Future Enhancements (Out of Scope for MVP)

- [ ] Multi-statement transaction analysis
- [ ] Query plan visualization
- [ ] Performance cost estimation
- [ ] Historical query pattern matching
- [ ] Integration with migration tools (Flyway)
- [ ] Save and share analyzed queries
- [ ] Batch query analysis

---

## Files Created/Modified

### Backend (Java)
- **New Enums:**
  - `SqlOperationType.java`

- **New DTOs:**
  - `SqlAnalysisRequest.java`
  - `SqlAnalysisResponse.java`
  - `SqlAnalysisFinding.java`
  - `IndexMatchResult.java`
  - `ConstraintViolationRisk.java`
  - `CascadeAnalysisResult.java`

- **New Services:**
  - `SqlParserService.java`
  - `SqlIndexAnalysisService.java`
  - `SqlConstraintAnalysisService.java`
  - `SqlCascadeAnalysisService.java`
  - `SqlAnalysisService.java`

- **New Controller/Handler:**
  - `SqlAnalysisController.java`
  - `SqlAnalysisHandler.java`

- **New Exception:**
  - `SqlAnalysisException.java`

### Frontend (React)
- **New Component:**
  - `SqlSandboxPanel.jsx`

- **Modified Files:**
  - `apiService.js` - Added `analyzeSql()` method
  - `App.jsx` - Integrated SqlSandboxPanel and `handleAnalyzeSql()`

---

## Testing Recommendations

### Backend Unit Tests
```java
@Test
void testSelectWithWhereClause() {
    String sql = "SELECT * FROM cart_item WHERE cart_id = ? AND los_id = ?";
    ParsedSql result = sqlParserService.parseSql(sql);
    
    assertEquals(SqlOperationType.SELECT, result.getOperationType());
    assertEquals("cart_item", result.getTableName());
    assertEquals(Arrays.asList("cart_id", "los_id"), result.getWhereColumns());
}
```

### Integration Tests
- Test with real database connection
- Verify index analysis accuracy
- Validate constraint detection
- Test cascade analysis with sample schema

### Frontend Tests
- Test component rendering
- Test API call handling
- Test error display
- Test result visualization

---

## Conclusion

The SQL Diagnostic Sandbox brings proactive database analysis to DB Doctor, enabling developers to catch issues before they reach production. By providing static analysis of SQL statements, it reduces risk, saves debugging time, and codifies database best practices into automated tooling.

This feature aligns perfectly with DB Doctor's philosophy of being diagnosis-first, preventing bugs before runtime, and bridging the gap between frontend, backend, and database layers.

