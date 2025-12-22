import { Box, Typography, Chip, Paper } from '@mui/material';

export default function SummaryPanel({ connectionStatus, summaryData }) {
  const getStatusColor = (status) => {
    if (status === 'HEALTHY' || status === 'PASS') return 'success';
    if (status === 'DEGRADED' || status === 'WARNING') return 'warning';
    if (status === 'FAILED' || status === 'FAIL') return 'error';
    return 'default';
  };

  return (
    <Paper sx={{ p: 2, mb: 2 }}>
      <Typography variant="h6" gutterBottom>
        Summary
      </Typography>

      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
        <Chip
          label={`Connection: ${connectionStatus}`}
          color={connectionStatus === 'connected' ? 'success' : 'default'}
          size="small"
        />

        {summaryData?.flyway && (
          <Chip
            label={`Flyway: ${summaryData.flyway.status || 'N/A'}`}
            color={getStatusColor(summaryData.flyway.status)}
            size="small"
          />
        )}

        {summaryData?.privileges && (
          <Chip
            label={`Privileges: ${summaryData.privileges.status || 'N/A'}`}
            color={getStatusColor(summaryData.privileges.status)}
            size="small"
          />
        )}
      </Box>

      {connectionStatus === 'disconnected' && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
          Ready. Enter connection info and click Connect.
        </Typography>
      )}
    </Paper>
  );
}

