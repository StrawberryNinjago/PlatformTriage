# SQL Diagnostic Sandbox - Visual Guide

## 🎨 UI Layout & User Experience

This guide shows you exactly what to expect when using the SQL Diagnostic Sandbox feature.

---

## 📍 Location in UI

The SQL Sandbox appears in the **right panel**, between the Summary Panel and Results Panel.

```
┌─────────────────────────────────────────────────────────────────┐
│                    DB Doctor - Top Bar                          │
│              Connection: connected ✓                            │
└─────────────────────────────────────────────────────────────────┘

┌────────────────────┬────────────────────────────────────────────┐
│   Left Panel       │         Right Panel                        │
│   (33%)            │         (67%)                              │
│                    │                                            │
│  ┌──────────────┐  │  ┌──────────────────────────────────┐      │
│  │ Connection   │  │  │  Summary Panel                   │      │
│  │ Form         │  │  │  (Database info, Flyway status)  │      │
│  └──────────────┘  │  └──────────────────────────────────┘      │
│                    │                                            │
│  ┌──────────────┐  │  ┌──────────────────────────────────┐      │
│  │ Action       │  │  │  🧪 SQL Diagnostic Sandbox       │  ◀── │
│  │ Buttons      │  │  │     (Collapsible Panel)          │      │
│  │              │  │  └──────────────────────────────────┘      │
│  │              │  │                                            │
│  └──────────────┘  │  ┌──────────────────────────────────┐      │
│                    │  │  Results Panel                   │      │
│                    │  │  (Action results, diagnostics)   │      │
│                    │  └──────────────────────────────────┘      │
└────────────────────┴────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    Console Panel                                │
│               (Full width at bottom)                            │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🎭 Panel States

### State 1: Collapsed (Default)
```
┌─────────────────────────────────────────────────────────┐
│ 🧪 SQL Diagnostic Sandbox (Static Analysis)        ▼    │
└─────────────────────────────────────────────────────────┘
```
- Takes minimal space
- Click to expand
- Blue highlight on hover

### State 2: Expanded (Ready to Use)
```
┌─────────────────────────────────────────────────────────┐
│ 🧪 SQL Diagnostic Sandbox (Static Analysis)        ▲    │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ ⚠️ This analysis is static. SQL is NEVER executed.      │
│ DB Doctor will analyze your SQL for index coverage,     │
│ constraint violations, cascade deletes, and more.       │
│                                                         │
│ ┌─────────────────────────────────────────────────┐     │
│ │ Paste INSERT / UPDATE / DELETE / SELECT SQL     │     │
│ │                                                 │     │
│ │ Example:                                        │     │
│ │ SELECT * FROM cart_item                         │     │
│ │ WHERE cart_id = ?                               │     │
│ │   AND los_id = ?                                │     │
│ │   AND product_code = ?                          │     │
│ └─────────────────────────────────────────────────┘     │
│                                                         │
│ ┌────────────────────────────────┐                      │
│ │ Operation Type: Auto-detect ▼  │  [Analyze SQL]       │
│ └────────────────────────────────┘  [Clear]             │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### State 3: Analyzing (Loading)
```
┌─────────────────────────────────────────────────────────┐
│ 🧪 SQL Diagnostic Sandbox (Static Analysis)        ▲    │
├─────────────────────────────────────────────────────────┤
│ [SQL text area with user's query]                       │
│                                                         │
│ [Operation Type dropdown]  [Analyzing...]  (disabled)   │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### State 4: Results Displayed
```
┌─────────────────────────────────────────────────────────┐
│ 🧪 SQL Diagnostic Sandbox (Static Analysis)        ▲    │
├─────────────────────────────────────────────────────────┤
│ [SQL text area]                                         │
│ [Operation Type]  [Analyze SQL]  [Clear]                │
│                                                         │
│ ─────────────────────────────────────────────────────   │
│                                                         │
│ Analysis Results                                        │
│ ┌─┐ SELECT  ┌─┐ ✓ Valid SQL                             │
│ └─┘         └─┘                                         │
│                                                         │
│ 🔍 Findings (2)                                         │
│ ├─ ⚠️  WARN  Index Coverage                             │
│ │   Partial Index Coverage                              │
│ │   Only partial index coverage found...                │
│ │   💡 Consider creating composite index: CREATE...     │
│ │                                                       │
│ └─ ℹ️  INFO  Query Structure                            │
│     Query structure is valid                            │
│     💡 No action needed                                 │
│                                                         │
│ 📊 Index Coverage Analysis                              │
│ Table: cart_item                                        │
│ Query Columns: cart_id, los_id                          │
│ Composite Index: ❌                                     │
│ Partial Coverage: ⚠️ Yes                                │
│                                                         │
│ Matched Indexes:                                        │
│ • idx_cart_item_cart_id (prefix match)                  │
│                                                         │
│ 💡 Suggested Indexes:                                   │
│ CREATE INDEX idx_cart_item_composite                    │
│   ON cart_item (cart_id, los_id);                       │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 🎨 Color Scheme

