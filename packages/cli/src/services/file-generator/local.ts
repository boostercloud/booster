import * as path from 'path'
import * as Mustache from 'mustache'
import { Target } from './target'
import { classNameToFileName, checkResourceNameIsValid } from '../../common/filenames'
import { Component } from '../../common/component'
import { Logger } from '@boostercloud/framework-types'
import Brand from '../../common/brand'
import { FileSystem } from '../file-system'
import { FileGenerator, TemplateType } from '.'
import { CliError, cliErrorCatch } from '../../common/errors'
import { UserInput } from '../user-input'
import { Process } from '../process'

@Component({ throws: CliError })
export class LocalFileGenerator implements FileGenerator {
  private resourceTemplatesPath = path.join(__dirname, '..', '..', 'templates')

  constructor(
    readonly logger: Logger,
    readonly fileSystem: FileSystem,
    readonly userInput: UserInput,
    readonly process: Process
  ) {}

  async catch(e: unknown): Promise<CliError> {
    return cliErrorCatch('GeneratorError', e)
  }

  async copyStubs(force = false): Promise<void> {
    const cwd = await this.process.cwd()
    const destination = path.join(cwd, 'stubs')
    const stubFolderExists = await this.fileSystem.exists(destination)
    if (stubFolderExists && !force) {
      const confirmation = await this.userInput.defaultBoolean(
        Brand.dangerize(
          'The "stubs" folder already exists. Do you want to overwrite it? This will remove all the files inside it.'
        )
      )
      if (!confirmation) {
        throw new CliError('GeneratorError', 'The "stubs" folder already exists. Aborting...')
      } else {
        await this.fileSystem.remove(destination, { recursive: true, force: true })
      }
    }
    await this.fileSystem.makeDirectory(destination, { recursive: true })
    const mapping = this.createTemplateDestinationMap()
    for (const [from, to] of Object.entries(mapping)) {
      await this.fileSystem.copy(from, to)
    }
  }

  async generate<TInfo>(target: Target<TInfo>): Promise<void> {
    const { resourcePath, exists } = await this.lookupResource(target)
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

  async lookupResource<TInfo>({
    name,
    placementDir,
    extension,
  }: Target<TInfo>): Promise<{ resourcePath: string; exists: boolean }> {
    const fileName = classNameToFileName(name)
    const cwd = await this.process.cwd()
    const absolutePath = path.resolve(cwd)
    const resourcePath = path.join(absolutePath, placementDir, `${fileName}.${extension}`)
    const exists = await this.fileSystem.exists(resourcePath)
    return { resourcePath, exists }
  }
}
