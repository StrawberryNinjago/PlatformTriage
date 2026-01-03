# SQL Diagnostic Sandbox - Quick Start Guide

## 🚀 Getting Started in 5 Minutes

### Prerequisites
- Java 21+
- Maven 3.6+
- Node.js 18+
- PostgreSQL database (for testing)

---

## Step 1: Start the Backend

```bash
# Navigate to project root
cd /Users/yanalbright/Downloads/Triage

# Start the Spring Boot application
cd apps/dbtriage
mvn spring-boot:run
```

The backend will start on **http://localhost:8081**

---

## Step 2: Start the Frontend

```bash
# In a new terminal, navigate to frontend
cd /Users/yanalbright/Downloads/Triage/frontend

# Install dependencies (if not already done)
npm install

# Start the development server
npm run dev
```

The frontend will start on **http://localhost:5173**

---

## Step 3: Connect to Your Database

1. Open **http://localhost:5173** in your browser
2. Fill in the connection form:
   - **Host**: localhost (or your DB host)
   - **Port**: 5432
   - **Database**: your_database_name
   - **Username**: your_username
   - **Password**: your_password
   - **Schema**: public (or your schema)
3. Click **"Connect"**
4. Wait for "✓ Connected successfully" message

---

## Step 4: Use the SQL Sandbox

1. Scroll down to find **"🧪 SQL Diagnostic Sandbox (Static Analysis)"**
2. Click to expand the panel
3. Paste your SQL statement (see examples below)
4. Click **"Analyze SQL"**
5. Review the results

---

## 📝 Example SQL Statements to Try

### Example 1: SELECT with Potential Index Issues

```sql
SELECT * FROM cart_item
WHERE cart_id = 123
  AND los_id = 456
  AND product_code = 'ABC'
```

**What to look for:**
- Index coverage analysis
- Suggestions for composite indexes
- Performance warnings

---

### Example 2: INSERT with Missing Columns

```sql
INSERT INTO cart_item (cart_id, product_code)
VALUES (123, 'ABC')
```

**What to look for:**
- Missing NOT NULL columns warnings
- Unique constraint alerts
- Foreign key validation notices

---

### Example 3: UPDATE with Constraint Risks

```sql
UPDATE cart_item
SET quantity = 5
WHERE cart_id = 123
```

**What to look for:**
- Update hot-spot detection
- Constraint violation risks
- Performance impact warnings

---

### Example 4: DELETE with Cascade Analysis

```sql
DELETE FROM cart
WHERE cart_id = 123
```

**What to look for:**
- Cascading foreign key warnings
- List of affected tables
- Recursive cascade detection
- Cascade depth estimation

---

## 🎯 Understanding the Results

### Severity Levels

- **🔴 ERROR**: Critical issues that will cause runtime failures
- **🟡 WARN**: Potential problems or performance issues
- **🔵 INFO**: Informational messages about query structure

### Result Sections

#### 1. Findings
General issues and recommendations categorized by:
- Index Coverage
- Constraint Validation
- Dangerous Operations
- Performance Impact

#### 2. Index Analysis
- **Composite Index**: Whether a composite index covers all WHERE columns
- **Partial Coverage**: Whether only some columns are indexed
- **Matched Indexes**: Existing indexes that apply to the query
- **Suggested Indexes**: SQL statements to create missing indexes

#### 3. Constraint Risks
- **Missing NOT NULL Columns**: Required columns not provided
- **Unique Constraint Columns**: Columns that must have unique values
- **Foreign Key Columns**: Columns referencing other tables

#### 4. Cascade Analysis (DELETE only)
- **Cascading Foreign Keys**: Number of tables affected by cascade delete
- **Affected Tables**: List of tables that will be affected
- **Recursive Cascade**: Whether the cascade is self-referential
- **Cascade Depth**: Estimated impact (LOW, MEDIUM, HIGH)

---

## 🛠️ Testing with Sample Database

If you want to test with the sample cart database:

```bash
# Start the PostgreSQL container
cd /Users/yanalbright/Downloads/Triage/apps/dbtriage/cart-pg
docker-compose up -d

# The database will be available at:
# Host: localhost
# Port: 5432
# Database: cartdb
# Username: cartuser
# Password: cartpass
```

---

## 💡 Tips for Best Results

### 1. Use Placeholders
Use `?` for parameter placeholders instead of literal values:
```sql
-- ✅ Good
SELECT * FROM cart WHERE cart_id = ?

-- ❌ Avoid (but still works)
SELECT * FROM cart WHERE cart_id = 123
```

### 2. Keep It Simple
Analyze one statement at a time. Multi-statement queries are blocked for safety.

### 3. Review All Sections
Don't just look at findings. Index analysis and constraint risks provide valuable insights even when there are no critical errors.

### 4. Copy Suggested Indexes
The tool provides ready-to-use `CREATE INDEX` statements. Copy them and run them in your migration tool (e.g., Flyway).

### 5. Test DELETE Carefully
Always analyze DELETE statements, especially on tables with foreign keys. The cascade analysis can prevent accidental data loss.

---

## 🐛 Troubleshooting

### Backend Issues

**Problem**: Backend won't start
```bash
# Check Java version
java -version  # Should be 21+

# Check if port 8081 is in use
lsof -i :8081

# Clean and rebuild
mvn clean install
```

**Problem**: SQL analysis fails with connection error
- Ensure database connection is established (see Step 3)
- Check that the connection hasn't timed out
- Try reconnecting

---

### Frontend Issues

**Problem**: Frontend won't start
```bash
# Clear node_modules and reinstall
rm -rf node_modules package-lock.json
npm install

# Try a different port if 5173 is in use
npm run dev -- --port 3000
```

**Problem**: SQL Sandbox doesn't appear
- Ensure you're connected to a database
- Try refreshing the page
- Check browser console for errors

---

## 📊 Common Use Cases

### Use Case 1: Pre-Deployment Review
**When**: Before merging a PR with new SQL queries
**How**: Paste all new queries into the sandbox
**Goal**: Identify missing indexes or constraint issues

### Use Case 2: Performance Investigation
**When**: A query is slow in production
**How**: Analyze the query to check index coverage
**Goal**: Identify missing or suboptimal indexes

### Use Case 3: Data Safety Check
**When**: Before running a DELETE in production
**How**: Analyze the DELETE statement
**Goal**: Understand cascade impact and prevent data loss

### Use Case 4: Learning Tool
**When**: Junior developers are writing new queries
**How**: Have them analyze queries before implementation
**Goal**: Learn database best practices

---

## 📚 Next Steps

1. Read the full documentation: [SQL_SANDBOX_README.md](./SQL_SANDBOX_README.md)
2. Explore the codebase to understand the architecture
3. Add custom analysis rules for your specific use cases
4. Integrate with your CI/CD pipeline

---

## ✅ Success Checklist

- [ ] Backend is running on port 8081
- [ ] Frontend is running on port 5173
- [ ] Connected to database successfully
- [ ] SQL Sandbox panel is visible
- [ ] Analyzed a SELECT statement
- [ ] Analyzed an INSERT statement
- [ ] Analyzed a DELETE statement
- [ ] Reviewed all result sections
- [ ] Copied a suggested index statement

---

## 🤝 Support

If you encounter issues:
1. Check the console panel in the UI for error messages
2. Review backend logs for detailed error information
3. Ensure your database schema matches the expected structure
4. Verify that your SQL syntax is valid PostgreSQL

---

## 🎉 You're Ready!

You now have a powerful tool to analyze SQL statements before they reach production. Use it proactively to catch issues early and improve your database performance and reliability.

Happy analyzing! 🧪

