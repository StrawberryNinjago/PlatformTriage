import { Box, Typography, Paper, Alert } from '@mui/material';

export default function ConsolePanel({ messages }) {
  return (
    <Paper sx={{ p: 2, height: '200px', display: 'flex', flexDirection: 'column' }}>
      <Typography variant="h6" gutterBottom sx={{ flexShrink: 0 }}>
        Console
      </Typography>

      <Box 
        sx={{ 
          display: 'flex', 
          flexDirection: 'column', 
          gap: 1,
          flex: 1,
          overflowY: 'auto',
          overflowX: 'hidden',
          minHeight: 0,
          pr: 1
        }}
      >
        {messages && messages.length > 0 ? (
          messages.map((msg, idx) => (
            <Alert key={idx} severity={msg.type || 'info'} sx={{ fontSize: '0.875rem' }}>
              {msg.text}
            </Alert>
          ))
        ) : (
          <Alert severity="info" sx={{ fontSize: '0.875rem' }}>
            Tip: Start with "Verify Connection (DB Identity)" → "Flyway Health" → "List App Tables".
          </Alert>
        )}
      </Box>
    </Paper>
  );
}

