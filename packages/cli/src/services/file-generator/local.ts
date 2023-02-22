import * as path from 'path'
import { outputFile, readFileSync, removeSync } from 'fs-extra'
import * as Mustache from 'mustache'
import { Target, FileDir } from './target'
import { classNameToFileName, checkResourceNameIsValid } from '../../common/filenames'
import {
  checkResourceStubFileExists,
  resourceStubFilePath,
  checkStubsFolderExists,
  resourceTemplateFilePath,
} from '../stub-publisher'
import type { TemplateType } from '../stub-publisher'
import { Component } from '../../common/component'
import { Logger } from '@boostercloud/framework-types'
import Brand from '../../common/brand'
import { FileSystem } from '../file-system'
import { FileGenerator } from '.'
import { UserProject } from '../user-project'
import { CliError } from '../../common/errors'

@Component
export class LocalFileGenerator implements FileGenerator {
  constructor(readonly logger: Logger, readonly fileSystem: FileSystem, readonly userProject: UserProject) {}

  async generate<TInfo>(target: Target<TInfo>): Promise<void> {
    const { resourcePath, exists } = await this.userProject.lookupResource(target)
    if (exists) {
      await this.confirmRemoveResource(target.name, target.placementDir, target.extension, resourcePath)
    }
    checkResourceNameIsValid(target.name)
    const rendered = Mustache.render(target.template, { ...target.info })
    await outputFile(resourcePath, rendered)
  }

  async template(name: TemplateType): Promise<string> {
    const fileName = `${name}.stub`
    const stubFile = resourceStubFilePath(fileName)

    // FIXME: Make stub publisher a service too
    if (checkStubsFolderExists() && checkResourceStubFileExists(stubFile)) {
      return readFileSync(stubFile).toString()
    }

    return readFileSync(resourceTemplateFilePath(fileName)).toString()
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

    const isConfirmed = await Prompter.confirmPrompt({
      message: Brand.dangerize(`Resource: "${resourceName}${extension}" already exists. Do you want to overwrite it?`),
    })
    if (isConfirmed) {
      await this.fileSystem.remove(resourcePath, { recursive: true })
    }
    throw new CliError(
      'GeneratorError',
      `The '${resourceType}' resource "${resourceName}${extension}" already exists. Please use another resource name`
    )
  }
}
