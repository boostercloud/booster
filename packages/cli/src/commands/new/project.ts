import { Command, flags } from '@oclif/command'
import { Providers } from '@boostercloud/framework-core'
import { Script } from '../../common/script'
import Brand from '../../common/brand'
import {
  generateRootDirectory,
  ProjectInitializerConfig,
  generateConfigFiles,
  installDependencies,
} from '../../services/project-initializer'
import { Provider } from '@boostercloud/framework-types'
import Prompter from '../../services/user-prompt'

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
      description: 'cloud provider where the application will be deployed',
      options: Providers.list,
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
    const parsedFlags = {
      projectName: args.projectName,
      ...flags,
    }
    parsedFlags.provider = (undefined as unknown) as Provider
    await run(parsedFlags as Partial<ProjectInitializerConfig>, flags.provider)
  }
}

const run = async (flags: Partial<ProjectInitializerConfig>, provider?: string): Promise<void> =>
  Script.init(`boost ${Brand.energize('new')} 🚧`, parseConfig(new Prompter(), flags, provider))
    .step('creating project root', generateRootDirectory)
    .step('generating config files', generateConfigFiles)
    .step('installing dependencies', installDependencies)
    .info('Project generated!')
    .done()

const parseConfig = async (
  prompter: Prompter,
  flags: Partial<ProjectInitializerConfig>,
  providerName?: string
): Promise<ProjectInitializerConfig> => {
  const description = await prompter.defaultOrPrompt(flags.description, "What's your project description?")
  const version = await prompter.defaultOrPrompt(flags.version, "What's the first version?")
  const author = await prompter.defaultOrPrompt(flags.author, "Who's the author?")
  const homepage = await prompter.defaultOrPrompt(flags.homepage, "What's the website?")
  const license = await prompter.defaultOrPrompt(flags.license, 'What license will you be publishing this under?')
  const repository = await prompter.defaultOrPrompt(flags.repository, "What's the URL of the repository?")
  const chosenProvider = await prompter.defaultOrChoose(
    providerName,
    'What is the provider you want to use for deploying?',
    ['AWS']
  )
  const provider = await Providers.choose(chosenProvider)
  return Promise.resolve({
    projectName: flags.projectName as string,
    provider,
    description,
    version,
    author,
    homepage,
    license,
    repository,
  })
}
