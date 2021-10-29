import { expect } from './expect'
import { withContext, PartialRocketDescriptor } from '../src/rocket-descriptor'

describe('withContext', () => {
  const partialRocketDescriptors: Array<PartialRocketDescriptor> = [
    {
      packageName: 'asdf',
      parameters: {},
    },
  ]

  const fakeContext = { name: 'fakeContext' }
  expect(withContext(fakeContext, partialRocketDescriptors)).to.deep.equal([
    {
      packageName: 'asdf',
      parameters: {},
      context: { name: 'fakeContext' },
    },
  ])
})
