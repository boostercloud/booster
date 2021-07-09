export const template = `{{#imports}}
import { {{commaSeparatedComponents}} } from '{{{packagePath}}}'
{{/imports}}

const config = new BoosterConfig('test')
config.appName = 'testing-{{{projectName}}}'

describe('[{{{name}}}Test]', () => {
  it('should be true', () => {
    expect(true).to.be.true
  })
})
`
