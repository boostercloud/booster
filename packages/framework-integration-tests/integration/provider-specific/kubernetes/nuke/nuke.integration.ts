import * as chai from 'chai'
import * as Kubernetes from '@kubernetes/client-node'
import { kubernetesNamespace } from '../constants'

export const expect = chai.expect

describe('After nuke', () => {
  const kubernetesConfig = new Kubernetes.KubeConfig()
  kubernetesConfig.loadFromDefault()
  const k8sClient = kubernetesConfig.makeApiClient(Kubernetes.CoreV1Api)

  describe('the pods', () => {
    it('are deleted successfully', async () => {
      const services = await k8sClient.listNamespacedService(kubernetesNamespace)
      const pods = await k8sClient.listNamespacedPod(kubernetesNamespace)

      expect(services.body.items).to.be.empty
      expect(pods.body.items).to.be.empty
    })
  })
})
