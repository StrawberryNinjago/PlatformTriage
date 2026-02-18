import { useState } from 'react';
import { 
  Container, 
  Paper, 
  Typography, 
  Box, 
  Grid,
  Card,
  CardContent,
  Chip,
  Alert,
  CircularProgress,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  List,
  ListItem,
  ListItemText,
  TextField,
  Button,
  Tooltip
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorIcon from '@mui/icons-material/Error';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import InfoIcon from '@mui/icons-material/Info';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import RefreshIcon from '@mui/icons-material/Refresh';
import CheckIcon from '@mui/icons-material/Check';
import axios from 'axios';
import { apiService } from '../services/apiService';
import AiAssistantPanel from '../components/AiAssistantPanel';

// ⭐ Demo Scenarios Configuration
const WORKLOAD_SCENARIOS = [
  {
    id: "healthy",
    label: "Healthy App",
    description: "All pods running, no findings",
    tooltip: "Baseline, no findings",
    namespace: "cart",
    selector: "app=cart-app"
  },
  {
    id: "warn",
    label: "Restart Warning",
    description: "Pods running but restarted recently",
    tooltip: "Early instability signals",
    namespace: "cart",
    selector: "app=bad-app"
  },
  {
    id: "fail",
    label: "Config Failure",
    description: "Missing secret / Key Vault failure",
    tooltip: "Clear root cause",
    namespace: "cart",
    selector: "app=kv-misconfig-app"
  }
];

const GUARDRAIL_SCENARIOS = [
  {
    id: "validation",
    label: "Query Validation",
    description: "Demonstrates query validation and safe failure (not a workload issue)",
    tooltip: "Input validation & trust boundaries",
    namespace: "cart",
    selector: "app="
  }
];

function DeploymentDoctorPage({ addConsoleMessage, setK8sStatus }) {
  const [loading, setLoading] = useState(false);
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState(null);
  const [namespace, setNamespace] = useState('');
  const [selector, setSelector] = useState('');
  const [release, setRelease] = useState('');
  const [activeScenario, setActiveScenario] = useState(null);
  const [isCustomMode, setIsCustomMode] = useState(false);
  const [currentAction, setCurrentAction] = useState(null);
  const [lastToolResult, setLastToolResult] = useState(null);
  const [versionCheck, setVersionCheck] = useState(null);
  const [versionCheckLoading, setVersionCheckLoading] = useState(false);
  const [podLogPayload, setPodLogPayload] = useState(null);
  const [traceSearchLoading, setTraceSearchLoading] = useState(false);
  const [traceIdQuery, setTraceIdQuery] = useState('');
  const [tracePodNameQuery, setTracePodNameQuery] = useState('');
  const [traceLineLimitQuery, setTraceLineLimitQuery] = useState(500);
  const [traceSearchResult, setTraceSearchResult] = useState(null);

  const configFieldSx = {
    '& .MuiInputBase-input': { fontSize: '1.42rem', fontWeight: 600, lineHeight: 1.35 },
    '& .MuiInputBase-input.Mui-disabled': { WebkitTextFillColor: '#5f6368', opacity: 1, fontWeight: 600 },
    '& .MuiInputLabel-root': { fontSize: '1.24rem', fontWeight: 800 },
    '& .MuiInputLabel-root.MuiInputLabel-shrink': { fontSize: '1.12rem', fontWeight: 800 },
    '& .MuiFormHelperText-root': { fontSize: '1.08rem', fontWeight: 600 },
    '& .MuiFormHelperText-root.Mui-disabled': { opacity: 1 }
  };

  const isDeploymentSummaryPayload = (payload) => {
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
      return false;
    }
    return Boolean(payload.target && payload.health && Array.isArray(payload.findings));
  };

  const isPodListPayload = (payload) => {
    return Array.isArray(payload)
      && payload.length >= 0
      && payload.every(item => (
        typeof item === 'object'
        && item !== null
        && typeof item.name === 'string'
        && item.name.trim().length > 0
      ));
  };

  const isPodLogPayload = (payload) => {
    return Array.isArray(payload) && payload.length >= 0 && payload.every(item => typeof item === 'string');
  };

  const loadDeploymentSummary = async (overrideParams = {}) => {
    // Use override params if provided, otherwise use state
    const effectiveNamespace = overrideParams.namespace ?? (namespace || 'default');
    const effectiveSelector = overrideParams.selector ?? selector;
    const effectiveRelease = overrideParams.release ?? release;
    
    if (!effectiveNamespace) {
      addConsoleMessage('✗ Namespace is required', 'error');
      setError('Namespace is required');
      return;
    }

    // Validate selector format
    if (effectiveSelector && !effectiveSelector.includes('=')) {
      const errorMsg = 'Selector must be a valid label selector, e.g. app=cart-app';
      addConsoleMessage(`✗ ${errorMsg}`, 'error');
      setError(errorMsg);
      return;
    }

    setLoading(true);
    setError(null);
    setLastToolResult(null);
    setVersionCheck(null);
    setPodLogPayload(null);
    setTraceSearchResult(null);
    
    try {
      addConsoleMessage(`🔍 Loading deployment summary for namespace: ${effectiveNamespace}...`, 'info');
      
      const params = new URLSearchParams({ namespace: effectiveNamespace });
      if (effectiveSelector) params.append('selector', effectiveSelector);
      if (effectiveRelease) params.append('release', effectiveRelease);
      
      const response = await axios.get(`/api/deployment/summary?${params.toString()}`);
      setSummary(response.data);
      setCurrentAction('load_summary');
      setK8sStatus('connected');
      addConsoleMessage('✓ Deployment summary loaded', 'success');
    } catch (err) {
      const errorMsg = err.response?.data?.message || err.message;
      setError(errorMsg);
      setK8sStatus('disconnected');
      addConsoleMessage(`✗ Failed to load deployment summary: ${errorMsg}`, 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleAiActionResult = (actionName, payload) => {
    if (actionName) {
      setCurrentAction(actionName);
    }

    if (isDeploymentSummaryPayload(payload)) {
      setSummary(payload);
      setLastToolResult(null);
      setVersionCheck(payload.versionCheck || null);
      setPodLogPayload(null);
      setTraceSearchResult(null);
      return;
    }

    if (payload == null) {
      setLastToolResult(null);
      return;
    }

    if (actionName === 'check_versions' && typeof payload === 'object') {
      setVersionCheck(payload);
      setLastToolResult(null);
      return;
    }

    if (actionName === 'pod_logs' && isPodLogPayload(payload)) {
      setPodLogPayload(payload);
      return;
    }

    if (actionName === 'trace_search' && typeof payload === 'object') {
      setTraceSearchResult(payload);
      return;
    }

    setLastToolResult({
      action: actionName || 'assistant_tool',
      payload
    });
  };

  // ⭐ Handle demo scenario selection
  const handleScenarioSelect = (scenario) => {
    // Clear previous results
    setSummary(null);
    setError(null);
    setLastToolResult(null);
    setVersionCheck(null);
    setPodLogPayload(null);
    setTraceSearchResult(null);
    
    // Populate fields
    setNamespace(scenario.namespace);
    setSelector(scenario.selector);
    setRelease(scenario.release || '');
    
    // Mark scenario as active
    setActiveScenario(scenario.id);
    setIsCustomMode(false);
    
    // Show feedback
    addConsoleMessage(`📋 Demo scenario loaded: ${scenario.label}`, 'info');
    
    // Auto-trigger load immediately with scenario values (don't wait for state to update)
    loadDeploymentSummary({
      namespace: scenario.namespace,
      selector: scenario.selector,
      release: scenario.release || ''
    });
  };

  // Handle manual field changes (exit scenario mode)
  const handleManualFieldChange = (field, value) => {
    setIsCustomMode(true);
    setActiveScenario(null);
    
    if (field === 'namespace') setNamespace(value);
    if (field === 'selector') setSelector(value);
    if (field === 'release') setRelease(value);
  };

  const runVersionCheck = async () => {
    const effectiveNamespace = summary?.target?.namespace || namespace || '';
    const effectiveSelector = summary?.target?.selector || selector;
    const effectiveRelease = summary?.target?.release || release;

    if (!effectiveNamespace) {
      addConsoleMessage('✗ Namespace is required for version check', 'error');
      setError('Namespace is required for version check');
      return;
    }
    if (!effectiveSelector && !effectiveRelease) {
      addConsoleMessage('✗ Selector or release is required for version check', 'error');
      setError('Selector or release is required for version check');
      return;
    }

    setVersionCheckLoading(true);
    setError(null);

    try {
      const response = await apiService.getDeploymentVersions(
        effectiveNamespace,
        effectiveSelector,
        effectiveRelease
      );
      setVersionCheck(response.data);
      setCurrentAction('check_versions');
      addConsoleMessage('✓ Deployment version check completed', 'success');
    } catch (err) {
      const errorMsg = err.response?.data?.message || err.message;
      setError(errorMsg);
      addConsoleMessage(`✗ Version check failed: ${errorMsg}`, 'error');
      setVersionCheck(null);
    } finally {
      setVersionCheckLoading(false);
    }
  };

  const runTraceSearch = async () => {
    const trimmedQuery = (traceIdQuery || '').trim();
    if (!trimmedQuery) {
      setError('Trace ID or search text is required');
      addConsoleMessage('✗ Trace search requires trace id or text', 'error');
      return;
    }

    const effectiveNamespace = summary?.target?.namespace || namespace || '';
    const effectiveSelector = summary?.target?.selector || selector;
    const effectiveRelease = summary?.target?.release || release;
    const requestedPod = tracePodNameQuery.trim();
    const requestedLineLimit = Number.isFinite(Number(traceLineLimitQuery))
      ? Math.max(1, Math.min(1000, Number(traceLineLimitQuery)))
      : 500;

    if (!effectiveNamespace) {
      addConsoleMessage('✗ Namespace is required for trace search', 'error');
      setError('Namespace is required for trace search');
      return;
    }
    if (!effectiveSelector && !effectiveRelease) {
      addConsoleMessage('✗ Selector or release is required for trace search', 'error');
      setError('Selector or release is required for trace search');
      return;
    }

    setTraceSearchLoading(true);
    setError(null);

    try {
      const response = await apiService.findDeploymentTrace(
        effectiveNamespace,
        trimmedQuery,
        effectiveSelector,
        effectiveRelease,
        requestedPod || undefined,
        requestedLineLimit
      );
      setTraceSearchResult(response.data);
      setCurrentAction('trace_search');
      addConsoleMessage(`✓ Trace search complete for "${trimmedQuery}"`, 'success');
    } catch (err) {
      const errorMsg = err.response?.data?.message || err.message;
      setError(errorMsg);
      addConsoleMessage(`✗ Trace search failed: ${errorMsg}`, 'error');
      setTraceSearchResult(null);
    } finally {
      setTraceSearchLoading(false);
    }
  };

  // ⭐ IMPROVEMENT 1: Severity-driven icons (backend uses HIGH/MED/INFO)
  const getSeverityIcon = (severity) => {
    switch ((severity || '').toUpperCase()) {
      case 'HIGH':
        return <ErrorIcon color="error" />;
      case 'MED':
        return <WarningIcon color="warning" />;
      case 'INFO':
      default:
        return <CheckCircleIcon color="info" />;
    }
  };

  const getSeverityColor = (severity) => {
    switch ((severity || '').toUpperCase()) {
      case 'HIGH':
        return 'error';
      case 'MED':
        return 'warning';
      case 'INFO':
      default:
        return 'info';
    }
  };

  // ⭐ IMPROVEMENT 3: Owner badge helper
  const getOwnerBadge = (owner) => {
    const configs = {
      'APP': { label: 'Application', color: '#1976d2' },
      'PLATFORM': { label: 'Platform', color: '#9c27b0' },
      'SECURITY': { label: 'Security', color: '#d32f2f' },
      'UNKNOWN': { label: 'Unknown', color: '#757575' }
    };
    const config = configs[owner] || configs.UNKNOWN;
    return (
      <Chip
        label={`Owner: ${config.label}`}
        size="small"
        sx={{
          bgcolor: config.color,
          color: 'white',
          fontWeight: 600,
          fontSize: '0.95rem'
        }}
      />
    );
  };

  const getHealthFromSummary = () => {
    if (!summary?.health) {
      return {
        overall: 'PASS',
        deploymentsReady: '0/0',
        pods: { running: 0, pending: 0, crashLoop: 0, imagePullBackOff: 0, notReady: 0 }
      };
    }
    
    return {
      overall: summary.health.overall,
      deploymentsReady: summary.health.deploymentsReady,
      pods: summary.health.pods
    };
  };

  const renderVersionPanel = () => {
    if (!versionCheck) {
      return null;
    }

    const dockerImages = versionCheck.dockerImages || [];
    const dbVersion = versionCheck.databaseVersion || 'N/A';
    const flywayVersion = versionCheck.flywayVersion || 'N/A';
    const displayNamespace = summary?.target?.namespace || 'Unknown namespace';

    return (
      <Paper elevation={2} sx={{ p: 2, mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
          <Typography variant="h6">
            Version Check
          </Typography>
          <Chip label={displayNamespace} size="small" />
        </Box>

        <Box sx={{ mb: 2 }}>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            Runtime Images
          </Typography>
          <Typography variant="body1" sx={{ whiteSpace: 'pre-wrap' }}>
            {dockerImages.length > 0 ? dockerImages.join('\n') : 'No images detected'}
          </Typography>
        </Box>

        <Box sx={{ mb: 2 }}>
          <Typography variant="body1">
            Database version: <strong>{dbVersion}</strong>
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Source: {versionCheck.databaseVersionSource || 'N/A'}
          </Typography>
        </Box>

        <Box sx={{ mb: 2 }}>
          <Typography variant="body1">
            Flyway version: <strong>{flywayVersion}</strong>
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Source: {versionCheck.flywayVersionSource || 'N/A'}
          </Typography>
        </Box>

        <Typography variant="body2" color="text.secondary">
          {versionCheck.notes || ''}
        </Typography>
      </Paper>
    );
  };

  const renderQuickActions = () => (
    <Paper elevation={2} sx={{ p: 2, mb: 3 }}>
        <Typography variant="h6" sx={{ mb: 1 }}>
          Deployment Diagnostics Actions
        </Typography>
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', alignItems: 'center' }}>
        <Button
          variant="contained"
          onClick={runVersionCheck}
          disabled={versionCheckLoading || !summary}
          sx={{ minHeight: 40, fontSize: '1.05rem', fontWeight: 700 }}
        >
          {versionCheckLoading ? <CircularProgress size={18} /> : 'Check Versions'}
        </Button>
        <Typography variant="body2" color="text.secondary">
          Use this panel to query logs by trace id or plain text.
        </Typography>
      </Box>
    </Paper>
  );

  const renderPodLogPanel = () => {
    if (!podLogPayload) {
      return null;
    }

    return (
      <Paper elevation={2} sx={{ p: 2, mb: 3 }}>
        <Typography variant="h6" sx={{ mb: 1 }}>
          Pod Logs
        </Typography>
        <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
          Last lines from pod logs
        </Typography>
        <Box sx={{ maxHeight: 360, overflowY: 'auto', bgcolor: '#0f172a', color: '#f8fafc', p: 2, borderRadius: 1 }}>
          <Typography component="pre" variant="body2" sx={{ m: 0, whiteSpace: 'pre-wrap', fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace' }}>
            {podLogPayload.length > 0 ? podLogPayload.join('\n') : 'No logs returned.'}
          </Typography>
        </Box>
      </Paper>
    );
  };

  const renderTraceSearchPanel = () => {
    const matches = traceSearchResult?.matches || [];
    const hasNoMatchResult = traceSearchResult && (traceSearchResult.totalMatches === 0 || matches.length === 0);

    return (
      <Paper elevation={2} sx={{ p: 2.25, mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.5, gap: 1.5, flexWrap: 'wrap' }}>
          <Typography variant="h6">
            Trace / Text Search
          </Typography>
          <Chip label="Case-insensitive" variant="outlined" size="small" />
        </Box>

        <Box sx={{ p: 1.5, mb: 2, border: '1px solid', borderColor: 'divider', borderRadius: 1.5, bgcolor: 'rgba(15, 23, 42, 0.02)' }}>
          <Grid container spacing={1.25} alignItems="center">
            <Grid item xs={12} md={4}>
              <TextField
                label="Trace ID or text"
                value={traceIdQuery}
                onChange={(e) => setTraceIdQuery(e.target.value)}
                placeholder="trace-abc123 or process 38"
                fullWidth
                size="small"
              />
            </Grid>
            <Grid item xs={12} md={3}>
              <TextField
                label="Pod (optional)"
                value={tracePodNameQuery}
                onChange={(e) => setTracePodNameQuery(e.target.value)}
                fullWidth
                size="small"
              />
            </Grid>
            <Grid item xs={12} md={2}>
              <TextField
                label="Line limit"
                value={traceLineLimitQuery}
                onChange={(e) => setTraceLineLimitQuery(e.target.value)}
                type="number"
                fullWidth
                size="small"
                inputProps={{ min: 10, max: 1000 }}
              />
            </Grid>
            <Grid item xs={12} md={3}>
              <Box sx={{ display: 'flex', gap: 1 }}>
                <Button
                  variant="contained"
                  onClick={runTraceSearch}
                  disabled={traceSearchLoading}
                  sx={{ minHeight: 40, fontSize: '1.05rem', fontWeight: 700 }}
                >
                  {traceSearchLoading ? <CircularProgress size={18} /> : 'Search'}
                </Button>
                <Button
                  variant="outlined"
                  onClick={() => {
                    setTraceSearchResult(null);
                    setTraceIdQuery('');
                  }}
                  sx={{ minHeight: 40, fontSize: '1rem', fontWeight: 700 }}
                >
                  Clear
                </Button>
              </Box>
            </Grid>
          </Grid>
        </Box>

        {traceSearchResult && (
          <Box>
            <Box sx={{ mb: 1 }}>
              <Typography variant="subtitle2">
                Query: {traceSearchResult.traceId || 'N/A'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Namespace: {traceSearchResult.namespace || '-'} | Searched pods: {traceSearchResult.searchedPods}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Total matches: {traceSearchResult.totalMatches}
              </Typography>
            </Box>

            {hasNoMatchResult && (
              <Alert severity="info" sx={{ mb: 1 }}>
                No matches found. Try a different text, larger line limit, or broader pod scope.
              </Alert>
            )}

            {matches.map((match, idx) => (
              <Paper key={idx} variant="outlined" sx={{ p: 1.5, mb: 1.5, borderRadius: 1.5 }}>
                <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                  Pod: <strong>{match.podName || `Pod ${idx + 1}`}</strong>
                </Typography>
                <Box sx={{ bgcolor: '#0f172a', color: '#f8fafc', p: 1.5, borderRadius: 1, maxHeight: 220, overflow: 'auto' }}>
                  <Typography component="pre" variant="body2" sx={{ fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace', whiteSpace: 'pre-wrap', margin: 0 }}>
                    {(match.lines || []).join('\n') || 'No matched lines'}
                  </Typography>
                </Box>
              </Paper>
            ))}
          </Box>
        )}
      </Paper>
    );
  };

  const renderToolResultPanel = () => {
    if (!lastToolResult) {
      return null;
    }

        const { action, payload } = lastToolResult;
        const isPodList = action === 'list_pods' || isPodListPayload(payload);
        const isPodLog = action === 'pod_logs' || isPodLogPayload(payload);

    return (
      <Paper elevation={2} sx={{ p: 2, mb: 3 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Assistant Tool Result: {action}
        </Typography>

        {isPodList && !isPodLog && (
          <List dense>
            {payload.length === 0 ? (
              <ListItem>
                <ListItemText primary="No pods returned." />
              </ListItem>
            ) : (
              payload.map((pod) => (
                <ListItem key={pod.name}>
                  <ListItemText
                    primary={pod.name}
                    secondary={`phase=${pod.phase || 'unknown'} | ready=${pod.ready ? 'true' : 'false'} | restarts=${pod.restarts ?? 0}`}
                  />
                </ListItem>
              ))
            )}
          </List>
        )}

        {isPodLog && !isPodList && (
          <Box sx={{ maxHeight: 320, overflowY: 'auto', bgcolor: '#0f172a', color: '#f8fafc', p: 2, borderRadius: 1 }}>
            <Typography variant="caption" color="#e2e8f0" sx={{ mb: 1, display: 'block' }}>
              Last lines from pod logs
            </Typography>
            <Typography component="pre" variant="body2" sx={{ m: 0, whiteSpace: 'pre-wrap', fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace' }}>
              {payload.length > 0 ? payload.join('\\n') : 'No logs returned.'}
            </Typography>
          </Box>
        )}

        {!isPodList && !isPodLog && (
          <Typography component="pre" sx={{ m: 0, whiteSpace: 'pre-wrap', fontSize: '0.95rem' }}>
            {typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2)}
          </Typography>
        )}
      </Paper>
    );
  };

  const handleEvidenceClick = (ref) => {
    // Parse evidence ref format: "pod/name", "deployment/name", "service/name", "event/reason:name"
    const [type, name] = ref.split('/');
    const sanitizedName = name?.replace(/[^a-zA-Z0-9-]/g, '-');
    const elementId = `${type}-${sanitizedName}`;
    
    const element = document.getElementById(elementId);
    if (element) {
      element.scrollIntoView({ behavior: 'smooth', block: 'center' });
      // Highlight briefly
      element.style.backgroundColor = 'rgba(33, 150, 243, 0.2)';
      setTimeout(() => {
        element.style.backgroundColor = '';
      }, 2000);
    } else {
      addConsoleMessage(`📍 Cannot find ${ref} in current view`, 'info');
    }
  };

  const renderConfigurationForm = () => (
    <Box sx={{ mb: 3 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>
        Kubernetes Configuration
      </Typography>

      {/* ⭐ Demo Scenario Buttons */}
      <Box sx={{ mb: 3 }}>
        {/* Workload Scenarios */}
        <Typography 
          variant="subtitle2" 
          sx={{ 
            mb: 1.5, 
            fontWeight: 600,
            color: 'text.secondary',
            display: 'flex',
            alignItems: 'center',
            gap: 1
          }}
        >
          Demo Scenarios
          <Chip label="One-Click" size="small" color="primary" variant="outlined" sx={{ height: 22, fontSize: '0.9rem' }} />
        </Typography>
        
        <Box sx={{ display: 'flex', gap: 1.5, flexWrap: 'wrap', mb: 3 }}>
          {WORKLOAD_SCENARIOS.map((scenario) => (
            <Tooltip 
              key={scenario.id}
              title={
                <Box>
                  <Typography variant="caption" sx={{ fontWeight: 600, display: 'block', mb: 0.5 }}>
                    {scenario.description}
                  </Typography>
                  <Typography variant="caption" sx={{ fontSize: '0.95rem', fontStyle: 'italic', opacity: 0.9 }}>
                    {scenario.tooltip}
                  </Typography>
                </Box>
              }
              arrow
              placement="top"
            >
              <Button
                variant={activeScenario === scenario.id ? "contained" : "outlined"}
                size="medium"
                onClick={() => handleScenarioSelect(scenario)}
                disabled={loading}
                startIcon={activeScenario === scenario.id ? <CheckIcon /> : null}
                sx={{
                  fontWeight: 600,
                  textTransform: 'none',
                  borderWidth: 2,
                  '&:hover': {
                    borderWidth: 2
                  },
                  ...(activeScenario === scenario.id && {
                    boxShadow: '0 0 0 3px rgba(25, 118, 210, 0.2)'
                  })
                }}
              >
                {scenario.label}
              </Button>
            </Tooltip>
          ))}
          
          {/* Custom Mode Button */}
          <Tooltip 
            title="Edit fields manually to customize your query"
            arrow
            placement="top"
          >
            <Button
              variant={isCustomMode ? "contained" : "outlined"}
              size="medium"
              onClick={() => {
                setIsCustomMode(true);
                setActiveScenario(null);
              }}
              disabled={loading}
              color="secondary"
              sx={{
                fontWeight: 600,
                textTransform: 'none',
                borderWidth: 2,
                '&:hover': {
                  borderWidth: 2
                }
              }}
            >
              Custom
            </Button>
          </Tooltip>
        </Box>
        
        {/* Helper Text */}
        {activeScenario && (
          <Alert severity="info" sx={{ mt: 2, py: 0.75, '& .MuiAlert-message': { fontSize: '1.1rem', fontWeight: 600 } }}>
            Demo scenario loaded: <strong>
              {WORKLOAD_SCENARIOS.find(s => s.id === activeScenario)?.label || 
               GUARDRAIL_SCENARIOS.find(s => s.id === activeScenario)?.label}
            </strong>
          </Alert>
        )}
      </Box>

      <Grid container spacing={2} alignItems="flex-end">
        <Grid item xs={12} md={4}>
          <TextField
            label="Namespace"
            placeholder="default"
            value={namespace}
            onChange={(e) => handleManualFieldChange('namespace', e.target.value)}
            onFocus={(e) => e.target.select()}
            fullWidth
            required
            helperText="Kubernetes namespace to monitor"
            disabled={!isCustomMode && activeScenario !== null}
            sx={configFieldSx}
          />
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField
            label="Label Selector (e.g. app=cart-app)"
            value={selector}
            onChange={(e) => handleManualFieldChange('selector', e.target.value)}
            onFocus={(e) => e.target.select()}
            fullWidth
            helperText="Filter by label (optional)"
            error={selector && !selector.includes('=')}
            disabled={!isCustomMode && activeScenario !== null}
            sx={configFieldSx}
          />
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField
            label="Release (optional)"
            value={release}
            onChange={(e) => handleManualFieldChange('release', e.target.value)}
            onFocus={(e) => e.target.select()}
            fullWidth
            helperText="Helm release name"
            disabled={!isCustomMode && activeScenario !== null}
            sx={configFieldSx}
          />
        </Grid>
        <Grid item xs={12} md={2}>
          <Button
            variant="contained"
            fullWidth
            onClick={loadDeploymentSummary}
            disabled={loading || !namespace}
            startIcon={loading ? <CircularProgress size={20} /> : <RefreshIcon />}
            sx={{ minHeight: 52, fontSize: '1.08rem', fontWeight: 800 }}
          >
            {loading ? 'Loading...' : 'Load'}
          </Button>
        </Grid>
      </Grid>
    </Box>
  );

  return (
    <Container
      maxWidth={false}
      sx={{
        mt: 3,
        pb: 3,
        width: '100%',
        px: { xs: 2, sm: 2.5, md: 3 }
      }}
    >
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: {
            xs: '1fr',
            lg: 'minmax(540px, 1.2fr) minmax(0, 1.8fr)'
          },
          minHeight: { xs: 'auto', lg: 'calc(100vh - 250px)' },
          width: '100%',
          gap: 2,
          alignItems: 'stretch',
          minWidth: 0
        }}
      >
        <Box sx={{
          position: { lg: 'sticky' },
          top: { lg: 16 },
          minWidth: 0,
          alignSelf: 'stretch',
          height: { xs: 'auto', lg: 'calc(100vh - 250px)' }
        }}>
          <AiAssistantPanel
            tool="deployment-doctor"
            context={summary}
            currentAction={currentAction}
            onToolResult={handleAiActionResult}
            fitHeight={true}
          />
        </Box>

        <Paper
          elevation={2}
          sx={{
            minWidth: 0,
            minHeight: 0,
            height: { xs: 'auto', lg: 'calc(100vh - 250px)' },
            display: 'flex',
            flexDirection: 'column',
            '& .MuiTypography-h5': { fontSize: '2.1rem', fontWeight: 800 },
            '& .MuiTypography-h6': { fontSize: '1.6rem', fontWeight: 800 },
            '& .MuiTypography-subtitle2': { fontSize: '1.2rem', fontWeight: 700 },
            '& .MuiTypography-body1': { fontSize: '1.22rem', lineHeight: 1.55 },
            '& .MuiTypography-body2': { fontSize: '1.12rem', lineHeight: 1.5 },
            '& .MuiTypography-caption': { fontSize: '1.04rem', lineHeight: 1.45 },
            '& .MuiTableCell-root': { fontSize: '1.05rem' },
            '& .MuiButton-root': { fontSize: '1.08rem', fontWeight: 700 },
            '& .MuiChip-label': { fontSize: '1rem', fontWeight: 700 },
            '& .MuiInputBase-input': { fontSize: '1.3rem', fontWeight: 600 },
            '& .MuiInputLabel-root': { fontSize: '1.2rem', fontWeight: 800 },
            '& .MuiFormHelperText-root': { fontSize: '1.08rem', fontWeight: 600 },
            '& .MuiAlert-message': { fontSize: '1.08rem', fontWeight: 600 },
            '& .MuiAccordionSummary-content .MuiTypography-root': { fontSize: '1.12rem', fontWeight: 700 }
          }}
        >
          <Box sx={{ p: 3, flex: 1, minHeight: 0, overflowY: 'auto' }}>
            <Typography variant="h5" sx={{ mb: 3 }}>
              Kubernetes Deployment Doctor
            </Typography>

            {renderConfigurationForm()}

            {error && (
              <Alert severity="error" sx={{ mb: 2, '& .MuiAlert-message': { fontSize: '1.1rem', fontWeight: 600 } }}>
                {error}
              </Alert>
            )}

            {!summary && !loading && !error && (
              <Alert severity="info" sx={{ '& .MuiAlert-message': { fontSize: '1.1rem', fontWeight: 600 } }}>
                Enter a namespace and click Load to fetch deployment information.
              </Alert>
            )}

            {loading && (
              <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '40vh' }}>
                <Box sx={{ textAlign: 'center' }}>
                  <CircularProgress size={60} />
                  <Typography variant="h6" sx={{ mt: 2 }}>
                    Loading Deployment Summary...
                  </Typography>
                </Box>
              </Box>
            )}

            {!loading && (
              <>
                {renderQuickActions()}
                {renderVersionPanel()}
                {renderPodLogPanel()}
                {renderTraceSearchPanel()}
                {renderToolResultPanel()}
              </>
            )}

            {summary && !loading && (
              <Box>

          {/* Summary Cards - ⭐ IMPROVEMENT 4: De-emphasize when diagnosis exists */}
          {(() => {
            // Helper to rank severity
            const severityRank = (s) => {
              switch ((s || '').toUpperCase()) {
                case 'HIGH': return 3;
                case 'MED':  return 2;
                case 'INFO': return 1;
                default:     return 0;
              }
            };

            // Determine if there's a primary finding
            let hasPrimaryFinding = summary.primaryFailure;
            if (!hasPrimaryFinding && summary.findings && summary.findings.length > 0) {
              hasPrimaryFinding = summary.findings
                .slice()
                .sort((a, b) => severityRank(b.severity) - severityRank(a.severity))[0];
            }

            return (
          <Grid container spacing={3} sx={{ 
            mb: 3,
            ...(hasPrimaryFinding && {
              opacity: 0.7,
              filter: 'grayscale(0.3)'
            })
          }}>
            <Grid item xs={12} md={3}>
              <Card>
                <CardContent>
                  <Typography color="textSecondary" gutterBottom>
                    Overall
                  </Typography>
                  {(() => {
                    const overall = summary?.health?.overall;
                    const overallChip = (() => {
                      switch ((overall || '').toUpperCase()) {
                        case 'FAIL':
                          return { label: 'FAIL', color: 'error', icon: <ErrorIcon /> };
                        case 'WARN':
                          return { label: 'WARN', color: 'warning', icon: <WarningIcon /> };
                        case 'PASS':
                          return { label: 'PASS', color: 'success', icon: <CheckCircleIcon /> };
                        case 'UNKNOWN':
                          return { label: 'UNKNOWN', color: 'default', icon: <HelpOutlineIcon /> };
                        default:
                          return { label: 'PASS', color: 'success', icon: <CheckCircleIcon /> };
                      }
                    })();
                    
                    return (
                      <>
                        <Chip
                          label={overallChip.label}
                          color={overallChip.color}
                          icon={overallChip.icon}
                          sx={{ 
                            fontWeight: 'bold', 
                            fontSize: '1.1rem',
                            ...(overallChip.color === 'default' && {
                              border: '2px dashed',
                              backgroundColor: 'transparent'
                            })
                          }}
                        />
                        
                        {/* Explanatory microcopy for UNKNOWN status */}
                        {overall === 'UNKNOWN' && (
                          <Typography 
                            variant="caption" 
                            color="text.secondary" 
                            sx={{ 
                              display: 'block', 
                              mt: 1, 
                              fontSize: '0.95rem',
                              lineHeight: 1.3
                            }}
                          >
                            No matching resources found. Check your selector.
                          </Typography>
                        )}
                      </>
                    );
                  })()}
                </CardContent>
              </Card>
            </Grid>
            
            <Grid item xs={12} md={3}>
              <Card>
                <CardContent>
                  <Typography color="textSecondary" gutterBottom>
                    Deployments Ready
                  </Typography>
                  <Typography variant="h4">
                    {getHealthFromSummary().deploymentsReady}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            
            <Grid item xs={12} md={3}>
              <Card>
                <CardContent>
                  <Typography color="textSecondary" gutterBottom>
                    Pods Running
                  </Typography>
                  <Typography variant="h4" color="success.main">
                    {getHealthFromSummary().pods.running}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            
            <Grid item xs={12} md={3}>
              <Card>
                <CardContent>
                  <Typography color="textSecondary" gutterBottom>
                    CrashLoop
                  </Typography>
                  <Typography 
                    variant="h4" 
                    color={getHealthFromSummary().pods.crashLoop > 0 ? "error.main" : "text.primary"}
                  >
                    {getHealthFromSummary().pods.crashLoop}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
            );
          })()}

          {/* ⭐ IMPROVEMENT 2: Primary Failure/Warning Panel - Data-driven by highest severity */}
          {(() => {
            // Helper to rank severity
            const severityRank = (s) => {
              switch ((s || '').toUpperCase()) {
                case 'HIGH': return 3;
                case 'MED':  return 2;
                case 'INFO': return 1;
                default:     return 0;
              }
            };

            // Use backend primaryFailure if available, otherwise compute from findings
            let primaryFinding = summary.primaryFailure;
            if (!primaryFinding && summary.findings && summary.findings.length > 0) {
              primaryFinding = summary.findings
                .slice()
                .sort((a, b) => severityRank(b.severity) - severityRank(a.severity))[0];
            }

            if (!primaryFinding) return null;

            return (
            <Paper 
              elevation={4} 
              sx={{ 
                p: 3, 
                mb: 3, 
                border: '3px solid',
                borderColor: getSeverityColor(primaryFinding.severity) + '.main',
                bgcolor: getSeverityColor(primaryFinding.severity) + '.50',
                background: `linear-gradient(135deg, ${getSeverityColor(primaryFinding.severity) === 'error' ? 'rgba(211, 47, 47, 0.08)' : getSeverityColor(primaryFinding.severity) === 'warning' ? 'rgba(237, 108, 2, 0.08)' : 'rgba(2, 136, 209, 0.08)'} 0%, rgba(255, 255, 255, 0) 100%)`
              }}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                <Typography 
                  variant="overline" 
                  sx={{ 
                    fontWeight: 700, 
                    fontSize: '1rem',
                    letterSpacing: '0.1em',
                    color: getSeverityColor(primaryFinding.severity) + '.dark'
                  }}
                >
                  {/* ⭐ FIX 1: Different labels based on severity */}
                  {primaryFinding.severity === 'HIGH' 
                    ? '🎯 PRIMARY ROOT CAUSE'
                    : primaryFinding.severity === 'MED'
                    ? '⚠️ TOP WARNING'
                    : 'ℹ️ NOTABLE SIGNAL'}
                </Typography>
              </Box>

              <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2, mb: 2 }}>
                <Box sx={{ 
                  color: getSeverityColor(primaryFinding.severity) + '.main',
                  fontSize: '2rem',
                  lineHeight: 1
                }}>
                  {getSeverityIcon(primaryFinding.severity)}
                </Box>
                <Box sx={{ flex: 1 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1, flexWrap: 'wrap' }}>
                    <Typography variant="h5" sx={{ fontWeight: 700, color: 'text.primary' }}>
                      {primaryFinding.title}
                    </Typography>
                    <Chip 
                      label={primaryFinding.code} 
                      size="small"
                      sx={{ 
                        fontFamily: 'monospace', 
                        fontWeight: 600,
                        bgcolor: getSeverityColor(primaryFinding.severity) + '.main',
                        color: 'white'
                      }}
                    />
                    {primaryFinding.owner && getOwnerBadge(primaryFinding.owner)}
                  </Box>
                  
                  <Typography variant="body1" sx={{ mb: 2, color: 'text.primary', lineHeight: 1.6 }}>
                    {primaryFinding.explanation}
                  </Typography>

                  {/* ⭐ IMPROVEMENT 5: Evidence linkage with visual hierarchy */}
                  {primaryFinding.evidence && primaryFinding.evidence.length > 0 && (
                    <Box sx={{ 
                      mb: 2, 
                      p: 2, 
                      bgcolor: 'rgba(255, 255, 255, 0.7)',
                      borderRadius: 1,
                      border: '1px solid',
                      borderColor: 'divider'
                    }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1, display: 'flex', alignItems: 'center', gap: 0.5 }}>
                        📋 Evidence
                      </Typography>
                      <List dense sx={{ pl: 2 }}>
                        {primaryFinding.evidence.map((ev, idx) => (
                          <ListItem key={idx} sx={{ pl: 0, py: 0.5 }}>
                            <Typography variant="body2" component="div">
                              <Box component="span" sx={{ color: 'primary.main', fontWeight: 600 }}>
                                {ev.kind}:
                              </Box>{' '}
                              <Box component="span" sx={{ fontFamily: 'monospace', fontSize: '1rem' }}>
                                {ev.name}
                              </Box>
                              {ev.message && (
                                <Box component="div" sx={{ 
                                  ml: 2, 
                                  mt: 0.5, 
                                  color: 'text.secondary', 
                                  fontSize: '0.98rem',
                                  fontStyle: 'italic'
                                }}>
                                  {ev.message}
                                </Box>
                              )}
                            </Typography>
                          </ListItem>
                        ))}
                      </List>
                    </Box>
                  )}

                  {/* Next Steps */}
                  {primaryFinding.nextSteps && primaryFinding.nextSteps.length > 0 && (
                    <Box sx={{ 
                      p: 2, 
                      bgcolor: 'info.50',
                      borderRadius: 1,
                      borderLeft: '4px solid',
                      borderColor: 'info.main'
                    }}>
                      <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>
                        💡 Next Steps
                      </Typography>
                      <Box component="ol" sx={{ mt: 0, mb: 0, pl: 2.5 }}>
                        {primaryFinding.nextSteps.map((step, idx) => (
                          <Typography 
                            key={idx} 
                            component="li" 
                            variant="body2"
                            sx={{ mb: 0.75, lineHeight: 1.5 }}
                          >
                            {step}
                          </Typography>
                        ))}
                      </Box>
                    </Box>
                  )}
                </Box>
              </Box>
            </Paper>
            );
          })()}

          {/* All Findings Section - Show even when Overall = PASS if there are WARN findings */}
          {(() => {
            // ⭐ POLISH 1: Filter out primary failure to avoid duplication
            // Helper to rank severity
            const severityRank = (s) => {
              switch ((s || '').toUpperCase()) {
                case 'HIGH': return 3;
                case 'MED':  return 2;
                case 'INFO': return 1;
                default:     return 0;
              }
            };

            // Determine what the primary finding is (same logic as above)
            let primaryFinding = summary.primaryFailure;
            if (!primaryFinding && summary.findings && summary.findings.length > 0) {
              primaryFinding = summary.findings
                .slice()
                .sort((a, b) => severityRank(b.severity) - severityRank(a.severity))[0];
            }

            const additionalFindings = summary.findings?.filter(
              f => !primaryFinding || f.code !== primaryFinding.code
            ) || [];
            
            // Only show section if there are findings OR if we want to show a message
            if (summary.findings?.length === 0) return null;
            
            return (
              <Paper elevation={2} sx={{ p: 2, mb: 3 }}>
                <Typography variant="h6" sx={{ mb: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
                  {primaryFinding ? 'Additional Findings' : 'Findings'}
                </Typography>
                
                {additionalFindings.length === 0 && primaryFinding ? (
                  // ⭐ POLISH 1: Show subtle message when no additional findings
                  <Typography 
                    variant="body2" 
                    color="text.secondary" 
                    sx={{ fontStyle: 'italic', textAlign: 'center', py: 2 }}
                  >
                    No additional findings detected.
                  </Typography>
                ) : (
                  additionalFindings.map((f, idx) => (
                <Alert 
                  key={idx} 
                  severity={getSeverityColor(f.severity)}
                  icon={getSeverityIcon(f.severity)}
                  sx={{ mb: 1.5 }}
                >
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5, flexWrap: 'wrap' }}>
                    <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
                      {f.title || f.code}
                    </Typography>
                    <Chip 
                      label={f.code} 
                      size="small" 
                      sx={{ 
                        fontFamily: 'monospace', 
                        height: '24px',
                        fontSize: '0.9rem'
                      }} 
                    />
                    {/* ⭐ IMPROVEMENT 3: Owner badge on each finding */}
                    {f.owner && getOwnerBadge(f.owner)}
                  </Box>
                  
                  <Typography variant="body2" sx={{ mb: 1 }}>
                    {f.explanation || f.message}
                  </Typography>
                  
                  {/* ⭐ IMPROVEMENT 5: Evidence with visual hierarchy */}
                  {f.evidence && f.evidence.length > 0 && (
                    <Box sx={{ 
                      mt: 1, 
                      p: 1.5, 
                      bgcolor: 'rgba(0, 0, 0, 0.03)', 
                      borderRadius: 1,
                      borderLeft: '3px solid',
                      borderColor: getSeverityColor(f.severity) + '.main'
                    }}>
                      <Typography variant="body2" sx={{ fontWeight: 600, mb: 0.5 }}>
                        📋 Evidence:
                      </Typography>
                      <List dense sx={{ pl: 1 }}>
                        {f.evidence.map((ev, evIdx) => (
                          <ListItem key={evIdx} sx={{ py: 0.25, pl: 0 }}>
                            <Typography variant="body2" component="div" sx={{ fontSize: '1.02rem' }}>
                              <Box component="span" sx={{ color: 'primary.main', fontWeight: 600 }}>
                                {ev.kind}:
                              </Box>{' '}
                              <Box component="span" sx={{ fontFamily: 'monospace' }}>
                                {ev.name}
                              </Box>
                              {ev.message && (
                                <Box component="div" sx={{ 
                                  ml: 2, 
                                  mt: 0.25, 
                                  color: 'text.secondary', 
                                  fontSize: '0.95rem',
                                  fontStyle: 'italic'
                                }}>
                                  {ev.message}
                                </Box>
                              )}
                            </Typography>
                          </ListItem>
                        ))}
                      </List>
                    </Box>
                  )}

                  {/* Legacy evidenceRefs support (for backward compatibility) */}
                  {!f.evidence && Array.isArray(f.evidenceRefs) && f.evidenceRefs.length > 0 && (
                    <Box sx={{ mt: 1 }}>
                      <Typography variant="body2" sx={{ fontSize: '1.02rem', color: 'text.secondary' }}>
                        Evidence:{' '}
                        {f.evidenceRefs.map((ref, refIdx) => (
                          <span key={refIdx}>
                            {refIdx > 0 && ', '}
                            <Typography
                              component="span"
                              sx={{
                                fontFamily: 'monospace',
                                fontSize: '1.02rem',
                                color: 'primary.main',
                                cursor: 'pointer',
                                textDecoration: 'underline',
                                '&:hover': {
                                  color: 'primary.dark',
                                  textDecoration: 'underline'
                                }
                              }}
                              onClick={() => handleEvidenceClick(ref)}
                            >
                              {ref}
                            </Typography>
                          </span>
                        ))}
                      </Typography>
                    </Box>
                  )}
                  
                  {/* Next steps */}
                  {f.nextSteps && f.nextSteps.length > 0 && (
                    <Box 
                      sx={{ 
                        mt: 1.5, 
                        p: 1.5, 
                        bgcolor: 'rgba(33, 150, 243, 0.08)', 
                        borderRadius: 1,
                        borderLeft: '3px solid',
                        borderColor: 'info.main'
                      }}
                    >
                      <Typography variant="body2" sx={{ fontWeight: 'bold', mb: 0.5 }}>
                        💡 Next Steps:
                      </Typography>
                      <Box component="ol" sx={{ mt: 0, mb: 0, pl: 2.5 }}>
                        {f.nextSteps.map((step, stepIdx) => (
                          <Typography 
                            key={stepIdx} 
                            component="li" 
                            variant="body2"
                            sx={{ mb: 0.5, fontSize: '1.02rem' }}
                          >
                            {step}
                          </Typography>
                        ))}
                      </Box>
                    </Box>
                  )}

                  {/* Legacy hints support (for backward compatibility) */}
                  {!f.nextSteps && Array.isArray(f.hints) && f.hints.length > 0 && (
                    <Box 
                      sx={{ 
                        mt: 1, 
                        p: 1, 
                        bgcolor: 'rgba(33, 150, 243, 0.08)', 
                        borderRadius: 1,
                        borderLeft: '3px solid',
                        borderColor: 'info.main'
                      }}
                    >
                      <Typography variant="body2" sx={{ fontWeight: 'bold', mb: 0.5 }}>
                        💡 Common causes:
                      </Typography>
                      <Box component="ul" sx={{ mt: 0, mb: 0, pl: 2.5 }}>
                        {f.hints.map((hint, hintIdx) => (
                          <Typography 
                            key={hintIdx} 
                            component="li" 
                            variant="body2"
                            sx={{ fontStyle: 'italic', mb: 0.5 }}
                          >
                            {hint}
                          </Typography>
                        ))}
                      </Box>
                    </Box>
                  )}
                </Alert>
              ))
            )}
          </Paper>
        );
      })()}

          {/* Deployments Section */}
          {summary.objects?.deployments?.length > 0 && (
            <Paper elevation={2} sx={{ p: 2, mb: 3 }}>
              <Typography variant="h6" sx={{ mb: 2 }}>
                Deployments
              </Typography>
              {summary.objects.deployments.map((d, idx) => (
                <Accordion 
                  key={idx}
                  id={`deployment-${d.name?.replace(/[^a-zA-Z0-9-]/g, '-')}`}
                >
                  <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, width: '100%' }}>
                      <Typography sx={{ fontWeight: 'bold' }}>{d.name}</Typography>
                      <Chip label={d.kind} size="small" />
                      <Chip label={d.ready} size="small" sx={{ ml: 'auto' }} />
                    </Box>
                  </AccordionSummary>
                  <AccordionDetails>
                    <Typography variant="subtitle2" color="textSecondary" sx={{ mb: 1 }}>
                      Conditions
                    </Typography>
                    <List dense>
                      {(d.conditions || []).map((c, i) => (
                        <ListItem key={i}>
                          <ListItemText primary={c} />
                        </ListItem>
                      ))}
                    </List>
                  </AccordionDetails>
                </Accordion>
              ))}
            </Paper>
          )}

          {/* Pods Section */}
          {summary.objects?.pods?.length > 0 && (
            <Paper elevation={2} sx={{ p: 2, mb: 3 }}>
              <Typography variant="h6" sx={{ mb: 2 }}>
                Pods
              </Typography>
              {summary.objects.pods.map((p, idx) => (
                <Accordion 
                  key={idx}
                  id={`pod-${p.name?.replace(/[^a-zA-Z0-9-]/g, '-')}`}
                >
                  <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, width: '100%' }}>
                      <Typography sx={{ fontWeight: 'bold' }}>{p.name}</Typography>
                      <Chip label={p.phase || 'Unknown'} size="small" />
                      {p.reason && <Chip label={p.reason} size="small" color="warning" />}
                      <Chip
                        label={p.ready ? 'Ready' : 'Not Ready'}
                        size="small"
                        color={p.ready ? 'success' : 'error'}
                        sx={{ ml: 'auto' }}
                      />
                    </Box>
                  </AccordionSummary>
                  <AccordionDetails>
                    <Grid container spacing={2}>
                      <Grid item xs={12} md={6}>
                        <Typography variant="subtitle2" color="textSecondary">
                          Restarts
                        </Typography>
                        <Typography variant="body1">{p.restarts}</Typography>
                      </Grid>
                      <Grid item xs={12} md={6}>
                        <Typography variant="subtitle2" color="textSecondary">
                          Reason
                        </Typography>
                        <Typography variant="body1">{p.reason || '-'}</Typography>
                      </Grid>
                    </Grid>
                  </AccordionDetails>
                </Accordion>
              ))}
            </Paper>
          )}

          {/* Events Section */}
          {summary.objects?.events?.length > 0 && (
            <Paper elevation={2} sx={{ p: 2, mb: 3 }}>
              <Typography variant="h6" sx={{ mb: 2 }}>
                Events
              </Typography>
              <List dense>
                {summary.objects.events.map((e, idx) => (
                  <ListItem key={idx} alignItems="flex-start">
                    <ListItemText
                      primary={`${e.type || '-'} / ${e.reason || '-'} — ${e.involvedObjectKind || '-'} ${e.involvedObjectName || '-'}`}
                      secondary={
                        <Box sx={{ mt: 0.5 }}>
                          <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>
                            {e.timestamp || '-'}
                          </Typography>
                          <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                            {e.message || '-'}
                          </Typography>
                        </Box>
                      }
                    />
                  </ListItem>
                ))}
              </List>
            </Paper>
          )}

          {/* Services Section */}
          {summary.objects?.services?.length > 0 && (
            <Paper elevation={2} sx={{ p: 2, mb: 3 }}>
              <Typography variant="h6" sx={{ mb: 2 }}>
                Services
              </Typography>
              {summary.objects.services.map((s, idx) => {
                const eps = summary.objects.endpoints?.find((x) => x.serviceName === s.name);
                return (
                  <Accordion 
                    key={idx}
                    id={`service-${s.name?.replace(/[^a-zA-Z0-9-]/g, '-')}`}
                  >
                    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, width: '100%' }}>
                        <Typography sx={{ fontWeight: 'bold' }}>{s.name}</Typography>
                        <Chip label={s.type || 'Service'} size="small" />
                        {eps && (
                          <Chip
                            label={`Endpoints: ${eps.readyAddresses} ready / ${eps.notReadyAddresses} notReady`}
                            size="small"
                            color={eps.readyAddresses > 0 ? 'success' : 'error'}
                            sx={{ ml: 'auto' }}
                          />
                        )}
                      </Box>
                    </AccordionSummary>
                    <AccordionDetails>
                      <Typography variant="subtitle2" color="textSecondary">
                        Selector
                      </Typography>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', mb: 1 }}>
                        {Object.keys(s.selector || {}).length
                          ? Object.entries(s.selector).map(([k, v]) => `${k}=${v}`).join(', ')
                          : '-'}
                      </Typography>

                      <Typography variant="subtitle2" color="textSecondary">
                        Ports
                      </Typography>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
                        {(s.ports || []).join(', ') || '-'}
                      </Typography>
                    </AccordionDetails>
                  </Accordion>
                );
              })}
            </Paper>
          )}

          {summary && !summary.objects?.deployments?.length && !summary.objects?.pods?.length && (
            <Alert severity="info" sx={{ '& .MuiAlert-message': { fontSize: '1.1rem', fontWeight: 600 } }}>
              No deployments or pods found in namespace <strong>{namespace}</strong>. 
              {selector && ` with selector: ${selector}`}
              {release && ` for release: ${release}`}
            </Alert>
          )}
        </Box>
            )}
          </Box>
        </Paper>
      </Box>
    </Container>
  );
}

export default DeploymentDoctorPage;
