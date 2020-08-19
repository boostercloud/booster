export const uploadService = {
  name: 'fileuploader',
  template: `apiVersion: v1
kind: Service
metadata:
  name: fileuploader
  namespace: {{ namespace }}
  labels:
    app: fileuploader
spec:
  selector:
    app: fileuploader
  ports:
  - protocol: TCP
    port: 80
    targetPort: 2000
  type: LoadBalancer
`,
}
