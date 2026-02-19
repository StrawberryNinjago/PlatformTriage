import { 
  Container, 
  Paper, 
  Typography, 
  Box,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  Alert,
  Divider,
  Link,
  Chip
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';

function HelpPage() {
  
  return (
    <Container maxWidth="xl" sx={{ mt: 3, pb: 3 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
        <HelpOutlineIcon sx={{ mr: 1, fontSize: 32, color: 'primary.main' }} />
        <Typography variant="h5">
          Help & Documentation
        </Typography>
      </Box>

      <Alert severity="info" sx={{ mb: 3 }}>
        PlatformTriage is a comprehensive diagnostic tool for database and deployment troubleshooting.
      </Alert>

      <Paper elevation={2} sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Overview
        </Typography>
        <Typography variant="body1" paragraph>
          PlatformTriage helps you diagnose and resolve issues across your database and Kubernetes deployments. 
          It provides tools for schema validation, SQL analysis, environment comparison, and deployment health monitoring.
        </Typography>
      </Paper>

      {/* DB Doctor Help */}
      <Accordion defaultExpanded>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Box>
            <Typography variant="h6">
              DB Doctor
            </Typography>
            <Typography variant="body2" color="textSecondary">
              Database diagnostics and schema management
            </Typography>
          </Box>
        </AccordionSummary>
        <AccordionDetails>
          <Box>
            <Typography variant="subtitle1" sx={{ fontWeight: 'bold', mb: 1 }}>
              Connection & Setup
            </Typography>
            <Typography variant="body2" paragraph>
              1. Enter your database connection details (host, port, database, username, password)<br/>
              2. Click <strong>Connect</strong> to establish a connection<br/>
              3. Use <strong>Load Summary</strong> to fetch database metadata
            </Typography>

            <Divider sx={{ my: 2 }} />

            <Typography variant="subtitle1" sx={{ fontWeight: 'bold', mb: 1 }}>
              Single Environment Tab
            </Typography>
            <Typography variant="body2" paragraph>
              • <strong>SQL Sandbox:</strong> Test and validate SQL queries before deployment<br/>
              • <strong>Table Search:</strong> Find tables by name or pattern<br/>
              • <strong>Table Diagnostics:</strong> Analyze table structure, indexes, and constraints<br/>
              • <strong>Flyway Health:</strong> Check migration status and version history
            </Typography>

            <Divider sx={{ my: 2 }} />

            <Typography variant="subtitle1" sx={{ fontWeight: 'bold', mb: 1 }}>
              Compare Environments Tab
            </Typography>
            <Typography variant="body2" paragraph>
              Compare schema differences between two environments (e.g., dev vs prod):<br/>
              • Identify missing or extra tables<br/>
              • Detect column type mismatches<br/>
              • Find constraint differences<br/>
              • Export comparison results
            </Typography>
          </Box>
        </AccordionDetails>
      </Accordion>

      {/* Deployment Doctor Help */}
      <Accordion>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Box>
            <Typography variant="h6">
              Deployment Doctor
            </Typography>
            <Typography variant="body2" color="textSecondary">
              Kubernetes deployment monitoring
            </Typography>
          </Box>
        </AccordionSummary>
        <AccordionDetails>
          <Box>
            <Typography variant="body2" paragraph>
              Monitor your Kubernetes deployments and identify issues:
            </Typography>
            <Typography variant="body2" paragraph>
              • <strong>Service Health:</strong> Check pod status and replica counts<br/>
              • <strong>Events:</strong> View recent Kubernetes events and warnings<br/>
              • <strong>Image Versions:</strong> Track deployed container images<br/>
              • <strong>Issues:</strong> Automatic detection of common deployment problems
            </Typography>
            <Alert severity="info" sx={{ mt: 2 }}>
              Requires Kubernetes connectivity to be configured in the backend.
            </Alert>
          </Box>
        </AccordionDetails>
      </Accordion>

      {/* Exports Help */}
      <Accordion>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Box>
            <Typography variant="h6">
              Exports & Sharing
            </Typography>
            <Typography variant="body2" color="textSecondary">
              Export directly from each doctor page
            </Typography>
          </Box>
        </AccordionSummary>
        <AccordionDetails>
          <Box>
            <Typography variant="body2" paragraph>
              There is no separate Exports page. Use the in-page export actions:
            </Typography>
            <Typography variant="body2" paragraph>
              • <strong>Deployment Doctor:</strong> Use <strong>Export Diagnostics</strong> in Deployment Diagnostics Actions<br/>
              • <strong>DB Doctor:</strong> Use <strong>Export Diagnostics</strong> in the connected status rail<br/>
              • <strong>Smoke Tests:</strong> Download generated artifacts from the Generated Files section
            </Typography>
            <Alert severity="info" sx={{ mt: 2 }}>
              Each export downloads a JSON artifact you can attach to tickets or share with teams.
            </Alert>
          </Box>
        </AccordionDetails>
      </Accordion>

      {/* Tips & Best Practices */}
      <Paper elevation={2} sx={{ p: 3, mt: 3 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Tips & Best Practices
        </Typography>
        
        <Box sx={{ mb: 2 }}>
          <Chip label="Tip" color="primary" size="small" sx={{ mr: 1, mb: 1 }} />
          <Typography variant="body2" component="span">
            Always test SQL changes in the SQL Sandbox before running them in production
          </Typography>
        </Box>

        <Box sx={{ mb: 2 }}>
          <Chip label="Tip" color="primary" size="small" sx={{ mr: 1, mb: 1 }} />
          <Typography variant="body2" component="span">
            Use Environment Comparison before deploying schema changes to ensure consistency
          </Typography>
        </Box>

        <Box sx={{ mb: 2 }}>
          <Chip label="Tip" color="primary" size="small" sx={{ mr: 1, mb: 1 }} />
          <Typography variant="body2" component="span">
            Check the Console Panel at the bottom for detailed operation logs
          </Typography>
        </Box>

        <Box>
          <Chip label="Tip" color="primary" size="small" sx={{ mr: 1, mb: 1 }} />
          <Typography variant="body2" component="span">
            Use read-only credentials when possible to prevent accidental modifications
          </Typography>
        </Box>
      </Paper>

      {/* Contact & Support */}
      <Paper elevation={2} sx={{ p: 3, mt: 3, bgcolor: 'grey.50' }}>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Need Help?
        </Typography>
        <Typography variant="body2">
          For additional support or to report issues, contact your platform team or check the project documentation.
        </Typography>
      </Paper>
    </Container>
  );
}

export default HelpPage;
