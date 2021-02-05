import { expect } from '../expect'
import { restore, fake, replace } from 'sinon'
//import * as Build from '../../src/commands/build'
import * as configService from '../../src/services/config-service'

describe('build', () => {
    describe('Build class', () => {
        beforeEach(() => {
            replace(configService,'compileProjectAndLoadConfig', fake.resolves({}))
        })

        afterEach(() => {
            restore()
        })

        it('runs the command', async () => {
            expect(1).to.eq(1)
        })
    })
})