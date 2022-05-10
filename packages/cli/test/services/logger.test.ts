import { logger, oraLogger, appendOnErrorsFile } from '../../src/services/logger'
import * as fs from 'fs'
import * as path from 'path'
import { restore, replace, fake } from 'sinon'
import { expect } from '../expect'

describe('Booster logger', (): void => {
    afterEach(() => {
       restore()
    }) 
  
    describe('logger', () => {
        const message: string = 'this is a message for the logger'

        beforeEach(() => {
            replace(oraLogger,'warn', fake.resolves({}))
            replace(oraLogger,'info', fake.resolves({}))
            replace(oraLogger,'fail', fake.resolves({}))
        }) 

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

    describe('appendOnErrorsFile', () => {
        beforeEach(() => {
            replace(fs, 'appendFileSync', fake.returns(true))
        })

        it('writes in error.log file', async () => {
            const data = "this is a message\nseparated with new line"
            const errorsFile = path.join(process.cwd(), 'errors.log')
            appendOnErrorsFile(data) 
            expect(fs.appendFileSync).to.have.been.calledWithMatch(errorsFile, /this is a message/)
            expect(fs.appendFileSync).to.have.been.calledWithMatch(errorsFile, /separated with new line/)
        })
    })
})