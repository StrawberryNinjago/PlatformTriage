# Table Diagnostics Panel - Component Structure

## Visual Layout

```
┌─────────────────────────────────────────────────────────────────┐
│ Table: public.cart_item                    [Copy Diagnostics]   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 📊 Diagnostics Summary                                          │
├─────────────────────────────────────────────────────────────────┤
│  🟢 Ownership OK    🟢 SELECT Access    🟡 Write Access Limited │
│  🟢 FK Integrity OK    🟡 2 Cascade FKs                         │
│  (clickable pills that scroll to sections)                      │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ ⚠️ Potential Credential Drift                                   │
├─────────────────────────────────────────────────────────────────┤
│ This table was likely created by a different role than the one │
│ currently connected.                                            │
│                                                                 │
│ Flyway installed by: flyway • Table owner: cart_admin •        │
│ Connected as: cart_user                                         │
│                                                                 │
│ 💡 This connects Flyway diagnostics with ownership issues...   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 📅 Diagnostic Timeline                                          │
├─────────────────────────────────────────────────────────────────┤
│ │ ✔️ Flyway migration applied                                  │
│ │    10 days ago by flyway                                     │
│ │    Version 1.0: Create cart schema                           │
│ │                                                              │
│ │ ✔️ Table created                                             │
│ │    Owner: cart_admin                                         │
│ │                                                              │
│ │ ⚠️ Ownership differs from current user                       │
│ │    Connected as cart_user, but table owned by cart_admin    │
│ │                                                              │
│ │ ❌ Write privileges missing                                  │
│ │    INSERT, UPDATE, DELETE                                    │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 🔐 Ownership & Access Diagnostics                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ PRE-CHECK STATE:                                                │
│ Check ownership and access privileges for this table.           │
│ [Check Ownership & Grants]                                      │
│                                                                 │
│ POST-CHECK STATE:                                               │
│ ┌─────────────┬──────────────┬─────────────────────────────┐  │
│ │ Check       │ Result       │ Details                     │  │
│ ├─────────────┼──────────────┼─────────────────────────────┤  │
│ │ Table Owner │ ❌ cart_admin│ Connected as cart_user      │  │
│ │ SELECT      │ ✅ Allowed   │ Permission granted          │  │
│ │ INSERT      │ ❌ Missing   │ No permission               │  │
│ │ UPDATE      │ ❌ Missing   │ No permission               │  │
│ │ DELETE      │ ❌ Missing   │ No permission               │  │
│ └─────────────┴──────────────┴─────────────────────────────┘  │
│                                                                 │
│ ⚠️ Interpretation                                               │
│ This table is owned by cart_admin.                              │
│ The connected user cart_user can read data but cannot modify   │
│ it. This is acceptable for read-only services but will fail    │
│ for write paths.                                                │
│                                                                 │
│ [ℹ️ Why this matters]                                           │
│   Ownership and access control determine what operations your  │
│   application can perform. Mismatched ownership is a common    │
│   cause of production failures...                              │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 🔍 Indexes                                      [5 total]    ▼  │
├─────────────────────────────────────────────────────────────────┤
│ (Collapsed by default - click to expand)                       │
│                                                                 │
│ EXPANDED:                                                       │
│ ┌────────────────┬──────────┬──────────────┬──────────┐       │
│ │ Name           │ Type     │ Columns      │ Method   │       │
│ ├────────────────┼──────────┼──────────────┼──────────┤       │
│ │ cart_item_pkey │ 🔑 PRIMARY│ id          │ btree    │       │
│ │ idx_cart_id    │ INDEX    │ cart_id     │ btree    │       │
│ │ idx_product_id │ INDEX    │ product_id  │ btree    │       │
│ └────────────────┴──────────┴──────────────┴──────────┘       │
│                                                                 │
│ [ℹ️ Why this matters]                                           │
│   Missing or misaligned indexes can cause severe performance   │
│   degradation under load...                                    │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ 🔒 Constraints                                  [8 total]    ▼  │
├─────────────────────────────────────────────────────────────────┤
│ (Collapsed by default - click to expand)                       │
│                                                                 │
│ EXPANDED:                                                       │
│                                                                 │
│ 🔑 Primary Keys                                                 │
│ ┌────────────────┬──────────────┐                              │
│ │ Name           │ Columns      │                              │
│ ├────────────────┼──────────────┤                              │
│ │ cart_item_pkey │ id           │                              │
│ └────────────────┴──────────────┘                              │
│                                                                 │
│ 🔗 Foreign Keys                      [2 with CASCADE]          │
│ ┌─────────────┬─────────┬──────────────────┬──────────────┐   │
│ │ Name        │ Columns │ Definition       │ Risk         │   │
│ ├─────────────┼─────────┼──────────────────┼──────────────┤   │
│ │ fk_cart     │ cart_id │ REFERENCES cart  │ 🟡 High      │   │
│ │             │         │ ON DELETE CASCADE│   impact     │   │
│ │ fk_product  │ prod_id │ REFERENCES prod  │ Normal       │   │
│ └─────────────┴─────────┴──────────────────┴──────────────┘   │
│                                                                 │
│ [ℹ️ Why this matters]                                           │
│   Foreign key cascades can amplify delete operations and       │
│   cause unexpected data loss...                                │
│                                                                 │
│ Unique Constraints                                              │
│ (similar table)                                                 │
│                                                                 │
│ 🧪 Check Constraints                                            │
│ (similar table)                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## Component Hierarchy

```
TableDiagnosticsPanel
├── Header Section
│   ├── Table name
│   └── Copy Diagnostics button
│
├── Diagnostics Summary Strip (Paper)
│   ├── Title
│   └── DiagnosticPill components (clickable)
│       ├── Ownership status
│       ├── SELECT access
│       ├── Write access
│       ├── FK integrity
│       └── Cascade warnings
│
├── Drift Warning Alert (conditional)
│   ├── AlertTitle
│   ├── Drift explanation
│   └── Comparison of roles
│
├── Diagnostic Timeline (Paper, conditional)
│   ├── Flyway migration event
│   ├── Table creation event
│   ├── Ownership mismatch event (conditional)
│   └── Missing privileges event (conditional)
│
├── Ownership & Access Section (Paper)
│   ├── Section header
│   ├── Pre-check state (conditional)
│   │   └── Check button
│   ├── Post-check state (conditional)
│   │   ├── Diagnostic table
│   │   ├── Interpretation alert
│   │   └── WhyThisMatters component
│   └── WhyThisMatters component
│
├── Indexes Section (Paper, collapsible)
│   ├── Header with count chip
│   ├── Expand/collapse icon
│   ├── Indexes table (when expanded)
│   └── WhyThisMatters component
│
└── Constraints Section (Paper, collapsible)
    ├── Header with count chip
    ├── Expand/collapse icon
    └── When expanded:
        ├── Primary Keys subsection
        ├── Foreign Keys subsection
        │   ├── Cascade warning chip
        │   ├── FK table with risk indicators
        │   └── WhyThisMatters component
        ├── Unique Constraints subsection
        └── Check Constraints subsection
