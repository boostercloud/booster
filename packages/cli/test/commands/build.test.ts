import { expect } from '../expect'
import { restore, fake, replace } from 'sinon'
import * as Build from '../../src/commands/build'
import * as configService from '../../src/services/config-service'
import { oraLogger } from '../../src/services/logger'
import { IConfig } from '@oclif/config'

describe('build', () => {
    describe('Build class', () => {
        beforeEach(() => {
            replace(configService,'checkAndCompileProject', fake.resolves({}))
            replace(oraLogger, 'info', fake.resolves({}))
            replace(oraLogger, 'start', fake.resolves({}))
        })

        afterEach(() => {
            restore()
        })

        it('runs the command', async () => {
            await new Build.default([], {} as IConfig).run()
            expect(configService.checkAndCompileProject).to.have.been.called
            expect(oraLogger.info).to.have.been.calledWithMatch('Build complete!')
        })
    })
})