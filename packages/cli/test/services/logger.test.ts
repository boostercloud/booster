import { logger, oraLogger } from '../../src/services/logger'
import { restore, replace, fake } from 'sinon'
import { expect } from '../expect'

describe('Booster logger', (): void => {
    beforeEach(() => {
        replace(oraLogger,'warn', fake.resolves({}))
        replace(oraLogger,'info', fake.resolves({}))
        replace(oraLogger,'fail', fake.resolves({}))
    }) 

    afterEach(() => {
       restore()
    }) 

    const message: string = 'this is a message for the logger'

    it('log a debug message', async () => {
        logger.debug(message) 
        expect(oraLogger.warn).to.have.been.calledWith(message)
    })

    it('log a info message', async () => {
        logger.info(message) 
        expect(oraLogger.info).to.have.been.calledWith(message)
    })
    
    it('log a error message', async () => {
        logger.error(message) 
        expect(oraLogger.fail).to.have.been.calledWith(message)
    })
})