```

## State Management

```javascript
const [privilegesData, setPrivilegesData] = useState(null);
const [loadingPrivileges, setLoadingPrivileges] = useState(false);
const [privilegesChecked, setPrivilegesChecked] = useState(false);
const [copySuccess, setCopySuccess] = useState(false);
const [expandedSections, setExpandedSections] = useState({
  indexes: false,
  constraints: false,
  ownership: false
});
```

## Key Functions

### Diagnostic Calculations
```javascript
const ownershipOk = owner === currentUser;
const hasSelectAccess = privilegesData?.grantedPrivileges?.includes('SELECT');
const hasWriteAccess = privilegesData?.grantedPrivileges?.some(p => 
  ['INSERT', 'UPDATE', 'DELETE'].includes(p)
);
const fkIntegrityOk = foreignKeys.every(fk => fk.definition);
const hasCascadeRisk = cascadingFKs.length > 0;
const hasFlywayDrift = flywayInfo && owner && 
  flywayInfo.installedBy !== currentUser && 
  owner !== currentUser;
```

### Scroll Navigation
```javascript
const scrollToSection = (ref) => {
  ref.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
};
```

### Copy to Clipboard
```javascript
const generateDiagnosticText = () => {
  // Generates formatted text report
};

const handleCopyDiagnostics = async () => {
  const text = generateDiagnosticText();
  await navigator.clipboard.writeText(text);
  setCopySuccess(true);
  setTimeout(() => setCopySuccess(false), 2000);
};
```

## Reusable Components

### DiagnosticPill
```javascript
<DiagnosticPill
  icon={<CheckCircleIcon />}
  label="Ownership OK"
  status="success"  // 'success' | 'warning' | 'error' | 'info'
  onClick={() => scrollToSection(ownershipRef)}
/>
```

### WhyThisMatters
```javascript
<WhyThisMatters>
  Explanation text that helps users understand the impact
  of this diagnostic area.
</WhyThisMatters>
```

## Color Coding

### Status Colors
- **Success (Green):** `#e8f5e9` background, `#4caf50` border, `#2e7d32` text
- **Warning (Orange):** `#fff3e0` background, `#ff9800` border, `#e65100` text
- **Error (Red):** `#ffebee` background, `#f44336` border, `#c62828` text
- **Info (Blue):** `#e3f2fd` background, `#2196f3` border, `#1565c0` text

### Constraint Type Colors
- **Primary Keys:** Red background (`#ffebee`)
- **Foreign Keys:** Blue background (`#e3f2fd`)
- **Unique:** Green background (`#e8f5e9`)
- **Check:** Orange background (`#fff3e0`)

## Interaction Patterns

1. **Initial Load:** Shows summary, timeline, and collapsed sections
2. **Check Privileges:** Button → API call → Persistent diagnostic card
3. **Pill Click:** Smooth scroll to relevant section
4. **Section Toggle:** Expand/collapse with rotation animation
5. **Copy Button:** Click → Copy → Success feedback → Reset after 2s
6. **Why This Matters:** Click → Expand explanation → Click again to collapse

## Responsive Behavior

- Diagnostic pills wrap on smaller screens
- Tables scroll horizontally if needed
- Sections stack vertically
- Timeline maintains left border on all sizes

