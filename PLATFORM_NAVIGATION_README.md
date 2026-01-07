# Platform Triage Navigation Structure

## Overview

PlatformTriage now has a unified navigation shell that brings together multiple diagnostic modules under one consistent interface. DB Doctor is now one module within the broader PlatformTriage application.

## Architecture

### Top-Level Structure

```
┌─────────────────────────────────────────────────────────────┐
│ App Bar: PlatformTriage + Status Chips (DB, K8s)            │
├─────────────────────────────────────────────────────────────┤
│ Module Tabs: DB Doctor | Deployment Doctor | Exports | Help │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                    Page Content Area                        │
│                (rendered based on active tab)               │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│ Console Panel (shared across all modules)                   │
└─────────────────────────────────────────────────────────────┘
```

## Components

### 1. Global App Bar

**Location:** Top of the application  
**Features:**
- **Left:** Product name "PlatformTriage"
- **Right:** Status chips showing connection status
  - **DB:** Shows `connected` or `disconnected`
  - **K8s:** Shows Kubernetes connectivity (currently "not configured")

**Implementation:** `App.jsx` - AppBar component

### 2. Module Navigation Tabs

**Location:** Directly under the App Bar  
**Modules:**

#### DB Doctor
The existing database diagnostics module with:
- Connection management
- SQL Sandbox
- Table diagnostics
- Environment comparison
- Flyway health checks

**Layout:** Two-column layout (33% left panel, 67% right panel)
- **Left Panel:** Connection form + Action buttons (fixed width)
- **Right Panel:** Tabbed workspace (Single Environment | Compare Environments)

#### Deployment Doctor
Kubernetes deployment monitoring module:
- Service health monitoring
- Pod status and replica tracking
- Recent events and warnings
- Automatic issue detection

**Features:**
- Namespace configuration
- Label selector filtering
- Helm release filtering
- Real-time deployment status

#### Exports
Diagnostic export and sharing module:
- JIRA bundle export (coming soon)
- Copy all diagnostics to clipboard
- Export history tracking
- Multiple export formats

#### Help
Comprehensive documentation and help:
- Module overviews
- Feature explanations
- Tips and best practices
- Support information

### 3. Console Panel

**Location:** Bottom of the application (full width)  
**Features:**
- Shared across all modules
- Real-time operation logs
- Color-coded messages (success, warning, error, info)
- Persistent across tab navigation

## File Structure

```
frontend/src/
├── App.jsx                          # Main app with navigation shell
├── App.css                          # Global styles
├── main.jsx                         # Application entry point
├── components/                      # Reusable components
│   ├── ActionButtons.jsx
│   ├── ConnectionForm.jsx
│   ├── ConsolePanel.jsx            # Shared console
│   ├── EnvironmentComparisonPanel.jsx
│   ├── FlywayHealthPanel.jsx
│   ├── ResultsPanel.jsx
│   ├── SqlSandboxPanel.jsx
│   ├── SummaryPanel.jsx
│   └── TableDiagnosticsPanel.jsx
├── pages/                           # Module pages
│   ├── DBDoctorPage.jsx            # Database diagnostics
│   ├── DeploymentDoctorPage.jsx    # Kubernetes monitoring
│   ├── ExportsPage.jsx             # Export functionality
│   └── HelpPage.jsx                # Documentation
└── services/
    └── apiService.js                # API client
```

## Usage

### Navigating Between Modules

Click on any of the top-level tabs to switch between modules:
- **DB Doctor:** Database diagnostics (default)
- **Deployment Doctor:** Kubernetes monitoring
- **Exports:** Export and sharing tools
- **Help:** Documentation and support

### Module-Specific Features

#### DB Doctor
1. Enter database connection details in the left panel
2. Click "Connect" to establish connection
3. Use action buttons to perform diagnostics
4. Switch between "Single Environment" and "Compare Environments" tabs
5. Results appear in the right panel
6. All operations logged to console panel

#### Deployment Doctor
1. Enter Kubernetes namespace (required)
2. Optionally add label selector or release name
3. Click "Load" to fetch deployment information
4. View health metrics in summary cards
5. Expand workload details for deeper inspection
6. Review findings and recommendations

#### Exports
1. Choose export format (JIRA bundle or clipboard)
2. Export captures current diagnostic state
3. View export history
4. Re-download previous exports

#### Help
- Browse module documentation
- Learn about features
- Read tips and best practices
- Find support information

