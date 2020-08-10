export const uploaderPod = {
  name: 'fileuploader',
  template: `apiVersion: apps/v1
kind: Deployment
metadata:
  name: fileuploader
  namespace: {{ namespace }}
  labels:
    app: fileuploader
spec:
  replicas: 1
  selector:
    matchLabels:
      app: fileuploader
  template:
    metadata:
      labels:
        app: fileuploader
    spec:
      volumes: 
        - name: app-code
          persistentVolumeClaim: 
            claimName: {{ clusterVolume }}
      containers:
      - name: fileuploader
        image: boostercloud/boosterfileuploader:latest
        env:
        - name: BOOSTER_ENV
          value: {{ environment }}
        ports:
        - containerPort: 2000
        volumeMounts:
          - mountPath: "/data/appCode"
            name: app-code
        imagePullPolicy: Always
`,
}
