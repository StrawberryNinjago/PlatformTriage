import { useState } from 'react';
import { Container, Box, Tabs, Tab, Chip, AppBar, Toolbar, Typography } from '@mui/material';
import { ThemeProvider, createTheme } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import ConsolePanel from './components/ConsolePanel';
import DBDoctorPage from './pages/DBDoctorPage';
import DeploymentDoctorPage from './pages/DeploymentDoctorPage';
import ExportsPage from './pages/ExportsPage';
import HelpPage from './pages/HelpPage';
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
  const [k8sStatus, setK8sStatus] = useState('not configured');
  const [consoleMessages, setConsoleMessages] = useState([]);
  const [activeModuleTab, setActiveModuleTab] = useState(0);

  const addConsoleMessage = (text, type = 'info') => {
    setConsoleMessages(prev => [...prev, { text, type }]);
  };

  const handleModuleTabChange = (event, newValue) => {
    setActiveModuleTab(newValue);
  };

  const getConnectionStatusColor = () => {
    return connectionStatus === 'connected' ? 'success' : 'default';
  };

  const getK8sStatusColor = () => {
    return k8sStatus === 'connected' ? 'success' : 'default';
  };

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
        {/* Global App Bar */}
        <AppBar position="static" elevation={2}>
          <Toolbar>
            <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            Triage Platform
            </Typography>
            
            {/* Status Chips */}
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Chip 
                label={`DB: ${connectionStatus}`}
                color={getConnectionStatusColor()}
                size="small"
                sx={{ 
                  bgcolor: connectionStatus === 'connected' ? 'success.main' : 'grey.500',
                  color: 'white',
                  fontWeight: 'bold'
                }}
              />
              <Chip 
                label={`K8s: ${k8sStatus}`}
                color={getK8sStatusColor()}
                size="small"
                sx={{ 
                  bgcolor: k8sStatus === 'connected' ? 'success.main' : 'grey.500',
                  color: 'white',
                  fontWeight: 'bold'
                }}
              />
            </Box>
          </Toolbar>
        </AppBar>

        {/* Module Navigation Tabs */}
        <Box sx={{ borderBottom: 1, borderColor: 'divider', bgcolor: 'white' }}>
          <Container maxWidth="xl">
            <Tabs 
              value={activeModuleTab} 
              onChange={handleModuleTabChange}
              sx={{ 
                '& .MuiTab-root': { 
                  textTransform: 'none',
                  fontSize: '1rem',
                  fontWeight: 500,
                  minWidth: 120
                }
              }}
            >
              <Tab label="DB Doctor" />
              <Tab label="Deployment Doctor" />
              <Tab label="Exports" />
              <Tab label="Help" />
            </Tabs>
          </Container>
        </Box>

        {/* Page Content */}
        <Box sx={{ flexGrow: 1, bgcolor: '#f5f5f5' }}>
          {activeModuleTab === 0 && (
            <DBDoctorPage
              connectionId={connectionId}
              setConnectionId={setConnectionId}
              connectionStatus={connectionStatus}
              setConnectionStatus={setConnectionStatus}
              addConsoleMessage={addConsoleMessage}
            />
          )}

          {activeModuleTab === 1 && (
            <DeploymentDoctorPage
              addConsoleMessage={addConsoleMessage}
              k8sStatus={k8sStatus}
              setK8sStatus={setK8sStatus}
            />
          )}

          {activeModuleTab === 2 && (
            <ExportsPage
              addConsoleMessage={addConsoleMessage}
            />
          )}

          {activeModuleTab === 3 && (
            <HelpPage />
          )}
        </Box>

        {/* Console Panel - Full Width at Bottom (Shared across all tabs) */}
        <Container maxWidth="xl" sx={{ pb: 3 }}>
          <ConsolePanel messages={consoleMessages} />
        </Container>
      </Box>
    </ThemeProvider>
  );
}

export default App;
