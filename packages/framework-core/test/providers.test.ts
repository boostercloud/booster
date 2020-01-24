/* eslint-disable @typescript-eslint/explicit-function-return-type */
/* eslint-disable @typescript-eslint/no-explicit-any */
/* eslint-disable @typescript-eslint/no-magic-numbers */

import { expect } from 'chai'
import * as chai from 'chai'
import { Providers } from '../src'
import { Provider } from '@boostercloud/framework-types'

chai.use(require('sinon-chai'))
chai.use(require('chai-as-promised'))

describe('the "choose" method', () => {
  it('rejects a not-known provider', async () => {
    await expect(Providers.choose('nonExistingProvider')).to.be.rejectedWith(/Provider not supported/)
  })

  it('resolves correctly with the expected provider', async () => {
    await expect(Providers.choose('aws')).to.become(Provider.AWS)
  })
})
