# Platform Triage Navigation - Visual Guide

## Before (Old Structure)

```
┌─────────────────────────────────────────────────────────────────┐
│ ███████████████████████████████████████████████████████████████ │
│ █  PlatformTriage – DB Doctor (MVP)    Connection: connected █ │
│ ███████████████████████████████████████████████████████████████ │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐  ┌────────────────────────────────────┐ │
│  │                  │  │                                    │ │
│  │  Connection      │  │  [Single Env] [Compare Envs] <--  │ │
│  │  Form            │  │   Content tabs inside page        │ │
│  │                  │  │                                    │ │
│  │  [Connect]       │  │  Summary Panel                     │ │
│  │  [Load Summary]  │  │  SQL Sandbox                       │ │
│  │                  │  │  Results                           │ │
│  ├──────────────────┤  │                                    │ │
│  │                  │  │                                    │ │
│  │  Action Buttons  │  │                                    │ │
│  │                  │  │                                    │ │
│  │  • Verify        │  │                                    │ │
│  │  • Flyway Health │  │                                    │ │
│  │  • List Tables   │  │                                    │ │
│  │  • Find Table    │  │                                    │ │
│  │  • Check Owner   │  │                                    │ │
│  │  • Table Details │  │                                    │ │
│  │                  │  │                                    │ │
│  └──────────────────┘  └────────────────────────────────────┘ │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  Console Panel: [✓ Connected] [✓ Summary loaded] ...          │
└─────────────────────────────────────────────────────────────────┘
```

**Limitations:**
- Only DB Doctor available
- No clear product identity
- No way to add other diagnostic modules
- Rigid single-page structure

---

## After (New Structure)

```
┌─────────────────────────────────────────────────────────────────┐
│ ███████████████████████████████████████████████████████████████ │
│ █  PlatformTriage          [DB:connected] [K8s:not configured]█ │
│ ███████████████████████████████████████████████████████████████ │
├─────────────────────────────────────────────────────────────────┤
│ ╔═══════════════════════════════════════════════════════════╗ │
│ ║ [DB Doctor] [Deployment Doctor] [Exports] [Help]         ║ │ <-- Top-level tabs
│ ╚═══════════════════════════════════════════════════════════╝ │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  📄 DB Doctor Page (activeTab === 0)                           │
│  ┌──────────────────┐  ┌────────────────────────────────────┐ │
│  │                  │  │                                    │ │
│  │  Connection      │  │  [Single Env] [Compare Envs]       │ │
│  │  Form            │  │                                    │ │
│  │                  │  │  Summary Panel                     │ │
│  │  [Connect]       │  │  SQL Sandbox                       │ │
│  │  [Load Summary]  │  │  Results                           │ │
│  │                  │  │                                    │ │
│  ├──────────────────┤  │                                    │ │
│  │  Action Buttons  │  │                                    │ │
│  └──────────────────┘  └────────────────────────────────────┘ │
│                                                                 │
│  OR                                                             │
│                                                                 │
│  🚀 Deployment Doctor Page (activeTab === 1)                   │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │ Kubernetes Configuration                                  │ │
│  │ [Namespace:____] [Selector:____] [Release:____] [Load]   │ │
│  └───────────────────────────────────────────────────────────┘ │
│  ┌─────┬─────┬─────┬─────┐                                    │
│  │Total│Heal │Warn │Crit │  <- Health summary cards          │
│  └─────┴─────┴─────┴─────┘                                    │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │ ▼ Deployment 1                          [HEALTHY]         │ │
│  │ ▼ Deployment 2                          [WARNING]         │ │
│  │ ▼ Deployment 3                          [CRITICAL]        │ │
│  └───────────────────────────────────────────────────────────┘ │
│                                                                 │
│  OR                                                             │
│                                                                 │
│  📦 Exports Page (activeTab === 2)                             │
│  ┌──────────────────┐  ┌──────────────────┐                   │
│  │ JIRA Bundle      │  │ Copy Diagnostics │                   │
│  │ [Export]         │  │ [Copy]           │                   │
│  └──────────────────┘  └──────────────────┘                   │
│  ┌─────────────────────────────────────────┐                  │
│  │ Export History (coming soon)            │                  │
│  └─────────────────────────────────────────┘                  │
│                                                                 │
│  OR                                                             │
│                                                                 │
│  ❓ Help Page (activeTab === 3)                                │
│  ┌───────────────────────────────────────────────────────────┐ │
│  │ ▼ DB Doctor                                               │ │
│  │ ▼ Deployment Doctor                                       │ │
│  │ ▼ Exports                                                 │ │
│  │ Tips & Best Practices                                     │ │
│  └───────────────────────────────────────────────────────────┘ │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  Console Panel: [✓ Connected] [🔍 Comparing...] [✓ Done]      │
│                 (shared across all tabs)                       │
└─────────────────────────────────────────────────────────────────┘
```

