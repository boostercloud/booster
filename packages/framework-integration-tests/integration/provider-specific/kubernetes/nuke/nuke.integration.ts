import * as chai from 'chai'
import { K8SManager } from '../k8s-manager'
import { waitForIt } from '../../../helper/sleep'
import { V1PodList, V1ServiceList } from '@kubernetes/client-node'

export const expect = chai.expect

describe('After nuke', () => {
  const k8sManager = new K8SManager()

  describe('the pods and services', () => {
    it('are deleted successfully', async () => {
      const services = await waitForIt(
        async () => await k8sManager.getKubernetesServices(),
        (service: V1ServiceList) => {
          return service.items.length === 0
        },
        5000,
        150000
      )

      const pods = await waitForIt(
        async () => await k8sManager.getKubernetesPods(),
        (pods: V1PodList) => {
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