### Severity Colors
- **🔴 ERROR (Red)**: Critical issues that will cause failures
  - Missing NOT NULL columns
  - No WHERE clause in UPDATE/DELETE
  - High-impact cascade deletes

- **🟡 WARN (Yellow)**: Potential problems or performance issues
  - Partial index coverage
  - Unique constraint columns being updated
  - Medium-impact cascades

- **🔵 INFO (Blue)**: Informational messages
  - Optimal index coverage
  - Valid query structure
  - No issues found

### Operation Type Colors
- **SELECT**: Primary Blue
- **INSERT**: Success Green
- **UPDATE**: Warning Orange
- **DELETE**: Error Red

### Panel Colors
- **Findings Section**: Light gray (`#fafafa`)
- **Index Analysis**: Light blue (`#f5f5ff`)
- **Constraint Risks**: Light orange (`#fff9f0`)
- **Cascade Analysis**: Light red (`#ffebee`)

---

## 📝 Example Scenarios

### Scenario 1: SELECT with Missing Index

**User Input:**
```sql
SELECT * FROM cart_item
WHERE cart_id = 123 AND los_id = 456
```

**Display:**
```
Analysis Results
┌─────────────────────────────────────┐
│ SELECT  ✓ Valid SQL                 │
└─────────────────────────────────────┘

🔍 Findings (1)
┌─────────────────────────────────────────────────────┐
│ ⚠️ WARN  Index Coverage                             │
│ Partial Index Coverage                              │
│                                                     │
│ Only partial index coverage found. Query may be     │
│ slower than optimal.                                │
│                                                     │
│ 💡 Consider creating composite index:               │
│    CREATE INDEX idx_cart_item_composite             │
│    ON cart_item (cart_id, los_id);                  │
└─────────────────────────────────────────────────────┘

📊 Index Coverage Analysis
┌─────────────────────────────────────────────────────┐
│ Table: cart_item                                    │
│ Query Columns: cart_id, los_id                      │
│ Composite Index: ❌                                 │
│ Partial Coverage: ⚠️ Yes                            │
│                                                     │
│ Matched Indexes:                                    │
│ • idx_cart_item_cart_id (prefix match)              │
│                                                     │
│ 💡 Suggested Indexes:                               │
│ CREATE INDEX idx_cart_item_composite                │
│   ON cart_item (cart_id, los_id);                   │
└─────────────────────────────────────────────────────┘

Console:
🧪 Analyzing SQL...
✓ SQL analysis complete: 0 errors, 1 warnings
```

---

### Scenario 2: INSERT with Missing Columns

**User Input:**
```sql
INSERT INTO cart_item (cart_id, product_code)
VALUES (123, 'ABC')
```

**Display:**
```
Analysis Results
┌─────────────────────────────────────┐
│ INSERT  ✓ Valid SQL                 │
└─────────────────────────────────────┘

🔍 Findings (2)
┌─────────────────────────────────────────────────────┐
│ ❌ ERROR  Constraint Violation                      │
│ Missing NOT NULL Columns                            │
│                                                     │
│ Required columns not provided: quantity,            │
│ unit_price_cents                                    │
│                                                     │
│ 💡 Include all NOT NULL columns in INSERT           │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ ⚠️ WARN  Foreign Key                                │
│ Foreign Key Columns Present                         │
│                                                     │
│ Foreign key columns: cart_id                        │
│                                                     │
│ 💡 Ensure referenced records exist before INSERT    │
└─────────────────────────────────────────────────────┘

⚠️ Constraint Violation Risks
┌─────────────────────────────────────────────────────┐
│ ❌ Missing NOT NULL Columns:                        │
│    quantity, unit_price_cents                       │
│                                                     │
│ ⚠️ Foreign Key Columns:                             │
│    cart_id                                          │
└─────────────────────────────────────────────────────┘

Console:
🧪 Analyzing SQL...
✗ SQL analysis complete: 1 errors, 1 warnings
```

---

### Scenario 3: DELETE with Cascade Warning

**User Input:**
```sql
DELETE FROM cart WHERE cart_id = 123
```

