# Environment Comparison Quick Start Guide

## What is Environment Comparison?

Environment Comparison helps you detect schema drift between two database environments (e.g., DEV vs PROD). It answers the question: **"Why does this work in DEV but fail in PROD?"**

## Quick Start (5 minutes)

### Step 1: Connect to Source Environment (DEV)

1. Open the DB Doctor application
2. Fill in the connection form with your DEV database credentials:
   - Host: `your-dev-host.postgres.database.azure.com`
   - Port: `5432`
   - Database: `your_database`
   - Username: `your_user`
   - Password: `your_password`
   - Schema: `public`
3. Click **Connect**
4. **Note your connection ID** (e.g., `pt-abc123-def456`)

### Step 2: Open Environment Comparison Panel

Scroll down to the **Environment Comparison (Schema Drift)** section in the main interface.

### Step 3: Configure Comparison

Fill in the comparison form:
- **Source Environment Name:** `DEV` (or any descriptive name)
- **Target Environment Name:** `PROD` (or any descriptive name)
- **Target Connection ID:** Enter the connection ID for your PROD database
  - If you don't have it, open a new tab and connect to PROD, then copy the connection ID
- **Schema:** `public` (or your schema name)
- **Specific Tables:** Leave empty to compare all tables, or enter comma-separated table names

### Step 4: Run Comparison

Click **Compare Environments** and wait for the analysis to complete (typically 5-30 seconds).

### Step 5: Review Results

The comparison report shows:

#### 1. Comparison Mode Banner
- ✅ **Full Comparison:** Complete access to both environments
- ⚠️ **Partial Comparison:** Limited access to target environment
- ❌ **Blocked Comparison:** Insufficient access to proceed

#### 2. Capability Matrices
Shows what metadata is accessible in each environment:
- Connect ✅/❌
- Identity ✅/❌
- Tables ✅/❌
- Columns ✅/❌
- Constraints ✅/❌
- Indexes ✅/❌
- Flyway History ✅/❌
- Grants ✅/❌

#### 3. Flyway Comparison
If Flyway is in use:
- Source version vs Target version
- Failed migration counts
- Version match status

#### 4. Diagnostic Conclusions
Human-readable insights like:
- "Schema drift detected: 3 critical differences found"
- "Index mismatch likely explains PROD-only performance regression"
- "Flyway version mismatch indicates migrations not consistently applied"

#### 5. Drift Analysis Sections

Each section shows:
- **Match count:** Objects that are identical
- **Differ count:** Objects that differ
- **Unknown count:** Objects that couldn't be compared

##### Tables Section
- Missing tables (ERROR)
- Extra tables (WARN)

##### Columns Section
- Missing columns (ERROR)
- Type mismatches (ERROR)
- Nullability mismatches (ERROR)
- Default value differences (WARN)

##### Constraints Section
- Missing primary keys (ERROR)
- Missing unique constraints (ERROR)
- Missing foreign keys (WARN)
- Constraint type mismatches (ERROR)

##### Indexes Section
- Missing indexes (WARN)
- Index definition differences (WARN)

## Common Scenarios

### Scenario 1: Full Access to Both Environments

**Result:** Complete drift detection across all sections

**Action:** Review all differences and apply necessary migrations to align environments

### Scenario 2: Limited Access to PROD

**Result:** Partial comparison with some sections unavailable

**What you'll see:**
- Available sections show complete drift
- Unavailable sections show locked cards with explanations
- Diagnostic conclusions note the limitations

**Action:** 
- Use available information to identify known drift
- Request additional read-only access for complete analysis
- Or accept partial comparison as sufficient

### Scenario 3: Only Flyway Access to PROD

**Result:** Flyway-only comparison

**What you'll see:**
- Capability matrix shows most access blocked
- Flyway comparison shows version mismatch
- Diagnostic conclusion: "Schema drift likely caused by incomplete Flyway migration"

