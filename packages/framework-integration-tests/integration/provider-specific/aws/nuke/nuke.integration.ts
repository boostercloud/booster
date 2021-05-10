import * as chai from 'chai'
import { waitForIt } from '../../../helper/sleep'
import { K8SManager } from '../k8s-manager'

export const expect = chai.expect

describe('After nuke', () => {
  const k8sManager = new K8SManager()

  describe('the pods and services', () => {
    it('are deleted successfully', async () => {
      const services = await waitForIt(
        async () => {
          return await k8sManager.getKubernetesServices()
        },
        (services) => {
          return services.items.length === 0
        },
        5000,
        150000
      )

      const pods = await waitForIt(
        async () => {
          return await k8sManager.getKubernetesPods()
        },
        (pods) => {
          return pods.items.length === 0
        },
        5000,
        150000
      )

      expect(services.items).to.be.empty
      expect(pods.items).to.be.empty
    })
  })
})
