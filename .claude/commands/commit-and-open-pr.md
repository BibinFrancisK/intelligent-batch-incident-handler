# commit-and-open-pr

## Objective
Safely review changes, validate quality, and create a pull request only if everything passes.

---

## Steps

### 1. Review Changes
- Analyze all modified files in the current branch.
- Check for:
    - Code quality issues (readability, duplication, bad naming)
    - Potential bugs or edge cases
    - Missing error handling on external calls (Kafka, DynamoDB, LLM, Slack)
    - Security concerns (secrets in code, unvalidated deserialization, unchecked DynamoDB writes)
    - Violations of project conventions (see `.claude/rules/`)
    - No hardcoded secrets, API keys, or endpoint URLs
    - No raw `System.out.println` — all logging must use SLF4J `Logger`
    - Sealed interface `permits` clause is up to date whenever a new implementation is added
    - New packages follow the layout defined in `CLAUDE.md`

### 2. Report Issues (If Any)
- If any issues are found:
    - Clearly list them with file names and reasoning.
    - DO NOT modify code.
    - Ask the user how to proceed.

### 3. Confirm Clean State
- If no issues are found:
    - Summarize the changes in 2–5 bullet points.
    - Ask for confirmation before proceeding to tests.

### 4. Run Quality Checks
Run the following commands in order from the project root:
1. `./mvnw clean verify` — compiles, runs all tests (unit + integration + E2E)
2. If only unit tests are needed: `./mvnw test`
3. If a single test class needs targeting: `./mvnw -Dtest=<ClassName> test`

### 5. Handle Failures
- If any check fails:
    - Show the failing output.
    - DO NOT modify code.
    - Ask the user how to proceed.

### 6. Prepare Commit
- Generate a commit message using Conventional Commits format:
    - Type: `feat` | `fix` | `refactor` | `chore` | `test` | `docs`
    - Scope (optional): `kafka` | `ml` | `llm` | `notify` | `persistence` | `infra` | `config` | `observability`
    - Structure:
      ```
      <type>(<scope>): <short summary>
      ```
- Show the commit message to the user and ask for approval.

### 7. Commit & Push
- After approval:
    - Stage relevant changes (specific files, never `git add .`)
    - Commit using the approved message
    - Push to the remote branch

### 8. Create Pull Request
- Open a PR from the current feature branch to `main` with:
    - Clear, concise title (same as commit summary)
    - Description including:
        - What changed
        - Why it was needed
        - Any risks or notes for reviewers (e.g. Kafka consumer group offset impact, DynamoDB schema changes)
    - Use GitHub MCP server for creating the pull request

### 9. Final Output
- Provide:
    - PR link
    - Summary of actions taken

---

## General Rules
- NEVER modify code without explicit user approval.
- NEVER proceed past a step if it fails.
- ALWAYS show outputs (review, tests, commit message) before taking irreversible actions.
- NEVER push directly to `main` — always use a feature branch.

## Security Rules
- NEVER read or access sensitive configuration files:
  - `docker/.env`
  - `.env`
  - `.env.local`
  - Any file matching `.env.*`

- If access is required:
  - STOP and ask the user explicitly
