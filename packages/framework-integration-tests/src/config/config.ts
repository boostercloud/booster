import { Booster, PublicKeyTokenVerifier } from '@boostercloud/framework-core'
import { BoosterConfig, DecodedToken, TraceActionTypes } from '@boostercloud/framework-types'
import * as fs from 'fs'
import * as path from 'path'
import { CustomTracer } from '../common/custom-tracer'
import { CustomLogger } from '../common/custom-logger'

class CustomPublicKeyTokenVerifier extends PublicKeyTokenVerifier {
  public async verify(token: string): Promise<DecodedToken> {
    await super.verify(token)
    throw new Error('Unauthorized')
  }
}

function configureLogger(config: BoosterConfig): void {
  config.logger = new CustomLogger()
}

function configureInvocationsHandler(config: BoosterConfig) {
  config.traceConfiguration = {
    enableTraceNotification: [TraceActionTypes.COMMAND_HANDLER, 'CHANGE_CART_ITEM_HANDLER'],
    includeInternal: false,
    onStart: CustomTracer.onStart,
    onEnd: CustomTracer.onEnd,
  }
}

function configureBoosterSensorHealth(config: BoosterConfig) {
  Object.values(config.sensorConfiguration.health.booster).forEach((indicator) => {
    indicator.enabled = true
  })
}

Booster.configure('local', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.providerPackage = '@boostercloud/framework-provider-local'
  config.tokenVerifiers = [
    new PublicKeyTokenVerifier(
      'booster',
      // Read the content of the public RS256 cert, used to sign the JWT tokens
      Promise.resolve(fs.readFileSync(path.join(__dirname, '..', '..', 'assets', 'certs', 'public.key'), 'utf8')),
      'booster:role'
    ),
    new CustomPublicKeyTokenVerifier(
      'booster',
      // Read the content of the public RS256 cert, used to sign the JWT tokens
      Promise.resolve(fs.readFileSync(path.join(__dirname, '..', '..', 'assets', 'certs', 'public.key'), 'utf8')),
      'booster:role'
    ),
  ]
  configureInvocationsHandler(config)
  configureLogger(config)
  configureBoosterSensorHealth(config)
})

Booster.configure('development', (config: BoosterConfig): void => {
  config.appName = 'my-store'
  config.providerPackage = '@boostercloud/framework-provider-aws'
  config.assets = ['assets', 'assetFile.txt']
  configureInvocationsHandler(config)
  configureBoosterSensorHealth(config)
})

Booster.configure('production', (config: BoosterConfig): void => {
  /* We use an automatically generated app name suffix to allow
   * running integration tests for different branches concurrently.
   */
  const appNameSuffix = process.env.BOOSTER_APP_SUFFIX ?? 'default'

  // The app suffix must be copied to the test app lambdas
  config.env['BOOSTER_APP_SUFFIX'] = appNameSuffix

  config.appName = 'my-store-' + appNameSuffix
  config.providerPackage = '@boostercloud/framework-provider-aws'
  config.assets = ['assets']
  config.tokenVerifiers = [
    new PublicKeyTokenVerifier(
      'booster',
      // Read the content of the public RS256 cert, used to sign the JWT tokens
      Promise.resolve(fs.readFileSync(path.join(__dirname, '..', '..', 'assets', 'certs', 'public.key'), 'utf8')),
      'booster:role'
    ),
    new CustomPublicKeyTokenVerifier(
      'booster',
      // Read the content of the public RS256 cert, used to sign the JWT tokens
      Promise.resolve(fs.readFileSync(path.join(__dirname, '..', '..', 'assets', 'certs', 'public.key'), 'utf8')),
      'booster:role'
    ),
  ]
  configureInvocationsHandler(config)
  configureBoosterSensorHealth(config)
})

Booster.configure('azure', (config: BoosterConfig): void => {
  /* We use an automatically generated app name suffix to allow
   * running integration tests for different branches concurrently.
   */
  const appNameSuffix = process.env.BOOSTER_APP_SUFFIX ?? 'default'

  // The app suffix must be copied to the test app lambdas
  config.env['BOOSTER_APP_SUFFIX'] = appNameSuffix

  config.appName = 'my-store-' + appNameSuffix
  config.providerPackage = '@boostercloud/framework-provider-azure'
  config.assets = ['assets', 'assetFile.txt', 'host.json']
  config.tokenVerifiers = [
    new PublicKeyTokenVerifier(
      'booster',
      // Read the content of the public RS256 cert, used to sign the JWT tokens
      Promise.resolve(fs.readFileSync(path.join(__dirname, '..', '..', 'assets', 'certs', 'public.key'), 'utf8')),
      'booster:role'
    ),
    new CustomPublicKeyTokenVerifier(
      'booster',
      // Read the content of the public RS256 cert, used to sign the JWT tokens
      Promise.resolve(fs.readFileSync(path.join(__dirname, '..', '..', 'assets', 'certs', 'public.key'), 'utf8')),
      'booster:role'
    ),
  ]
  configureInvocationsHandler(config)
  configureLogger(config)
  configureBoosterSensorHealth(config)
})
