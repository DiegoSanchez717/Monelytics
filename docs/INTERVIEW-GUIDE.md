# Interview guide

Monelytics brings recorded personal finance data into a responsive Angular interface: spending, category budgets, recurring commitments, savings and educational purchase assessments. Java/Spring Boot applies the business rules and PostgreSQL stores exact-decimal ledger data. Docker makes the local demonstration repeatable; AWS CDK illustrates a future deployment while explicit safeguards prevent spending.

Useful design decisions to explain:

1. **Money integrity:** editing/deleting an entry reverses its prior effect in the same transaction; transfers atomically update both owned accounts and do not count as income/expenses. Row locks and deterministic ordering protect concurrency.
2. **Secure access:** database-backed HttpOnly sessions, CSRF tokens, BCrypt passwords, ownership checks, role-protected audit records, encrypted TOTP secrets and one-use recovery links. Reset revokes sessions while retaining MFA.
3. **Planning versus real money:** savings contributions are allocations and recurring payments are expense records. The app neither debits a bank nor moves money. Derived charts and notifications use actual recorded entries.
4. **Safe assistant:** facts and affordability decisions are deterministic; mock education is the default. The replaceable local provider classifies topics only. Generated prose cannot fabricate balances or perform actions, and every response is educational.
5. **Verification:** Angular unit tests, Spring API/service tests, real PostgreSQL migration/concurrency tests, Docker runtime checks and browser/accessibility tests cover the important flows. CI validates with pinned actions and no cloud deploy job.
6. **Cost constraints:** open-source PostgreSQL and local Docker are the permanent$0 path. AWS Free plans expire; synth-only templates and command guards enforce the user's request without pretending free hosting is permanent.

Demonstrate a category budget, an overspending alert, a recurring payment retry, a savings allocation, a CSV export and a purchase assessment that explains the limiting factor. Use the audit view to show important actions. Use local Mailpit for recovery without exposing private email or secrets.

Do not claim real bank integrations, banking certification, production adoption, live AWS operation, Copilot-generated code, or benchmark results that were not measured. Copilot guidance is configured for future review; the résumé bullets describe that accurately.
