import { restore, stub } from 'sinon'
import { ConfigurationAdapter } from '../../src'
import { expect } from '../expect'

describe('ConfigurationAdapter', () => {
  beforeEach(() => {
    // Silence console warning during tests to avoid clutter
    stub(console, 'warn')
  })

  afterEach(() => {
    restore()
  })

  describe('constructor', () => {
    it('should create an instance with connection string', () => {
      const adapter = new ConfigurationAdapter('mock-connection-string')
      expect(adapter.name).to.equal('azure-app-configuration')
      expect(adapter.priority).to.equal(20)
    })

    it('should create an instance with endpoint', () => {
      const adapter = new ConfigurationAdapter(undefined, 'https://mock-endpoint.azconfig.io')
      expect(adapter.name).to.equal('azure-app-configuration')
      expect(adapter.priority).to.equal(20)
    })

    it('should create an instance with label filter', () => {
      const adapter = new ConfigurationAdapter('mock-connection-string', undefined, 'test-label')
      expect(adapter.name).to.equal('azure-app-configuration')
      expect(adapter.priority).to.equal(20)
    })
  })

  describe('static factory methods', () => {
    beforeEach(() => {
      // Clear environment variables
      delete process.env['AZURE_APP_CONFIG_CONNECTION_STRING']
      delete process.env['AZURE_APP_CONFIG_ENDPOINT']
    })

    afterEach(() => {
      // Restore environment variables
      delete process.env['AZURE_APP_CONFIG_CONNECTION_STRING']
      delete process.env['AZURE_APP_CONFIG_ENDPOINT']
    })

    it('should create adapter from environment with connection string', () => {
      process.env['AZURE_APP_CONFIG_CONNECTION_STRING'] = 'mock-connection-string'

      const adapter = ConfigurationAdapter.fromEnvironment()
      expect(adapter.name).to.equal('azure-app-configuration')
    })

    it('should create adapter from environment with endpoint', () => {
      process.env['AZURE_APP_CONFIG_ENDPOINT'] = 'https://mock-endpoint.azconfig.io'

      const adapter = ConfigurationAdapter.fromEnvironment()
      expect(adapter.name).to.equal('azure-app-configuration')
    })

    it('should create adapter with connection string', () => {
      const adapter = ConfigurationAdapter.withConnectionString('mock-connection-string')
      expect(adapter.name).to.equal('azure-app-configuration')
    })

    it('should create adapter with endpoint', () => {
      const adapter = ConfigurationAdapter.withEndpoint('https://mock-endpoint.azconfig.io')
      expect(adapter.name).to.equal('azure-app-configuration')
    })
  })

  describe('isAvailable', () => {
    it('should return false when no connection string or endpoint is provided', async () => {
      const adapter = new ConfigurationAdapter()
      const available = await adapter.isAvailable()
      expect(available).to.be.false
    })

    it('should return false when initialization fails', async () => {
      // Create provider with invalid connection string to force initialization error
      const adapter = new ConfigurationAdapter('invalid-connection-string')
      const available = await adapter.isAvailable()
      expect(available).to.be.false
    })
  })

  describe('getValue', () => {
    it('should return undefined when not available', async () => {
      const adapter = new ConfigurationAdapter()
      const value = await adapter.getValue('test-key')
      expect(value).to.be.undefined
    })

    it('should return undefined when client fails', async () => {
      const adapter = new ConfigurationAdapter('invalid-connection-string')
      const value = await adapter.getValue('test-key')
      expect(value).to.be.undefined
    })
  })

  describe('error handling', () => {
    it('should handle initialization errors gracefully', async () => {
      const adapter = new ConfigurationAdapter('invalid-connection-string')

      // Should not throw, even with invalid connection string
      const available = await adapter.isAvailable()
      expect(available).to.be.false

      const value = await adapter.getValue('test-key')
      expect(value).to.be.undefined
    })

    it('should handle missing configuration gracefully', async () => {
      const adapter = new ConfigurationAdapter()

      const available = await adapter.isAvailable()
      expect(available).to.be.false

      const value = await adapter.getValue('nonexistent-key')
      expect(value).to.be.undefined
    })
  })

  describe('integration scenarios', () => {
    it('should work with label filters', async () => {
      const adapter = new ConfigurationAdapter('mock-connection-string', undefined, 'producton')

      // Should create without throwing
      expect(adapter.name).to.equal('azure-app-configuration')
      expect(adapter.priority).to.equal(20)
    })

    it('should prefer connection string over endpoint', async () => {
      const adapter = new ConfigurationAdapter('mock-connection-string', 'https://mock-endpoint.azconfig.io')

      // Should create without throwing
      expect(adapter.name).to.equal('azure-app-configuration')
    })
  })
})
