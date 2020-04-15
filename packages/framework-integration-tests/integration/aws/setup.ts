import util = require('util')
const exec = util.promisify(require('child_process').exec)
import { deploy, nuke } from '../../src/deploy'

before(async () => {
  await setEnv()
  await deploy()
})

after(async () => {
  await nuke()
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
  // The following variable is set to make AWS SDK load the config from `~/.aws/credentials`
  // Notice that this file must be set as described in [the documentation](https://github.com/boostercloud/booster/blob/master/docs/documentation/_08-deployment.md#configure-your-provider-credentials)
  process.env['AWS_SDK_LOAD_CONFIG'] = 'true'
  console.log('setting AWS_SDK_LOAD_CONFIG=' + process.env.AWS_SDK_LOAD_CONFIG)
}
