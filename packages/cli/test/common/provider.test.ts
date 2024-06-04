import { Provider } from '../../src/common/provider'
import { expect } from '../expect'

describe('selectedProvider', (): void => {
  it('get selected provider: AWS', async () => {
    expect(Provider.AWS).to.be.equal('@boostercloud/framework-provider-aws (AWS) - Currently deprecated')
  })
  it('get selected provider: Azure', async () => {
    expect(Provider.AZURE).to.be.equal('@boostercloud/framework-provider-azure (Azure)')
  })
})
