export const boosterService = {
  name: 'booster',
  template: `apiVersion: v1
kind: Service
metadata:
  name: booster
  namespace: {{ namespace }}
  labels:
    app: booster
spec:
  selector:
    app: booster
  ports:
  - protocol: TCP
    port: 80
    targetPort: 3000
  type: LoadBalancer`,
}
