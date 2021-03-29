export const stateStoreRole = {
  name: 'statestorerole',
  template: `apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: secret-reader
  namespace: {{namespace}}
rules:
- apiGroups: [""]
  resources: ["secrets"]
  verbs: ["get", "list"]`,
}