**Action:** 
- Apply missing Flyway migrations to PROD
- Re-run comparison after migrations

### Scenario 4: No Schema Drift Detected

**Result:** All sections show matches

**What you'll see:**
- Green checkmarks across all sections
- Diagnostic conclusion: "No schema drift detected"
- Recommendation: "Continue monitoring for future changes"

**Action:** 
- Celebrate! Your environments are aligned
- Look for other causes of the issue (data, configuration, etc.)

## Understanding Drift Severity

### 🔴 ERROR (Red)
**Critical differences that may cause failures:**
- Missing tables or columns
- Type mismatches
- Nullability differences
- Missing primary keys or unique constraints

**Action Required:** Immediate attention needed

### ⚠️ WARN (Orange)
**Differences that may cause issues:**
- Missing indexes (performance impact)
- Missing foreign keys (data integrity)
- Default value differences
- Extra tables in target

**Action Required:** Review and address as needed

### ℹ️ INFO (Blue)
**Informational differences:**
- Metadata differences with no functional impact

**Action Required:** Awareness only

## Tips for Success

### 1. Start with DEV as Source
Always use your development environment as the source and production as the target. This makes drift easier to understand.

### 2. Use Descriptive Environment Names
Instead of "SOURCE" and "TARGET", use "DEV", "STAGING", "PROD", etc. for clarity.

### 3. Compare Specific Tables First
If you know which table is causing issues, compare just that table first for faster results.

### 4. Check Flyway First
If Flyway comparison shows a version mismatch, that's often the root cause of all drift.

### 5. Request Read-Only Access
For PROD environments, request read-only access to:
- `information_schema`
- `pg_catalog`
- `flyway_schema_history` (if using Flyway)

This enables full comparison without security concerns.

### 6. Save Comparison Results
Copy the comparison results for documentation:
- Include in incident reports
- Attach to JIRA tickets
- Reference in postmortems

### 7. Run Regular Comparisons
Don't wait for incidents. Run comparisons regularly to catch drift early.

## Troubleshooting

### "Connection not found" Error
- **Cause:** Connection ID is invalid or expired
- **Solution:** Reconnect to both environments and use fresh connection IDs

### "Comparison failed" Error
- **Cause:** Network issues or database unavailable
- **Solution:** Check database connectivity and retry

### All Sections Show "Unavailable"
- **Cause:** Insufficient database privileges
- **Solution:** Request read-only access to metadata tables

### Comparison Takes Too Long
- **Cause:** Large number of tables
- **Solution:** Use "Specific Tables" to compare only relevant tables

### Results Show "Unknown" Status
- **Cause:** Partial access to one environment
- **Solution:** This is normal for restricted PROD access. Use available information or request additional access.

## Next Steps

After identifying drift:

1. **Document the differences** - Save the comparison report
2. **Identify root cause** - Check Flyway history, recent deployments, manual changes
3. **Create migration plan** - Determine which migrations need to be applied
4. **Test in staging** - Apply migrations to staging first
5. **Deploy to production** - Apply migrations during maintenance window
6. **Verify alignment** - Run comparison again to confirm drift is resolved

## Access Requirements Reminder

For full comparison, you need **read-only** access to:
- ✅ `information_schema.tables`
- ✅ `information_schema.columns`
- ✅ `information_schema.table_constraints`
- ✅ `pg_catalog.pg_indexes`
- ✅ `information_schema.table_privileges`
- ✅ `flyway_schema_history` (optional, if using Flyway)

**These are all read-only operations - no data is modified.**

## Support

If you encounter issues:
1. Check the diagnostic conclusions for guidance
2. Review the capability matrices to understand access limitations
3. Consult the full documentation: `ENVIRONMENT_COMPARISON_README.md`
4. Contact your DBA for privilege-related issues

---

**Remember:** Even partial comparison is valuable. The system is designed to provide useful diagnostics even with limited access.

