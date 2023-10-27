import { Flags } from '@oclif/core'
import BaseCommand from '../../common/base-command'
import { Script } from '../../common/script'
import Brand from '../../common/brand'
import { createStubsFolder, publishStubFiles, checkStubsFolderExists } from '../../services/stub-publisher'
import { checkCurrentDirIsABoosterProject } from '../../services/project-checker'
import Prompter from '../../services/user-prompt'

export default class Publish extends BaseCommand {
  public static description = 'publish all resource template stubs that are available for customization'

  public static usage = 'boost stub:publish --force'

  public static examples = ['$ boost stub:publish --force', '$ boost stub:publish']

  public static flags = {
    force: Flags.boolean({
      char: 'f',
      description: 'Overwrite any existing stub files',
    }),
  }

  public async run(): Promise<void> {
    const { flags } = await this.parse(Publish)

    const stubFolderExists: boolean = checkStubsFolderExists()

    if (!stubFolderExists) {
      createStubsFolder()
    }

    if (stubFolderExists && !flags.force) {
      await Prompter.confirmPrompt({
        message: Brand.dangerize('Stubs folder already exists. Do you want to overwrite it?'),
      }).then((confirm: boolean) => {
        if (!confirm) {
          throw new Error(Brand.dangerize('Stubs folder already exists. Use --force option to overwrite files in it'))
        }
      })
    }

    await run()
  }
}

const run = async (): Promise<void> =>
  Script.init(`boost ${Brand.energize('stub:publish')} 🗄`, Promise.resolve(process.cwd()))
    .step('Verifying project', checkCurrentDirIsABoosterProject)
    .step('Publishing stubs', publishStubFiles)
    .info('Resource template stubs published!')
    .done()
