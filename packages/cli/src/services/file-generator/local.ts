import * as path from 'path'
import * as Mustache from 'mustache'
import { Target } from './target'
import { classNameToFileName, checkResourceNameIsValid } from '../../common/filenames'
import { Component } from '../../common/component'
import { Logger } from '@boostercloud/framework-types'
import Brand from '../../common/brand'
import { FileSystem } from '../file-system'
import { FileGenerator, TemplateType } from '.'
import { UserProject } from '../user-project'
import { CliError } from '../../common/errors'
import { UserInput } from '../user-input'
import { Process } from '../process'

@Component({ throws: CliError })
export class LocalFileGenerator implements FileGenerator {
  private resourceTemplatesPath = path.join(__dirname, '..', '..', 'resources', 'templates')

  constructor(
    readonly logger: Logger,
    readonly fileSystem: FileSystem,
    readonly userProject: UserProject,
    readonly userInput: UserInput,
    readonly process: Process
  ) {}

  async catch(e: unknown): Promise<CliError> {
    if (e instanceof CliError) return e
    return new CliError('GeneratorError', 'An unknown error occurred', e)
  }

  async copyStubs(): Promise<void> {
    const mapping = this.createTemplateDestinationMap()
    for (const [from, to] of Object.entries(mapping)) {
      await this.fileSystem.copy(from, to)
    }
  }

  async generate<TInfo>(target: Target<TInfo>): Promise<void> {
    const { resourcePath, exists } = await this.userProject.lookupResource(target)
    if (exists) {
      await this.confirmRemoveResource(target.name, target.placementDir, target.extension, resourcePath)
    }
    checkResourceNameIsValid(target.name)
    const rendered = Mustache.render(target.template, { ...target.info })
    await this.fileSystem.outputFile(resourcePath, rendered)
  }

  async template(name: TemplateType): Promise<string> {
    const cwd = await this.process.cwd()
    const fileName = `${name}.stub`
    const stubFile = `${cwd}/stubs/${fileName}`
    const templateFilePath = `${this.resourceTemplatesPath}/${fileName}`
    const stubsFolderExists = await this.fileSystem.exists(`${cwd}/stubs`)
    const stubFileExists = await this.fileSystem.exists(stubFile)

    if (stubsFolderExists && stubFileExists) {
      return this.fileSystem.readFileContents(stubFile)
    }
    return this.fileSystem.readFileContents(templateFilePath)
  }

  private async confirmRemoveResource(
    name: string,
    placementDir: string,
    extension: string,
    resourcePath: string
  ): Promise<void> {
    const resourceName = classNameToFileName(name)
    const resourceType = path.parse(placementDir).name
    this.logger.info(Brand.mellancholize('Checking if resource already exists...'))

    const isConfirmed = await this.userInput.defaultBoolean(
      Brand.dangerize(`Resource: "${resourceName}${extension}" already exists. Do you want to overwrite it?`)
    )
    if (!isConfirmed) {
      throw new CliError(
        'GeneratorError',
        `The '${resourceType}' resource "${resourceName}${extension}" already exists. Please use another resource name`
      )
    }
    await this.fileSystem.remove(resourcePath, { recursive: true })
  }

  /**
   * Creates a mapping of template files to their destination
   * in the users project
   */
  private async createTemplateDestinationMap(): Promise<Record<string, string>> {
    const entries = await this.fileSystem.readDirectoryContents(this.resourceTemplatesPath)
    const mapping: Record<string, string> = {}
    for (const entry of entries) {
      if (!entry.isFile() || !entry.name.includes('.stub')) continue
      const templatePath = path.join(this.resourceTemplatesPath, entry.name)
      const destinationPath = path.join(process.cwd(), 'stubs', entry.name)
      mapping[templatePath] = destinationPath
    }
    return mapping
  }
}
