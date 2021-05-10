import * as chai from 'chai'
import * as Kubernetes from '@kubernetes/client-node'
import { kubernetesNamespace } from '../constants'
import { waitForIt } from '../../../helper/sleep'

export const expect = chai.expect

describe('After nuke', () => {
  const kubernetesConfig = new Kubernetes.KubeConfig()
  kubernetesConfig.loadFromDefault()
  const k8sClient = kubernetesConfig.makeApiClient(Kubernetes.CoreV1Api)

  describe('the pods and services', () => {
    it('are deleted successfully', async () => {
      const services = await waitForIt(
        async () => {
          return await k8sClient.listNamespacedService(kubernetesNamespace)
        },
        (services) => {
          return services.body.items.length === 0
        },
        5000,
        150000
      )

      const pods = await waitForIt(
        async () => {
          return await k8sClient.listNamespacedPod(kubernetesNamespace)
        },
        (pods) => {
          return pods.body.items.length === 0
        },
        5000,
        150000
      )

      expect(services.body.items).to.be.empty
      expect(pods.body.items).to.be.empty
    })
  })
})
