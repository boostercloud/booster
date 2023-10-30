import { Command, Flags, Args } from '@oclif/core'
import { Script } from '../../common/script'
import Brand from '../../common/brand'
import {
  generateConfigFiles,
  generateRootDirectory,
  initializeGit,
  installDependencies,
  ProjectInitializerConfig,
} from '../../services/project-initializer'
import Prompter from '../../services/user-prompt'
import { assertNameIsCorrect } from '../../services/provider-service'
import { Provider } from '../../common/provider'
import { checkProjectAlreadyExists } from '../../services/project-checker'

export default class Project extends Command {
  public static description = 'create a new project from scratch'
  public static flags = {
    help: Flags.help({ char: 'h' }),
    description: Flags.string({
      char: 'd',
      description: 'a short description',
    }),
    version: Flags.string({
      char: 'v',
      description: 'the initial version',
    }),
    author: Flags.string({
      char: 'a',
      description: 'who is writing this?',
    }),
    homepage: Flags.string({
      char: 'H',
      description: 'the website of this project',
    }),
    license: Flags.string({
      char: 'l',
      description: 'which license will you use?',
    }),
    repository: Flags.string({
      char: 'r',
      description: 'the URL of the repository',
    }),
    providerPackageName: Flags.string({
      char: 'p',
      description:
        'package name implementing the cloud provider integration where the application will be deployed (i.e: "@boostercloud/framework-provider-azure")',
    }),
    default: Flags.boolean({
      description: 'generates the project with default parameters (i.e. --license=MIT)',
      default: false,
    }),
    skipInstall: Flags.boolean({
      description: 'skip dependencies installation',
      default: false,
    }),
    skipGit: Flags.boolean({
      description: 'skip git initialization',
      default: false,
    }),
  }

  public static args = {
    projectName: Args.string(),
  }

  public async run(): Promise<void> {
    const { args, flags } = await this.parse(Project)
    const { projectName } = args

    try {
      if (!projectName) throw "You haven't provided a project name, but it is required, run with --help for usage"
      assertNameIsCorrect(projectName)
      await checkProjectAlreadyExists(projectName)
      const parsedFlags = { projectName, ...flags }
      await run(parsedFlags as Partial<ProjectInitializerConfig>, this.config.version)
    } catch (error) {
      console.error(error)
    }
  }
}

const run = async (flags: Partial<ProjectInitializerConfig>, boosterVersion: string): Promise<void> =>
  Script.init(`boost ${Brand.energize('new')} 🚧`, parseConfig(new Prompter(), flags, boosterVersion))
    .step('Creating project root', generateRootDirectory)
    .step('Generating config files', generateConfigFiles)
    .optionalStep(Boolean(flags.skipInstall), 'Installing dependencies', installDependencies)
    .optionalStep(Boolean(flags.skipGit), 'Initializing git repository', initializeGit)
    .info('Project generated!')
    .done()

const getSelectedProviderPackage = (provider: Provider): string => {
  switch (provider) {
    case Provider.AWS:
      return '@boostercloud/framework-provider-aws'
    case Provider.AZURE:
      return '@boostercloud/framework-provider-azure'
    default:
      return ''
  }
}

const getProviderPackageName = async (prompter: Prompter, providerPackageName?: string): Promise<string> => {
  if (providerPackageName) {
    return providerPackageName
  }

  const providerSelection: Provider = (await prompter.defaultOrChoose(
    providerPackageName,
    "What's the package name of your provider infrastructure library?",
    [Provider.AWS, Provider.AZURE, Provider.OTHER]
  )) as Provider

  if (providerSelection === Provider.OTHER) {
    return await prompter.defaultOrPrompt(
      undefined,
      "What's the other provider integration library? e.g. @boostercloud/framework-provider-azure"
    )
  } else {
    return getSelectedProviderPackage(providerSelection)
  }
}

export const parseConfig = async (
  prompter: Prompter,
  flags: Partial<ProjectInitializerConfig>,
  boosterVersion: string
): Promise<ProjectInitializerConfig> => {
  if (flags.default) {
    return Promise.resolve({
      projectName: flags.projectName as string,
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
    })
  }

  const description = await prompter.defaultOrPrompt(
    flags.description,
    'What\'s your project description? (default: "")'
  )
  const versionPrompt = await prompter.defaultOrPrompt(flags.version, "What's the first version? (default: 0.1.0)")
  const version = versionPrompt || '0.1.0'
  const author = await prompter.defaultOrPrompt(flags.author, 'Who\'s the author? (default: "")')
  const homepage = await prompter.defaultOrPrompt(flags.homepage, 'What\'s the website? (default: "")')
  const licensePrompt = await prompter.defaultOrPrompt(
    flags.license,
    'What license will you be publishing this under? (default: MIT)'
  )
  const license = licensePrompt || 'MIT'
  const repository = await prompter.defaultOrPrompt(
    flags.repository,
    'What\'s the URL of the repository? (default: "")'
  )
  const providerPackageName = await getProviderPackageName(prompter, flags.providerPackageName)

  return Promise.resolve({
    projectName: flags.projectName as string,
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
  })
}
