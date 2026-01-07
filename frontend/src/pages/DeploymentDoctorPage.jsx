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
  Button
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import WarningIcon from '@mui/icons-material/Warning';
import ErrorIcon from '@mui/icons-material/Error';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import RefreshIcon from '@mui/icons-material/Refresh';
import axios from 'axios';

function DeploymentDoctorPage({ addConsoleMessage, k8sStatus, setK8sStatus }) {
  const [loading, setLoading] = useState(false);
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState(null);
  const [namespace, setNamespace] = useState('');
  const [selector, setSelector] = useState('');
  const [release, setRelease] = useState('');

  const loadDeploymentSummary = async () => {
    const effectiveNamespace = namespace || 'default';
    
    if (!effectiveNamespace) {
      addConsoleMessage('✗ Namespace is required', 'error');
      setError('Namespace is required');
      return;
    }

    // Validate selector format
    if (selector && !selector.includes('=')) {
      const errorMsg = 'Selector must be a valid label selector, e.g. app=cart-app';
      addConsoleMessage(`✗ ${errorMsg}`, 'error');
      setError(errorMsg);
      return;
    }

    setLoading(true);
    setError(null);
    
    try {
      addConsoleMessage(`🔍 Loading deployment summary for namespace: ${effectiveNamespace}...`, 'info');
      
      const params = new URLSearchParams({ namespace: effectiveNamespace });
      if (selector) params.append('selector', selector);
      if (release) params.append('release', release);
      
      const response = await axios.get(`/api/deployment/summary?${params.toString()}`);
      setSummary(response.data);
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

  const getSeverityIcon = (severity) => {
    switch ((severity || '').toUpperCase()) {
      case 'HIGH':
        return <ErrorIcon color="error" />;
      case 'MED':
        return <WarningIcon color="warning" />;
      case 'LOW':
        return <WarningIcon color="info" />;
      case 'INFO':
      default:
        return <CheckCircleIcon color="success" />;
    }
  };

  const getSeverityColor = (severity) => {
    switch ((severity || '').toUpperCase()) {
      case 'HIGH':
        return 'error';
      case 'MED':
        return 'warning';
      case 'LOW':
        return 'info';
      case 'INFO':
      default:
        return 'success';
    }
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
    <Paper elevation={2} sx={{ p: 3, mb: 3 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>
        Kubernetes Configuration
      </Typography>
      <Grid container spacing={2} alignItems="flex-end">
        <Grid item xs={12} md={4}>
          <TextField
            label="Namespace"
            placeholder="default"
            value={namespace}
            onChange={(e) => setNamespace(e.target.value)}
            onFocus={(e) => e.target.select()}
            fullWidth
            required
            helperText="Kubernetes namespace to monitor"
          />
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField
            label="Label Selector (e.g. app=cart-app)"
            value={selector}
            onChange={(e) => setSelector(e.target.value)}
            onFocus={(e) => e.target.select()}
            fullWidth
            helperText="Filter by label (optional)"
            error={selector && !selector.includes('=')}
          />
        </Grid>
        <Grid item xs={12} md={3}>
          <TextField
            label="Release (optional)"
            value={release}
            onChange={(e) => setRelease(e.target.value)}
            onFocus={(e) => e.target.select()}
            fullWidth
            helperText="Helm release name"
          />
        </Grid>
        <Grid item xs={12} md={2}>
          <Button
            variant="contained"
            fullWidth
            onClick={loadDeploymentSummary}
            disabled={loading || !namespace}
            startIcon={loading ? <CircularProgress size={20} /> : <RefreshIcon />}
          >
            {loading ? 'Loading...' : 'Load'}
          </Button>
        </Grid>
      </Grid>
    </Paper>
  );

  return (
    <Container maxWidth="xl" sx={{ mt: 3, pb: 3 }}>
      <Typography variant="h5" sx={{ mb: 3 }}>
        Kubernetes Deployment Doctor
      </Typography>

      {renderConfigurationForm()}

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}

      {!summary && !loading && !error && (
        <Alert severity="info">
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

      {summary && !loading && (
        <Box>

          {/* Summary Cards */}
          <Grid container spacing={3} sx={{ mb: 3 }}>
            <Grid item xs={12} md={3}>
              <Card>
                <CardContent>
                  <Typography color="textSecondary" gutterBottom>
                    Overall
                  </Typography>
                  <Chip
                    label={getHealthFromSummary().overall}
                    color={
                      getHealthFromSummary().overall === 'FAIL' ? 'error' : 
                      getHealthFromSummary().overall === 'WARN' ? 'warning' : 
                      'success'
                    }
                    sx={{ fontWeight: 'bold', fontSize: '1rem' }}
                  />
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

          {/* Findings Section */}
          {summary.findings?.length > 0 && (
            <Paper elevation={2} sx={{ p: 2, mb: 3 }}>
              <Typography variant="h6" sx={{ mb: 2 }}>
                Findings
              </Typography>
              {summary.findings.map((f, idx) => (
                <Alert 
                  key={idx} 
                  severity={getSeverityColor(f.severity)}
                  sx={{ mb: 1 }}
                >
                  <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
                    {f.code}
                  </Typography>
                  <Typography variant="body2">
                    {f.message}
                  </Typography>
                  
                  {/* Render hints if present */}
                  {Array.isArray(f.hints) && f.hints.length > 0 && (
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
                  
                  {/* Evidence refs with click-to-scroll */}
                  {Array.isArray(f.evidenceRefs) && f.evidenceRefs.length > 0 && (
                    <Box sx={{ mt: 1 }}>
                      <Typography variant="body2" sx={{ fontSize: '0.875rem', color: 'text.secondary' }}>
                        Evidence:{' '}
                        {f.evidenceRefs.map((ref, refIdx) => (
                          <span key={refIdx}>
                            {refIdx > 0 && ', '}
                            <Typography
                              component="span"
                              sx={{
                                fontFamily: 'monospace',
                                fontSize: '0.875rem',
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
                </Alert>
              ))}
            </Paper>
          )}

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
            <Alert severity="info">
              No deployments or pods found in namespace <strong>{namespace}</strong>. 
              {selector && ` with selector: ${selector}`}
              {release && ` for release: ${release}`}
            </Alert>
          )}
        </Box>
      )}
    </Container>
  );
}

export default DeploymentDoctorPage;

