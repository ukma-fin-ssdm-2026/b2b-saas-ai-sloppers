1. Connection string
   Copy the connection details for your database.
   Details:
   Shared pooler
   If your database password contains special characters, percent-encode them in the connection string.
   Connection parameters
   host:aws-1-eu-west-3.pooler.supabase.com
   port:5432
   database:postgres
   user:postgres.vvmwzgiekjomyqgvxaay
   Code:
   File: Code
```
jdbc:postgresql://aws-1-eu-west-3.pooler.supabase.com:5432/postgres?user=postgres.vvmwzgiekjomyqgvxaay&password=[YOUR-PASSWORD]
```

2. Install Agent Skills (optional)
   Agent Skills give AI coding tools ready-made instructions, scripts, and resources for working with Supabase more accurately and efficiently.
   Code:
   File: Code
```
npx skills add supabase/agent-skills
```