import { useMemo, useState } from 'react';
import {
  Box,
  Button,
  Collapse,
  Divider,
  TextField,
  Typography
} from '@mui/material';

export default function ActionButtons({
  isConnected,
  onAction,
  currentAction
}) {
  const [tableName, setTableName] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [showAdvanced, setShowAdvanced] = useState(false);

  const handleAction = (actionName, requiresTable = false, customTableName = null) => {
    const selectedTable = customTableName ?? tableName;

    if (requiresTable && !selectedTable) {
      return;
    }

    onAction(actionName, {
      tableName: selectedTable,
      searchQuery
    });
  };

  const buttonStyle = (actionName) => {
    if (currentAction === actionName) {
      return {
        boxShadow: '0 0 0 3px rgba(25, 118, 210, 0.25)',
        borderWidth: '2px',
        fontWeight: 700,
        transition: 'all 0.2s ease',
      };
    }
    return {};
  };

  const noConnectionHint = useMemo(() => (
    !isConnected ? 'Connect first to enable actions.' : null
  ), [isConnected]);

  return (
    <Box sx={{ p: 1.5 }}>
      <Typography variant="subtitle2" sx={{ mb: 1 }}>
        Common Actions
      </Typography>

      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1.25 }}>
        Use AI for natural language tasks, or tap these quick workflows.
      </Typography>

      {noConnectionHint && (
        <Typography variant="caption" color="warning.main" sx={{ display: 'block', mb: 1 }}>
          {noConnectionHint}
        </Typography>
      )}

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.75 }}>
        <Button
          variant={currentAction === 'flyway-health' ? 'contained' : 'outlined'}
          onClick={() => handleAction('flyway-health')}
          disabled={!isConnected}
          fullWidth
          size="small"
          sx={buttonStyle('flyway-health')}
        >
          Flyway Health
        </Button>

        <Button
          variant={currentAction === 'list-tables' ? 'contained' : 'outlined'}
          onClick={() => handleAction('list-tables')}
          disabled={!isConnected}
          fullWidth
          size="small"
          sx={buttonStyle('list-tables')}
        >
          List All Tables
        </Button>

        <Divider sx={{ mt: 1, mb: 0.75 }}>
          <Typography variant="caption" color="text.secondary">
            Inspect a table
          </Typography>
        </Divider>

        <TextField
          label="Table name"
          value={tableName}
          onChange={(e) => setTableName(e.target.value)}
          size="small"
          placeholder="e.g. cart_item"
          fullWidth
          autoComplete="off"
          sx={{ '& .MuiOutlinedInput-root': { bgcolor: '#e8f0fe' } }}
        />

        <Button
          variant={currentAction === 'table-details' ? 'contained' : 'outlined'}
          onClick={() => handleAction('table-details', true)}
          disabled={!isConnected || !tableName}
          fullWidth
          size="small"
          sx={buttonStyle('table-details')}
        >
          Inspect Table
        </Button>

        <Divider sx={{ mt: 1, mb: 0.75 }}>
          <Typography variant="caption" color="text.secondary">
            Search tables
          </Typography>
        </Divider>

        <TextField
          label="Search tables"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          size="small"
          placeholder="e.g. cart (matches cart, cart_item, ...)"
          fullWidth
          autoComplete="off"
          sx={{ '& .MuiOutlinedInput-root': { bgcolor: '#e8f5e9' } }}
        />

        <Button
          variant={currentAction === 'find-table' ? 'contained' : 'outlined'}
          color="success"
          onClick={() => handleAction('find-table')}
          disabled={!isConnected || !searchQuery}
          fullWidth
          size="small"
          sx={buttonStyle('find-table')}
        >
          Search Tables
        </Button>

        <Button
          variant="text"
          onClick={() => setShowAdvanced((prev) => !prev)}
          size="small"
          sx={{ mt: 0.25, alignSelf: 'flex-start' }}
        >
          {showAdvanced ? 'Hide advanced action' : 'Show advanced action'}
        </Button>

        <Collapse in={showAdvanced} unmountOnExit>
          <Divider sx={{ my: 0.75 }} />

          <Typography variant="subtitle2" color="primary.main" sx={{ mb: 1 }}>
            Advanced
          </Typography>

          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
            Run this if you need grants checks after inspecting a table.
          </Typography>

          <Button
            variant={currentAction === 'check-ownership' ? 'contained' : 'outlined'}
            onClick={() => handleAction('check-ownership', true)}
            disabled={!isConnected || !tableName}
            fullWidth
            size="small"
            sx={buttonStyle('check-ownership')}
          >
            Check Ownership & Grants
          </Button>
        </Collapse>
      </Box>
    </Box>
  );
}
