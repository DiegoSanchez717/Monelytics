# AWS deployment

The repository contains executable AWS CDK TypeScript infrastructure, assertion tests and a deployment script. No AWS resources are created by local builds, tests or synthesis. Deployment creates billed resources; choose an account and budget before running it.

## Services and network

- CloudFront serves the Angular application from a private, encrypted, versioned S3 bucket using Origin Access Control. JavaScript, CSS, fonts and asset paths use optimized caching; the default HTML behavior is uncached. Angular route rewriting runs only for static requests.
- `/api` and `/api/*` share the browser origin and go to an HTTPS Application Load Balancer. API caching is disabled. All viewer cookies, query strings and headers except `Host` reach the origin, preserving Spring Session and CSRF protection. CloudFront sets the origin host to the DNS name covered by the ALB certificate.
- The ALB accepts HTTPS and forwards only requests carrying a random CloudFront origin header; all other requests receive 403. ECS tasks accept traffic from the ALB security group only.
- Two ECS Fargate tasks run the Java application in private application subnets, with deployment rollback and CPU autoscaling to four tasks. CDK publishes immutable backend image assets to the ECR repository created by CDK bootstrap. Spring Session JDBC shares sessions between replicas.
- RDS PostgreSQL 17 uses private isolated subnets, encrypted storage, Multi-AZ, seven-day automated backups, forced TLS and deletion protection. Its generated database credentials live in Secrets Manager. The application MFA encryption key is a separate stable secret.
- CloudWatch stores structured application logs for 30 days and exposes an alarm for sustained target errors. RDS PostgreSQL logs are exported. Connect the alarm to the operator's notification channel before a live release.
- A NAT gateway allows private ECS tasks to pull images and send logs. Optional Route 53 records connect the supplied domain to the ALB and CloudFront. ACM supplies the certificates.

See AWS guidance for [restricting CloudFront access to an ALB](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/restrict-access-to-load-balancer.html) and [certificate domain and Region requirements](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cnames-and-https-requirements.html).