**Display:**
```
Analysis Results
┌─────────────────────────────────────┐
│ DELETE  ✓ Valid SQL                 │
└─────────────────────────────────────┘

🔍 Findings (1)
┌─────────────────────────────────────────────────────┐
│ 🔥 ERROR  Cascade Delete                            │
│ Cascading Delete Detected                           │
│                                                     │
│ DELETE will cascade to 3 related table(s):          │
│ cart_item (CASCADE), cart_history (SET NULL),       │
│ cart_audit (CASCADE)                                │
│                                                     │
│ 💡 Review cascade impact before executing DELETE    │
└─────────────────────────────────────────────────────┘

🔥 Cascade Delete Analysis
┌─────────────────────────────────────────────────────┐
│ Cascading Foreign Keys: 3                           │
│ Cascade Depth: HIGH (3 tables)                      │
│ Recursive Cascade: No                               │
│                                                     │
│ Affected Tables:                                    │
│ • cart_item (CASCADE)                               │
│ • cart_history (SET NULL)                           │
│ • cart_audit (CASCADE)                              │
└─────────────────────────────────────────────────────┘

Console:
🧪 Analyzing SQL...
✗ SQL analysis complete: 1 errors, 0 warnings
```

---

### Scenario 4: Invalid SQL

**User Input:**
```sql
SELECT * FROM cart; DELETE FROM cart;
```

**Display:**
```
❌ Parse Error
Multiple statements detected. Only single statements 
are allowed.

Console:
🧪 Analyzing SQL...
✗ SQL analysis failed: Multiple statements detected
```

---

## 🎯 Interactive Elements

### Text Area
- **Font**: Monospace
- **Rows**: 8 lines
- **Placeholder**: Example SQL with line breaks
- **Background**: Light gray (#fafafa) when enabled
- **Disabled State**: Darker gray (#f5f5f5)

### Dropdown (Operation Type)
- **Default**: "Auto-detect"
- **Options**: SELECT, INSERT, UPDATE, DELETE
- **Width**: 250px
- **Size**: Small

### Buttons
- **Analyze SQL**:
  - Color: Primary blue
  - Text changes to "Analyzing..." when loading
  - Disabled when not connected or SQL is empty
  
- **Clear**:
  - Color: Gray outline
  - Resets form and clears results
  - Disabled when form is already empty

### Chips (Tags)
- **Operation Type**: Rounded, colored by operation
- **Severity**: Rounded, colored by severity
- **Category**: Outline style

---

## 📱 Responsive Behavior

### Desktop (> 1200px)
- Full layout as shown above
- SQL text area: 8 rows
- Results displayed in full width

### Tablet (768px - 1200px)
- Layout adjusts to narrower width
- Text remains readable
- Buttons stack if needed

### Mobile (< 768px)
- Single column layout
- SQL text area: 6 rows
- Findings list becomes more compact

---

## ⌨️ Keyboard Shortcuts

- **Enter** in SQL text area: Does NOT submit (allows multi-line)
- **Ctrl/Cmd + Enter**: Could trigger analysis (future enhancement)
- **Tab**: Moves between form elements
- **Escape**: Could collapse panel (future enhancement)

---

## 🎬 Animation & Transitions

- **Panel Expand/Collapse**: Smooth slide animation (0.3s)
- **Loading State**: Button text fade (0.2s)
- **Results Appear**: Fade in (0.3s)
- **Hover Effects**: Button highlight (0.2s)

---

## 🔔 User Feedback

### Console Messages
Every action produces a console message:

```
🧪 Analyzing SQL...                           (INFO - Blue)
✓ SQL analysis complete: 0 errors, 1 warnings (WARNING - Yellow)
✗ SQL analysis failed: Connection error       (ERROR - Red)
✓ SQL analysis complete: 0 errors, 0 warnings (SUCCESS - Green)
```

### Visual Feedback
- **Loading**: Button text changes to "Analyzing..."
- **Success**: Results appear with colored sections
- **Error**: Red alert box with error message
- **Empty State**: Helpful placeholder text

---

## 🎓 UX Best Practices Applied

1. **Progressive Disclosure**: Collapsed by default, expands on demand
2. **Clear Safety Message**: Banner at top of panel
3. **Visual Hierarchy**: Severity colors guide attention
4. **Scannable Results**: Organized into clear sections
5. **Actionable Feedback**: Every finding has a recommendation
6. **Error Prevention**: Disables buttons when not ready
7. **Clear State**: Loading states, disabled states clearly shown
8. **Helpful Defaults**: Auto-detect operation type
9. **Copy-Friendly**: SQL suggestions in monospace, easy to copy
10. **Consistent Design**: Matches existing DB Doctor style

---

## 🖼️ Icon Reference

- 🧪 Science Flask: SQL Sandbox feature indicator
- ⚠️ Warning Triangle: Warning severity
- ❌ Red X: Error severity
- ℹ️ Info Circle: Info severity
- 💡 Light Bulb: Recommendations
- 🔍 Magnifying Glass: Findings section
- 📊 Bar Chart: Index analysis
- 🔥 Fire: Cascade delete (danger)
- ✓ Check Mark: Success, valid
- ▼ Down Arrow: Expand panel
- ▲ Up Arrow: Collapse panel

---

This visual guide should help you understand exactly what to expect when using the SQL Diagnostic Sandbox feature!

