// Mock configuration provider for testing
import { BoosterConfig, ConfigurationProvider } from '@boostercloud/framework-types'
import { ConfigurationService, resolveConfigurationValue, resolveConfigurationWithSource } from '../../src'
import { restore } from 'sinon'
import { expect } from '../expect'

class MockConfigurationProvider implements ConfigurationProvider {
  constructor(
    public readonly name: string,
    public readonly priority: number,
    private readonly values: Record<string, string> = {},
    private readonly available: boolean = true
  ) {}

  async getValue(key: string): Promise<string | undefined> {
    return this.values[key]
  }

  async isAvailable(): Promise<boolean> {
    return this.available
  }
}

describe('ConfigurationService', () => {
  let mockConfig: BoosterConfig

  beforeEach(() => {
    ConfigurationService.reset()
    mockConfig = new BoosterConfig('test')
    // Override the readonly env property for testing
    Object.assign(mockConfig, {
      env: {
        CONFIG_VAR: 'config-value',
        SHARED_VAR: 'config-shared-value',
      },
    })
  })

  afterEach(() => {
    restore()
    ConfigurationService.reset()
  })

  describe('getInstance', () => {
    it('should create singleton instance', () => {
      const instance1 = ConfigurationService.getInstance(mockConfig)
      const instance2 = ConfigurationService.getInstance(mockConfig)

      expect(instance1).to.equal(instance2)
    })

    it('should initialize with default providers', () => {
      const instance = ConfigurationService.getInstance(mockConfig)
      const providers = instance.getProviders()

      expect(providers).to.have.length(2)
      expect(providers.some((p) => p.name === 'environment-variables')).to.be.true
      expect(providers.some((p) => p.name === 'booster-config-env')).to.be.true
    })

    it('should include registered configuration providers', () => {
      const customProvider = new MockConfigurationProvider('custom', 25)
      mockConfig.addConfigurationProvider(customProvider)

      const instance = ConfigurationService.getInstance(mockConfig)
      const providers = instance.getProviders()

      expect(providers).to.have.length(3)
      expect(providers.some((p) => p.name === 'custom')).to.be.true
      expect(providers[0].name).to.equal('custom') // Highest priority first
    })
  })

  describe('getValue', () => {
    let originalEnv: typeof process.env

    beforeEach(() => {
      originalEnv = { ...process.env }
    })

    afterEach(() => {
      process.env = originalEnv
    })

    it('should resolve from highest priority provider', async () => {
      // Set up environment variable
      process.env['TEST_VAR'] = 'env-value'

      // Add higher priority provider
      const highPriorityProvider = new MockConfigurationProvider('high-priority', 25, {
        TEST_VAR: 'high-priority-value',
      })
      mockConfig.addConfigurationProvider(highPriorityProvider)

      const instance = ConfigurationService.getInstance(mockConfig)
      const value = await instance.getValue('TEST_VAR')

      expect(value).to.equal('high-priority-value')
    })

    it('should fallback to config.env', async () => {
      const instance = ConfigurationService.getInstance(mockConfig)
      const value = await instance.getValue('CONFIG_VAR')

      expect(value).to.equal('config-value')
    })

    it('should fallback to environment variables', async () => {
      process.env['ENV_ONLY_VAR'] = 'env-only-value'

      const instance = ConfigurationService.getInstance(mockConfig)
      const value = await instance.getValue('ENV_ONLY_VAR')

      expect(value).to.equal('env-only-value')
    })

    it('should return undefined for nonexistent keys', async () => {
      const instance = ConfigurationService.getInstance(mockConfig)
      const value = await instance.getValue('NONEXISTENT_KEY')

      expect(value).to.be.undefined
    })
  })

  describe('resolve', () => {
    it('should return resolution with source tracking', async () => {
      const customProvider = new MockConfigurationProvider('custom', 20, {
        TEST_KEY: 'custom-value',
      })
      mockConfig.addConfigurationProvider(customProvider)

      const instance = ConfigurationService.getInstance(mockConfig)
      const resolution = await instance.resolve('TEST_KEY')

      expect(resolution.value).to.equal('custom-value')
      expect(resolution.source).to.equal('custom')
      expect(resolution.key).to.equal('TEST_KEY')
    })

    it('should track source as none when no provider resolves', async () => {
      const instance = ConfigurationService.getInstance(mockConfig)
      const resolution = await instance.resolve('NONEXISTENT_KEY')

      expect(resolution.value).to.be.undefined
      expect(resolution.source).to.equal('none')
      expect(resolution.key).to.equal('NONEXISTENT_KEY')
    })
  })
})

