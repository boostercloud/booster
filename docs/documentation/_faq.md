# Frequently asked questions

**1.- When deploying my application in AWS for the first time, I got an error saying _"StagingBucket <your app name>-toolkit-bucket already exists"_**
  
When you deploy a Booster application to AWS, an S3 bucket needs to be created to upload the application code. Booster names that bucket
using your application name as a prefix. 
In AWS, bucket names must be unique _globally_, so if there is another bucket in the world with exactly the same name as
the one generated for your application, you will get this error.  

The solution is to change your application name in the configuration file so that the bucket name is unique. 
