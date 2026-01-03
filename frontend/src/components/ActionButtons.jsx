import { useState } from 'react';
import {
  Box,
  Button,
  TextField,
  Typography,
  Divider
} from '@mui/material';

export default function ActionButtons({
  isConnected,
  schema,
  onAction,
  currentAction
}) {
  const [tableName, setTableName] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  const handleTableNameChange = (e) => {
    setTableName(e.target.value);
  };

  const handleAction = (actionName, requiresTable = false) => {
    if (requiresTable && !tableName) {
      alert('Please enter a table name');
      return;
    }
    
    onAction(actionName, { tableName, searchQuery });
  };

  const getButtonStyle = (actionName) => {
    // Highlight button only if it's the current action being displayed
    if (currentAction === actionName) {
      return {
        boxShadow: '0 0 0 3px rgba(25, 118, 210, 0.3)',
        borderWidth: '2px',
        fontWeight: 'bold',
        transition: 'all 0.2s ease',
      };
    }
    return {};
  };

  return (
    <Box sx={{ p: 2 }}>
      <Typography variant="h6" gutterBottom>
        Actions
      </Typography>

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
        {/* Section 1: General Database Actions */}
        <Typography variant="subtitle2" sx={{ mt: 1, mb: 0.5, color: 'text.secondary', fontSize: '0.85rem' }}>
          📊 General Database
        </Typography>

        <Button
          variant={currentAction === 'verify-connection' ? 'contained' : 'outlined'}
          onClick={() => handleAction('verify-connection')}
          disabled={!isConnected}
          fullWidth
          size="small"
          sx={getButtonStyle('verify-connection')}
        >
          Verify Connection
        </Button>

        <Button
          variant={currentAction === 'flyway-health' ? 'contained' : 'outlined'}
          onClick={() => handleAction('flyway-health')}
          disabled={!isConnected}
          fullWidth
          size="small"
          sx={getButtonStyle('flyway-health')}
        >
          Flyway Health
        </Button>

        <Button
          variant={currentAction === 'list-tables' ? 'contained' : 'outlined'}
          onClick={() => handleAction('list-tables')}
          disabled={!isConnected}
          fullWidth
          size="small"
          sx={getButtonStyle('list-tables')}
        >
          List All Tables
        </Button>

        <Divider sx={{ my: 2 }} />

        {/* Section 2: Inspect Specific Table */}
        <Typography variant="subtitle2" sx={{ mb: 0.5, color: 'primary.main', fontSize: '0.85rem', fontWeight: 'bold' }}>
          🔬 Inspect Specific Table
        </Typography>
        <Typography variant="caption" sx={{ mb: 1, color: 'text.secondary', display: 'block' }}>
          Enter the exact table name you want to inspect
        </Typography>

        <TextField
          label="Enter exact table name"
          value={tableName}
          onChange={handleTableNameChange}
          size="small"
          placeholder="e.g. cart_item"
          fullWidth
          sx={{ '& .MuiOutlinedInput-root': { bgcolor: '#e3f2fd' } }}
        />

        <Button
          variant={currentAction === 'table-details' ? 'contained' : 'outlined'}
          onClick={() => handleAction('table-details', true)}
          disabled={!isConnected || !tableName}
          fullWidth
          size="small"
          sx={getButtonStyle('table-details')}
        >
          Show Table Details
        </Button>

        <Button
          variant={currentAction === 'check-ownership' ? 'contained' : 'outlined'}
          onClick={() => handleAction('check-ownership', true)}
          disabled={!isConnected || !tableName}
          fullWidth
          size="small"
          sx={getButtonStyle('check-ownership')}
        >
          Check Ownership & Grants
        </Button>

        <Divider sx={{ my: 2 }} />

        {/* Section 3: Search for Tables */}
        <Typography variant="subtitle2" sx={{ mb: 0.5, color: 'success.main', fontSize: '0.85rem', fontWeight: 'bold' }}>
          🔍 Search for Tables
        </Typography>
        <Typography variant="caption" sx={{ mb: 1, color: 'text.secondary', display: 'block' }}>
          Search for tables when you don't know the exact name
        </Typography>

        <TextField
          label="Search tables (partial name)"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          size="small"
          placeholder="e.g. cart (finds cart, cart_item, etc.)"
          fullWidth
          sx={{ '& .MuiOutlinedInput-root': { bgcolor: '#e8f5e9' } }}
        />

        <Button
          variant="contained"
          color={currentAction === 'find-table' ? 'primary' : 'success'}
          onClick={() => handleAction('find-table')}
          disabled={!isConnected || !searchQuery}
          fullWidth
          size="small"
          sx={currentAction === 'find-table' ? getButtonStyle('find-table') : {}}
        >
          Search Tables
        </Button>
      </Box>
    </Box>
  );
}

