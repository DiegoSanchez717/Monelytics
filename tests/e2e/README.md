# Live end-to-end verification

Start the full stack from the repository root, then run:

```powershell
cd tests/e2e
npm.cmd ci
npx.cmd playwright install chromium
npm.cmd test
```

`E2E_BASE_URL` defaults to `http://localhost:8080`. Tests operate on synthetic accounts in the configured database. Run against a disposable development environment. API tests create independent users and verify exact ledger mutations, ownership boundaries, role checks, CSRF, beneficiary totals, pagination, and zero-return projections. Browser tests cover real rendered workflows, responsive layout, and accessibility.

The suite preserves traces and screenshots on failure in ignored output directories. The HTML report is local and is not committed because it can contain session and request details.
