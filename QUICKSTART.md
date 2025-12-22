# Quick Start Guide

Get PlatformTriage DB Doctor running in 5 minutes.

## Step 1: Start the Backend

```bash
cd /Users/yanalbright/Downloads/Triage/apps/dbtriage
mvn spring-boot:run
```

Wait for: `Started TriageApplication in X seconds`

Backend is now running on **http://localhost:8081**

## Step 2: Start the Frontend

Open a new terminal:

```bash
cd /Users/yanalbright/Downloads/Triage/frontend
npm run dev
```

Wait for: `Local: http://localhost:3000/`

Frontend is now running on **http://localhost:3000**

## Step 3: Open the UI

Open your browser to: **http://localhost:3000**

You should see the PlatformTriage interface.

## Step 4: Connect to Your Database

Fill in the connection form:

- **Host**: your-server.postgres.database.azure.com (or localhost)
- **Port**: 5432
- **Database**: postgres (or your database name)
- **Username**: your-username
- **Password**: your-password
- **SSL Mode**: require (for Azure) or disable (for local)
- **Schema**: public (or your schema)

Click **Connect**

## Step 5: Run Diagnostics

Try these actions in order:

1. Click **"Verify Connection (DB Identity)"**
   - Confirms you're connected to the right database

2. Click **"Flyway Health"**
   - Shows migration status

3. Click **"List App Tables"**
   - Shows all application tables

4. Enter a table name (e.g., `cart_item`) and click **"Table Details"**
   - Shows complete table structure

5. With the same table, click **"Check Ownership & Grants"**
   - Validates your permissions

## What You'll See

- **Summary Panel**: Connection status, Flyway status, Privileges status
- **Results Panel**: JSON output from each action
- **Console Panel**: Success/error messages and tips

## Common Issues

### "Connection failed"
- Check your database is running
- Verify credentials
- For Azure: use SSL mode "require"
- For local: use SSL mode "disable"

### "Port 8081 already in use"
- Another app is using port 8081
- Stop it or change the port in `application.yaml`

### "Port 3000 already in use"
- Another app is using port 3000
- Stop it or change the port in `vite.config.js`

## Next Steps

- Try the **Find Table** action to search for tables
- Use **List Indexes** to check index coverage
- Run **Check Ownership & Grants** on multiple tables to validate permissions

## Stopping the Application

**Backend**: Press `Ctrl+C` in the backend terminal

**Frontend**: Press `Ctrl+C` in the frontend terminal

## Need Help?

See the full [README.md](README.md) for detailed documentation.

