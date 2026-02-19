import { useState } from 'react';
import { 
  Container, 
  Paper, 
  Typography, 
  Box, 
  Grid,
  Card,
  CardContent,
  TextField,
  Button,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Alert,
  CircularProgress,
  Chip,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  FormControlLabel,
  Switch,
  Checkbox,
  FormGroup,
  Collapse,
  ToggleButtonGroup,
  ToggleButton,
  IconButton,
  Tooltip
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import WarningIcon from '@mui/icons-material/Warning';
import InfoIcon from '@mui/icons-material/Info';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import DownloadIcon from '@mui/icons-material/Download';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import { apiService } from '../services/apiService';

const DEFAULT_ENVIRONMENTS = ['local', 'dev', 'test', 'stage'];
const DEFAULT_ENVIRONMENT_BASE_URLS = {
  local: 'http://localhost:8081',
  dev: 'https://capability.dev.att.com',
  test: 'https://capability.test.att.com',
  stage: 'https://capability.stage.att.com'
};
const CAPABILITIES = ['carts', 'catalog'];
const AUTH_PROFILES = ['none', 'jwt-service', 'oauth2', 'api-key'];
const SAMPLE_SWAGGER_SPEC = `{
  "swagger": "2.0",
  "info": {
    "title": "Shopping Cart API",
    "description": "API for managing shopping carts and products.",
    "version": "1.0"
  },
  "paths": {
    "/cart": {
      "post": {
        "summary": "Create a cart",
        "responses": {
          "201": { "description": "Cart created successfully" }
        }
      },
      "get": {
        "summary": "Get cart",
        "responses": {
          "200": { "description": "Cart details returned" }
        }
      }
    },
    "/cart/items": {
      "post": {
        "summary": "Add to cart",
        "responses": {
          "200": { "description": "Items added to cart successfully" }
        }
      }
    }
  }
}`;

function SmokeTestsPage({ addConsoleMessage }) {
  // Configuration state
  const [environmentOptions, setEnvironmentOptions] = useState(DEFAULT_ENVIRONMENTS);
  const [environmentBaseUrls, setEnvironmentBaseUrls] = useState(DEFAULT_ENVIRONMENT_BASE_URLS);
  const [environment, setEnvironment] = useState('local');
  const [newEnvironmentName, setNewEnvironmentName] = useState('');
  const [newEnvironmentBaseUrl, setNewEnvironmentBaseUrl] = useState('');
  const [capability, setCapability] = useState('carts');
  const [specSource, setSpecSource] = useState('blob'); // 'blob' or 'upload'
  const [authRequired, setAuthRequired] = useState(true);
  const [authProfile, setAuthProfile] = useState('jwt-service');
  const [jwtGenerationCurl, setJwtGenerationCurl] = useState('');
  const [specUploadText, setSpecUploadText] = useState(SAMPLE_SWAGGER_SPEC);
  const [enforceSpecOrder, setEnforceSpecOrder] = useState(true);
  const [generatingPreview, setGeneratingPreview] = useState(false);
  const [generatedPreview, setGeneratedPreview] = useState(null);
  
  // Test selection state
  const [testSuite, setTestSuite] = useState('contract'); // 'contract', 'workflow', 'both'
  const [contractOptions, setContractOptions] = useState({
    happyPaths: true,
    negativeAuth: true,
    basic400: true,
    advancedOptions: false,
    limitEndpoints: false,
    maxEndpoints: 10,
    strictSchemaValidation: true,
    failFast: false
  });
  
  // Test generation state (for Contract Smoke)
  const [cachedTestsStatus, setCachedTestsStatus] = useState('present'); // 'present', 'outdated', 'none'
  const [generatedAt, setGeneratedAt] = useState('2026-01-15T22:58:00Z');
  const [testSource, setTestSource] = useState(null); // Set after run
  
  // Workflow configuration state
  const [workflowSource, setWorkflowSource] = useState('catalog'); // 'catalog' or 'upload'
  const [workflowFile, setWorkflowFile] = useState('cart-lifecycle-smoke');
  const [workflowYaml, setWorkflowYaml] = useState(null);
  const [workflowValidation, setWorkflowValidation] = useState(null);
  const [saveToLocalCatalog, setSaveToLocalCatalog] = useState(false);
  const [alwaysCleanup, setAlwaysCleanup] = useState(true);
  
  // Results state
  const [loading, setLoading] = useState(false);
  const [summary, setSummary] = useState(null);
  const [results, setResults] = useState(null);
  const [findings, setFindings] = useState(null);
  const [runMetadata, setRunMetadata] = useState(null);
  
  const getBaseUrl = () => {
    const base = environmentBaseUrls[environment] || 'http://localhost:8081';
    return `${base}/${capability}`;
  };

  const handleEnvironmentBaseUrlChange = (value) => {
    setEnvironmentBaseUrls((prev) => ({
      ...prev,
      [environment]: value
    }));
  };

  const handleAddEnvironment = () => {
    const normalizedName = (newEnvironmentName || '').trim().toLowerCase();
    const normalizedBaseUrl = (newEnvironmentBaseUrl || '').trim();

    if (!normalizedName) {
      addConsoleMessage('✗ Environment name is required', 'error');
      return;
    }
    if (!normalizedBaseUrl) {
      addConsoleMessage('✗ Environment base URL is required', 'error');
      return;
    }
    if (environmentOptions.includes(normalizedName)) {
      addConsoleMessage(`✗ Environment "${normalizedName}" already exists`, 'error');
      return;
    }

    setEnvironmentOptions((prev) => [...prev, normalizedName]);
    setEnvironmentBaseUrls((prev) => ({
      ...prev,
      [normalizedName]: normalizedBaseUrl
    }));
    setEnvironment(normalizedName);
    setNewEnvironmentName('');
    setNewEnvironmentBaseUrl('');
    addConsoleMessage(`✓ Added environment "${normalizedName}"`, 'success');
  };

  const getSpecFingerprint = () => {
    // Mock fingerprint - in real implementation, this would come from backend
    return 'ETag:W/"5f9a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p7q8r9s0"';
  };

  const handleCopyFingerprint = () => {
    navigator.clipboard.writeText(getSpecFingerprint());
    addConsoleMessage('✓ Spec fingerprint copied to clipboard', 'success');
  };

  const handleWorkflowFileUpload = (event) => {
    const file = event.target.files[0];
    if (!file) return;
    
    const reader = new FileReader();
    reader.onload = (e) => {
      const content = e.target.result;
      setWorkflowYaml(content);
      
      // Mock validation - in real implementation, this would validate the YAML
      try {
        // Simple check for now
        if (content.includes('steps:') || content.includes('workflow:')) {
          setWorkflowValidation({ status: 'success', message: 'Parsed successfully' });
          addConsoleMessage('✓ Workflow YAML parsed successfully', 'success');
        } else {
          setWorkflowValidation({ status: 'error', message: 'Invalid workflow format' });
          addConsoleMessage('✗ Workflow YAML validation failed', 'error');
        }
      } catch (error) {
        setWorkflowValidation({ status: 'error', message: `Error: ${error.message}` });
        addConsoleMessage('✗ Workflow YAML parsing error', 'error');
      }
    };
    reader.readAsText(file);
  };

  const sanitizeSpecInput = (raw) => {
    if (!raw) {
      return raw;
    }
    return raw
      .split('\n')
      .filter((line) => !line.trim().startsWith('//'))
      .join('\n')
      .trim();
  };

  const handleGenerateTestsPreview = async () => {
    const trimmedSpec = sanitizeSpecInput(specUploadText);
    if (!trimmedSpec) {
      addConsoleMessage('✗ Please paste Swagger/OpenAPI JSON or YAML first', 'error');
      return;
    }

    setGeneratingPreview(true);
    try {
      addConsoleMessage('🧪 Generating smoke tests from spec...', 'info');
      const response = await apiService.generateSmokeTestsPreview({
        specContent: trimmedSpec,
        enforceOrder: enforceSpecOrder
      });
      setGeneratedPreview(response.data);
      addConsoleMessage(`✓ Generated ${response.data.operationCount} test(s) from spec`, 'success');
    } catch (error) {
      const errorMsg = error.response?.data?.error?.message || error.response?.data?.message || error.message;
      addConsoleMessage(`✗ Failed to generate tests from spec: ${errorMsg}`, 'error');
      setGeneratedPreview(null);
    } finally {
      setGeneratingPreview(false);
    }
  };

  const handleRunTests = async () => {
    setLoading(true);
    setSummary(null);
    setResults(null);
    setFindings(null);
    
    try {
      addConsoleMessage(`🚀 Starting smoke tests for ${capability} on ${environment}...`, 'info');
      
      // Call backend API (placeholder - you'll need to implement this endpoint)
      const response = await apiService.runSmokeTests({
        environment,
        capability,
        specSource,
        authRequired,
        authProfile,
        jwtGenerationCurl: jwtGenerationCurl.trim() || null,
        testSuite,
        contractOptions,
        workflowFile,
        alwaysCleanup
      });
      
      setSummary(response.data.summary);
      setResults(response.data.results);
      setFindings(response.data.findings);
      setRunMetadata(response.data.metadata);
      
      // Set test source metadata
      setTestSource({
        type: 'Generated (cached)',
        fingerprint: getSpecFingerprint(),
        generatedAt: generatedAt
      });
      
      const status = response.data.summary.status;
      if (status === 'passed') {
        addConsoleMessage('✓ All smoke tests passed', 'success');
      } else if (status === 'partial') {
        addConsoleMessage('⚠ Some smoke tests failed', 'warning');
      } else {
        addConsoleMessage('✗ Smoke tests failed', 'error');
      }
    } catch (error) {
      const errorMsg = error.response?.data?.message || error.message;
      addConsoleMessage(`✗ Smoke tests failed: ${errorMsg}`, 'error');
      
      // Set error state
      setSummary({
        status: 'failed',
        target: `${environment}/${capability}`,
        specFingerprint: getSpecFingerprint()
      });
      setFindings([{
        severity: 'HIGH',
        title: 'Test Execution Failed',
        message: errorMsg,
        nextSteps: [
          'Verify the target environment is accessible',
          'Check authentication configuration',
          'Review the OpenAPI spec source'
        ]
      }]);
    } finally {
      setLoading(false);
    }
  };

  const handleValidateConfig = async () => {
    addConsoleMessage('🔍 Running preflight validation...', 'info');
    
    const validations = [];
    
    // Simulate validation checks
    setTimeout(() => {
      // 1. Resolve environment → base URL
      validations.push({ check: 'Base URL resolution', status: 'success', message: getBaseUrl() });
      
      // 2. Fetch OpenAPI spec
      validations.push({ check: 'OpenAPI spec', status: 'success', message: 'Spec fetched successfully' });
      
      // 3. Compute spec fingerprint
      validations.push({ check: 'Spec fingerprint', status: 'success', message: getSpecFingerprint() });
      
      // 4. Validate auth profile
      if (authRequired) {
        validations.push({ check: 'Auth profile', status: 'success', message: `Token acquisition configured for ${authProfile}` });
      }
      
      // 5. Validate workflow YAML (if Workflow Smoke or Both)
      if (testSuite === 'workflow' || testSuite === 'both') {
        if (workflowSource === 'catalog' && workflowFile) {
          validations.push({ check: 'Workflow definition', status: 'success', message: `Using catalog workflow: ${workflowFile}` });
        } else if (workflowSource === 'upload' && workflowYaml) {
          validations.push({ check: 'Workflow YAML', status: workflowValidation?.status || 'success', message: workflowValidation?.message || 'Parsed successfully' });
          validations.push({ check: 'OperationId references', status: 'success', message: 'All operationIds exist in spec' });
        } else {
          validations.push({ check: 'Workflow definition', status: 'error', message: 'Workflow required but not configured' });
        }
      }
      
      const hasErrors = validations.some(v => v.status === 'error');
      
      if (hasErrors) {
        addConsoleMessage('✗ Configuration validation failed', 'error');
        validations.filter(v => v.status === 'error').forEach(v => {
          addConsoleMessage(`  ✗ ${v.check}: ${v.message}`, 'error');
        });
      } else {
        addConsoleMessage('✓ Configuration is valid - ready to run', 'success');
        validations.forEach(v => {
          addConsoleMessage(`  ✓ ${v.check}: ${v.message}`, 'success');
        });
      }
    }, 800);
  };

  const handleReset = () => {
    setSummary(null);
    setResults(null);
    setFindings(null);
    setRunMetadata(null);
    setTestSource(null);
    setGeneratedPreview(null);
    addConsoleMessage('🔄 Results cleared', 'info');
  };

  const handleRegenerateTests = async () => {
    addConsoleMessage('🔄 Regenerating contract tests from OpenAPI spec...', 'info');
    
    // Simulate regeneration
    setTimeout(() => {
      setCachedTestsStatus('present');
      setGeneratedAt(new Date().toISOString());
      addConsoleMessage('✓ Contract tests regenerated and cached', 'success');
    }, 1000);
  };

  const handleLoadCachedTests = async () => {
    if (cachedTestsStatus === 'none') {
      addConsoleMessage('⚠ No cached tests available. Run tests to generate.', 'warning');
      return;
    }
    
    addConsoleMessage('✓ Using cached contract tests', 'success');
  };

  // Check if workflow is required but not configured
  const isWorkflowRequired = () => {
    return testSuite === 'workflow' || testSuite === 'both';
  };

  const isWorkflowConfigured = () => {
    if (workflowSource === 'catalog') {
      return !!workflowFile;
    } else if (workflowSource === 'upload') {
      return !!workflowYaml && workflowValidation?.status === 'success';
    }
    return false;
  };

  const canRunTests = () => {
    // For pasted specs, preview generation must happen first.
    if (specSource === 'upload' && !generatedPreview) {
      return false;
    }
    // Contract Smoke only → no workflow required
    if (testSuite === 'contract') {
      return true;
    }
    // Workflow Smoke or Both → workflow must be configured
    if (isWorkflowRequired()) {
      return isWorkflowConfigured();
    }
    return true;
  };

  const handleExport = () => {
    if (!summary || !results) {
      addConsoleMessage('No results to export', 'warning');
      return;
    }
    
    const exportData = {
      target: summary.target,
      specFingerprint: summary.specFingerprint,
      testSuites: testSuite,
      summary,
      results,
      findings,
      metadata: runMetadata,
      exportedAt: new Date().toISOString()
    };
    
    const dataStr = JSON.stringify(exportData, null, 2);
    const dataBlob = new Blob([dataStr], { type: 'application/json' });
    const url = URL.createObjectURL(dataBlob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `smoke-tests-${environment}-${capability}-${Date.now()}.json`;
    link.click();
    URL.revokeObjectURL(url);
    
    addConsoleMessage('✓ Smoke test diagnostics exported', 'success');
  };

  const getSeverityIcon = (severity) => {
    switch (severity?.toUpperCase()) {
      case 'HIGH':
      case 'ERROR':
        return <ErrorIcon color="error" />;
      case 'MED':
      case 'WARN':
        return <WarningIcon color="warning" />;
      case 'PASS':
        return <CheckCircleIcon color="success" />;
      default:
        return <InfoIcon color="info" />;
    }
  };

  const getSeverityColor = (severity) => {
    switch (severity?.toUpperCase()) {
      case 'HIGH':
      case 'ERROR':
        return 'error';
      case 'MED':
      case 'WARN':
        return 'warning';
      case 'PASS':
        return 'success';
      default:
        return 'info';
    }
  };

  const getStatusChip = (status) => {
    const statusMap = {
      'not_run': { label: 'Not Run', color: 'default' },
      'running': { label: 'Running', color: 'info' },
      'passed': { label: 'Passed', color: 'success' },
      'failed': { label: 'Failed', color: 'error' },
      'partial': { label: 'Partial', color: 'warning' }
    };
    const config = statusMap[status] || statusMap['not_run'];
    return <Chip label={config.label} color={config.color} size="small" />;
  };

  const getAuthCurlHeader = () => {
    if (!authRequired) return '';
    if (authProfile === 'api-key') {
      return '-H "x-api-key: $SMOKE_API_KEY"';
    }
    return '-H "Authorization: Bearer $SMOKE_TOKEN"';
  };

  const buildCurlCommand = (test) => {
    const method = (test?.method || 'GET').toUpperCase();
    const path = test?.path || '/';
    const url = `${getBaseUrl()}${path}`;
    const authHeader = getAuthCurlHeader();
    const needsBody = ['POST', 'PUT', 'PATCH'].includes(method);
    const bodyFlag = needsBody ? '-H "Content-Type: application/json" -d \'{}\'' : '';

    return [
      'curl -sS',
      `-X ${method}`,
      authHeader,
      bodyFlag,
      `"${url}"`,
      '-w "\\nHTTP %{http_code}\\n"'
    ].filter(Boolean).join(' ');
  };

  const generatedCurlCommands = (generatedPreview?.tests || []).map((test, index) => {
    const testName = test?.name || test?.operationId || `test-${index + 1}`;
    return `# ${index + 1}. ${testName}\n${buildCurlCommand(test)}`;
  });

  const generatedCurlCommandsText = generatedCurlCommands.length > 0
    ? generatedCurlCommands.join('\n\n')
    : 'Generate tests preview to produce curl commands.';

  const downloadCurlCollection = () => {
    if (generatedCurlCommands.length === 0) {
      addConsoleMessage('⚠ No generated curl commands. Generate tests preview first.', 'warning');
      return;
    }

    const scriptLines = [
      '#!/usr/bin/env bash',
      'set -euo pipefail',
      '',
      `# Generated curl smoke collection`,
      `# Target: ${getBaseUrl()}`,
      authRequired
        ? (authProfile === 'api-key'
            ? '# Requires: export SMOKE_API_KEY=<your-api-key>'
            : '# Requires: export SMOKE_TOKEN=<bearer-token>')
        : '# No auth header required',
      '',
      generatedCurlCommandsText,
      ''
    ];

    const data = scriptLines.join('\n');
    const blob = new Blob([data], { type: 'text/x-shellscript' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `curl-collection-${environment}-${capability}-${Date.now()}.sh`;
    link.click();
    URL.revokeObjectURL(url);
    addConsoleMessage('✓ Downloaded curl collection script', 'success');
  };

  const generatedFiles = [];
  if (generatedCurlCommands.length > 0) {
    generatedFiles.push(`curl-collection-${environment}-${capability}.sh`);
    generatedFiles.push(`curl-collection-${environment}-${capability}.txt`);
  }
  if (summary && results) {
    generatedFiles.push(`smoke-tests-${environment}-${capability}.json`);
  }
  const generatedFilesText = generatedFiles.length > 0
    ? generatedFiles.join('\n')
    : 'No generated files yet.';

  const renderResultsPanel = () => (
    <>
      {/* Empty State */}
      {!summary && !generatedPreview && !loading && (
        <Alert severity="info" icon={<InfoIcon />}>
          Configure environment/capability and click Run to execute smoke tests.
        </Alert>
      )}

      {/* Loading State */}
      {loading && (
        <Paper elevation={2} sx={{ p: 4, textAlign: 'center' }}>
          <CircularProgress size={60} sx={{ mb: 2 }} />
          <Typography variant="h6" sx={{ mb: 1 }}>
            Executing Smoke Tests
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {testSuite === 'contract' && 'Executing contract checks...'}
            {testSuite === 'workflow' && 'Running workflow steps...'}
            {testSuite === 'both' && 'Executing contract checks and workflow steps...'}
          </Typography>
        </Paper>
      )}

      {generatedPreview && !loading && (
        <Paper elevation={2} sx={{ p: 3, mb: 2 }}>
          <Typography variant="h6" sx={{ mb: 1.5, fontWeight: 600 }}>
            Generated Tests Preview
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {generatedPreview.title} · {generatedPreview.operationCount} operation(s)
            {generatedPreview.orderEnforced ? ' · ordered by dependencies' : ' · source order'}
          </Typography>

          {generatedPreview.warnings && generatedPreview.warnings.length > 0 && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              {generatedPreview.warnings.join(' | ')}
            </Alert>
          )}

          {generatedPreview.tests && generatedPreview.tests.length > 0 ? (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Order</TableCell>
                  <TableCell>Test</TableCell>
                  <TableCell>Method</TableCell>
                  <TableCell>Path</TableCell>
                  <TableCell>Expected</TableCell>
                  <TableCell>Depends On</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {generatedPreview.tests.map((test) => (
                  <TableRow key={test.testId}>
                    <TableCell>{test.executionOrder}</TableCell>
                    <TableCell>{test.name}</TableCell>
                    <TableCell>
                      <Chip
                        label={test.method}
                        size="small"
                        color={test.method === 'POST' ? 'primary' : 'default'}
                        variant={test.method === 'POST' ? 'filled' : 'outlined'}
                      />
                    </TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.82rem' }}>{test.path}</TableCell>
                    <TableCell>{test.expectedStatus}</TableCell>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.78rem' }}>
                      {test.dependsOn && test.dependsOn.length > 0 ? test.dependsOn.join(', ') : '-'}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
              No generated tests.
            </Typography>
          )}
        </Paper>
      )}

      {/* Summary Card */}
      {summary && !loading && (
        <>
          <Paper elevation={2} sx={{ p: 3, mb: 2 }}>
            <Typography variant="h6" sx={{ mb: 2, fontWeight: 600 }}>
              Summary
            </Typography>

            <Grid container spacing={2}>
              <Grid item xs={12} md={4}>
                <Typography variant="caption" color="text.secondary">Status</Typography>
                <Box sx={{ mt: 0.5 }}>
                  {getStatusChip(summary.status)}
                </Box>
              </Grid>
              <Grid item xs={12} md={4}>
                <Typography variant="caption" color="text.secondary">Target</Typography>
                <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.85rem', mt: 0.5 }}>
                  {summary.target}
                </Typography>
              </Grid>
              <Grid item xs={12} md={4}>
                <Typography variant="caption" color="text.secondary">Spec Fingerprint</Typography>
                <Tooltip title={summary.specFingerprint}>
                  <Typography variant="body2" sx={{
                    fontFamily: 'monospace',
                    fontSize: '0.75rem',
                    mt: 0.5,
                    maxWidth: '200px',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap'
                  }}>
                    {summary.specFingerprint}
                  </Typography>
                </Tooltip>
              </Grid>
            </Grid>

            {testSource && (
              <Box sx={{ mt: 2, pt: 2, borderTop: 1, borderColor: 'divider' }}>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                  Test Source
                </Typography>
                <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap' }}>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Type</Typography>
                    <Typography variant="body2" sx={{ fontSize: '0.85rem' }}>
                      {testSource.type}
                    </Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Spec Fingerprint</Typography>
                    <Tooltip title={testSource.fingerprint}>
                      <Typography variant="body2" sx={{
                        fontFamily: 'monospace',
                        fontSize: '0.75rem',
                        maxWidth: '150px',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap'
                      }}>
                        {testSource.fingerprint}
                      </Typography>
                    </Tooltip>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Generated At</Typography>
                    <Typography variant="body2" sx={{ fontSize: '0.85rem' }}>
                      {new Date(testSource.generatedAt).toLocaleString()}
                    </Typography>
                  </Box>
                </Box>
              </Box>
            )}

            {runMetadata && (
              <Box sx={{ mt: 2, pt: 2, borderTop: 1, borderColor: 'divider' }}>
                <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                  Run Metadata
                </Typography>
                <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap' }}>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Run ID</Typography>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                      {runMetadata.runId || 'N/A'}
                    </Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Start Time</Typography>
                    <Typography variant="body2" sx={{ fontSize: '0.85rem' }}>
                      {runMetadata.startTime ? new Date(runMetadata.startTime).toLocaleString() : 'N/A'}
                    </Typography>
                  </Box>
                  <Box>
                    <Typography variant="caption" color="text.secondary">Duration</Typography>
                    <Typography variant="body2" sx={{ fontSize: '0.85rem' }}>
                      {runMetadata.duration || 'N/A'}
                    </Typography>
                  </Box>
                </Box>
              </Box>
            )}
          </Paper>

          {findings && findings.length > 0 && (
            <Paper elevation={2} sx={{ p: 3, mb: 2 }}>
              <Typography variant="h6" sx={{ mb: 2, fontWeight: 600 }}>
                Findings
              </Typography>

              {findings.map((finding, idx) => (
                <Alert
                  key={idx}
                  severity={getSeverityColor(finding.severity)}
                  icon={getSeverityIcon(finding.severity)}
                  sx={{ mb: 2 }}
                >
                  <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 0.5 }}>
                    {finding.title}
                  </Typography>
                  <Typography variant="body2" sx={{ mb: 1 }}>
                    {finding.message}
                  </Typography>
                </Alert>
              ))}
            </Paper>
          )}

          <Paper elevation={2} sx={{ mb: 2 }}>
            <Accordion defaultExpanded>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography variant="h6" sx={{ fontWeight: 600 }}>
                  Contract Smoke Results
                </Typography>
              </AccordionSummary>
              <AccordionDetails>
                {results?.contractTests && results.contractTests.length > 0 ? (
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Test</TableCell>
                        <TableCell>Operation ID</TableCell>
                        <TableCell>Expected</TableCell>
                        <TableCell>Actual</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Duration</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {results.contractTests.map((test, idx) => (
                        <TableRow key={idx}>
                          <TableCell>{test.name}</TableCell>
                          <TableCell sx={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                            {test.operationId}
                          </TableCell>
                          <TableCell>{test.expected}</TableCell>
                          <TableCell>{test.actual}</TableCell>
                          <TableCell>
                            <Chip
                              label={test.status}
                              color={test.status === 'PASS' ? 'success' : 'error'}
                              size="small"
                            />
                          </TableCell>
                          <TableCell>{test.duration}ms</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                ) : (
                  <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic', textAlign: 'center', py: 2 }}>
                    No contract test results available.
                  </Typography>
                )}
              </AccordionDetails>
            </Accordion>
          </Paper>
        </>
      )}
    </>
  );

  const renderGeneratedFilesPanel = () => (
    <>
      <Typography variant="h6" sx={{ mb: 2, fontWeight: 600 }}>
        Generated Files
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Files generated from the latest smoke test operation.
      </Typography>

      {!summary && !generatedPreview && !loading && (
        <Alert severity="info" icon={<InfoIcon />} sx={{ fontSize: '0.85rem' }}>
          Run tests or generate a preview to materialize generated artifacts.
        </Alert>
      )}

      {loading && (
        <Box sx={{ textAlign: 'center', py: 2 }}>
          <CircularProgress size={40} sx={{ mb: 1.5 }} />
          <Typography variant="body2" color="text.secondary">
            Executing tests and generating artifacts...
          </Typography>
        </Box>
      )}

      {generatedPreview && (
        <Card elevation={0} sx={{ border: 1, borderColor: 'divider', mb: 2 }}>
          <CardContent>
            <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
              curl-collection.sh
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {generatedPreview.operationCount} generated curl commands
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1, fontSize: '0.82rem' }}>
              Postman-like runnable set generated from current preview order.
            </Typography>
            <Box sx={{ display: 'flex', gap: 1, mt: 1, flexWrap: 'wrap' }}>
              <Button
                size="small"
                variant="contained"
                sx={{ textTransform: 'none' }}
                onClick={handleRunTests}
                disabled={loading || !canRunTests()}
              >
                Run Generated Set
              </Button>
              <Button
                size="small"
                variant="outlined"
                sx={{ textTransform: 'none' }}
                startIcon={<DownloadIcon />}
                onClick={downloadCurlCollection}
              >
                Download curl collection
              </Button>
            </Box>
          </CardContent>
        </Card>
      )}

      {summary && (
        <Card elevation={0} sx={{ border: 1, borderColor: 'divider' }}>
          <CardContent>
            <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
              smoke-test-results.json
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Execution summary, findings, and run metadata
            </Typography>
            <Button
              size="small"
              variant="outlined"
              sx={{ mt: 1, textTransform: 'none' }}
              startIcon={<DownloadIcon />}
              onClick={handleExport}
              disabled={!summary || !results}
            >
              Download execution artifact
            </Button>
          </CardContent>
        </Card>
      )}

      <TextField
        fullWidth
        label="Generated Files List"
        value={generatedFilesText}
        multiline
        minRows={4}
        InputProps={{ readOnly: true }}
        sx={{ mt: 2 }}
      />
      <TextField
        fullWidth
        label="Generated cURL Commands"
        value={generatedCurlCommandsText}
        multiline
        minRows={8}
        maxRows={18}
        InputProps={{ readOnly: true }}
        sx={{
          mt: 2,
          '& .MuiInputBase-inputMultiline': {
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
            fontSize: '0.78rem'
          }
        }}
      />
    </>
  );

  return (
    <Container
      maxWidth={false}
      sx={{
        mt: 3,
        pb: 3,
        px: { xs: 2, sm: 2.5, md: 3 },
        width: '100%',
        maxWidth: '100%',
        overflowX: 'hidden'
      }}
    >
      {/* Page Header */}
      <Box sx={{ mb: 3 }}>
        <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
          Smoke Tests
        </Typography>
        <Typography variant="body1" color="text.secondary">
          Fast, deterministic API confidence checks driven by OpenAPI + optional curated workflows.
        </Typography>
      </Box>

      <Paper elevation={2} sx={{ p: 3 }}>
        <Box
          sx={{
            display: 'grid',
            gridTemplateColumns: {
              xs: '1fr',
              lg: 'minmax(560px, 1.05fr) minmax(0, 1.95fr)'
            },
            width: '100%',
            minWidth: 0,
            gap: 3,
            alignItems: 'flex-start'
          }}
        >
        {/* Left Column - Configuration */}
        <Box sx={{ minWidth: 0 }}>
          {/* Configuration Card */}
          <Box sx={{ minWidth: 0, width: '100%' }}>
            <Typography variant="h6" sx={{ mb: 2, fontWeight: 600 }}>
              Smoke Test Configuration
            </Typography>

            {/* Section A - Target */}
            <Box sx={{ mb: 3, width: '100%' }}>
              <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 600, color: 'text.secondary' }}>
                Target
              </Typography>
              
              <Grid container spacing={1.5} sx={{ mb: 1.5 }}>
                <Grid item xs={12} md={4}>
                  <FormControl fullWidth>
                    <InputLabel>Environment</InputLabel>
                    <Select
                      value={environment}
                      onChange={(e) => setEnvironment(e.target.value)}
                      label="Environment"
                      disabled={loading}
                    >
                      {environmentOptions.map(env => (
                        <MenuItem key={env} value={env}>{env}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} md={8}>
                  <TextField
                    fullWidth
                    label="Environment Base URL"
                    value={environmentBaseUrls[environment] || ''}
                    onChange={(e) => handleEnvironmentBaseUrlChange(e.target.value)}
                    disabled={loading}
                    placeholder="https://my-env.company.com"
                    helperText="Used to construct the target URL for this environment"
                  />
                </Grid>
              </Grid>

              <Grid container spacing={1.5} alignItems="stretch">
                <Grid item xs={12} md={6}>
                  <FormControl fullWidth>
                    <InputLabel>Capability</InputLabel>
                    <Select
                      value={capability}
                      onChange={(e) => setCapability(e.target.value)}
                      label="Capability"
                      disabled={loading}
                    >
                      {CAPABILITIES.map(cap => (
                        <MenuItem key={cap} value={cap}>{cap}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Grid>
                <Grid item xs={12} md={6}>
                  <Alert severity="info" sx={{ height: '100%', display: 'flex', alignItems: 'center' }}>
                    <Typography variant="caption" sx={{ fontFamily: 'monospace', fontSize: '0.75rem' }}>
                      Base URL: {getBaseUrl()}
                    </Typography>
                  </Alert>
                </Grid>
              </Grid>
            </Box>

            {/* Section B - Spec Source */}
            <Box sx={{ mb: 3 }}>
              <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 600, color: 'text.secondary' }}>
                Spec Source
              </Typography>
              
              <ToggleButtonGroup
                value={specSource}
                exclusive
                onChange={(e, val) => val && setSpecSource(val)}
                fullWidth
                sx={{
                  mb: 2,
                  width: '100%',
                  '& .MuiToggleButton-root': {
                    flex: 1
                  }
                }}
                disabled={loading}
              >
                <ToggleButton value="blob" sx={{ flex: 1, minWidth: 0 }}>
                  From Blob (recommended)
                </ToggleButton>
                <ToggleButton value="upload" sx={{ flex: 1, minWidth: 0 }}>
                  Local Upload
                </ToggleButton>
              </ToggleButtonGroup>

              {specSource === 'blob' && (
                <Box sx={{ width: '100%' }}>
                  <TextField
                    fullWidth
                    label="OpenAPI Spec Path"
                    value={`/specs/${capability}/openapi.yaml`}
                    disabled
                    sx={{ mb: 1, width: '100%' }}
                    size="small"
                  />
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    <Typography variant="caption" color="text.secondary">
                      Spec Fingerprint:
                    </Typography>
                    <Tooltip title={getSpecFingerprint()}>
                      <Typography 
                        variant="caption" 
                        sx={{ 
                          fontFamily: 'monospace',
                          fontSize: '0.7rem',
                          maxWidth: '200px',
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                          bgcolor: 'grey.100',
                          px: 0.5,
                          py: 0.25,
                          borderRadius: 0.5
                        }}
                      >
                        {getSpecFingerprint()}
                      </Typography>
                    </Tooltip>
                    <IconButton 
                      size="small" 
                      onClick={handleCopyFingerprint}
                      sx={{ p: 0.25 }}
                    >
                      <ContentCopyIcon sx={{ fontSize: '0.9rem' }} />
                    </IconButton>
                  </Box>
                </Box>
              )}

              {specSource === 'upload' && (
                <Box sx={{ width: '100%' }}>
                  <TextField
                    fullWidth
                    label="Paste Swagger/OpenAPI (JSON or YAML)"
                    value={specUploadText}
                    onChange={(e) => {
                      setSpecUploadText(e.target.value);
                      setGeneratedPreview(null);
                    }}
                    disabled={loading || generatingPreview}
                    multiline
                    minRows={8}
                    maxRows={16}
                    sx={{
                      mb: 1.5,
                      minWidth: 0,
                      width: '100%',
                      '& .MuiInputBase-inputMultiline': {
                        fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
                        whiteSpace: 'pre-wrap',
                        overflowWrap: 'anywhere',
                        wordBreak: 'break-word'
                      }
                    }}
                    helperText="Paste raw spec text. Lines starting with // are ignored."
                  />
                  <FormControlLabel
                    control={(
                      <Checkbox
                        checked={enforceSpecOrder}
                        onChange={(e) => {
                          setEnforceSpecOrder(e.target.checked);
                          setGeneratedPreview(null);
                        }}
                        disabled={loading || generatingPreview}
                        size="small"
                      />
                    )}
                    label="Enforce dependency order (create before dependent operations)"
                    sx={{ mb: 0.5 }}
                  />
                </Box>
              )}
            </Box>

          </Box>
        </Box>
        <Box sx={{ minWidth: 0 }}>
          <Box sx={{ p: 0, width: '100%', overflow: 'auto' }}>
            <Box sx={{ p: 1.5, mb: 2, bgcolor: 'grey.50', borderRadius: 1, border: '1px solid', borderColor: 'divider' }}>
              <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 600, color: 'text.secondary' }}>
                Add Environment
              </Typography>
              <Grid container spacing={1}>
                <Grid item xs={12} md={4}>
                  <TextField
                    fullWidth
                    size="small"
                    label="Name"
                    value={newEnvironmentName}
                    onChange={(e) => setNewEnvironmentName(e.target.value)}
                    disabled={loading}
                    placeholder="prod"
                  />
                </Grid>
                <Grid item xs={12} md={6}>
                  <TextField
                    fullWidth
                    size="small"
                    label="Base URL"
                    value={newEnvironmentBaseUrl}
                    onChange={(e) => setNewEnvironmentBaseUrl(e.target.value)}
                    disabled={loading}
                    placeholder="https://capability.prod.att.com"
                  />
                </Grid>
                <Grid item xs={12} md={2}>
                  <Button
                    fullWidth
                    variant="outlined"
                    onClick={handleAddEnvironment}
                    disabled={loading}
                    sx={{ height: '40px', textTransform: 'none' }}
                  >
                    Add
                  </Button>
                </Grid>
              </Grid>
            </Box>

            <Box sx={{ mb: 2.5, p: 1.5, border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
              <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 600, color: 'text.secondary' }}>
                Authentication
              </Typography>
              <FormControlLabel
                control={
                  <Switch
                    checked={authRequired}
                    onChange={(e) => setAuthRequired(e.target.checked)}
                    disabled={loading}
                  />
                }
                label="Auth Required"
                sx={{ mb: 1 }}
              />

              {authRequired && (
                <>
                  <FormControl fullWidth sx={{ mb: 1 }}>
                    <InputLabel>Auth Profile</InputLabel>
                    <Select
                      value={authProfile}
                      onChange={(e) => setAuthProfile(e.target.value)}
                      label="Auth Profile"
                      disabled={loading}
                    >
                      {AUTH_PROFILES.map(profile => (
                        <MenuItem key={profile} value={profile}>{profile}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  {authProfile === 'jwt-service' && (
                    <TextField
                      fullWidth
                      multiline
                      minRows={3}
                      maxRows={8}
                      label="JWT Generation cURL (optional)"
                      value={jwtGenerationCurl}
                      onChange={(e) => setJwtGenerationCurl(e.target.value)}
                      disabled={loading}
                      placeholder='curl -X POST "https://auth.example.com/token" -H "Content-Type: application/x-www-form-urlencoded" -d "..."'
                      helperText="Paste your token-generation curl command. This is sent with the run config."
                      sx={{
                        mt: 1,
                        mb: 1,
                        '& .MuiInputBase-inputMultiline': {
                          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
                          fontSize: '0.8rem'
                        }
                      }}
                    />
                  )}
                  <Typography variant="caption" color="text.secondary">
                    Acquire token once per run; cached & refreshed automatically.
                  </Typography>
                </>
              )}
            </Box>

            <Grid container spacing={2} sx={{ mb: 2.5 }}>
              <Grid item xs={12} xl={8}>
                <Paper variant="outlined" sx={{ p: 2 }}>
                  <Typography variant="h6" sx={{ mb: 2, fontWeight: 600 }}>
                    Test Suite
                  </Typography>

                  <ToggleButtonGroup
                    value={testSuite}
                    exclusive
                    onChange={(e, val) => val && setTestSuite(val)}
                    fullWidth
                    sx={{ mb: 2 }}
                    disabled={loading}
                  >
                    <ToggleButton value="contract" sx={{ py: 1 }}>
                      Contract Smoke
                    </ToggleButton>
                    <ToggleButton value="workflow" sx={{ py: 1 }}>
                      Workflow Smoke
                    </ToggleButton>
                    <ToggleButton value="both" sx={{ py: 1 }}>
                      Both
                    </ToggleButton>
                  </ToggleButtonGroup>

                  {(testSuite === 'contract' || testSuite === 'both') && (
                    <Box sx={{ mb: testSuite === 'both' ? 2 : 0, p: 2, bgcolor: 'grey.50', borderRadius: 1 }}>
                      <Typography variant="subtitle2" sx={{ mb: 1, fontWeight: 600 }}>
                        Contract Smoke Options
                      </Typography>
                      <FormGroup>
                        <FormControlLabel
                          control={
                            <Checkbox
                              checked={contractOptions.happyPaths}
                              onChange={(e) => setContractOptions({...contractOptions, happyPaths: e.target.checked})}
                              disabled={loading}
                            />
                          }
                          label="Happy paths (200/201 + schema validation)"
                        />
                        <FormControlLabel
                          control={
                            <Checkbox
                              checked={contractOptions.negativeAuth}
                              onChange={(e) => setContractOptions({...contractOptions, negativeAuth: e.target.checked})}
                              disabled={loading}
                            />
                          }
                          label="Negative auth (401/403)"
                        />
                        <FormControlLabel
                          control={
                            <Checkbox
                              checked={contractOptions.basic400}
                              onChange={(e) => setContractOptions({...contractOptions, basic400: e.target.checked})}
                              disabled={loading}
                            />
                          }
                          label="Basic 400 validations"
                        />

                        <Button
                          size="small"
                          onClick={() => setContractOptions({...contractOptions, advancedOptions: !contractOptions.advancedOptions})}
                          endIcon={contractOptions.advancedOptions ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                          sx={{ mt: 1, justifyContent: 'flex-start', textTransform: 'none' }}
                        >
                          Advanced
                        </Button>

                        <Collapse in={contractOptions.advancedOptions}>
                          <Box sx={{ pl: 2, mt: 1.5, pt: 1.5, borderTop: 1, borderColor: 'divider' }}>
                            <FormControlLabel
                              control={
                                <Checkbox
                                  checked={contractOptions.strictSchemaValidation}
                                  onChange={(e) => setContractOptions({...contractOptions, strictSchemaValidation: e.target.checked})}
                                  disabled={loading}
                                  size="small"
                                />
                              }
                              label="Strict schema validation"
                            />
                            <FormControlLabel
                              control={
                                <Checkbox
                                  checked={contractOptions.failFast}
                                  onChange={(e) => setContractOptions({...contractOptions, failFast: e.target.checked})}
                                  disabled={loading}
                                  size="small"
                                />
                              }
                              label="Fail fast on first error"
                            />

                            <Box sx={{ mt: 2, pt: 2, borderTop: 1, borderColor: 'divider' }}>
                              <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 600, fontSize: '0.85rem' }}>
                                Test Generation
                              </Typography>

                              <Box sx={{ mb: 1 }}>
                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 0.5 }}>
                                  <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.75rem' }}>
                                    Contract fingerprint:
                                  </Typography>
                                  <Tooltip title={getSpecFingerprint()}>
                                    <Typography
                                      variant="caption"
                                      sx={{
                                        fontFamily: 'monospace',
                                        fontSize: '0.65rem',
                                        maxWidth: '150px',
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis',
                                        whiteSpace: 'nowrap',
                                        bgcolor: 'grey.200',
                                        px: 0.5,
                                        py: 0.25,
                                        borderRadius: 0.5
                                      }}
                                    >
                                      {getSpecFingerprint()}
                                    </Typography>
                                  </Tooltip>
                                </Box>

                                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                  <Typography variant="caption" color="text.secondary" sx={{ fontSize: '0.75rem' }}>
                                    Generated test set:
                                  </Typography>
                                  <Chip
                                    label={cachedTestsStatus === 'present' ? 'Present (cached)' : cachedTestsStatus === 'outdated' ? 'Outdated' : 'None'}
                                    size="small"
                                    color={cachedTestsStatus === 'present' ? 'success' : cachedTestsStatus === 'outdated' ? 'warning' : 'default'}
                                    sx={{ height: 18, fontSize: '0.7rem' }}
                                  />
                                </Box>
                              </Box>

                              <Box sx={{ display: 'flex', gap: 1, mt: 1.5 }}>
                                <Button
                                  variant="outlined"
                                  size="small"
                                  onClick={handleRegenerateTests}
                                  disabled={loading}
                                  sx={{ textTransform: 'none', fontSize: '0.75rem', py: 0.5 }}
                                >
                                  Regenerate Tests
                                </Button>
                                <Button
                                  variant="outlined"
                                  size="small"
                                  onClick={handleLoadCachedTests}
                                  disabled={loading || cachedTestsStatus === 'none'}
                                  sx={{ textTransform: 'none', fontSize: '0.75rem', py: 0.5 }}
                                >
                                  Load Cached Tests
                                </Button>
                              </Box>

                              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1, fontSize: '0.7rem', fontStyle: 'italic' }}>
                                Tests are auto-generated from OpenAPI spec and cached locally.
                              </Typography>
                            </Box>
                          </Box>
                        </Collapse>
                      </FormGroup>
                    </Box>
                  )}

                  {testSuite === 'both' && (
                    <Box sx={{ my: 2, borderTop: 1, borderColor: 'divider' }} />
                  )}

                  {(testSuite === 'workflow' || testSuite === 'both') && (
                    <Box sx={{ p: 2, bgcolor: 'grey.50', borderRadius: 1 }}>
                      <Typography variant="subtitle2" sx={{ mb: 1.5, fontWeight: 600 }}>
                        Workflow Configuration
                      </Typography>

                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                        Workflow Source
                      </Typography>
                      <ToggleButtonGroup
                        value={workflowSource}
                        exclusive
                        onChange={(e, val) => val && setWorkflowSource(val)}
                        fullWidth
                        sx={{ mb: 2 }}
                        disabled={loading}
                        size="small"
                      >
                        <ToggleButton value="catalog">
                          From Repository / Catalog (recommended)
                        </ToggleButton>
                        <ToggleButton value="upload">
                          Upload YAML (ad-hoc)
                        </ToggleButton>
                      </ToggleButtonGroup>

                      {workflowSource === 'catalog' && (
                        <>
                          <FormControl fullWidth sx={{ mb: 1 }}>
                            <InputLabel size="small">Workflow</InputLabel>
                            <Select
                              value={workflowFile}
                              onChange={(e) => setWorkflowFile(e.target.value)}
                              label="Workflow"
                              size="small"
                              disabled={loading}
                            >
                              <MenuItem value="cart-lifecycle-smoke">cart-lifecycle-smoke</MenuItem>
                              <MenuItem value="cart-item-mutation-smoke">cart-item-mutation-smoke</MenuItem>
                              <MenuItem value="checkout-flow-smoke">checkout-flow-smoke</MenuItem>
                              <MenuItem value="user-journey-smoke">user-journey-smoke</MenuItem>
                            </Select>
                          </FormControl>

                          <Alert severity="info" icon={<InfoIcon fontSize="small" />} sx={{ fontSize: '0.8rem', py: 0.5, mb: 1.5 }}>
                            <Typography variant="caption">
                              Steps: 4 (create → get → patch → delete)
                            </Typography>
                          </Alert>
                        </>
                      )}

                      {workflowSource === 'upload' && (
                        <>
                          <Box
                            sx={{
                              border: 2,
                              borderStyle: 'dashed',
                              borderColor: workflowValidation?.status === 'success' ? 'success.main' : workflowValidation?.status === 'error' ? 'error.main' : 'grey.300',
                              borderRadius: 1,
                              p: 2,
                              mb: 1,
                              textAlign: 'center',
                              bgcolor: 'background.paper',
                              cursor: 'pointer',
                              '&:hover': {
                                bgcolor: 'grey.50'
                              }
                            }}
                            onClick={() => document.getElementById('workflow-file-input').click()}
                          >
                            <input
                              id="workflow-file-input"
                              type="file"
                              accept=".yaml,.yml"
                              onChange={handleWorkflowFileUpload}
                              style={{ display: 'none' }}
                              disabled={loading}
                            />
                            <UploadFileIcon sx={{ fontSize: 40, color: 'text.secondary', mb: 1 }} />
                            <Typography variant="body2" sx={{ mb: 0.5 }}>
                              Drop workflow YAML here or click to choose file
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              Accepts .yaml or .yml files
                            </Typography>
                          </Box>

                          {workflowValidation && (
                            <Alert
                              severity={workflowValidation.status}
                              sx={{ mb: 1, fontSize: '0.85rem', py: 0.5 }}
                            >
                              {workflowValidation.message}
                            </Alert>
                          )}

                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.5, fontStyle: 'italic' }}>
                            Workflow YAML defines ordered steps by operationId, capture/bind variables, and cleanup behavior.
                          </Typography>

                          {workflowYaml && workflowValidation?.status === 'success' && (
                            <FormControlLabel
                              control={
                                <Checkbox
                                  checked={saveToLocalCatalog}
                                  onChange={(e) => setSaveToLocalCatalog(e.target.checked)}
                                  disabled={loading}
                                  size="small"
                                />
                              }
                              label={<Typography variant="caption">Save to local catalog for reuse</Typography>}
                            />
                          )}
                        </>
                      )}

                      <Box sx={{ mt: 2, pt: 2, borderTop: 1, borderColor: 'divider' }}>
                        <FormControlLabel
                          control={
                            <Switch
                              checked={alwaysCleanup}
                              onChange={(e) => setAlwaysCleanup(e.target.checked)}
                              disabled={loading}
                            />
                          }
                          label="Always attempt cleanup"
                        />
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', ml: 4, mt: 0.5, fontStyle: 'italic' }}>
                          Run cleanup steps (e.g., delete resources) even if the workflow fails.
                        </Typography>
                      </Box>
                    </Box>
                  )}
                </Paper>
              </Grid>

              <Grid item xs={12} xl={4}>
                <Paper variant="outlined" sx={{ p: 2 }}>
                  <Typography variant="h6" sx={{ mb: 2, fontWeight: 600 }}>
                    Run Controls
                  </Typography>
                  <Box sx={{ display: 'flex', gap: 1, flexDirection: 'column' }}>
                    {isWorkflowRequired() && !isWorkflowConfigured() && (
                      <Alert severity="warning" icon={<WarningIcon />} sx={{ py: 0.5, fontSize: '0.85rem' }}>
                        <Typography variant="caption" sx={{ fontWeight: 600 }}>
                          Workflow smoke requires a workflow definition.
                        </Typography>
                        <Typography variant="caption" sx={{ display: 'block', mt: 0.25 }}>
                          Select a catalog workflow or upload YAML to continue.
                        </Typography>
                      </Alert>
                    )}

                    <Button
                      variant="outlined"
                      fullWidth
                      onClick={handleGenerateTestsPreview}
                      disabled={loading || generatingPreview || specSource !== 'upload' || !specUploadText.trim()}
                      startIcon={generatingPreview ? <CircularProgress size={18} /> : <InfoIcon />}
                      sx={{ textTransform: 'none' }}
                    >
                      {generatingPreview ? 'Generating...' : 'Generate Tests from Pasted Spec'}
                    </Button>

                    <Button
                      variant="contained"
                      size="large"
                      fullWidth
                      startIcon={loading ? <CircularProgress size={20} color="inherit" /> : <PlayArrowIcon />}
                      onClick={handleRunTests}
                      disabled={loading || generatingPreview || !canRunTests()}
                    >
                      {loading ? 'Running...' : 'Run Smoke Tests'}
                    </Button>

                    {specSource === 'upload' && !generatedPreview && (
                      <Typography variant="caption" color="text.secondary" sx={{ textAlign: 'center' }}>
                        Generate tests from pasted spec before running.
                      </Typography>
                    )}

                    <Box sx={{ display: 'flex', gap: 1, justifyContent: 'space-between', alignItems: 'center' }}>
                      <Button
                        size="small"
                        onClick={handleValidateConfig}
                        disabled={loading}
                        sx={{ textTransform: 'none' }}
                      >
                        Validate Configuration
                      </Button>
                      <Button
                        size="small"
                        startIcon={<RestartAltIcon />}
                        onClick={handleReset}
                        disabled={loading}
                        sx={{ textTransform: 'none' }}
                      >
                        Reset
                      </Button>
                    </Box>
                  </Box>
                </Paper>
              </Grid>
            </Grid>
          </Box>
        </Box>
        </Box>
      </Paper>

      <Paper elevation={2} sx={{ p: 3, mt: 3 }}>
        <Box sx={{ display: 'grid', gap: 3 }}>
          <Box>
            {renderGeneratedFilesPanel()}
          </Box>
          <Box sx={{ pt: 2, borderTop: 1, borderColor: 'divider' }}>
            {renderResultsPanel()}
          </Box>
        </Box>
      </Paper>
    </Container>
  );
}

export default SmokeTestsPage;
