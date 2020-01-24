describe('the `framework-provider-aws` public interface', () => {
  context('exposes a `Infrastructure` module', () => {
    it('provides a `deploy` method that performs the deploy action')
    it('provides a `nuke` method that performs the nuke action')
  })

  context('exposes a `Library` module', () => {
    it('provides the `rawCommandToEnvelope` method')
    it('provides the `handleCommandResult` method')
    it('provides the `handleCommandError` method')
    it('provides the `rawEventsToEnvelopes` method')
    it('provides the `storeEvent` method')
    it('provides the `readEntityEventsSince` method')
    it('provides the `readEntityLatestSnapshot` method')
    it('provides the `fetchReadModels` method')
    it('provides the `rawSignUpDataToUserEnvelope` method')
  })
})