**Improvements:**
✅ Clear product branding ("PlatformTriage")  
✅ Status chips show connectivity at a glance  
✅ Modular navigation - easy to add more modules  
✅ Each module is self-contained  
✅ Shared console for unified logging  
✅ Consistent layout across modules  

---

## Component Hierarchy

### Old Structure
```
App
├── AppBar (inline)
├── Container
│   └── Grid (left/right split)
│       ├── Left Panel
│       │   ├── ConnectionForm
│       │   └── ActionButtons
│       └── Right Panel
│           ├── Tabs (Single Env / Compare Envs)
│           ├── SummaryPanel
│           ├── SqlSandboxPanel
│           ├── ResultsPanel
│           └── EnvironmentComparisonPanel
└── ConsolePanel
```

### New Structure
```
App
├── AppBar (Material-UI component)
│   └── Status Chips (DB, K8s)
├── Module Tabs (DB Doctor, Deployment Doctor, Exports, Help)
├── Page Content (conditional)
│   ├── DBDoctorPage
│   │   ├── Container
│   │   └── Grid (left/right split) - same as before
│   │       ├── ConnectionForm
│   │       ├── ActionButtons
│   │       ├── Tabs (Single/Compare)
│   │       ├── SummaryPanel
│   │       ├── SqlSandboxPanel
│   │       ├── ResultsPanel
│   │       └── EnvironmentComparisonPanel
│   ├── DeploymentDoctorPage
│   │   ├── Configuration Form
│   │   ├── Health Cards
│   │   └── Workload Accordions
│   ├── ExportsPage
│   │   └── Export Feature Cards
│   └── HelpPage
│       └── Documentation Accordions
└── ConsolePanel (shared)
```

---

## Navigation Flow

### User Journey: Database Diagnostics

```
1. User opens app
   ↓
2. Lands on "DB Doctor" tab (default)
   ↓
3. Fills connection form
   ↓
4. Clicks "Connect"
   ↓
5. App bar shows "DB: connected" (green chip)
   ↓
6. Uses action buttons / SQL Sandbox
   ↓
7. Switches to "Compare Environments" tab
   ↓
8. Compares dev vs prod
   ↓
9. Reviews results
   ↓
10. Checks console panel for operation log
```

### User Journey: Kubernetes Monitoring

```
1. User clicks "Deployment Doctor" tab
   ↓
2. Enters namespace (e.g., "production")
   ↓
3. Optionally adds selector (e.g., "app=api")
   ↓
4. Clicks "Load"
   ↓
5. Views health summary cards
   ↓
6. Expands workload for details
   ↓
7. Reviews findings and recommendations
   ↓
8. Checks console for operation log
   ↓
9. Switches back to "DB Doctor" if needed
   ↓
10. Console shows messages from both modules
```

---

## Status Indicators

### Connection Status Chip - Database

| State | Display | Color | Example |
|-------|---------|-------|---------|
| Disconnected | `DB: disconnected` | Gray | Default state |
| Connected | `DB: connected` | Green | After successful connection |

```
┌─────────────────────────────┐
│ DB: disconnected            │  <- Gray background
└─────────────────────────────┘

┌─────────────────────────────┐
│ DB: connected               │  <- Green background
└─────────────────────────────┘
```

### Connection Status Chip - Kubernetes

| State | Display | Color | Example |
|-------|---------|-------|---------|
| Not configured | `K8s: not configured` | Gray | Always (for now) |
| Connected (future) | `K8s: connected` | Green | After K8s integration |

```
┌─────────────────────────────┐
│ K8s: not configured         │  <- Gray background
└─────────────────────────────┘
```

---

## Module Tab States

### Active Tab
```
╔═══════════╗ ┌─────────────┐ ┌─────────┐ ┌──────┐
║ DB Doctor ║ │ Deployment  │ │ Exports │ │ Help │
╚═══════════╝ │   Doctor    │ └─────────┘ └──────┘
              └─────────────┘
              
^ Active      ^ Inactive tabs
  (bold,        (normal text)
   underline)
```

### Hover State
```
┌───────────┐ ╔═════════════╗ ┌─────────┐ ┌──────┐
│ DB Doctor │ ║ Deployment  ║ │ Exports │ │ Help │
└───────────┘ ║   Doctor    ║ └─────────┘ └──────┘
              ╚═════════════╝
              
              ^ Hovered
                (highlighted)
```

---

## Console Panel Messages

### Message Types

```
┌────────────────────────────────────────────────────────────┐
│ Console                                          [Clear]   │
├────────────────────────────────────────────────────────────┤
│ ✓ Connected successfully                       [SUCCESS]  │
│ 🔍 Comparing environments: dev → prod...       [INFO]     │
│ ⚠ Schema drift detected: 3 differences        [WARNING]  │
│ ✗ Connection failed: timeout                   [ERROR]    │
│ 🧪 Analyzing SQL...                            [INFO]     │
│ ✓ SQL analysis complete: 0 errors              [SUCCESS]  │
└────────────────────────────────────────────────────────────┘
```

