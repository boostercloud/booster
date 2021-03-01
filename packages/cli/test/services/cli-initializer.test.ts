import { createBoosterHomeFolder } from '../../src/services/cli-initializer'
import * as fs from 'fs'
import * as path from 'path'
import { restore, replace, fake } from 'sinon'
import { expect } from '../expect'
const os = require('os')

describe('cli initializer', () => {

    afterEach(() => {
        restore()
    })

    describe('createBoosterHomeFolder', () => {
        const boosterHomeDir = path.join(os.homedir(), '.booster')

        beforeEach(() => {
            replace(fs,'mkdirSync', fake.resolves({}))
        })

        it('folder do not exist', async () => {
            replace(fs, 'existsSync', fake.returns(false))
            await createBoosterHomeFolder()
            expect(fs.mkdirSync).to.have.been.calledWithMatch(boosterHomeDir)
        })

        it('folder exists', async () => {
            replace(fs, 'existsSync', fake.returns(true))
            await createBoosterHomeFolder()
            expect(fs.mkdirSync).to.have.not.been.calledWithMatch(boosterHomeDir)
        })
    })
})