import { BoosterConfig, ProviderInfrastructure, ScheduledCommandMetadata } from '@boostercloud/framework-types'
import * as fs from 'fs'
import * as os from 'os'
import * as path from 'path'
import { restore, stub } from 'sinon'
import { Infrastructure } from '../src'
import { expect } from './expect'

describe('Local provider infrastructure', () => {
  describe('start', () => {
    let projectDir: string

    beforeEach(() => {
      projectDir = fs.mkdtempSync(path.join(os.tmpdir(), 'booster-local-'))
      fs.mkdirSync(path.join(projectDir, 'dist'))
      fs.writeFileSync(path.join(projectDir, 'dist', 'index.js'), 'module.exports = {}')
      stub(process, 'cwd').returns(projectDir)
    })

    afterEach(() => {
      restore()
      fs.rmSync(projectDir, { recursive: true, force: true })
    })

    it('rejects when scheduled commands are defined but the app exports no trigger', async () => {
      const config = new BoosterConfig('test')
      config.scheduledCommandHandlers['CheckCart'] = {
        class: class CheckCart {},
        scheduledOn: { minute: '0' },
      } as unknown as ScheduledCommandMetadata

      const start = Infrastructure().start as NonNullable<ProviderInfrastructure['start']>

      await expect(start(config, 0)).to.be.rejectedWith(/boosterTriggerScheduledCommands/)
    })
  })
})
