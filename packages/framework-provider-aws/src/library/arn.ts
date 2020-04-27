/**
 * NOTE: Extracted from AWS CDK, DO NOT MODIFY
 */
export interface ArnComponents {
  /**
   * The partition that the resource is in. For standard AWS regions, the
   * partition is aws. If you have resources in other partitions, the
   * partition is aws-partitionname. For example, the partition for resources
   * in the China (Beijing) region is aws-cn.
   *
   * @default The AWS partition the stack is deployed to.
   */
  readonly partition?: string
  /**
   * The service namespace that identifies the AWS product (for example,
   * 's3', 'iam', 'codepipline').
   */
  readonly service: string
  /**
   * The region the resource resides in. Note that the ARNs for some resources
   * do not require a region, so this component might be omitted.
   *
   * @default The region the stack is deployed to.
   */
  readonly region?: string
  /**
   * The ID of the AWS account that owns the resource, without the hyphens.
   * For example, 123456789012. Note that the ARNs for some resources don't
   * require an account number, so this component might be omitted.
   *
   * @default The account the stack is deployed to.
   */
  readonly account?: string
  /**
   * Resource type (e.g. "table", "autoScalingGroup", "certificate").
   * For some resource types, e.g. S3 buckets, this field defines the bucket name.
   */
  readonly resource: string
  /**
   * Separator between resource type and the resource.
   *
   * Can be either '/', ':' or an empty string. Will only be used if resourceName is defined.
   * @default '/'
   */
  readonly sep?: string
  /**
   * Resource name or path within the resource (i.e. S3 bucket object key) or
   * a wildcard such as ``"*"``. This is service-dependent.
   */
  readonly resourceName?: string
}
export class Arn {
  /**
   * Creates an ARN from components.
   *
   * If `partition`, `region` or `account` are not specified, the stack's
   * partition, region and account will be used.
   *
   * If any component is the empty string, an empty string will be inserted
   * into the generated ARN at the location that component corresponds to.
   *
   * The ARN will be formatted as follows:
   *
   *   arn:{partition}:{service}:{region}:{account}:{resource}{sep}{resource-name}
   *
   * The required ARN pieces that are omitted will be taken from the stack that
   * the 'scope' is attached to. If all ARN pieces are supplied, the supplied scope
   * can be 'undefined'.
   */
  static format(components: ArnComponents): string {
    const partition = components.partition
    const region = components.region
    const account = components.account
    const sep = components.sep !== undefined ? components.sep : '/'
    const values = ['arn', ':', partition, ':', components.service, ':', region, ':', account, ':', components.resource]
    if (sep !== '/' && sep !== ':' && sep !== '') {
      throw new Error('resourcePathSep may only be ":", "/" or an empty string')
    }
    if (components.resourceName != null) {
      values.push(sep)
      values.push(components.resourceName)
    }
    return values.join('')
  }
  /**
   * Given an ARN, parses it and returns components.
   *
   * If the ARN is a concrete string, it will be parsed and validated. The
   * separator (`sep`) will be set to '/' if the 6th component includes a '/',
   * in which case, `resource` will be set to the value before the '/' and
   * `resourceName` will be the rest. In case there is no '/', `resource` will
   * be set to the 6th components and `resourceName` will be set to the rest
   * of the string.
   *
   * If the ARN includes tokens (or is a token), the ARN cannot be validated,
   * since we don't have the actual value yet at the time of this function
   * call. You will have to know the separator and the type of ARN. The
   * resulting `ArnComponents` object will contain tokens for the
   * subexpressions of the ARN, not string literals. In this case this
   * function cannot properly parse the complete final resourceName (path) out
   * of ARNs that use '/' to both separate the 'resource' from the
   * 'resourceName' AND to subdivide the resourceName further. For example, in
   * S3 ARNs:
   *
   *    arn:aws:s3:::my_corporate_bucket/path/to/exampleobject.png
   *
   * After parsing the resourceName will not contain
   * 'path/to/exampleobject.png' but simply 'path'. This is a limitation
   * because there is no slicing functionality in CloudFormation templates.
   *
   * @param arn The ARN to parse
   *
   * @returns an ArnComponents object which allows access to the various
   * components of the ARN.
   */
  static parse(arn: string): ArnComponents {
    const components = arn.split(':')
    if (components.length < 6) {
      throw new Error('ARNs must have at least 6 components: ' + arn)
    }
    const [arnPrefix, partition, service, region, account, sixth, ...rest] = components
    if (arnPrefix !== 'arn') {
      throw new Error('ARNs must start with "arn:": ' + arn)
    }
    if (!service) {
      throw new Error('The `service` component (3rd component) is required: ' + arn)
    }
    if (!sixth) {
      throw new Error('The `resource` component (6th component) is required: ' + arn)
    }
    let resource
    let resourceName
    let sep
    let sepIndex = sixth.indexOf('/')
    if (sepIndex !== -1) {
      sep = '/'
    } else if (rest.length > 0) {
      sep = ':'
      sepIndex = -1
    }
    if (sepIndex !== -1) {
      resource = sixth.substr(0, sepIndex)
      resourceName = sixth.substr(sepIndex + 1)
    } else {
      resource = sixth
    }
    if (rest.length > 0) {
      if (!resourceName) {
        resourceName = ''
      } else {
        resourceName += ':'
      }
      resourceName += rest.join(':')
    }
    // "|| undefined" will cause empty strings to be treated as "undefined".
    // Optional ARN attributes (e.g. region, account) should return as empty string
    // if they are provided as such.
    return {
      service: service,
      resource: resource,
      partition: partition,
      region,
      account,
      resourceName,
      sep,
    }
  }

  private constructor() {}
}
