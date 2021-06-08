import { Booster } from '@boostercloud/framework-core'
import { BoosterConfig } from '@boostercloud/framework-types'
import * as AWS from '@boostercloud/framework-provider-aws'
import * as Kubernetes from '@boostercloud/framework-provider-kubernetes'
import * as fs from 'fs'
import * as path from 'path'

Booster.configureLocal('local', (config: BoosterConfig): void => {
  config.appName = 'my-store'
})

Booster.configure('kubernetes', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.provider = Kubernetes.Provider()
  config.assets = ['assets', 'components', 'assetFile.txt']
})

Booster.configure('development', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.provider = AWS.Provider()
  config.assets = ['assets', 'assetFile.txt']
})

Booster.configure('production', (config: BoosterConfig): void => {
  /* We use an automatically generated app name suffix to allow
   * running integration tests for different branches concurrently.
   */
  const appNameSuffix = process.env.BOOSTER_APP_SUFFIX ?? 'default'

  // The app suffix must be copied to the test app lambdas
  config.env['BOOSTER_APP_SUFFIX'] = appNameSuffix

  config.appName = 'my-store-' + appNameSuffix
  config.provider = AWS.Provider()
  config.assets = ['assets']
  config.tokenVerifier = {
    issuer: 'booster',
    // Read the content of the public RS256 cert, used to sign the JWT tokens
    publicKey: fs.readFileSync(path.join(__dirname, '..', '..', 'assets', 'certs', 'public.key'), 'utf8'),
    rolesClaim: 'booster:role',
  }
})
