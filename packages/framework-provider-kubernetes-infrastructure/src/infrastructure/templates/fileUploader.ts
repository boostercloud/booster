export const fileUploader = `kind: Service
apiVersion: v1
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

---
apiVersion: apps/v1
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
        image: braeder/testuploadfilekubernetes:latest
        env:
        - name: BOOSTER_ENV
          value: {{ environment }}
        ports:
        - containerPort: 2000
        volumeMounts:
          - mountPath: "/data/appCode"
            name: app-code
        imagePullPolicy: Always
`
