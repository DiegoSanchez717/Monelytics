import {
  Aspects, CfnCondition, CfnOutput, CfnResource, Fn, Stack, StackProps, Validations,
  aws_ec2 as ec2, aws_iam as iam,
} from 'aws-cdk-lib';
import { Construct } from 'constructs';

/** Offline architecture reference. Its resources cannot be provisioned by this template. */
export class MonelyticsStack extends Stack {
  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, { ...props, analyticsReporting: false });
    Validations.of(this).acknowledge({ id: 'CloudFormation-Validate::W8003',
      reason: 'The constant-false condition is intentional: the zero-spend reference must never provision resources' });

    const referenceOnly = new CfnCondition(this, 'ProvisioningDisabled', {
      expression: Fn.conditionEquals('reference-only', 'never-provision'),
    });
    // Defence in depth: even direct submission of the synthesized template creates no resources.
    Aspects.of(this).add({
      visit(node: Construct) {
        if (node instanceof CfnResource) node.cfnOptions.condition = referenceOnly;
      },
    });

    const vpc = new ec2.Vpc(this, 'ReferenceNetwork', {
      maxAzs: 1, natGateways: 0, restrictDefaultSecurityGroup: false,
      subnetConfiguration: [{ name: 'reference', subnetType: ec2.SubnetType.PUBLIC, cidrMask: 24 }],
    });
    const security = new ec2.SecurityGroup(this, 'ReferenceSecurity', {
      vpc, allowAllOutbound: true,
      description: 'No inbound access; an optional future evaluation would use SSM port forwarding',
    });
    const role = new iam.Role(this, 'ReferenceSessionRole', {
      assumedBy: new iam.ServicePrincipal('ec2.amazonaws.com'),
      managedPolicies: [iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonSSMManagedInstanceCore')],
    });
    const instance = new ec2.Instance(this, 'ReferenceHost', {
      vpc, vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      securityGroup: security, role,
      instanceType: new ec2.InstanceType('t3.small'),
      machineImage: ec2.MachineImage.latestAmazonLinux2023({ cpuType: ec2.AmazonLinuxCpuType.X86_64 }),
      requireImdsv2: true, detailedMonitoring: false, creditSpecification: ec2.CpuCredits.STANDARD,
      associatePublicIpAddress: true,
      blockDevices: [{ deviceName: '/dev/xvda', volume: ec2.BlockDeviceVolume.ebs(8, {
        encrypted: true, volumeType: ec2.EbsDeviceVolumeType.GP3, deleteOnTermination: true,
      }) }],
    });
    // No registry publishing, source downloads, credentials, email delivery or AI API calls.
    // A separately approved future deployment would install Compose and transfer prebuilt images.
    instance.userData.addCommands('set -euo pipefail', 'dnf install -y docker', 'systemctl enable --now docker');

    new CfnOutput(this, 'ReferenceNotice', {
      value: 'Synthesis only. Provisioning is disabled; run Monelytics locally with Docker Compose.',
    });
    new CfnOutput(this, 'ReferenceInstanceId', { value: instance.instanceId, condition: referenceOnly });
  }
}
