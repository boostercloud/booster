import { expect } from '../expect'
import { restore, fake, replace } from 'sinon'
import * as Clean from '../../src/commands/clean'
import * as configService from '../../src/services/config-service'
import * as projectChecker from '../../src/services/project-checker'
import { oraLogger } from '../../src/services/logger'
import { IConfig } from '@oclif/config'

describe('clean', () => {
    describe('Clean class', () => {
        beforeEach(() => {
            replace(configService,'cleanProject', fake.resolves({}))
            replace(projectChecker,'checkCurrentDirIsABoosterProject', fake.resolves({}))
            replace(projectChecker,'checkCurrentDirBoosterVersion', fake.resolves({}))
            replace(oraLogger, 'info', fake.resolves({}))
            replace(oraLogger, 'start', fake.resolves({}))
        })

        afterEach(() => {
            restore()
        })

        it('init calls checkCurrentDirBoosterVersion', async () => {
            await new Clean.default([], {} as IConfig).init()
            expect(projectChecker.checkCurrentDirBoosterVersion).to.have.been.called
        })

        it('runs the command', async () => {
            await new Clean.default([], {} as IConfig).run()
            expect(projectChecker.checkCurrentDirIsABoosterProject).to.have.been.called
            expect(configService.cleanProject).to.have.been.called
            expect(oraLogger.start).to.have.been.calledWithMatch('Checking project structure')
            expect(oraLogger.start).to.have.been.calledWithMatch('Cleaning project')
            expect(oraLogger.info).to.have.been.calledWithMatch('Clean complete!')
        })
    })
})