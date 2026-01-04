# Environment Comparison - Quick Reference Card

## 🚀 Quick Start (30 seconds)

1. **Connect to both environments** (DEV and PROD)
2. **Scroll to Environment Comparison panel**
3. **Enter target connection ID**
4. **Click "Compare Environments"**
5. **Review results**

## 📍 API Endpoint

```
POST /api/db/environments/compare
```

## 📊 Comparison Modes

| Mode | Icon | Meaning |
|------|------|---------|
| **FULL** | ✅ | Complete metadata access in both environments |
| **PARTIAL** | ⚠️ | Limited metadata access in target environment |
| **BLOCKED** | ❌ | Insufficient metadata access to proceed |

## 🔍 Drift Status

| Status | Icon | Meaning |
|--------|------|---------|
| **MATCH** | ✅ | Values match between environments |
| **DIFFER** | ❌ | Values differ between environments |
| **UNKNOWN** | 🔒 | Cannot determine due to privilege limitations |

## 🎯 Drift Severity

| Severity | Color | Examples |
|----------|-------|----------|
| **ERROR** | 🔴 Red | Missing tables, type mismatches, nullability differences |
| **WARN** | 🟠 Orange | Missing indexes, FK differences, default value changes |
| **INFO** | 🔵 Blue | Informational differences |

## 📋 Drift Levels

| Level | Category | Severity | Examples |
|-------|----------|----------|----------|
| **1** | Structural | ERROR | Missing tables/columns, type mismatches |
| **2** | Constraints | ERROR/WARN | Missing PK/FK, constraint differences |
| **3** | Indexes | WARN | Missing indexes, definition differences |
| **4** | Ownership | WARN | Owner/grant differences |
| **5** | Flyway | ERROR/WARN | Version mismatches, failed migrations |

## 🔑 Required Access (Read-Only)

### Minimum (Basic Comparison)
- ✅ `information_schema.tables`

### Full Comparison
- ✅ `information_schema.columns`
- ✅ `information_schema.table_constraints`
- ✅ `pg_catalog.pg_indexes`
- ✅ `information_schema.table_privileges`
- ✅ `flyway_schema_history` (if using Flyway)

## 📦 Response Structure

```json
{
  "comparisonMode": "FULL|PARTIAL|BLOCKED",
  "modeBanner": "Status message",
  "sourceCapabilities": { /* 8 capability checks */ },
  "targetCapabilities": { /* 8 capability checks */ },
  "driftSections": [
    {
      "sectionName": "Tables|Columns|Constraints|Indexes",
      "availability": { /* available, partial, unavailable */ },
      "driftItems": [ /* individual differences */ ],
      "matchCount": 0,
      "differCount": 0,
      "unknownCount": 0
    }
  ],
  "flywayComparison": { /* version comparison */ },
  "conclusions": [ /* human-readable insights */ ]
}
```

## 🎨 UI Components

| Component | Purpose |
|-----------|---------|
| **Configuration Form** | Set up comparison parameters |
| **Comparison Mode Banner** | Show access level (FULL/PARTIAL/BLOCKED) |
| **Capability Matrices** | Show what's accessible in each environment |
| **Flyway Comparison** | Show migration version alignment |
| **Diagnostic Conclusions** | Show human-readable insights |
| **Drift Sections** | Show detailed drift by category |
| **Access Requirements** | Show what access is needed for full comparison |

## 🔧 Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| "Connection not found" | Invalid/expired connection ID | Reconnect and use fresh ID |
| "Comparison failed" | Network/database issue | Check connectivity and retry |
| All sections "Unavailable" | Insufficient privileges | Request read-only metadata access |
| Takes too long | Too many tables | Use "Specific Tables" option |
| Shows "Unknown" | Partial access | Normal for restricted PROD; use available info |

## 📈 Performance

| Operation | Expected Time |
|-----------|---------------|
| Capability Checks | 1-2 seconds |
| Full Comparison (50 tables) | 5-15 seconds |
| Partial Comparison | 3-10 seconds |
| Flyway-Only | 1-3 seconds |

## 🎯 Common Scenarios

### ✅ No Drift
```
All sections: [N Match] [0 Differ] [0 Unknown]
Conclusion: "No schema drift detected"
Action: Look for other causes
```

### ⚠️ Flyway Mismatch
```
Flyway: 1.5.0 vs 1.4.0
Conclusion: "Flyway version mismatch"
Action: Apply missing migrations
```

### ❌ Structural Drift
```
Tables: [10 Match] [2 Differ] [0 Unknown]
Conclusion: "Schema drift detected: 2 critical differences"
Action: Review and apply migrations
```

### 🔒 Partial Access
```
Tables: Available ✅
Columns: Unavailable ❌
Conclusion: "Partial comparison due to limited access"
Action: Use available info or request more access
```

## 📚 Documentation

| Document | Purpose |
|----------|---------|
| `ENVIRONMENT_COMPARISON_QUICKSTART.md` | 5-minute getting started guide |
| `ENVIRONMENT_COMPARISON_README.md` | Complete feature documentation |
| `ENVIRONMENT_COMPARISON_VISUAL_GUIDE.md` | Visual interface guide |
| `ENVIRONMENT_COMPARISON_IMPLEMENTATION_SUMMARY.md` | Technical implementation details |
| `ENVIRONMENT_COMPARISON_QUICK_REFERENCE.md` | This document |

## 🔐 Security

- ✅ All operations are **read-only**
- ✅ No SQL execution
- ✅ No data modification
- ✅ No DDL generation
- ✅ Uses existing connection security

## 🚫 What It Does NOT Do

- ❌ Execute SQL
- ❌ Compare data content
- ❌ Run EXPLAIN queries
- ❌ Generate DDL
- ❌ Auto-fix drift
- ❌ Modify any data

## ✅ What It DOES Do

- ✅ Compare table structure
- ✅ Compare column definitions
- ✅ Compare constraints
- ✅ Compare indexes
- ✅ Compare Flyway versions
- ✅ Generate diagnostics
- ✅ Handle partial access gracefully

## 📞 Support

1. Check diagnostic conclusions for guidance
2. Review capability matrices for access limitations
3. Consult full documentation
4. Contact DBA for privilege issues

## 💡 Pro Tips

1. **Use descriptive environment names** (DEV, STAGING, PROD)
2. **Start with specific tables** for faster results
3. **Check Flyway first** - often the root cause
4. **Request read-only access** for full comparison
5. **Save results** for documentation
6. **Run regular comparisons** to catch drift early

## 🎓 Mental Model

> DB Doctor does not promise answers.  
> It promises clarity.

Even "I cannot know because PROD blocks this" is a valid, useful diagnostic.

---

**Quick Access:**
- Backend: `EnvironmentComparisonController.java`
- Frontend: `EnvironmentComparisonPanel.jsx`
- API: `POST /api/db/environments/compare`
- Docs: `ENVIRONMENT_COMPARISON_*.md`

**Status:** ✅ Production Ready