describe('utility functions', () => {
  let mockConfig: BoosterConfig

  beforeEach(() => {
    ConfigurationService.reset()
    mockConfig = new BoosterConfig('test')
    // Override the readonly env property for testing
    Object.assign(mockConfig, {
      env: {
        UTIL_TEST_VAR: 'util-config-value',
      },
    })
  })

  afterEach(() => {
    ConfigurationService.reset()
  })

  describe('resolveConfigurationValue', () => {
    it('should resolve configuration value', async () => {
      const value = await resolveConfigurationValue(mockConfig, 'UTIL_TEST_VAR')
      expect(value).to.equal('util-config-value')
    })

    it('should return undefined for missing values', async () => {
      const value = await resolveConfigurationValue(mockConfig, 'MISSING_VAR')
      expect(value).to.be.undefined
    })
  })

  describe('resolveConfigurationWithSource', () => {
    it('should resolve with source information', async () => {
      const resolution = await resolveConfigurationWithSource(mockConfig, 'UTIL_TEST_VAR')

      expect(resolution.value).to.equal('util-config-value')
      expect(resolution.source).to.equal('booster-config-env')
      expect(resolution.key).to.equal('UTIL_TEST_VAR')
    })
  })
})

describe('integration scenarios', () => {
  let mockConfig: BoosterConfig
  let originalEnv: typeof process.env

  beforeEach(() => {
    ConfigurationService.reset()
    mockConfig = new BoosterConfig('test')
    originalEnv = { ...process.env }
  })

  afterEach(() => {
    process.env = originalEnv
    ConfigurationService.reset()
  })

  it('should implement correct precedence order', async () => {
    // Set up all configuration sources
    process.env['PRECEDENCE_TEST'] = 'env-value'
    mockConfig.env['PRECEDENCE_TEST'] = 'config-value'

    const customProvider = new MockConfigurationProvider('custom', 25, {
      PRECEDENCE_TEST: 'custom-value',
    })
    mockConfig.addConfigurationProvider(customProvider)

    const instance = ConfigurationService.getInstance(mockConfig)

    // should resolve from highest priority (custom provider)
    const value = await instance.getValue('PRECEDENCE_TEST')
    expect(value).to.equal('custom-value')

    const resolution = await instance.resolve('PRECEDENCE_TEST')
    expect(resolution.source).to.equal('custom')
  })

  it('should handle provider unavailability', async () => {
    process.env['FALLBACK_TEST'] = 'env-fallback'

    const unavailableProvider = new MockConfigurationProvider(
      'unavailable',
      25,
      { FALLBACK_TEST: 'unavailable-value' },
      false // Not available
    )
    mockConfig.addConfigurationProvider(unavailableProvider)

    const instance = ConfigurationService.getInstance(mockConfig)
    const value = await instance.getValue('FALLBACK_TEST')

    // Should fallback to environment variables
    expect(value).to.equal('env-fallback')
  })

  it('should handle multiple custom providers with correct priority', async () => {
    const lowProvider = new MockConfigurationProvider('low', 15, { MULTI_TEST: 'low-value' })
    const highProvider = new MockConfigurationProvider('high', 25, { MULTI_TEST: 'high-value' })
    const mediumProvider = new MockConfigurationProvider('mid', 20, { MULTI_TEST: 'medium-value' })

    mockConfig.addConfigurationProvider(lowProvider)
    mockConfig.addConfigurationProvider(highProvider)
    mockConfig.addConfigurationProvider(mediumProvider)

    const instance = ConfigurationService.getInstance(mockConfig)
    const value = await instance.getValue('MULTI_TEST')

    expect(value).to.equal('high-value')
  })
})
