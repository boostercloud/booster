import * as path from 'path'
import * as fs from 'fs-extra'
import * as Mustache from 'mustache'
import { Target } from './generator/target'
import * as inflection from 'inflection'

export async function generate<TInfo>(target: Target<TInfo>): Promise<void> {
  const rendered = Mustache.render(target.template, target.info)
  const fileName = inflection.underscore(target.name).replace(/_/g, '-')
  const renderPath = path.join(process.cwd(), target.placementDir, `${fileName}${target.extension}`)
  await fs.outputFile(renderPath, rendered)
}
