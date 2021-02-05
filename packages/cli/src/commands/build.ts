import { Command, flags } from '@oclif/command'
import {
  //cleanDeploymentSandbox,
  compileProjectAndLoadConfig,
  //createDeploymentSandbox,
} from '../services/config-service'
import { BoosterConfig } from '@boostercloud/framework-types'
import { Script } from '../common/script'
import Brand from '../common/brand'

const runTasks = async (
  compileAndLoad: Promise<BoosterConfig>
): Promise<void> =>
  Script.init(`boost ${Brand.dangerize('build')} 🚀`, compileAndLoad)
    //.step('Cleaning up deployment files', cleanDeploymentSandbox)
    .info('Build complete!')
    .done()

export default class Build extends Command {
  public static description = 'Build the current application as configured in your `index.ts` file.'

  public static flags = {
    help: flags.help({ char: 'h' })
  }

  public async run(): Promise<void> {
    //const deploymentProjectPath = await createDeploymentSandbox()
    //await runTasks(compileProjectAndLoadConfig(deploymentProjectPath))
    await runTasks(compileProjectAndLoadConfig(process.cwd()))
  }
}
