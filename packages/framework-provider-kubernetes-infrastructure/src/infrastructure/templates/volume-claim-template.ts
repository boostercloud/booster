export const boosterVolumeClaim = {
  name: 'booster-pvc',
  template: `apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: booster-pvc
  namespace: {{ namespace }}
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi`,
}
