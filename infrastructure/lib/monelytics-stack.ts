import * as path from 'node:path';
import {
  CfnOutput, CfnParameter, Duration, RemovalPolicy, Stack, StackProps,
  aws_certificatemanager as acm, aws_cloudfront as cloudfront,
  aws_cloudfront_origins as origins, aws_cloudwatch as cloudwatch,
  aws_ec2 as ec2, aws_ecs as ecs, aws_ecr_assets as ecrAssets,
  aws_elasticloadbalancingv2 as elbv2,
  aws_logs as logs, aws_rds as rds, aws_route53 as route53,
  aws_route53_targets as targets, aws_s3 as s3, aws_secretsmanager as secretsmanager,
} from 'aws-cdk-lib';
import { Construct } from 'constructs';

export class MonelyticsStack extends Stack {
  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);

    // Certificate hostname and DNS must match the origin, rather than the AWS-owned ALB name.
    const apiDomain = new CfnParameter(this, 'ApiOriginDomain', {
      type: 'String', description: 'Public DNS name routed to ALB and covered by its ACM certificate',
      allowedPattern: '(?=.{1,253}$)[a-zA-Z0-9][a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}',
    });
    const albCertificateArn = new CfnParameter(this, 'AlbCertificateArn', {
      type: 'String', description: 'Issued ACM certificate in the same AWS Region as this stack',
      allowedPattern: 'arn:aws:acm:[a-z0-9-]+:[0-9]{12}:certificate/[a-f0-9-]+',
    });
    const mfaSecretArn = new CfnParameter(this, 'MfaSecretArn', {
      type: 'String', noEcho: true, description: 'Secrets Manager ARN containing a stable base64 32-byte MFA encryption key',
      allowedPattern: 'arn:aws:secretsmanager:[a-z0-9-]+:[0-9]{12}:secret:.+',
    });
    const originHeader = new CfnParameter(this, 'OriginHeaderSecret', {
      type: 'String', noEcho: true, minLength: 32, maxLength: 128,
      allowedPattern: '[A-Za-z0-9_-]+',
      description: 'Random secret used to prevent direct public ALB access',
    });

    const vpc = new ec2.Vpc(this, 'Network', {
      maxAzs: 2, natGateways: 1,
      subnetConfiguration: [
        { name: 'public', subnetType: ec2.SubnetType.PUBLIC, cidrMask: 24 },
        { name: 'application', subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS, cidrMask: 24 },
        { name: 'database', subnetType: ec2.SubnetType.PRIVATE_ISOLATED, cidrMask: 24 },
      ],
    });
    const applicationSecurity = new ec2.SecurityGroup(this, 'ApplicationSecurity', { vpc });
    const databaseSecurity = new ec2.SecurityGroup(this, 'DatabaseSecurity', {
      vpc, allowAllOutbound: false,
    });
    databaseSecurity.addIngressRule(applicationSecurity, ec2.Port.tcp(5432), 'Only application tasks');

    const database = new rds.DatabaseInstance(this, 'Database', {
      engine: rds.DatabaseInstanceEngine.postgres({ version: rds.PostgresEngineVersion.of('17.11', '17') }),
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T4G, ec2.InstanceSize.MICRO),
      vpc, vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      securityGroups: [databaseSecurity], databaseName: 'monelytics',
      credentials: rds.Credentials.fromGeneratedSecret('monelytics'),
      publiclyAccessible: false, storageEncrypted: true, allocatedStorage: 20,
      maxAllocatedStorage: 100, multiAz: true, backupRetention: Duration.days(7),
      deletionProtection: true, removalPolicy: RemovalPolicy.SNAPSHOT,
      cloudwatchLogsExports: ['postgresql'],
      cloudwatchLogsRetention: logs.RetentionDays.ONE_MONTH,
      autoMinorVersionUpgrade: true,
      parameters: { 'rds.force_ssl': '1' },
    });
    database.secret!.applyRemovalPolicy(RemovalPolicy.RETAIN);
    const mfaKey = secretsmanager.Secret.fromSecretCompleteArn(this, 'MfaEncryptionKey', mfaSecretArn.valueAsString);

    const cluster = new ecs.Cluster(this, 'Cluster', { vpc });
    const applicationLogs = new logs.LogGroup(this, 'ApplicationLogs', {
      retention: logs.RetentionDays.ONE_MONTH, removalPolicy: RemovalPolicy.RETAIN,
    });
    const task = new ecs.FargateTaskDefinition(this, 'ApiTask', {
      cpu: 512, memoryLimitMiB: 1024,
      runtimePlatform: { cpuArchitecture: ecs.CpuArchitecture.X86_64, operatingSystemFamily: ecs.OperatingSystemFamily.LINUX },
    });
    const container = task.addContainer('api', {
      // CDK publishes this immutable content-addressed image to its bootstrap ECR repository.
      image: ecs.ContainerImage.fromAsset(path.resolve(__dirname, '../../../backend'), { platform: ecrAssets.Platform.LINUX_AMD64 }),
      logging: ecs.LogDrivers.awsLogs({ logGroup: applicationLogs, streamPrefix: 'api' }),
      environment: {
        DATABASE_URL: `jdbc:postgresql://${database.dbInstanceEndpointAddress}:5432/monelytics?sslmode=require`,
        COOKIE_SECURE: 'true', DEMO_ENABLED: 'false', API_DOCS_ENABLED: 'false',
        SERVER_FORWARD_HEADERS_STRATEGY: 'framework',
        JAVA_TOOL_OPTIONS: '-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError',
      },
      secrets: {
        DATABASE_USERNAME: ecs.Secret.fromSecretsManager(database.secret!, 'username'),
        DATABASE_PASSWORD: ecs.Secret.fromSecretsManager(database.secret!, 'password'),
        MFA_ENCRYPTION_KEY: ecs.Secret.fromSecretsManager(mfaKey),
      },
      healthCheck: {
        command: ['CMD-SHELL', 'curl --fail --silent http://localhost:8080/actuator/health/readiness || exit 1'],
        interval: Duration.seconds(30), timeout: Duration.seconds(5),
        retries: 3, startPeriod: Duration.seconds(90),
      },
    });
    container.addPortMappings({ containerPort: 8080 });
    const service = new ecs.FargateService(this, 'ApiService', {
      cluster, taskDefinition: task, desiredCount: 2,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_WITH_EGRESS },
      assignPublicIp: false, securityGroups: [applicationSecurity],
      healthCheckGracePeriod: Duration.seconds(120),
      circuitBreaker: { rollback: true }, enableExecuteCommand: false,
      minHealthyPercent: 100, maxHealthyPercent: 200,
    });
    service.node.addDependency(database);
    const scaling = service.autoScaleTaskCount({ minCapacity: 2, maxCapacity: 4 });
    scaling.scaleOnCpuUtilization('CpuScaling', { targetUtilizationPercent: 60 });

    const loadBalancer = new elbv2.ApplicationLoadBalancer(this, 'ApiLoadBalancer', {
      vpc, internetFacing: true, dropInvalidHeaderFields: true,
    });
    const listener = loadBalancer.addListener('Https', {
      port: 443, certificates: [acm.Certificate.fromCertificateArn(this, 'OriginCertificate', albCertificateArn.valueAsString)],
      sslPolicy: elbv2.SslPolicy.RECOMMENDED_TLS,
      defaultAction: elbv2.ListenerAction.fixedResponse(403, { contentType: 'text/plain', messageBody: 'Access denied' }),
    });
    listener.addTargets('ApiTargets', {
      port: 8080, protocol: elbv2.ApplicationProtocol.HTTP,
      priority: 1, conditions: [elbv2.ListenerCondition.httpHeader('X-Monelytics-Origin', [originHeader.valueAsString])],
      targets: [service], deregistrationDelay: Duration.seconds(30),
      healthCheck: { path: '/actuator/health/readiness', healthyHttpCodes: '200' },
    });

    const website = new s3.Bucket(this, 'Website', {
      blockPublicAccess: s3.BlockPublicAccess.BLOCK_ALL,
      enforceSSL: true, encryption: s3.BucketEncryption.S3_MANAGED,
      versioned: true, removalPolicy: RemovalPolicy.RETAIN,
    });
    const browserSecurity = new cloudfront.ResponseHeadersPolicy(this, 'BrowserSecurity', {
      securityHeadersBehavior: {
        contentTypeOptions: { override: true },
        frameOptions: { frameOption: cloudfront.HeadersFrameOption.DENY, override: true },
        referrerPolicy: { referrerPolicy: cloudfront.HeadersReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN, override: true },
        strictTransportSecurity: { accessControlMaxAge: Duration.days(365), includeSubdomains: true, override: true },
        contentSecurityPolicy: {
          contentSecurityPolicy: "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'; upgrade-insecure-requests",
          override: true,
        },
      },
    });
    const rewriteRoutes = new cloudfront.Function(this, 'AngularRoutes', {
      code: cloudfront.FunctionCode.fromInline(`function handler(event) {
  var request = event.request;
  // Attached only to the static behavior: API failures keep their real status and JSON body.
  if (!request.uri.split('/').pop().includes('.')) request.uri = '/index.html';
  return request;
}`),
    });
    const webDomain = this.node.tryGetContext('webDomain') as string | undefined;
    const webCertificateArn = this.node.tryGetContext('webCertificateArn') as string | undefined;
    if (Boolean(webDomain) !== Boolean(webCertificateArn)) {
      throw new Error('webDomain and webCertificateArn must be supplied together');
    }
    if (webCertificateArn && !/^arn:aws:acm:us-east-1:\d{12}:certificate\/.+/.test(webCertificateArn)) {
      throw new Error('CloudFront viewer certificate must be an ACM certificate in us-east-1');
    }
    const apiOrigin = new origins.HttpOrigin(apiDomain.valueAsString, {
      protocolPolicy: cloudfront.OriginProtocolPolicy.HTTPS_ONLY,
      originSslProtocols: [cloudfront.OriginSslPolicy.TLS_V1_2],
      customHeaders: { 'X-Monelytics-Origin': originHeader.valueAsString },
    });
    const apiBehavior: cloudfront.BehaviorOptions = {
      origin: apiOrigin, viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.HTTPS_ONLY,
      allowedMethods: cloudfront.AllowedMethods.ALLOW_ALL,
      cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
      // Preserve sessions, CSRF headers, filters and pagination; origin Host matches the TLS certificate.
      originRequestPolicy: cloudfront.OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER,
      responseHeadersPolicy: browserSecurity,
    };
    const staticOrigin = origins.S3BucketOrigin.withOriginAccessControl(website);
    const assetBehavior: cloudfront.BehaviorOptions = {
      origin: staticOrigin,
      viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
      cachePolicy: cloudfront.CachePolicy.CACHING_OPTIMIZED,
      responseHeadersPolicy: browserSecurity,
    };
    const distribution = new cloudfront.Distribution(this, 'Distribution', {
      defaultRootObject: 'index.html',
      defaultBehavior: {
        origin: staticOrigin,
        viewerProtocolPolicy: cloudfront.ViewerProtocolPolicy.REDIRECT_TO_HTTPS,
        cachePolicy: cloudfront.CachePolicy.CACHING_DISABLED,
        responseHeadersPolicy: browserSecurity,
        functionAssociations: [{ eventType: cloudfront.FunctionEventType.VIEWER_REQUEST, function: rewriteRoutes }],
      },
      additionalBehaviors: {
        '/api/*': apiBehavior, '/api': apiBehavior,
        '*.js': assetBehavior, '*.css': assetBehavior,
        '/assets/*': assetBehavior, '*.woff2': assetBehavior,
      },
      domainNames: webDomain ? [webDomain] : undefined,
      certificate: webCertificateArn ? acm.Certificate.fromCertificateArn(this, 'ViewerCertificate', webCertificateArn) : undefined,
      minimumProtocolVersion: webCertificateArn ? cloudfront.SecurityPolicyProtocol.TLS_V1_2_2021 : undefined,
      priceClass: cloudfront.PriceClass.PRICE_CLASS_100,
    });

    const hostedZoneId = this.node.tryGetContext('hostedZoneId') as string | undefined;
    const hostedZoneName = this.node.tryGetContext('hostedZoneName') as string | undefined;
    if (Boolean(hostedZoneId) !== Boolean(hostedZoneName)) throw new Error('hostedZoneId and hostedZoneName must be supplied together');
    if (hostedZoneId && hostedZoneName) {
      const zone = route53.HostedZone.fromHostedZoneAttributes(this, 'Dns', { hostedZoneId, zoneName: hostedZoneName });
      // Explicit trailing dot prevents CDK from appending the zone to an unresolved parameter.
      new route53.ARecord(this, 'ApiDns', { zone, recordName: `${apiDomain.valueAsString}.`, target: route53.RecordTarget.fromAlias(new targets.LoadBalancerTarget(loadBalancer)) });
      if (webDomain) new route53.ARecord(this, 'WebsiteDns', { zone, recordName: webDomain, target: route53.RecordTarget.fromAlias(new targets.CloudFrontTarget(distribution)) });
    }

    new cloudwatch.Alarm(this, 'ApplicationErrors', {
      metric: loadBalancer.metrics.httpCodeTarget(elbv2.HttpCodeTarget.TARGET_5XX_COUNT, { period: Duration.minutes(5) }),
      threshold: 10, evaluationPeriods: 2, treatMissingData: cloudwatch.TreatMissingData.NOT_BREACHING,
      alarmDescription: 'Wire this alarm to your production operations notification channel',
    });
    new CfnOutput(this, 'WebsiteUrl', { value: `https://${webDomain ?? distribution.distributionDomainName}` });
    new CfnOutput(this, 'WebsiteBucket', { value: website.bucketName });
    new CfnOutput(this, 'DistributionId', { value: distribution.distributionId });
    new CfnOutput(this, 'ApiLoadBalancerDns', { value: loadBalancer.loadBalancerDnsName });
    new CfnOutput(this, 'ClusterName', { value: cluster.clusterName });
    new CfnOutput(this, 'ServiceName', { value: service.serviceName });
    new CfnOutput(this, 'DatabaseSecretArn', { value: database.secret!.secretArn });
    new CfnOutput(this, 'ApplicationLogGroup', { value: applicationLogs.logGroupName });
  }
}
