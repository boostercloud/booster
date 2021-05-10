import { expect } from '../../../helper/expect'
import * as Kubernetes from '@kubernetes/client-node'
import { boosterKubernetesServices, kubernetesNamespace } from '../constants'
import { waitForIt } from '../../../helper/sleep'
import { K8SManager } from '../k8s-manager'

describe('Kubernetes provider', () => {
  const k8sManager = new K8SManager()

  it('deploys the Booster application with accessible nodePort for fileuploader and booster services', async () => {
    const services = await k8sManager.getKubernetesServices()
    const pods = await k8sManager.getKubernetesPods()

    const serviceNames = services.items.map((item: Kubernetes.V1Service) => {
      expect(item.metadata?.namespace).to.equal(kubernetesNamespace)

      if (item?.metadata?.name === 'booster' || item?.metadata?.name === 'fileuploader') {
        expect(item?.spec?.ports?.length).to.equal(1)
        expect(item.spec?.ports?.[0]?.nodePort?.toString()).to.not.be.undefined
        // Only minikube uses NodePort type
        expect(item.spec?.type).to.equal('NodePort')
        expect(item.metadata?.labels?.app).to.equal(item?.metadata?.name)
      }

      return item?.metadata?.name
    })

    pods.items.map(async (pod: Kubernetes.V1Pod) => {
      await waitForIt(
        async () => {
          return Promise.resolve('Keep trying')
        },
        () => {
          return pod?.status?.phase === 'Running'
        }
      )

      expect(pod?.status?.phase).to.be.equal('Running')
    })

    expect(serviceNames).to.include.members(boosterKubernetesServices)
  })
})