RDS is pinned to PostgreSQL 17.11, which includes current security fixes and is [supported by RDS](https://docs.aws.amazon.com/AmazonRDS/latest/PostgreSQLReleaseNotes/postgresql-versions.html). Verify availability in the selected Region before deployment.

## Estimated monthly cost

For a continuously running demonstration in **US East (N. Virginia), `us-east-1`**, allow approximately **USD $125–$135 per month before traffic charges and taxes**. This planning estimate uses 730 hours per month, on-demand pricing, two Linux/x86 Fargate tasks with 0.5 vCPU and 1 GB each, one Multi-AZ `db.t4g.micro` PostgreSQL instance with 20 GB of gp2 storage, one NAT gateway, one ALB across two Availability Zones, and three public IPv4 addresses. No free-tier allowances, promotional credits, Savings Plans or reserved-instance discounts are deducted. Prices were checked against the linked official AWS pages on October 7–8, 2026.

| Service | Assumption and rate | Approximate monthly USD |
| --- | --- | ---: |
| Fargate | Two tasks × 730 hours; CPU $0.000011244/vCPU-second and memory $0.000001235/GB-second. Default 20 GB ephemeral storage per task is included. [Fargate pricing](https://aws.amazon.com/fargate/pricing/) | $36.04 |
| RDS PostgreSQL | Multi-AZ `db.t4g.micro`, including standby: 730 hours × $0.032/hour, plus 20 GB gp2 storage × $0.23/GB-month. [Regional AWS Price List](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonRDS/current/us-east-1/index.json), [RDS pricing](https://aws.amazon.com/rds/postgresql/pricing/) | $27.96 |
| NAT gateway | One gateway × 730 hours × $0.045/hour; processing and transfer are additional. [VPC pricing](https://aws.amazon.com/vpc/pricing/) | $32.85 |
| Application Load Balancer | 730 hours × $0.0225/hour; LCU consumption is additional at $0.008/LCU-hour. [ALB pricing](https://aws.amazon.com/elasticloadbalancing/pricing/) | $16.43 |
| Public IPv4 | Two ALB addresses plus one NAT Elastic IP × 730 hours × $0.005/address-hour. Larger ALB deployments can consume more addresses. [IPv4 pricing](https://aws.amazon.com/vpc/pricing/) | $10.95 |
| Secrets Manager | Two secrets—database credentials and MFA key—at $0.40/secret-month. API requests are additional at $0.05/10,000 calls. [Secrets pricing](https://aws.amazon.com/secrets-manager/pricing/) | $0.80 |
| CloudWatch | Small application/database log allowance and one alarm; log ingestion starts at $0.50/GB and archive storage at $0.03/GB-month. Actual log volume determines the bill. [CloudWatch pricing](https://aws.amazon.com/cloudwatch/pricing/) | $1–$5 allowance |
| S3 | Approximately 1–5 GB of website versions and deployment artifacts, at $0.023/GB-month for the initial Standard storage tier; requests are additional. [S3 pricing](https://aws.amazon.com/s3/pricing/) | $0.03–$0.12 storage |
| ECR | Approximately 1–5 GB of retained private backend images at $0.10/GB-month; additional versions increase storage. [ECR pricing](https://aws.amazon.com/ecr/pricing/) | $0.10–$0.50 storage |
| CloudFront | This stack uses pay-as-you-go rather than a subscribed flat-rate plan. Request, transfer, function invocation and invalidation charges depend on usage and are excluded from this baseline. [CloudFront pricing](https://aws.amazon.com/cloudfront/pricing/) | Usage additional |

The rounded range combines the verified fixed infrastructure charges with small log and storage allowances. The RDS rates come from the regional AWS Price List published October 6, 2026, for the Multi-AZ PostgreSQL instance and gp2-storage SKUs. This is an approximate planning estimate, **not an AWS quote or a budget cap**. Recalculate the complete architecture in the [AWS Pricing Calculator](https://calculator.aws/) with the selected Region, PostgreSQL deployment/storage options, expected traffic and retention before provisioning it.

Excluded variable charges include CloudFront requests/data transfer/functions, ALB LCUs, NAT processing at $0.045/GB, cross-AZ and internet transfer, database CPU credits above the burstable baseline, excess backup/snapshot storage, S3/API requests, optional security services, domain registration and tax. Enabling four Fargate tasks throughout the month adds approximately $36.04 above the two-task baseline; deployment overlap can also temporarily increase task usage. RDS storage autoscaling and retained snapshots, logs, S3 versions and ECR images can continue increasing charges.

An existing Route 53 zone is reused by the stack. If you need a separate zone, add approximately $0.50/month for an initial hosted zone, plus applicable DNS/domain charges; [Route 53 pricing](https://aws.amazon.com/route53/pricing/) provides the details. Standard non-exportable public ACM certificates for integrated AWS services have no certificate fee; [ACM pricing](https://aws.amazon.com/certificate-manager/pricing/) distinguishes these from paid certificate products. Create AWS billing alerts and review Cost Explorer regularly; an alert does not automatically stop spending. Follow the retained-resource deletion notes below when retiring the deployment.

## Temporary tooling dependency exception

As of October 7, 2026, the latest AWS CDK library, 2.272.0, bundles `brace-expansion` 5.0.9. A raw `npm audit` reports **one high-severity dependency finding** containing [GHSA-qhr7-859c-m2p7](https://github.com/advisories/GHSA-qhr7-859c-m2p7), [GHSA-6j4f-fj2g-mc7p](https://github.com/advisories/GHSA-6j4f-fj2g-mc7p) and [GHSA-q2hr-2g5m-vwhr](https://github.com/advisories/GHSA-q2hr-2g5m-vwhr). The library bundle cannot be replaced by npm overrides or `npm audit fix`. Patched `brace-expansion` 5.0.12 exists, but no patched CDK release is currently published.

This is a denial-of-service vulnerability in glob parsing by deployment tooling. The application does not ship CDK or accept user input into CDK glob patterns. This repository's CDK asset input is the trusted backend source directory. The exception is therefore limited to this exact CDK version, bundled package version, dependency path and three advisory URLs, and expires **November 7, 2026**. New advisories, additional paths, changed versions, critical findings and any other high-severity package fail CI.

`npm run audit --prefix infrastructure` saves the unmodified npm report to `infrastructure/audit-report.json` and prints the accepted finding. CI retains that raw report as an artifact. The policy has tests for fail-closed behavior. This exception does not apply to frontend dependencies, Java dependencies or production container scans. Upgrade CDK and remove the exception as soon as AWS publishes the patched bundle. This is an explicitly accepted tooling risk, not a claim of zero vulnerabilities.

## Prerequisites

1. Install Node 24.x (24.12 or newer), Docker and AWS CLI v2. Authenticate with an AWS profile or temporary role credentials; do not add access keys to this repository.
2. Choose an AWS account and Region. Request and DNS-validate a public ACM certificate **in that Region** for an origin domain you own, such as `origin.example.com`. CloudFront cannot verify an ACM certificate against the AWS-owned ALB DNS name.
3. Route the origin domain to the ALB. Supplying an existing Route 53 hosted zone lets CDK create this alias automatically. Otherwise create the DNS record from `ApiLoadBalancerDns` after deploying; first-run service health checks do not depend on the public domain, but browser API calls do.
4. A custom frontend domain is optional for a demo. Its ACM certificate must be issued in `us-east-1`, even if ECS is deployed elsewhere. Without a custom frontend domain the application uses the supplied CloudFront HTTPS domain and AWS's fixed default certificate security policy, which permits legacy TLS. Configure the custom domain and viewer certificate to enforce this stack's TLS 1.2 minimum for a production release.
5. Bootstrap the target account/Region. This creates the CDK artifact bucket, ECR asset repository and deployment roles:

   ```sh
   npm ci --prefix infrastructure
   cd infrastructure
   npx cdk bootstrap aws://ACCOUNT_ID/REGION
   cd ..
   ```

6. Create the stable MFA secret once with AWS CLI authentication configured:

   ```sh
   # PowerShell: $env:AWS_REGION = "us-east-1"
   # Bash: export AWS_REGION=us-east-1
   node scripts/create-mfa-secret.mjs
   ```

   The script prints only the ARN. Store that ARN as `MFA_SECRET_ARN`. The key is a base64 encoding of 32 random bytes. Keep it available across releases and database restores. Replacing it requires re-encrypting existing TOTP secrets. Creating a Secrets Manager secret incurs AWS charges.

## Deployment configuration

Set these environment variables in PowerShell (`$env:NAME = "value"`), Bash (`export NAME=value`) or the protected GitHub production environment:

| Name | Value |
| --- | --- |
| `AWS_REGION` | Region containing ECS, RDS and the ALB |
| `API_ORIGIN_DOMAIN` | DNS name covered by the origin certificate, e.g. `origin.example.com` |
| `ALB_CERTIFICATE_ARN` | Issued regional ACM certificate ARN |
| `MFA_SECRET_ARN` | ARN of the stable secret created above |
| `ORIGIN_HEADER_SECRET` | 32–128 random letters, numbers, `_` or `-`; e.g. generate with `node -e "console.log(require('crypto').randomBytes(32).toString('base64url'))"` |
| `HOSTED_ZONE_ID`, `HOSTED_ZONE_NAME` | Optional existing Route 53 zone, supplied together |
| `WEB_DOMAIN`, `WEB_CERTIFICATE_ARN` | Optional frontend DNS name and issued us-east-1 certificate, supplied together |

Do not check these values into source control. Keep the origin header in a GitHub environment secret. Certificate ARNs and zone IDs can be environment variables. Avoid shell tracing during deployment because CDK receives the secret as a `NoEcho` CloudFormation parameter. CloudFront and ALB administrators can inspect origin configuration; restrict those administrative permissions.

```sh
npm ci --prefix frontend
npm ci --prefix infrastructure
npm test --prefix infrastructure
node scripts/deploy-aws.mjs          # Synthesis only; creates no resources
node scripts/deploy-aws.mjs --deploy # Explicitly provisions AWS resources and publishes the app
```

The deployment script builds Angular, deploys CloudFormation with the backend image asset, uploads static assets and invalidates CloudFront. A healthy replacement ECS service is required before CloudFormation completes. It leaves production demo users and Swagger disabled and requires secure session cookies. Production administrators must be provisioned through an audited operator procedure; sample administrator credentials exist only in local demo mode.

## GitHub OIDC and release gate

The workflow runs frontend lint, tests, build and dependency audit; Java unit and real PostgreSQL integration tests; CDK tests and synth; Compose configuration validation; Docker image vulnerability scans; and Playwright browser journeys against the full three-container application. A deployment runs only when `workflow_dispatch` has `deploy=true`, the branch is `main`, and all validation jobs pass.

1. Create a GitHub environment named `production`, protect it with required reviewers and restrict deployment branches to `main`.
2. Add the deployment variables above, plus `AWS_DEPLOY_ROLE_ARN`. Put `ORIGIN_HEADER_SECRET` in environment secrets. No persistent AWS access key is needed.
3. Create an AWS IAM OIDC provider for `https://token.actions.githubusercontent.com`, audience `sts.amazonaws.com`, and a role trusted by the exact repository environment. The trust statement must include:

   ```json
   {
     "Effect": "Allow",
     "Principal": { "Federated": "arn:aws:iam::ACCOUNT_ID:oidc-provider/token.actions.githubusercontent.com" },
     "Action": "sts:AssumeRoleWithWebIdentity",
     "Condition": {
       "StringEquals": {
         "token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
         "token.actions.githubusercontent.com:sub": "repo:DiegoSanchez717/Monelytics:environment:production"
       }
     }
   }
   ```

4. Grant that role `sts:AssumeRole` on the account's specific CDK bootstrap deployment, lookup and asset-publishing roles, not a wildcard account. Grant CloudFormation stack-output read, static publication and invalidation permissions below. Restrict CDK bootstrap CloudFormation execution policies to the services used by this stack and apply an organization permission boundary where required.
5. After the first stack deploy, scope direct publication permissions to the output website bucket and distribution:

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [
       { "Effect": "Allow", "Action": ["cloudformation:DescribeStacks"], "Resource": "arn:aws:cloudformation:REGION:ACCOUNT_ID:stack/WealthPath/*" },
       { "Effect": "Allow", "Action": ["s3:ListBucket"], "Resource": "arn:aws:s3:::WEBSITE_BUCKET" },
       { "Effect": "Allow", "Action": ["s3:PutObject", "s3:DeleteObject"], "Resource": "arn:aws:s3:::WEBSITE_BUCKET/*" },
       { "Effect": "Allow", "Action": ["cloudfront:CreateInvalidation"], "Resource": "arn:aws:cloudfront::ACCOUNT_ID:distribution/DISTRIBUTION_ID" }
     ]
   }
   ```

The first deployment can be performed locally by an authorized operator to establish the exact resource ARNs. Add the scoped permissions before enabling CI deployment. Configure ECR scanning in the bootstrap repository/account, AWS billing alerts, operational notifications and retention policies appropriate to your use case. [GitHub documents AWS OIDC setup](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws).

## Operations and remaining production preparation

- This deployable portfolio application records user-entered IRA contributions and goals. It does not move money or connect to brokerage accounts. Its configured educational contribution cap must be updated or replaced with qualified eligibility and tax-year rules before real retirement compliance use.
- Registration creates regular users only. Provision production administrator roles using a controlled, audited database operation or add your organization's identity provider and approval process.
- Database migration and runtime currently share the generated database owner credential. For a regulated production deployment, provision a separate migration owner and restricted runtime PostgreSQL role, grant only application and Spring Session table DML privileges, and execute Flyway from the release job before switching runtime to that role. Test your restore and rollback process with the resulting grants.
- Configure CloudWatch alarm actions, security monitoring, recovery objectives, secret access reviews, real user acceptance testing and domain ownership. AWS deployment itself cannot be verified without your account, certificates and DNS.
- The single NAT gateway is a documented availability/cost tradeoff. For stricter availability use one NAT gateway per AZ or VPC endpoints for AWS services.
- Rotate database passwords using a tested procedure that refreshes ECS tasks. Rotate the origin header with overlapping ALB rules to avoid requests failing while CloudFront propagates. MFA key rotation requires re-encryption, not simple secret replacement.
- Review the CloudFormation diff before an update. Deletion protection prevents accidental RDS deletion. Database snapshots, database secrets, logs and the website bucket are retained; stack removal does not remove these or their ongoing charges. Disabling protection and deleting retained resources is an explicit operator action.
- Roll back the ECS task definition to a previous image asset and restore the corresponding S3 object versions when necessary. Prefer additive migrations; automatic task rollback does not reverse applied SQL migrations.
