import { Command, flags } from '@oclif/command'
import { deployToCloudProvider } from '../services/provider-service'
import { compileProjectAndLoadConfig } from '../services/config-service'
import { BoosterConfig, Logger } from '@boostercloud/framework-types'
import { Script } from '../common/script'
import Brand from '../common/brand'
import { logger } from '../services/logger'
import { exec } from 'child-process-promise'
import { wrapExecError } from '../common/errors'
import * as path from 'path'


let skipRestoreDependencies = false;

async function pruneDependencies(config: BoosterConfig): Promise<void> {
  try {
    await exec('npm install --production', { cwd: projectDir(config) })
  } catch (e) {
    throw wrapExecError(e, 'Could not prune dev dependencies')
  }
}

async function reinstallDependencies(config: BoosterConfig): Promise<void> {
  try {
    if(skipRestoreDependencies === false){
      await exec('npm install', { cwd: projectDir(config) })
    }
  } catch (e) {
    throw wrapExecError(e, 'Could not reinstall dependencies')
  }
}

// TODO: Before loading, we should check:
//    * we're in a booster project
//    * run the compiler to be sure that we're deploying the last version and stop the process if it fails
const runTasks = async (
  environment: string,
  loader: Promise<BoosterConfig>,
  deployer: (config: BoosterConfig, logger: Logger) => Promise<void>
): Promise<void> =>
  Script.init(`boost ${Brand.dangerize('deploy')} [${environment}] 🚀`, loader)
    //TODO: We should install dependencies in production mode before deploying
    .step('Removing dev dependencies', pruneDependencies)
    .step('Deploying', (config) => deployer(config, logger))
    .step('Reinstalling dependencies', reinstallDependencies)
    .info('Deployment complete!')
    .done()

export default class Deploy extends Command {
  public static description = 'Deploy the current application as configured in your `index.ts` file.'

  public static flags = {
    help: flags.help({ char: 'h' }),
    environment: flags.string({
      char: 'e',
      description: 'environment configuration to run',
    }),
    skipRestoreDependencies: flags.boolean({
      char: 's',
      description: 'skips restoring dependencies after deployment'
    })
  }

  public async run(): Promise<void> {
    const { flags } = this.parse(Deploy)
    if (!flags.environment) {
      console.log('Error: no environment name provided. Usage: `boost deploy -e <environment>`.')
      return
    }

    if(flags.skipRestoreDependencies){
      skipRestoreDependencies = true;
    }

    process.env.BOOSTER_ENV = flags.environment
    await runTasks(flags.environment, compileProjectAndLoadConfig(), deployToCloudProvider)

  }
}

function projectDir(config: BoosterConfig): string {
  return path.join(process.cwd());
}