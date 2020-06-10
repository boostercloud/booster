import util = require('util')
const exec = util.promisify(require('child_process').exec)
import { deploy, nuke } from '../../src/deploy'
import { config } from 'aws-sdk'

// TODO: remove the AWS config with BOOST-729 to make end-to-end tests provider unaware

before(async () => {
  await setEnv()
  await checkConfigAnd(deploy)
})

after(async () => {
  await checkConfigAnd(nuke)
})

async function setEnv(): Promise<void> {
  if (!process.env.BOOSTER_APP_SUFFIX) {
    // If the user doesn't set an app name suffix, use the current git commit hash
    // to build a unique suffix for the application name in AWS to avoid collisions
    // between tests from different branches.
    const { stdout } = await exec('git rev-parse HEAD')
    process.env['BOOSTER_APP_SUFFIX'] = stdout.trim().substring(0, 6)
    console.log('setting BOOSTER_APP_SUFFIX=' + process.env.BOOSTER_APP_SUFFIX)
  }
}

async function checkConfigAnd(action: () => Promise<void>): Promise<void> {
  console.log('Checking AWS configuration...')
  if (!config.credentials?.accessKeyId || !config.credentials?.secretAccessKey) {
    throw new Error(
      "AWS credentials were not properly loaded by the AWS SDK and are required to run the integration tests. Check that you've set them in your `~/.aws/credentials` file or environment variables. Refer to AWS documentation for more details https://docs.aws.amazon.com/sdk-for-javascript/v2/developer-guide/setting-credentials-node.html"
    )
  }
  if (!config.region) {
    throw new Error(
      "AWS region was not properly loaded by the AWS SDK and is required to run the integration tests. Check that you've set it in your `~/.aws/config` file or AWS_REGION environment variable. Refer to AWS documentation for more details https://docs.aws.amazon.com/sdk-for-javascript/v2/developer-guide/setting-region.html#setting-region-order-of-precedence"
    )
  } else {
    console.log('AWS Region set to ' + config.region)
  }
  await action()
}
