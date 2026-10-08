# Code walkthrough

## Start in the browser

`frontend/src/main.ts` bootstraps the standalone Angular application. `app.routes.ts` defines public authentication pages and lazily loaded authenticated features. `core/models.ts` describes API response shapes. `core/auth.service.ts` owns the signed-in profile, while the guard redirects unauthenticated navigation. The server separately checks every request, so disabling the browser guard does not grant access.

Feature components render semantic HTML and bind reactive forms. Form validation gives immediate feedback; HTTP validation errors remain authoritative. Shared components provide icons, charts, loading/error/empty states, and consistent formatting. Styles use teal and coral accents with darker text for contrast, rounded panels, and responsive grids. TypeScript compiles to JavaScript; HTML and CSS are genuine application assets, not technologies added only for a resume.

## Follow a contribution

The transaction form selects an owned account, contribution type, amount, date, and description. `core/api.service.ts` sends a typed request through Angular HttpClient. The security interceptor ensures cookie credentials and a CSRF token on mutations. The API route is relative, allowing the same frontend build to run behind Nginx or CloudFront.

Spring Security authenticates the server-side session before controller dispatch. The controller validates a request DTO. The service checks ownership and contribution policy, locks the account, changes its exact decimal balance, saves the transaction and audit event, then commits atomically. JPA maps the entities to PostgreSQL; a response DTO returns only the intended fields. Editing reverses the original financial effect before applying its replacement. A failure rolls back both operations.

The dashboard queries account balances and contributions rather than storing a separate dashboard total. Recent activity, trend points, allocations, and goal progress use persisted data. Retirement calculator results are scenarios rather than guaranteed investment performance.

## Follow authentication

The login page obtains a CSRF token before sending credentials. BCrypt checks the password; optional MFA adds a time-based authenticator code. Login rotates session identity. Spring Session persists authentication in PostgreSQL, and an HttpOnly cookie identifies the browser. Credentials and tokens are not persisted in browser storage. Logout invalidates the server session; expired sessions return authentication errors handled by Angular.

## Follow deployment

Multi-stage Docker builds compile Angular and Java in build images, then copy output into smaller runtime images. Compose links frontend, API, and PostgreSQL through an internal network. Nginx serves static Angular files, rewrites browser routes to `index.html`, and proxies `/api` to Spring Boot. Flyway applies versioned SQL during API startup. Health checks wait for dependent services.

AWS CDK defines S3/CloudFront delivery, ECR images, ECS Fargate tasks, HTTPS ALB, private RDS PostgreSQL, Secrets Manager, and CloudWatch. CloudFront sends uncached API requests with authentication cookies and CSRF headers to the ALB. Static assets use separate caching. GitHub Actions validates changes before a manually triggered AWS deployment assumes an OIDC role.

## Explain the choices

The monorepo makes UI, API, schema, and deployment changes reviewable together. A modular Spring service is easier to operate than multiple microservices for this domain and scale. PostgreSQL offers transactional constraints, exact numeric storage, and shared session persistence. Cookie authentication limits token exposure while requiring explicit CSRF protection. Native SVG charts keep the frontend small and accessible. Infrastructure-as-code makes deployment reproducible, but synthesis alone does not prove an AWS account can deploy it; certificates, DNS, IAM, quotas, and budgets remain prerequisites.
