import { App, Stack } from '@aws-cdk/core'
import { ProviderContext, Logger, BoosterConfig } from '@boostercloud/framework-types'
import { ISDK, Mode, SdkProvider } from 'aws-cdk'
import { emptyBucket, bucketExists } from './s3utils'
import { CdkToolkit } from 'aws-cdk/lib/cdk-toolkit'
import { CloudAssembly, Environment } from '@aws-cdk/cx-api'
import { Configuration } from 'aws-cdk/lib/settings'
import { CloudExecutable } from 'aws-cdk/lib/api/cxapp/cloud-executable'
import { CloudFormationDeployments } from 'aws-cdk/lib/api/cloudformation-deployments'
import { Rocket } from '@boostercloud/rockets-base'
import { ApplicationStackBuilder } from '../stacks/application-stack'

export interface S3Utils {
  emptyBucket: (bucketName: string) => Promise<void>
  bucketExists: (bucketName: string) => Promise<boolean>
}

export interface StackInfo {
  names: Array<string>
  toolkitName: string
  toolkitBucketName: string
}

export interface AWSUtils {
  s3: S3Utils
  stack: StackInfo
}

export class AWSProviderContext implements ProviderContext {
  public name = 'aws'
  public stack: Stack
  private app: App
  private _cdkToolkit: CdkToolkit | undefined = undefined
  private _utils: AWSUtils | undefined = undefined

  private constructor(
    readonly logger: Logger,
    readonly config: BoosterConfig,
    readonly sdk: ISDK,
    readonly environment: Environment,
    private readonly sdkProvider: SdkProvider,
    private readonly awsConfiguration: Configuration,
    readonly rockets?: Rocket<AWSProviderContext>[]
  ) {
    this.app = new App()
    this.stack = new Stack(this.app, config.resourceNames.applicationStack)
  }

  private assemble(): CloudAssembly {
    new ApplicationStackBuilder(this.config).buildOn(this)

    // Mount the rockets in the context
    this.rockets?.forEach((rocket) => rocket.mount(this))

    return this.app.synth()
  }

  public static async build(
    logger: Logger,
    config: BoosterConfig,
    rockets?: Rocket<AWSProviderContext>[]
  ): Promise<AWSProviderContext> {
    const sdkProvider = await SdkProvider.withAwsCliCompatibleDefaults()
    const environment = await AWSProviderContext.getEnvironment(sdkProvider)
    const sdk = await sdkProvider.forEnvironment(environment, Mode.ForWriting)
    const configuration = await new Configuration().load()
    return new AWSProviderContext(logger, config, sdk, environment, sdkProvider, configuration, rockets)
  }

  /* The following lazy getters provide non-optional accessors for attributes that can't be initialized at build time */
  public get cdkToolkit(): CdkToolkit {
    if (!this._cdkToolkit) {
      const cloudExecutable = new CloudExecutable({
        configuration: this.awsConfiguration,
        sdkProvider: this.sdkProvider,
        synthesizer: async (): Promise<CloudAssembly> => this.assemble(),
      })
      this._cdkToolkit = new CdkToolkit({
        sdkProvider: this.sdkProvider,
        cloudExecutable,
        cloudFormation: new CloudFormationDeployments({ sdkProvider: this.sdkProvider }),
        configuration: this.awsConfiguration,
      })
    }
    return this._cdkToolkit
  }

  public get utils(): AWSUtils {
    if (!this._utils) {
      this._utils = {
        s3: {
          emptyBucket: emptyBucket.bind(null, this.logger, this.sdk),
          bucketExists: bucketExists.bind(null, this.sdk.s3()),
        },
        stack: {
          names: [this.config.resourceNames.applicationStack],
          toolkitName: this.config.appName + '-toolkit',
          toolkitBucketName: this.config.appName + '-toolkit-bucket',
        },
      }
    }
    return this._utils
  }

  private static async getEnvironment(sdkProvider: SdkProvider): Promise<Environment> {
    const account = await sdkProvider.defaultAccount()
    const region = sdkProvider.defaultRegion

    if (!account) {
      throw new Error(
        'Unable to load default AWS account. Check that you have properly set your AWS credentials in `~/.aws/credentials` file or the corresponding environment variables. Refer to AWS documentation for more details https://docs.aws.amazon.com/sdk-for-javascript/v2/developer-guide/setting-credentials-node.html'
      )
    }
    if (!region) {
      throw new Error(
        'Unable to determine default region. Check that you have set it in your `~/.aws/config` file or AWS_REGION environment variable. Refer to AWS documentation for more details https://docs.aws.amazon.com/sdk-for-javascript/v2/developer-guide/setting-region.html#setting-region-order-of-precedence'
      )
    }

    return {
      name: 'Default environment',
      account: account.accountId,
      region,
    }
  }
}