### Color Coding

| Type | Icon | Color | Use Case |
|------|------|-------|----------|
| Success | ✓ | Green | Operations completed successfully |
| Error | ✗ | Red | Operations failed |
| Warning | ⚠ | Orange | Issues detected but not critical |
| Info | 🔍 🧪 ℹ | Blue | Informational messages |

---

## Responsive Behavior

### Desktop (> 1200px)
```
┌───────────────────────────────────────────────┐
│ App Bar: Full width                          │
├───────────────────────────────────────────────┤
│ Tabs: Horizontal                              │
├───────────────────────────────────────────────┤
│ Content: Two columns (DB Doctor)              │
│   Left: 33%  |  Right: 67%                    │
└───────────────────────────────────────────────┘
```

### Tablet (768px - 1200px)
```
┌─────────────────────────────────┐
│ App Bar: Full width             │
├─────────────────────────────────┤
│ Tabs: Horizontal, scrollable    │
├─────────────────────────────────┤
│ Content: Adjusted columns        │
│   Left: 40%  |  Right: 60%      │
└─────────────────────────────────┘
```

### Mobile (< 768px)
```
┌─────────────────────┐
│ App Bar            │
│ Status chips stack │
├─────────────────────┤
│ Tabs: Scrollable   │
├─────────────────────┤
│ Content: Stacked   │
│ ┌─────────────────┐│
│ │ Left Panel      ││
│ └─────────────────┘│
│ ┌─────────────────┐│
│ │ Right Panel     ││
│ └─────────────────┘│
└─────────────────────┘
```

---

## Color Palette

### Primary Colors
```
Primary Blue:    ████  #1976d2
Primary Dark:    ████  #115293
Primary Light:   ████  #4791db
```

### Status Colors
```
Success Green:   ████  #2e7d32
Warning Orange:  ████  #ed6c02
Error Red:       ████  #d32f2f
Info Blue:       ████  #0288d1
```

### Background Colors
```
Page Background: ████  #f5f5f5 (light gray)
Paper/Card:      ████  #ffffff (white)
App Bar:         ████  #1976d2 (primary blue)
```

---

## Keyboard Shortcuts (Future Enhancement)

| Shortcut | Action |
|----------|--------|
| `Ctrl + 1` | Switch to DB Doctor |
| `Ctrl + 2` | Switch to Deployment Doctor |
| `Ctrl + 3` | Switch to Exports |
| `Ctrl + 4` | Switch to Help |
| `Ctrl + K` | Focus search (when implemented) |
| `Ctrl + L` | Clear console |
| `Escape` | Close modals/dialogs |

---

## Animation & Transitions

### Tab Switching
```
Fade in/out: 200ms
Ease: cubic-bezier(0.4, 0, 0.2, 1)
```

### Status Chip Changes
```
Color transition: 300ms
Ease: ease-in-out
```

### Accordion Expand/Collapse
```
Height transition: 250ms
Ease: ease-out
```

---

## Accessibility

### ARIA Labels
```html
<AppBar role="banner" aria-label="Main navigation">
<Tabs role="tablist" aria-label="Module navigation">
<Tab role="tab" aria-selected="true">DB Doctor</Tab>
<Console role="log" aria-label="Operation console">
```

### Keyboard Navigation
- Tab through form fields
- Arrow keys to navigate tabs
- Enter/Space to activate buttons
- Escape to close dialogs

### Screen Reader Support
- Status chips announced on change
- Console messages announced as they appear
- Loading states announced
- Error messages have proper ARIA roles

---

## Print Layout (Future Enhancement)

When printing diagnostic results:
```
┌─────────────────────────────────────┐
│ PlatformTriage Report               │
│ Generated: 2026-01-06               │
├─────────────────────────────────────┤
│ [Module content]                    │
│ [Console log summary]               │
└─────────────────────────────────────┘
```

Hide in print:
- Navigation tabs
- Action buttons
- Interactive elements

Show in print:
- Current module content
- Console messages
- Summary information
- Timestamp

---

## Summary: Key Visual Changes

| Aspect | Old | New |
|--------|-----|-----|
| **App Name** | "PlatformTriage – DB Doctor (MVP)" | "PlatformTriage" |
| **Status Display** | Text: "Connection: connected" | Chips: "[DB: connected] [K8s: ...]" |
| **Navigation** | None (single page) | Top-level tabs (4 modules) |
| **Modules** | 1 (DB Doctor only) | 4 (DB, Deployment, Exports, Help) |
| **Console** | At bottom of page | Shared across all modules |
| **Layout** | Fixed two-column | Module-specific layouts |
| **Extensibility** | Hard to add features | Easy to add new modules |

---

**Legend:**
- █ = Colored block
- ┌─┐ = Box borders
- ═ = Strong emphasis
- ▼ = Expandable section
- ✓ ✗ ⚠ = Status icons
- 🔍 🧪 📦 ❓ 🚀 = Emoji icons


