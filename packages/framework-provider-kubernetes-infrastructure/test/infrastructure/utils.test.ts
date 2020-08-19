import { expect } from '../expect'
import { waitForIt } from '../../src/infrastructure/utils'

describe('Users want to use utility methods', () => {
  it('and they want for a specific element', async () => {
    const tryFunction = async (): Promise<boolean> => {
      return true
    }

    const checkFunction = (checkResult: boolean): boolean => {
      return checkResult
    }

    let error = false
    waitForIt(tryFunction, checkFunction, 'error!!!').catch(() => {
      error = true
    })
    expect(error).to.be.false
  })

  it('and they want for a specific element but does not repond', async () => {
    const tryFunction = async (): Promise<boolean> => {
      return false
    }

    const checkFunction = (checkResult: boolean): boolean => {
      return checkResult
    }

    let error = false
    let errorMessage = ''
    await waitForIt(tryFunction, checkFunction, 'error!!!', 10).catch((err) => {
      error = true
      errorMessage = err.toString()
    })
    expect(error).to.be.true
    expect(errorMessage).to.be.equal('Error: error!!!')
  })
})
