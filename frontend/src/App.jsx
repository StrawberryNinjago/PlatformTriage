import { useState } from 'react';
import { Container, Grid, Paper, Typography, Box } from '@mui/material';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import ConnectionForm from './components/ConnectionForm';
import ActionButtons from './components/ActionButtons';
import SummaryPanel from './components/SummaryPanel';
import ResultsPanel from './components/ResultsPanel';
import ConsolePanel from './components/ConsolePanel';
import { apiService } from './services/apiService';
import './App.css';

const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#1976d2',
    },
  },
});

function App() {
  const [connectionId, setConnectionId] = useState(null);
  const [connectionStatus, setConnectionStatus] = useState('disconnected');
  const [summaryData, setSummaryData] = useState(null);
  const [results, setResults] = useState(null);
  const [consoleMessages, setConsoleMessages] = useState([]);
  const [schema, setSchema] = useState('public');

  const addConsoleMessage = (text, type = 'info') => {
    setConsoleMessages(prev => [...prev, { text, type }]);
  };

  const handleConnect = async (formData) => {
    try {
      const response = await apiService.connect(formData);
      setConnectionId(response.data.connectionId);
      setConnectionStatus('connected');
      setSchema(formData.schema || 'public');
      addConsoleMessage('✓ Connected successfully', 'success');
      setResults(response.data);
    } catch (error) {
      setConnectionStatus('disconnected');
      addConsoleMessage(`✗ Connection failed: ${error.response?.data?.message || error.message}`, 'error');
      throw error;
    }
  };

  const handleLoadSummary = async () => {
    if (!connectionId) return;
    try {
      const response = await apiService.getSummary(connectionId);
      setSummaryData(response.data);
      setResults(response.data);
      addConsoleMessage('✓ Summary loaded', 'success');
    } catch (error) {
      addConsoleMessage(`✗ Failed to load summary: ${error.message}`, 'error');
      throw error;
    }
  };

  const handleAction = async (actionName, params) => {
    if (!connectionId) {
      addConsoleMessage('✗ Not connected. Please connect first.', 'error');
      return;
    }

    try {
      let response;
      switch (actionName) {
        case 'verify-connection':
          response = await apiService.getIdentity(connectionId);
          addConsoleMessage('✓ Identity verified', 'success');
          break;

        case 'flyway-health':
          response = await apiService.getFlywayHealth(connectionId);
          setSummaryData(prev => ({ ...prev, flyway: response.data }));
          addConsoleMessage(`✓ Flyway health: ${response.data.status}`, 
            response.data.status === 'HEALTHY' ? 'success' : 'warning');
          break;

        case 'list-tables':
          response = await apiService.getTables(connectionId, schema);
          addConsoleMessage(`✓ Found ${response.data.tables.length} tables`, 'success');
          break;

        case 'find-table':
          response = await apiService.searchTables(connectionId, schema, params.searchQuery);
          addConsoleMessage(`✓ Found ${response.data.tables?.length || 0} matching tables`, 'success');
          break;

        case 'check-ownership':
          response = await apiService.getPrivileges(connectionId, schema, params.tableName);
          setSummaryData(prev => ({ ...prev, privileges: response.data }));
          addConsoleMessage(`✓ Privileges checked: ${response.data.status}`, 
            response.data.status === 'PASS' ? 'success' : 'warning');
          break;

        case 'table-details':
          response = await apiService.getTableIntrospect(connectionId, schema, params.tableName);
          addConsoleMessage(`✓ Table details retrieved for ${params.tableName}`, 'success');
          break;

        default:
          addConsoleMessage(`Unknown action: ${actionName}`, 'error');
          return;
      }

      setResults(response.data);
    } catch (error) {
      const errorMsg = error.response?.data?.message || error.message;
      addConsoleMessage(`✗ ${actionName} failed: ${errorMsg}`, 'error');
    }
  };

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Box sx={{ minHeight: '100vh', bgcolor: '#f5f5f5' }}>
        <Box sx={{ bgcolor: 'primary.main', color: 'white', p: 2 }}>
          <Container maxWidth="xl">
            <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <Typography variant="h5" component="h1">
                PlatformTriage – DB Doctor (MVP)
              </Typography>
              <Typography variant="body2">
                Connection: {connectionStatus}
              </Typography>
            </Box>
          </Container>
        </Box>

        <Container maxWidth="xl" sx={{ mt: 3, pb: 3, height: 'calc(100vh - 120px)', display: 'flex', flexDirection: 'column' }}>
          <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', minHeight: 0 }}>
            <Grid container spacing={3} sx={{ flex: 1, minHeight: 0 }}>
              {/* Left Panel */}
              <Grid item xs={12} md={4} sx={{ display: 'flex', flexDirection: 'column' }}>
                <Paper elevation={2}>
                  <ConnectionForm
                    onConnect={handleConnect}
                    onLoadSummary={handleLoadSummary}
                    isConnected={connectionStatus === 'connected'}
                  />
                </Paper>

                <Paper elevation={2} sx={{ mt: 2, flex: 1, overflow: 'auto' }}>
                  <ActionButtons
                    isConnected={connectionStatus === 'connected'}
                    schema={schema}
                    onAction={handleAction}
                  />
                </Paper>
              </Grid>

              {/* Right Panel */}
              <Grid item xs={12} md={8} sx={{ display: 'flex', flexDirection: 'column', minHeight: 0 }}>
                <SummaryPanel
                  connectionStatus={connectionStatus}
                  summaryData={summaryData}
                />

                <Box sx={{ flex: 1, minHeight: 0 }}>
                  <ResultsPanel
                    results={results}
                    onClear={() => setResults(null)}
                  />
                </Box>
              </Grid>
            </Grid>

            {/* Console Panel - Fixed at Bottom */}
            <Box sx={{ mt: 2, flexShrink: 0 }}>
              <ConsolePanel messages={consoleMessages} />
            </Box>
          </Box>
        </Container>
      </Box>
    </ThemeProvider>
  );
}

export default App;
