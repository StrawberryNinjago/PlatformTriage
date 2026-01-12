import { useState, useEffect } from 'react';
import { 
  Container, 
  Paper, 
  Typography, 
  Box,
  Alert,
  Button,
  Card,
  CardContent,
  CardActions,
  Grid,
  Divider,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  CircularProgress,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import HistoryIcon from '@mui/icons-material/History';
import DatabaseIcon from '@mui/icons-material/Storage';
import CloudIcon from '@mui/icons-material/Cloud';
import { apiService } from '../services/apiService';

function ExportsPage({ addConsoleMessage }) {
  const [activeConnections, setActiveConnections] = useState([]);
  const [selectedConnectionId, setSelectedConnectionId] = useState('');
  const [namespace, setNamespace] = useState('cart');
  const [selector, setSelector] = useState('');
  const [release, setRelease] = useState('');
  const [loading, setLoading] = useState(false);
  const [exportHistory, setExportHistory] = useState([]);
  
  useEffect(() => {
    loadActiveConnections();
  }, []);

  const loadActiveConnections = async () => {
    try {
      const response = await apiService.listConnections();
      setActiveConnections(response.data || []);
      if (response.data && response.data.length > 0) {
        setSelectedConnectionId(response.data[0].connectionId);
      }
    } catch (error) {
      console.error('Failed to load connections:', error);
      addConsoleMessage('Failed to load active connections', 'error');
    }
  };
  
  const handleExportDbDiagnostics = async () => {
    if (!selectedConnectionId) {
      addConsoleMessage('Please select a database connection first', 'error');
      return;
    }
    
    setLoading(true);
    try {
      addConsoleMessage(`Exporting DB diagnostics for connection: ${selectedConnectionId}...`, 'info');
      const response = await apiService.exportDiagnosticsBundle(selectedConnectionId);
      
      // Download as JSON
      const dataStr = JSON.stringify(response.data, null, 2);
      const dataBlob = new Blob([dataStr], { type: 'application/json' });
      const url = URL.createObjectURL(dataBlob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `db-export-${selectedConnectionId}-${Date.now()}.json`;
      link.click();
      URL.revokeObjectURL(url);
      
      // Add to history
      const historyItem = {
        id: Date.now(),
        type: 'database',
        timestamp: new Date().toISOString(),
        identifier: selectedConnectionId,
        status: response.data.health?.status || 'UNKNOWN',
        data: response.data
      };
      setExportHistory(prev => [historyItem, ...prev]);
      
      addConsoleMessage('✓ DB diagnostics exported successfully', 'success');
    } catch (error) {
      console.error('Export failed:', error);
      addConsoleMessage(`✗ Export failed: ${error.response?.data?.message || error.message}`, 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleExportDeploymentDiagnostics = async () => {
    if (!namespace) {
      addConsoleMessage('Please enter a namespace', 'error');
      return;
    }
    
    setLoading(true);
    try {
      addConsoleMessage(`Exporting deployment diagnostics for namespace: ${namespace}...`, 'info');
      const response = await apiService.exportDeploymentDiagnostics(namespace, selector, release);
      
      // Download as JSON
      const dataStr = JSON.stringify(response.data, null, 2);
      const dataBlob = new Blob([dataStr], { type: 'application/json' });
      const url = URL.createObjectURL(dataBlob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `deployment-export-${namespace}-${Date.now()}.json`;
      link.click();
      URL.revokeObjectURL(url);
      
      // Add to history
      const historyItem = {
        id: Date.now(),
        type: 'deployment',
        timestamp: new Date().toISOString(),
        identifier: `${namespace}${selector ? '/' + selector : ''}`,
        status: response.data.health?.status || 'UNKNOWN',
        data: response.data
      };
      setExportHistory(prev => [historyItem, ...prev]);
      
      addConsoleMessage('✓ Deployment diagnostics exported successfully', 'success');
    } catch (error) {
      console.error('Export failed:', error);
      addConsoleMessage(`✗ Export failed: ${error.response?.data?.message || error.message}`, 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleCopyToClipboard = (data) => {
    const text = JSON.stringify(data, null, 2);
    navigator.clipboard.writeText(text);
    addConsoleMessage('✓ Copied to clipboard', 'success');
  };

  const handleDownloadHistoryItem = (item) => {
    const dataStr = JSON.stringify(item.data, null, 2);
    const dataBlob = new Blob([dataStr], { type: 'application/json' });
    const url = URL.createObjectURL(dataBlob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${item.type}-export-${item.identifier}-${item.timestamp}.json`;
    link.click();
    URL.revokeObjectURL(url);
    addConsoleMessage('✓ Downloaded export', 'success');
  };

  const getStatusColor = (status) => {
    switch (status?.toUpperCase()) {
      case 'HEALTHY':
      case 'OK':
      case 'PASS':
        return 'success';
      case 'WARNING':
      case 'WARN':
        return 'warning';
      case 'ERROR':
      case 'FAIL':
      case 'CRITICAL':
        return 'error';
      default:
        return 'default';
    }
  };

  return (
    <Container maxWidth="xl" sx={{ mt: 3, pb: 3 }}>
      <Typography variant="h5" sx={{ mb: 3 }}>
        Exports & Diagnostics
      </Typography>

      <Alert severity="info" sx={{ mb: 3 }}>
        Export diagnostic data for sharing with support teams or tracking historical issues.
      </Alert>

      <Grid container spacing={3}>
        {/* Database Export */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <DatabaseIcon sx={{ mr: 1, color: 'primary.main' }} />
                <Typography variant="h6">
                  Database Diagnostics Export
                </Typography>
              </Box>
              <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>
                Export comprehensive database diagnostics including connection details, 
                Flyway migration status, schema information, and health metrics.
              </Typography>
              
              <FormControl fullWidth sx={{ mb: 2 }}>
                <InputLabel>Select Connection</InputLabel>
                <Select
                  value={selectedConnectionId}
                  onChange={(e) => setSelectedConnectionId(e.target.value)}
                  label="Select Connection"
                  disabled={activeConnections.length === 0}
                >
                  {activeConnections.length === 0 ? (
                    <MenuItem value="">No active connections</MenuItem>
                  ) : (
                    activeConnections.map((conn) => (
                      <MenuItem key={conn.connectionId} value={conn.connectionId}>
                        {conn.host}:{conn.port}/{conn.database} ({conn.username})
                      </MenuItem>
                    ))
                  )}
                </Select>
              </FormControl>

              <Divider sx={{ my: 2 }} />
              <Typography variant="body2">
                <strong>Includes:</strong>
              </Typography>
              <ul style={{ marginTop: '8px', paddingLeft: '20px' }}>
                <li>Database connection summary</li>
                <li>Flyway migration status</li>
                <li>Schema object counts</li>
                <li>Health metrics and findings</li>
              </ul>
            </CardContent>
            <CardActions>
              <Button 
                variant="contained" 
                startIcon={loading ? <CircularProgress size={20} /> : <DownloadIcon />}
                onClick={handleExportDbDiagnostics}
                disabled={loading || !selectedConnectionId || activeConnections.length === 0}
              >
                Export DB Diagnostics
              </Button>
            </CardActions>
          </Card>
        </Grid>

        {/* Deployment Export */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <CloudIcon sx={{ mr: 1, color: 'primary.main' }} />
                <Typography variant="h6">
                  Deployment Diagnostics Export
                </Typography>
              </Box>
              <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>
                Export Kubernetes deployment diagnostics including pod status, 
                health metrics, findings, and troubleshooting recommendations.
              </Typography>
              
              <Stack spacing={2}>
                <TextField
                  fullWidth
                  label="Namespace"
                  value={namespace}
                  onChange={(e) => setNamespace(e.target.value)}
                  required
                  placeholder="e.g., cart"
                />
                <TextField
                  fullWidth
                  label="Selector (optional)"
                  value={selector}
                  onChange={(e) => setSelector(e.target.value)}
                  placeholder="e.g., app=cart-app"
                />
                <TextField
                  fullWidth
                  label="Release (optional)"
                  value={release}
                  onChange={(e) => setRelease(e.target.value)}
                  placeholder="e.g., my-release"
                />
              </Stack>

              <Divider sx={{ my: 2 }} />
              <Typography variant="body2">
                <strong>Includes:</strong>
              </Typography>
              <ul style={{ marginTop: '8px', paddingLeft: '20px' }}>
                <li>Deployment health status</li>
                <li>Pod metrics and states</li>
                <li>Findings and recommendations</li>
                <li>Kubernetes object details</li>
              </ul>
            </CardContent>
            <CardActions>
              <Button 
                variant="contained" 
                startIcon={loading ? <CircularProgress size={20} /> : <DownloadIcon />}
                onClick={handleExportDeploymentDiagnostics}
                disabled={loading || !namespace}
              >
                Export Deployment Diagnostics
              </Button>
            </CardActions>
          </Card>
        </Grid>

        {/* Export History */}
        <Grid item xs={12}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <HistoryIcon sx={{ mr: 1, color: 'primary.main' }} />
                <Typography variant="h6">
                  Export History
                </Typography>
              </Box>
              <Typography variant="body2" color="textSecondary" sx={{ mb: 2 }}>
                View and re-download previous diagnostic exports.
              </Typography>
              
              {exportHistory.length === 0 ? (
                <Alert severity="info">
                  No export history available yet. Export history will appear here after you create your first export.
                </Alert>
              ) : (
                <Box sx={{ overflowX: 'auto' }}>
                  <Table>
                    <TableHead>
                      <TableRow>
                        <TableCell>Type</TableCell>
                        <TableCell>Identifier</TableCell>
                        <TableCell>Timestamp</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {exportHistory.map((item) => (
                        <TableRow key={item.id}>
                          <TableCell>
                            <Chip 
                              icon={item.type === 'database' ? <DatabaseIcon /> : <CloudIcon />}
                              label={item.type}
                              size="small"
                              color={item.type === 'database' ? 'primary' : 'secondary'}
                            />
                          </TableCell>
                          <TableCell>{item.identifier}</TableCell>
                          <TableCell>{new Date(item.timestamp).toLocaleString()}</TableCell>
                          <TableCell>
                            <Chip 
                              label={item.status}
                              size="small"
                              color={getStatusColor(item.status)}
                            />
                          </TableCell>
                          <TableCell>
                            <Button
                              size="small"
                              startIcon={<DownloadIcon />}
                              onClick={() => handleDownloadHistoryItem(item)}
                            >
                              Download
                            </Button>
                            <Button
                              size="small"
                              startIcon={<ContentCopyIcon />}
                              onClick={() => handleCopyToClipboard(item.data)}
                            >
                              Copy
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </Box>
              )}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Container>
  );
}

export default ExportsPage;

