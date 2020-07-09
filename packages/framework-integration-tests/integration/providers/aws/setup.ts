import util = require('util')
const exec = util.promisify(require('child_process').exec)
import { deploy, nuke } from '../../../src/deploy'
import { config } from 'aws-sdk'
import { sleep } from '../helpers'

before(async () => {
  await setEnv()
  await checkConfigAnd(deploy)
  console.log('Waiting 30 seconds after deployment to let the stack finish its initialization...')
  await sleep(30000)
  console.log('...sleep finished. Let the tests begin 🔥!')
})

after(async () => {
  if (!process.env['FULL_INTEGRATION_TEST']) {
    await checkConfigAnd(nuke)
  }
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
  // The following variable is set to make AWS SDK try to load the region config from
  // `~/.aws/config` if it fails reading it from `/.aws/credentials`. Loading the region doesn't seem
  // to be a very reliable process, so in some cases we'll need to set the environment variable
  // AWS_REGION to our chosen region to make this thing work...
  process.env['AWS_SDK_LOAD_CONFIG'] = 'true'
  console.log('setting AWS_SDK_LOAD_CONFIG=' + process.env.AWS_SDK_LOAD_CONFIG)
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
