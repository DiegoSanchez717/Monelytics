# AWS readiness under the zero-spend policy

Monelytics runs locally using open-source Docker Engine, PostgreSQL, a local SMTP inbox and mock AI responses. Local setup requires no AWS account or payment credentials. **AWS provisioning is disabled.** Setup, tests and CI do not create cloud resources or upgrade an account plan.

The AWS CDK project is an offline architecture reference. It describes one `t3.small` EC2 host with encrypted 8 GB gp3 storage, IMDSv2, standard CPU credits and an SSM role. It has no inbound security-group rules: a future evaluation would use Session Manager port forwarding to the loopback Docker website. A public subnet permits outbound SSM access without a NAT gateway. There are no ALBs, managed PostgreSQL instances, Fargate services, container registries, customer-managed KMS keys, paid SMTP providers or AI API calls.

The reference does **not** start the application, download application images or publish a public website. Its small disk is an architecture example, not a build host: a future approved evaluation would transfer prebuilt images, configure Compose and verify available storage and memory separately.

## Generate and inspect the reference locally

```sh
npm ci --prefix infrastructure
npm test --prefix infrastructure
npm run synth --prefix infrastructure
# Equivalent synthesis-only entry:
node scripts/deploy-aws.mjs
```

The generated template is `infrastructure/cdk.out/MonelyticsReference.template.json`. Synthesis runs the CDK app directly with environment-agnostic CloudFormation tokens; it performs no account or context lookups and needs no AWS credentials. The build safely cleans only `infrastructure/dist` and `infrastructure/cdk.out`, preventing stale compiled tests or obsolete cloud templates from surviving.

Every resource in the template has the constant-false `ProvisioningDisabled` condition. Even submitting that template directly cannot create its reference resources. Tests verify that condition, the absence of inbound access and paid managed services, and the deployment guards. `npm run deploy --prefix infrastructure` and `node scripts/deploy-aws.mjs --deploy` deliberately fail before compilation, credentials or cloud tool execution. CI has no deployment job, OIDC write permission or AWS credentials action. Do not bootstrap CDK or use a separate cloud provisioning tool under this project's zero-spend requirement.

## CI without metered runner or artifact usage

Every CI job requires a public repository before GitHub allocates a standard `ubuntu-24.04` runner. Jobs skip if the repository becomes private. The workflow has read-only repository permissions, does not publish images or deploy, disables dependency and Trivy caches, and does not upload artifacts. Test results and the unmodified infrastructure audit remain in job logs. Workflow-policy tests reject private-runner execution, larger runners, writable credentials, publisher actions, artifact uploads and enabled caches. GitHub states that standard runners are free for public repositories; artifacts and excess caches can consume billable storage. These guards apply to this workflow, not unrelated account usage or previously stored artifacts. [GitHub Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)

## What the AWS Free account plan actually provides

AWS offers an optional Free account plan to eligible new customers. Existing or previous AWS account holders are ineligible. It ends when credits run out or six months after sign-up, whichever comes first; it cannot be extended. AWS then closes the account. This is temporary evaluation hosting, not permanently free production hosting. [AWS Free Tier FAQs](https://aws.amazon.com/free/free-tier-faqs/) explain eligibility and expiration.

AWS states that an active Free account plan does not bill the customer. A Paid plan can bill beyond credits. Joining AWS Organizations, setting up Control Tower and certain enterprise or compliance programs can automatically upgrade a Free plan to Paid. Avoid those transitions under the zero-spend requirement, and keep local backups before expiration. A balance check cannot guarantee that an account will remain Free afterward. [AWS plan conditions](https://docs.aws.amazon.com/us_en/awsaccountbilling/latest/aboutv2/free-tier-plans.html) describe these transitions.

AWS currently lists `t3.small` among eligible instance types for newer accounts. Compute, disk and public IPv4 usage still consume credits; the instance type label alone does not guarantee a zero bill on a Paid or legacy account. The reference is not deployed to any account. [EC2 Free Tier documentation](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-free-tier-usage.html)

## Optional read-only account-plan verification

If you already have an eligible account and a current AWS CLI v2, use your own temporary profile credentials:

```sh
node scripts/check-aws-free-plan.mjs
# Or:
npm run free-plan:check --prefix infrastructure
```

The checker invokes only `freetier:GetAccountPlanState`. It requires an active `FREE` plan, a positive numeric USD credit balance and a future expiration timestamp. Paid, legacy, expired, incomplete, inaccessible and unrecognized responses fail closed. No resource creation, plan upgrade, cloud secret storage or deployment follows a successful check. The check itself is provided at no cost by AWS. [API reference](https://docs.aws.amazon.com/aws-cost-management/latest/APIReference/API_freetier_GetAccountPlanState.html), [CLI reference](https://docs.aws.amazon.com/cli/latest/reference/freetier/get-account-plan-state.html)

A reader identity needs only this permission; the service has no resource-specific ARN support:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "freetier:GetAccountPlanState",
    "Resource": "*"
  }]
}
```

Do not grant upgrade or provisioning permissions to the checker. [AWS service authorization reference](https://docs.aws.amazon.com/service-authorization/latest/reference/list_freetier.html)

The API policy is tested with offline fixtures. No live AWS account eligibility or deployment has been verified in this repository. The supported zero-cost path remains local execution; any cloud deployment requires a separate scope decision and fresh account verification, and cannot be enabled through a command flag here.

## Local MFA key generation

`node scripts/setup-local.mjs` generates the ignored local `.env` with a random 32-byte MFA encryption key. It preserves an existing `.env`. If you need a separate key for a new installation, `node scripts/create-mfa-secret.mjs` writes an ignored `.local-secrets/mfa-encryption.key` without printing the key, overwriting an existing key or calling AWS. Protect that file using your operating system's local access controls. Never replace an enrolled installation's key without re-encrypting its stored MFA secrets.

## Temporary development-tool audit exception

AWS CDK 2.272.0 bundles `brace-expansion` 5.0.9. The raw npm audit reports **one high-severity dependency finding** with [GHSA-qhr7-859c-m2p7](https://github.com/advisories/GHSA-qhr7-859c-m2p7), [GHSA-6j4f-fj2g-mc7p](https://github.com/advisories/GHSA-6j4f-fj2g-mc7p) and [GHSA-q2hr-2g5m-vwhr](https://github.com/advisories/GHSA-q2hr-2g5m-vwhr). npm overrides cannot replace the bundled package. It is a glob-processing denial-of-service issue in development tooling; CDK does not ship in the application, and this offline reference accepts no user-controlled glob input.

The tested exception permits only that exact CDK/package version, bundled dependency path and advisory set. It expires **November 7, 2026**. New advisories, changed versions or paths, other high findings and all critical findings fail CI. `npm run audit --prefix infrastructure` retains the unmodified report as `infrastructure/audit-report.json`; CI prints it in job logs. This exception does not apply to application dependencies or container scans. Upgrade the bundled tooling and remove the exception when a patched CDK is available; this is not a claim of zero development-tool vulnerabilities.
