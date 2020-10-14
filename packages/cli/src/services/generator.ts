import * as path from 'path'
import * as fs from 'fs-extra'
import * as Mustache from 'mustache'
import { Target } from './generator/target'
import * as inflected from 'inflected'

export async function generate<TInfo>(target: Target<TInfo>): Promise<void> {
  const rendered = Mustache.render(target.template, target.info)
  const fileName = inflected.dasherize(inflected.underscore(target.name))
  const renderPath = path.join(process.cwd(), target.placementDir, `${fileName}${target.extension}`)
  await fs.outputFile(renderPath, rendered)
}
