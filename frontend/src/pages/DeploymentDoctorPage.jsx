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
import InfoIcon from '@mui/icons-material/Info';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
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

  // ⭐ IMPROVEMENT 1: Severity-driven icons (not "check = detected")
  const getSeverityIcon = (severity) => {
    switch ((severity || '').toUpperCase()) {
      case 'ERROR':
      case 'HIGH':
        return <ErrorIcon />;
      case 'WARN':
      case 'MED':
        return <WarningIcon />;
      case 'INFO':
      case 'LOW':
        return <InfoIcon />;
      default:
        return <CheckCircleIcon />;
    }
  };

  const getSeverityColor = (severity) => {
    switch ((severity || '').toUpperCase()) {
      case 'ERROR':
      case 'HIGH':
        return 'error';
      case 'WARN':
      case 'MED':
        return 'warning';
      case 'INFO':
      case 'LOW':
        return 'info';
      default:
        return 'success';
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
          fontSize: '0.75rem'
        }}
      />
    );
  };

  const getHealthFromSummary = () => {
    if (!summary?.health) {
      return {
        overall: 'PASS',
        deploymentsReady: '0/0',
        pods: { running: 0, pending: 0, crashLoop: 0, imagePullBackOff: 0, notReady: 0 },
        hasWarnings: false
      };
    }
    
    // Check if there are WARN severity findings
    const hasWarnings = summary.findings?.some(f => 
      f.severity === 'WARN' || f.severity === 'MED'
    ) || false;
    
    return {
      overall: summary.health.overall,
      deploymentsReady: summary.health.deploymentsReady,
      pods: summary.health.pods,
      hasWarnings: hasWarnings
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

          {/* Summary Cards - ⭐ IMPROVEMENT 4: De-emphasize when diagnosis exists */}
          <Grid container spacing={3} sx={{ 
            mb: 3,
            ...(summary.primaryFailure && {
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
                  <Chip
                    label={
                      getHealthFromSummary().overall === 'PASS' && getHealthFromSummary().hasWarnings
                        ? 'PASS (with warnings)'
                        : getHealthFromSummary().overall
                    }
                    color={
                      getHealthFromSummary().overall === 'FAIL' ? 'error' : 
                      getHealthFromSummary().overall === 'WARN' ? 'warning' :
                      getHealthFromSummary().overall === 'UNKNOWN' ? 'default' :
                      getHealthFromSummary().hasWarnings ? 'warning' :  // PASS but has warnings → yellow
                      'success'
                    }
                    icon={
                      getHealthFromSummary().overall === 'FAIL' ? <ErrorIcon /> :
                      getHealthFromSummary().overall === 'WARN' ? <WarningIcon /> :
                      getHealthFromSummary().overall === 'UNKNOWN' ? <HelpOutlineIcon /> :
                      getHealthFromSummary().hasWarnings ? <WarningIcon /> :  // PASS with warnings → warning icon
                      <CheckCircleIcon />
                    }
                    sx={{ 
                      fontWeight: 'bold', 
                      fontSize: '1rem',
                      ...(getHealthFromSummary().overall === 'UNKNOWN' && {
                        border: '2px dashed',
                        backgroundColor: 'transparent'
                      })
                    }}
                  />
                  
                  {/* ⭐ POLISH 2: Explanatory microcopy for PASS with warnings */}
                  {getHealthFromSummary().overall === 'PASS' && getHealthFromSummary().hasWarnings && (
                    <Typography 
                      variant="caption" 
                      color="text.secondary" 
                      sx={{ 
                        display: 'block', 
                        mt: 1, 
                        fontSize: '0.75rem',
                        lineHeight: 1.3
                      }}
                    >
                      Deployment is healthy, but advisory signals were detected.
                    </Typography>
                  )}
                  
                  {/* Also add microcopy for UNKNOWN status */}
                  {getHealthFromSummary().overall === 'UNKNOWN' && (
                    <Typography 
                      variant="caption" 
                      color="text.secondary" 
                      sx={{ 
                        display: 'block', 
                        mt: 1, 
                        fontSize: '0.75rem',
                        lineHeight: 1.3
                      }}
                    >
                      No matching resources found. Check your selector.
                    </Typography>
                  )}
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

          {/* ⭐ IMPROVEMENT 2: Primary Failure/Warning Panel */}
          {summary.primaryFailure && (
            <Paper 
              elevation={4} 
              sx={{ 
                p: 3, 
                mb: 3, 
                border: '3px solid',
                borderColor: getSeverityColor(summary.primaryFailure.severity) + '.main',
                bgcolor: getSeverityColor(summary.primaryFailure.severity) + '.50',
                background: `linear-gradient(135deg, ${getSeverityColor(summary.primaryFailure.severity) === 'error' ? 'rgba(211, 47, 47, 0.08)' : getSeverityColor(summary.primaryFailure.severity) === 'warning' ? 'rgba(237, 108, 2, 0.08)' : 'rgba(2, 136, 209, 0.08)'} 0%, rgba(255, 255, 255, 0) 100%)`
              }}
            >
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
                <Typography 
                  variant="overline" 
                  sx={{ 
                    fontWeight: 700, 
                    fontSize: '0.85rem',
                    letterSpacing: '0.1em',
                    color: getSeverityColor(summary.primaryFailure.severity) + '.dark'
                  }}
                >
                  {/* ⭐ FIX 1: Different labels based on severity */}
                  {summary.primaryFailure.severity === 'ERROR' || summary.primaryFailure.severity === 'HIGH' 
                    ? '🎯 PRIMARY ROOT CAUSE'
                    : summary.primaryFailure.severity === 'WARN' || summary.primaryFailure.severity === 'MED'
                    ? '⚠️ TOP WARNING'
                    : 'ℹ️ NOTABLE SIGNAL'}
                </Typography>
              </Box>

              <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2, mb: 2 }}>
                <Box sx={{ 
                  color: getSeverityColor(summary.primaryFailure.severity) + '.main',
                  fontSize: '2rem',
                  lineHeight: 1
                }}>
                  {getSeverityIcon(summary.primaryFailure.severity)}
                </Box>
                <Box sx={{ flex: 1 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1, flexWrap: 'wrap' }}>
                    <Typography variant="h5" sx={{ fontWeight: 700, color: 'text.primary' }}>
                      {summary.primaryFailure.title}
                    </Typography>
                    <Chip 
                      label={summary.primaryFailure.code} 
                      size="small"
                      sx={{ 
                        fontFamily: 'monospace', 
                        fontWeight: 600,
                        bgcolor: getSeverityColor(summary.primaryFailure.severity) + '.main',
                        color: 'white'
                      }}
                    />
                    {summary.primaryFailure.owner && getOwnerBadge(summary.primaryFailure.owner)}
                  </Box>
                  
                  <Typography variant="body1" sx={{ mb: 2, color: 'text.primary', lineHeight: 1.6 }}>
                    {summary.primaryFailure.explanation}
                  </Typography>

                  {/* ⭐ IMPROVEMENT 5: Evidence linkage with visual hierarchy */}
                  {summary.primaryFailure.evidence && summary.primaryFailure.evidence.length > 0 && (
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
                        {summary.primaryFailure.evidence.map((ev, idx) => (
                          <ListItem key={idx} sx={{ pl: 0, py: 0.5 }}>
                            <Typography variant="body2" component="div">
                              <Box component="span" sx={{ color: 'primary.main', fontWeight: 600 }}>
                                {ev.kind}:
                              </Box>{' '}
                              <Box component="span" sx={{ fontFamily: 'monospace', fontSize: '0.875rem' }}>
                                {ev.name}
                              </Box>
                              {ev.message && (
                                <Box component="div" sx={{ 
                                  ml: 2, 
                                  mt: 0.5, 
                                  color: 'text.secondary', 
                                  fontSize: '0.85rem',
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
                  {summary.primaryFailure.nextSteps && summary.primaryFailure.nextSteps.length > 0 && (
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
                        {summary.primaryFailure.nextSteps.map((step, idx) => (
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
          )}

          {/* All Findings Section - Show even when Overall = PASS if there are WARN findings */}
          {(() => {
            // ⭐ POLISH 1: Filter out primary failure to avoid duplication
            const additionalFindings = summary.findings?.filter(
              f => !summary.primaryFailure || f.code !== summary.primaryFailure.code
            ) || [];
            
            // Only show section if there are findings OR if we want to show a message
            if (summary.findings?.length === 0) return null;
            
            return (
              <Paper elevation={2} sx={{ p: 2, mb: 3 }}>
                <Typography variant="h6" sx={{ mb: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
                  {summary.primaryFailure ? 'Additional Findings' : 'Findings'}
                  {getHealthFromSummary().overall === 'PASS' && getHealthFromSummary().hasWarnings && (
                    <Chip 
                      label="Advisory" 
                      size="small" 
                      color="warning" 
                      variant="outlined"
                      sx={{ fontSize: '0.7rem' }}
                    />
                  )}
                </Typography>
                
                {additionalFindings.length === 0 && summary.primaryFailure ? (
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
                        height: '20px',
                        fontSize: '0.7rem'
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
                            <Typography variant="body2" component="div" sx={{ fontSize: '0.875rem' }}>
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
                                  fontSize: '0.8rem',
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
                            sx={{ mb: 0.5, fontSize: '0.875rem' }}
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

