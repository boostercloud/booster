/* eslint-disable @typescript-eslint/no-explicit-any */
import { BoosterConfig, ProviderLibrary } from '@boostercloud/framework-types'
import { expect } from '../expect'
import { fake } from 'sinon'

const rewire = require('rewire')
const configValidator = rewire('../../src/services/config-validator')

describe('ConfigValidator', () => {
  describe('validate', () => {
    it('validates migrations', () => {
      const fakeValidateAllMigrations = fake()
      const unsetfakeValidateAllMigrations = configValidator.__set__('validateAllMigrations', fakeValidateAllMigrations)
      const fakeConfig = { an: 'object' }

      configValidator.ConfigValidator.validate(fakeConfig)

      expect(fakeValidateAllMigrations).to.have.been.calledOnceWith(fakeConfig)
      unsetfakeValidateAllMigrations()
    })
  })

  describe('validateAllMigrations', () => {
    it('validates each concept migrations', () => {
      const fakeValidateConceptMigrations = fake()
      const unsetFakeValidateConceptMigrations = configValidator.__set__(
        'validateConceptMigrations',
        fakeValidateConceptMigrations
      )

      const fakeOneClassMetadata = { one: 'object' }
      const fakeAnotherClassMetadata = { another: 'object' }
      const fakeConfig = {
        migrations: {
          OneClass: fakeOneClassMetadata,
          AnotherClass: fakeAnotherClassMetadata,
        },
      }

      const validateAllMigrations = configValidator.__get__('validateAllMigrations')
      validateAllMigrations(fakeConfig)

      expect(fakeValidateConceptMigrations).to.have.been.calledTwice
      expect(fakeValidateConceptMigrations).to.have.been.calledWith(fakeConfig, 'OneClass', fakeOneClassMetadata)
      expect(fakeValidateConceptMigrations).to.have.been.calledWith(
        fakeConfig,
        'AnotherClass',
        fakeAnotherClassMetadata
      )

      unsetFakeValidateConceptMigrations()
    })
  })

  describe('validateConceptMigrations', () => {
    it('throws when there are gaps in the migration versions for a concept', () => {
      const config = new BoosterConfig('test')
      config.provider = {} as ProviderLibrary
      const migrations = new Map()
      migrations.set(3, {} as any)
      migrations.set(2, {} as any)
      migrations.set(5, {} as any)
      config.migrations['concept'] = migrations

      const validateConceptMigrations = configValidator.__get__('validateConceptMigrations')
      expect(() => validateConceptMigrations(config, 'concept', migrations)).to.throw(
        /Migrations for 'concept' are invalid/
      )
    })

    it('does not throw when there are no gaps in the migration versions for a concept', () => {
      const config = new BoosterConfig('test')
      config.provider = {} as ProviderLibrary
      const migrations = new Map()
      migrations.set(4, {} as any)
      migrations.set(2, {} as any)
      migrations.set(3, {} as any)
      config.migrations['concept'] = migrations

      const validateConceptMigrations = configValidator.__get__('validateConceptMigrations')
      expect(() => validateConceptMigrations(config, 'concept', migrations)).to.not.throw()
    })
  })
})
