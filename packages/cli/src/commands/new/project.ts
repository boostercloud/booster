import { Command, flags } from '@oclif/command'
import { Script } from '../../common/script'
import Brand from '../../common/brand'
import {
  generateRootDirectory,
  ProjectInitializerConfig,
  generateConfigFiles,
  installDependencies,
} from '../../services/project-initializer'
import Prompter from '../../services/user-prompt'
import { assertNameIsCorrect } from '../../services/provider-service'

export default class Project extends Command {
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
      char: 'h',
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
    provider: flags.string({
      char: 'p',
      description:
        'package name implementing the cloud provider integration where the application will be deployed (i.e: "@boostercloud/framework-provider-aws"',
    }),
  }

  public static args = [{ name: 'projectName' }]

  public async run(): Promise<void> {
    return this.runWithErrors().catch(console.error)
  }

  private async runWithErrors(): Promise<void> {
    const { args, flags } = this.parse(Project)
    if (!args.projectName)
      return Promise.reject("You haven't provided a project name, but it is required, run with --help for usage")
    assertNameIsCorrect(args.projectName)
    const parsedFlags = {
      projectName: args.projectName,
      ...flags,
    }
    await run(parsedFlags as Partial<ProjectInitializerConfig>, this.config.version, flags.provider)
  }
}

const run = async (
  flags: Partial<ProjectInitializerConfig>,
  boosterVersion: string,
  provider?: string
): Promise<void> =>
  Script.init(`boost ${Brand.energize('new')} 🚧`, parseConfig(new Prompter(), flags, boosterVersion, provider))
    .step('Creating project root', generateRootDirectory)
    .step('Generating config files', generateConfigFiles)
    .step('Installing dependencies', installDependencies)
    .info('Project generated!')
    .done()

const parseConfig = async (
  prompter: Prompter,
  flags: Partial<ProjectInitializerConfig>,
  boosterVersion: string,
  providerName?: string
): Promise<ProjectInitializerConfig> => {
  const description = await prompter.defaultOrPrompt(flags.description, "What's your project description?")
  const version = await prompter.defaultOrPrompt(flags.version, "What's the first version?")
  const author = await prompter.defaultOrPrompt(flags.author, "Who's the author?")
  const homepage = await prompter.defaultOrPrompt(flags.homepage, "What's the website?")
  const license = await prompter.defaultOrPrompt(flags.license, 'What license will you be publishing this under?')
  const repository = await prompter.defaultOrPrompt(flags.repository, "What's the URL of the repository?")
  const providerPackageName = await prompter.defaultOrPrompt(
    providerName,
    'What is the package name of your provider integration library?'
  )
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
  })
}
