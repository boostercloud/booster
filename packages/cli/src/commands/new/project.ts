import { flags } from '@oclif/command'
import { IConfig } from '@oclif/config'
import { Args, BaseCommand, CliCommand, Flags } from '../../common/base-command'
import { FileGenerator } from '../../services/file-generator'
import { UserInput } from '../../services/user-input'
import { ProjectCreationConfig, UserProject } from '../../services/user-project'
import { Logger } from '@boostercloud/framework-types'
import Brand from '../../common/brand'
import { Provider } from '../../common/provider'

export default class Project extends BaseCommand<typeof Project> {
  public static description = 'create a new project from scratch'
  public static flags = {
    help: flags.help({ char: 'h' }),
    description: flags.string({
      char: 'd',
      description: 'a short description',
    }),
    version: flags.string({
      char: 'v',
      description: 'the initial version',
    }),
    author: flags.string({
      char: 'a',
      description: 'who is writing this?',
    }),
    homepage: flags.string({
      char: 'H',
      description: 'the website of this project',
    }),
    license: flags.string({
      char: 'l',
      description: 'which license will you use?',
    }),
    repository: flags.string({
      char: 'r',
      description: 'the URL of the repository',
    }),
    providerPackageName: flags.string({
      char: 'p',
      description:
        'package name implementing the cloud provider integration where the application will be deployed (i.e: "@boostercloud/framework-provider-aws"',
    }),
    default: flags.boolean({
      description: 'generates the project with default parameters (i.e. --license=MIT)',
      default: false,
    }),
    skipInstall: flags.boolean({
      description: 'skip dependencies installation',
      default: false,
    }),
    skipGit: flags.boolean({
      description: 'skip git initialization',
      default: false,
    }),
  }

  public static args = [{ name: 'projectName' }]

  implementation = Implementation
}

@CliCommand()
class Implementation {
  constructor(
    readonly logger: Logger,
    readonly userProject: UserProject,
    readonly fileGenerator: FileGenerator,
    readonly userInput: UserInput
  ) {}

  async run(flags: Flags<typeof Project>, args: Args<typeof Project>, cliConfig: IConfig): Promise<void> {
    const projectName = args.projectName
    if (!projectName) throw "You haven't provided a project name, but it is required, run with --help for usage"
    this.logger.info(`boost ${Brand.energize('new')} 🚧`)
    const parsedConfig = await this.parseConfig(projectName, cliConfig.version, flags)
    await this.logger.logProcess('Creating project', () => this.userProject.create(projectName, parsedConfig))
  }

  private async parseConfig(
    projectName: string,
    boosterVersion: string,
    flags: Flags<typeof Project>
  ): Promise<ProjectCreationConfig> {
    if (flags.default) {
      return {
        projectName: projectName,
        providerPackageName: '@boostercloud/framework-provider-aws',
        description: '',
        version: '0.1.0',
        author: '',
        homepage: '',
        license: 'MIT',
        repository: '',
        boosterVersion,
        default: flags.default,
        skipInstall: flags.skipInstall || false,
        skipGit: flags.skipGit || false,
      }
    }
    const description = await this.userInput.defaultString(
      'What\'s your project description? (default: "")',
      flags.description
    )
    const versionPrompt = await this.userInput.defaultString(
      "What's the first version? (default: 0.1.0)",
      flags.version
    )
    const version = versionPrompt || '0.1.0'
    const author = await this.userInput.defaultString('Who\'s the author? (default: "")', flags.author)
    const homepage = await this.userInput.defaultString('What\'s the website? (default: "")', flags.homepage)
    const licensePrompt = await this.userInput.defaultString(
      'What license will you be publishing this under? (default: MIT)',
      flags.license
    )
    const license = licensePrompt || 'MIT'
    const repository = await this.userInput.defaultString(
      'What\'s the URL of the repository? (default: "")',
      flags.repository
    )
    const providerPackageName = await this.getProviderPackageName(flags.providerPackageName)

    return {
      projectName,
      providerPackageName,
      description,
      version,
      author,
      homepage,
      license,
      repository,
      boosterVersion,
      default: false,
      skipInstall: flags.skipInstall || false,
      skipGit: flags.skipGit || false,
    }
  }

  private async getProviderPackageName(providerPackageName?: string): Promise<string> {
    if (providerPackageName) {
      return providerPackageName
    }

    const providerSelection: Provider = (await this.userInput.defaultChoice(
      "What's the package name of your provider infrastructure library?",
      [Provider.AWS, Provider.AZURE, Provider.KUBERNETES, Provider.OTHER],
      providerPackageName
    )) as Provider

    if (providerSelection === Provider.OTHER) {
      return await this.userInput.defaultString(
        "What's the other provider integration library? e.g. @boostercloud/framework-provider-aws"
      )
    } else {
      return this.getSelectedProviderPackage(providerSelection)
    }
  }

  private getSelectedProviderPackage(provider: Provider): string {
    switch (provider) {
      case Provider.AWS:
        return '@boostercloud/framework-provider-aws'
      case Provider.AZURE:
        return '@boostercloud/framework-provider-azure'
      case Provider.KUBERNETES:
        return '@boostercloud/framework-provider-kubernetes'
      default:
        return ''
    }
  }
}