## State Management

### Global State (App.jsx)
- `connectionId`: Active database connection ID
- `connectionStatus`: DB connection status (connected/disconnected)
- `consoleMessages`: Shared console log
- `activeModuleTab`: Current active module (0-3)

### Module-Specific State
Each module page manages its own local state:
- **DBDoctorPage:** Summary data, results, schema, action states, environment comparison data
- **DeploymentDoctorPage:** Kubernetes summary, namespace, selector, loading state
- **ExportsPage:** Export history, current export data
- **HelpPage:** No state (static content)

## API Endpoints

### DB Doctor APIs
- `POST /api/connect` - Database connection
- `GET /api/summary/{connectionId}` - Database summary
- `GET /api/tables/{connectionId}` - List tables
- `POST /api/sql/analyze` - SQL validation
- `POST /api/compare` - Environment comparison
- (and others - see existing documentation)

### Deployment Doctor APIs
- `GET /api/deployment/summary?namespace={ns}&selector={sel}&release={rel}`
  - Required: `namespace`
  - Optional: `selector`, `release`, `limitEvents`

### Exports APIs
(Coming soon)

## Styling Conventions

### Layout
- **App Bar:** Material-UI AppBar with primary color
- **Tabs:** Material-UI Tabs with no text transform
- **Content Area:** Container with `maxWidth="xl"`
- **Console:** Fixed at bottom, full width

### Colors
- **Primary:** `#1976d2` (Material-UI default blue)
- **Success:** Green for healthy/connected states
- **Warning:** Yellow/orange for warnings
- **Error:** Red for errors/critical issues
- **Background:** `#f5f5f5` (light gray)

### Spacing
- Consistent `mt: 3, pb: 3` for page content
- `spacing={3}` for Grid containers
- `elevation={2}` for Paper components

## Future Enhancements

### Phase 1 (Current)
- ✅ Top-level navigation shell
- ✅ Module tabs (DB Doctor, Deployment Doctor, Exports, Help)
- ✅ Global status chips
- ✅ Shared console panel

### Phase 2 (Planned)
- [ ] K8s connectivity indicator (real-time)
- [ ] Environment context in app bar (cluster name)
- [ ] Export functionality implementation
- [ ] JIRA bundle generation
- [ ] Export history tracking

### Phase 3 (Future)
- [ ] User preferences and settings
- [ ] Dark mode support
- [ ] Additional diagnostic modules
- [ ] Integration with monitoring tools
- [ ] Automated diagnostics scheduling

## Development Notes

### Adding a New Module

1. Create page component in `frontend/src/pages/ModulePage.jsx`
2. Import page in `App.jsx`
3. Add tab to module navigation:
   ```jsx
   <Tab label="New Module" />
   ```
4. Add conditional rendering:
   ```jsx
   {activeModuleTab === N && (
     <NewModulePage addConsoleMessage={addConsoleMessage} />
   )}
   ```
5. Update this documentation

### Props Pattern

All module pages receive these standard props:
- `addConsoleMessage(text, type)` - Function to add console messages
- Additional props as needed for specific modules

### Console Message Types
- `'success'` - Green checkmark (✓)
- `'error'` - Red cross (✗)
- `'warning'` - Orange warning
- `'info'` - Blue information (default)

## Testing

### Manual Testing Checklist
- [ ] All tabs are clickable and render correctly
- [ ] Status chips show correct connection state
- [ ] Console messages appear across all tabs
- [ ] Console persists when switching tabs
- [ ] DB Doctor functionality works as before
- [ ] Deployment Doctor loads summary correctly
- [ ] Exports page renders (even if features are disabled)
- [ ] Help page displays all content

### Browser Compatibility
- Chrome/Edge (latest)
- Firefox (latest)
- Safari (latest)

## Troubleshooting

### Module not rendering
- Check `activeModuleTab` state value
- Verify conditional rendering in App.jsx
- Check browser console for errors

### Console messages not appearing
- Ensure `addConsoleMessage` is passed as prop
- Verify function is called with correct parameters
- Check ConsolePanel component rendering

### Status chips not updating
- Check state updates in App.jsx
- Verify connection status propagation
- Review state setter functions

## References

- [Material-UI Documentation](https://mui.com/)
- [React Documentation](https://react.dev/)
- [Vite Documentation](https://vitejs.dev/)

## Support

For questions or issues with the navigation structure, refer to the Help module or contact the platform team.

