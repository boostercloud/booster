import {
  BoosterConfigEnvProvider,
  ConfigurationProvider,
  DefaultConfigurationResolver,
  EnvironmentVariablesProvider,
} from '../src'
import { expect } from './expect'

// Mock configuration provider for testing
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

describe('DefaultConfigurationResolver', () => {
  let resolver: DefaultConfigurationResolver

  beforeEach(() => {
    resolver = new DefaultConfigurationResolver()
  })

  describe('constructor', () => {
    it('should create an empty resolver', () => {
      expect(resolver.getProviders()).to.have.length(0)
    })

    it('should create resolver with initial providers', () => {
      const provider1 = new MockConfigurationProvider('test1', 10)
      const provider2 = new MockConfigurationProvider('test2', 20)

      const resolverWithProviders = new DefaultConfigurationResolver([provider1, provider2])

      const providers = resolverWithProviders.getProviders()
      expect(providers).to.have.length(2)
      expect(providers[0].name).to.equal('test2') // Higher priority first
      expect(providers[1].name).to.equal('test1')
    })
  })

  describe('addProvider', () => {
    it('should add providers and sort by priority', () => {
      const provider1 = new MockConfigurationProvider('test1', 10)
      const provider2 = new MockConfigurationProvider('test2', 20)
      const provider3 = new MockConfigurationProvider('test3', 15)

      resolver.addProvider(provider1)
      resolver.addProvider(provider2)
      resolver.addProvider(provider3)

      const providers = resolver.getProviders()
      expect(providers).to.have.length(3)
      expect(providers[0].name).to.equal('test2') // Priority 20
      expect(providers[1].name).to.equal('test3') // Priority 15
      expect(providers[2].name).to.equal('test1') // Priority 10
    })

    it('should replace provider with same name', () => {
      const provider1 = new MockConfigurationProvider('test', 10, { key: 'low-priority' })
      const provider2 = new MockConfigurationProvider('test', 20, { key: 'high-priority' })

      resolver.addProvider(provider1)
      resolver.addProvider(provider2)

      const providers = resolver.getProviders()
      expect(providers).to.have.length(1)
      expect(providers[0].priority).to.equal(20)
    })
  })

  describe('resolve', () => {
    it('should resolve from highest priority provider', async () => {
      const provider1 = new MockConfigurationProvider('test1', 10, { key1: 'low-priority' })
      const provider2 = new MockConfigurationProvider('test2', 20, { key1: 'high-priority' })

      resolver.addProvider(provider1)
      resolver.addProvider(provider2)

      const result = await resolver.resolve('key1')
      expect(result.value).to.equal('high-priority')
      expect(result.source).to.equal('test2')
      expect(result.key).to.equal('key1')
    })

    it('should fallback to lower priority providers', async () => {
      const provider1 = new MockConfigurationProvider('test1', 10, { key1: 'fallback-value' })
      const provider2 = new MockConfigurationProvider('test2', 20, {}) // No key1

      resolver.addProvider(provider1)
      resolver.addProvider(provider2)

      const result = await resolver.resolve('key1')
      expect(result.value).to.equal('fallback-value')
      expect(result.source).to.equal('test1')
    })

    it('should return undefined when no provider has the value', async () => {
      const provider1 = new MockConfigurationProvider('test1', 10, {})
      const provider2 = new MockConfigurationProvider('test2', 20, {})

      resolver.addProvider(provider1)
      resolver.addProvider(provider2)

      const result = await resolver.resolve('nonexistent-key')
      expect(result.value).to.be.undefined
      expect(result.source).to.equal('none')
      expect(result.key).to.equal('nonexistent-key')
    })

    it('should skip unavailable providers', async () => {
      const provider1 = new MockConfigurationProvider('test1', 10, { key1: 'unavailable-value' }, false)
      const provider2 = new MockConfigurationProvider('test2', 20, { key1: 'available-value' }, true)

      resolver.addProvider(provider1)
      resolver.addProvider(provider2)

      const result = await resolver.resolve('key1')
      expect(result.value).to.equal('available-value')
      expect(result.source).to.equal('test2')
    })

    it('should handle provider errors gracefully', async () => {
      const errorProvider = new MockConfigurationProvider('error-provider', 20)
      // Mock getValue to throw an error
      errorProvider.getValue = async () => {
        throw new Error('Provider error')
      }

      const fallbackProvider = new MockConfigurationProvider('fallback', 10, { key1: 'fallback-value' })

      resolver.addProvider(errorProvider)
      resolver.addProvider(fallbackProvider)

      const result = await resolver.resolve('key1')
      expect(result.value).to.equal('fallback-value')
      expect(result.source).to.equal('fallback')
    })
  })
})

describe('EnvironmentVariablesProvider', () => {
  let provider: EnvironmentVariablesProvider
  let originalEnv: typeof process.env

  beforeEach(() => {
    provider = new EnvironmentVariablesProvider()
    originalEnv = { ...process.env }
  })

  afterEach(() => {
    process.env = originalEnv
  })

  it('should have correct name and priority', () => {
    expect(provider.name).to.equal('environment-variables')
    expect(provider.priority).to.equal(0)
  })

  it('should always be available', async () => {
    const available = await provider.isAvailable()
    expect(available).to.be.true
  })

  it('should get values from process.env', async () => {
    process.env['TEST_VAR'] = 'test-value'

    const value = await provider.getValue('TEST_VAR')
    expect(value).to.equal('test-value')
  })

  it('should return undefined for missing variables', async () => {
    const value = await provider.getValue('NONEXISTENT_VAR')
    expect(value).to.be.undefined
  })
})

describe('BoosterConfigEnvProvider', () => {
  let provider: BoosterConfigEnvProvider

  beforeEach(() => {
    const envConfig = {
      VAR1: 'value1',
      VAR2: 'value2',
    }
    provider = new BoosterConfigEnvProvider(envConfig)
  })

  it('should have correct name and priority', () => {
    expect(provider.name).to.equal('booster-config-env')
    expect(provider.priority).to.equal(10)
  })

  it('should always be available', async () => {
    const available = await provider.isAvailable()
    expect(available).to.be.true
  })

  it('should get values from config.env', async () => {
    const value = await provider.getValue('VAR1')
    expect(value).to.equal('value1')
  })

  it('should return undefined for missing variables', async () => {
    const value = await provider.getValue('NONEXISTENT_VAR')
    expect(value).to.be.undefined
  })
})
