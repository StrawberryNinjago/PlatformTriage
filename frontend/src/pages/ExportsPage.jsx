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
  Divider
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import HistoryIcon from '@mui/icons-material/History';

function ExportsPage({ addConsoleMessage }) {
  
  const handleExportJiraBundle = () => {
    addConsoleMessage('🚧 JIRA bundle export coming soon...', 'info');
  };

  const handleCopyAllDiagnostics = () => {
    addConsoleMessage('🚧 Copy all diagnostics coming soon...', 'info');
  };

  const handleViewHistory = () => {
    addConsoleMessage('🚧 Export history coming soon...', 'info');
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
        {/* JIRA Bundle Export */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <DownloadIcon sx={{ mr: 1, color: 'primary.main' }} />
                <Typography variant="h6">
                  JIRA Bundle Export
                </Typography>
              </Box>
              <Typography variant="body2" color="textSecondary">
                Generate a comprehensive diagnostics bundle formatted for JIRA tickets. 
                Includes connection details, schema drift, SQL analysis results, and deployment status.
              </Typography>
              <Divider sx={{ my: 2 }} />
              <Typography variant="body2">
                <strong>Includes:</strong>
              </Typography>
              <ul style={{ marginTop: '8px', paddingLeft: '20px' }}>
                <li>Database connection summary</li>
                <li>Schema comparison results</li>
                <li>SQL validation findings</li>
                <li>Flyway migration status</li>
                <li>Table diagnostics</li>
              </ul>
            </CardContent>
            <CardActions>
              <Button 
                variant="contained" 
                startIcon={<DownloadIcon />}
                onClick={handleExportJiraBundle}
                disabled
              >
                Export JIRA Bundle
              </Button>
            </CardActions>
          </Card>
        </Grid>

        {/* Copy All Diagnostics */}
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <ContentCopyIcon sx={{ mr: 1, color: 'primary.main' }} />
                <Typography variant="h6">
                  Copy All Diagnostics
                </Typography>
              </Box>
              <Typography variant="body2" color="textSecondary">
                Copy all current diagnostic information to clipboard in a formatted text format. 
                Useful for quick sharing via Slack, email, or documentation.
              </Typography>
              <Divider sx={{ my: 2 }} />
              <Typography variant="body2">
                <strong>Format:</strong>
              </Typography>
              <ul style={{ marginTop: '8px', paddingLeft: '20px' }}>
                <li>Plain text markdown</li>
                <li>Includes timestamps</li>
                <li>Collapsible sections</li>
                <li>Ready to paste</li>
              </ul>
            </CardContent>
            <CardActions>
              <Button 
                variant="contained" 
                startIcon={<ContentCopyIcon />}
                onClick={handleCopyAllDiagnostics}
                disabled
              >
                Copy to Clipboard
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
                View and re-download previous diagnostic exports. Track when diagnostics were captured 
                and compare historical data.
              </Typography>
              <Alert severity="info">
                No export history available yet. Export history will appear here after you create your first export.
              </Alert>
            </CardContent>
            <CardActions>
              <Button 
                variant="outlined" 
                startIcon={<HistoryIcon />}
                onClick={handleViewHistory}
                disabled
              >
                View History
              </Button>
            </CardActions>
          </Card>
        </Grid>
      </Grid>

      <Box sx={{ mt: 4 }}>
        <Alert severity="warning">
          <Typography variant="subtitle2" sx={{ fontWeight: 'bold', mb: 1 }}>
            Coming Soon
          </Typography>
          Export functionality is under development. These features will allow you to:
          <ul style={{ marginTop: '8px', marginBottom: 0 }}>
            <li>Generate comprehensive diagnostic bundles</li>
            <li>Export data in multiple formats (JSON, Markdown, PDF)</li>
            <li>Schedule automated exports</li>
            <li>Integrate with ticketing systems</li>
          </ul>
        </Alert>
      </Box>
    </Container>
  );
}

export default ExportsPage;

