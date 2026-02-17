import { useState } from 'react';
import {
  Box,
  TextField,
  Button,
  Chip,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Typography,
  Alert
} from '@mui/material';

export default function ConnectionForm({ formData, setFormData, onConnect, onLoadSummary, isConnected }) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const fieldSx = {
    '& .MuiInputBase-root': {
      minHeight: 60
    },
    '& .MuiInputBase-input': {
      fontSize: '1.34rem',
      py: 1.25,
      fontWeight: 400
    },
    '& .MuiInputLabel-root': {
      fontSize: '1.24rem',
      fontWeight: 600
    },
    '& .MuiInputLabel-root.MuiInputLabel-shrink': {
      fontSize: '1.1rem',
      fontWeight: 600,
      transform: 'translate(14px, -10px) scale(0.85)'
    }
  };

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleConnect = async () => {
    setLoading(true);
    setError(null);
    try {
      await onConnect(formData);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Connection failed');
    } finally {
      setLoading(false);
    }
  };

  const handleLoadSummary = async () => {
    setLoading(true);
    setError(null);
    try {
      await onLoadSummary();
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Failed to load summary');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box
      sx={{
        p: { xs: 1.5, md: 2 },
        '& .MuiSelect-select': { fontSize: '1.34rem', fontWeight: 400 },
        '& .MuiFormHelperText-root': { fontSize: '1.05rem' },
        '& .MuiButton-root': { fontSize: '1.14rem', fontWeight: 700, minHeight: 52 },
        '& .MuiMenuItem-root': { fontSize: '1.08rem' }
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1.25 }}>
        <Typography variant="h4" sx={{ fontWeight: 900 }}>
          Connection
        </Typography>
        <Chip
          label="Postgres"
          size="medium"
          variant="outlined"
          color="primary"
          sx={{ '& .MuiChip-label': { fontSize: '1rem', fontWeight: 700 } }}
        />
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: {
            xs: '1fr',
            sm: 'repeat(2, minmax(0, 1fr))',
            lg: 'repeat(4, minmax(0, 1fr))'
          },
          gap: 1.25
        }}
      >
        <TextField
          fullWidth
          label="Host"
          name="host"
          value={formData.host}
          onChange={handleChange}
          sx={{ ...fieldSx, gridColumn: { xs: '1 / -1', lg: 'span 2' } }}
          size="medium"
        />

        <TextField
          fullWidth
          label="Port"
          name="port"
          type="number"
          value={formData.port}
          onChange={handleChange}
          sx={fieldSx}
          size="medium"
        />

        <TextField
          fullWidth
          label="Database"
          name="database"
          value={formData.database}
          onChange={handleChange}
          sx={fieldSx}
          size="medium"
        />

        <TextField
          fullWidth
          label="Username"
          name="username"
          value={formData.username}
          onChange={handleChange}
          sx={fieldSx}
          size="medium"
        />

        <TextField
          fullWidth
          label="Password / Token"
          name="password"
          type="password"
          value={formData.password}
          onChange={handleChange}
          sx={fieldSx}
          size="medium"
        />

        <FormControl fullWidth size="medium" sx={fieldSx}>
          <InputLabel>SSL Mode</InputLabel>
          <Select
            name="sslMode"
            value={formData.sslMode}
            onChange={handleChange}
            label="SSL Mode"
          >
            <MenuItem value="require">require</MenuItem>
            <MenuItem value="disable">disable</MenuItem>
            <MenuItem value="verify-full">verify-full</MenuItem>
          </Select>
        </FormControl>

        <TextField
          fullWidth
          label="Schema"
          name="schema"
          value={formData.schema}
          onChange={handleChange}
          sx={fieldSx}
          size="medium"
        />
      </Box>

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, gap: 1.25, mt: 1.5 }}>
        <Button
          variant="contained"
          onClick={handleConnect}
          disabled={loading || !formData.password}
        >
          {loading ? 'Connecting...' : 'Connect'}
        </Button>
        <Button
          variant="outlined"
          onClick={handleLoadSummary}
          disabled={loading || !isConnected}
        >
          Load Summary
        </Button>
      </Box>

      <Typography variant="body1" color="text.secondary" sx={{ display: 'block', mt: 1.25, fontSize: '1.2rem', fontWeight: 500 }}>
        This UI is diagnosis-first (read-only checks). It is not a DB admin client.
      </Typography>
    </Box>
  );
}
