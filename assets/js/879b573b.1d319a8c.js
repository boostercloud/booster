"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[2690],{2999:(e,n,o)=>{o.r(n),o.d(n,{assets:()=>l,contentTitle:()=>s,default:()=>h,frontMatter:()=>i,metadata:()=>a,toc:()=>c});var r=o(5893),t=o(1151);o(5163);const i={},s="Configuring Infrastructure Providers",a={id:"going-deeper/infrastructure-providers",title:"Configuring Infrastructure Providers",description:"The providers are different implementations of the Booster runtime to allow Booster applications run on different cloud providers or services. They all implement the same interface, and the main idea behind the providers is that no matter what the developer chooses as backend, they won't need to know anything about the underlying infrastructure.",source:"@site/docs/10_going-deeper/infrastructure-providers.mdx",sourceDirName:"10_going-deeper",slug:"/going-deeper/infrastructure-providers",permalink:"/going-deeper/infrastructure-providers",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/infrastructure-providers.mdx",tags:[],version:"current",lastUpdatedBy:"Mario Castro Squella",lastUpdatedAt:1739295071,formattedLastUpdatedAt:"Feb 11, 2025",frontMatter:{},sidebar:"docs",previous:{title:"Advanced uses of the Register object",permalink:"/going-deeper/register"},next:{title:"Create custom providers",permalink:"/going-deeper/custom-providers"}},l={},c=[{value:"Booster configuration",id:"booster-configuration",level:2},{value:"Providers setup",id:"providers-setup",level:2},{value:"AWS Provider setup",id:"aws-provider-setup",level:3},{value:"Creating an AWS account",id:"creating-an-aws-account",level:3},{value:"Getting the AWS credentials",id:"getting-the-aws-credentials",level:3},{value:"Setting the AWS credentials on Booster",id:"setting-the-aws-credentials-on-booster",level:4},{value:"AWS Provider configuration",id:"aws-provider-configuration",level:4},{value:"Azure Provider Setup",id:"azure-provider-setup",level:2},{value:"Creating an Azure account",id:"creating-an-azure-account",level:3},{value:"Installing the Azure CLI",id:"installing-the-azure-cli",level:3},{value:"Azure Provider configuration",id:"azure-provider-configuration",level:3},{value:"Azure synth command",id:"azure-synth-command",level:3},{value:"Azure and CI/CD environments",id:"azure-and-cicd-environments",level:3},{value:"Azure host.json file",id:"azure-hostjson-file",level:3},{value:"Local Provider",id:"local-provider",level:2},{value:"Cleaning Local Data for New Changes",id:"cleaning-local-data-for-new-changes",level:3}];function d(e){const n={a:"a",admonition:"admonition",code:"code",em:"em",h1:"h1",h2:"h2",h3:"h3",h4:"h4",li:"li",ol:"ol",p:"p",pre:"pre",strong:"strong",ul:"ul",...(0,t.a)(),...e.components};return(0,r.jsxs)(r.Fragment,{children:[(0,r.jsx)(n.h1,{id:"configuring-infrastructure-providers",children:"Configuring Infrastructure Providers"}),"\n",(0,r.jsx)(n.p,{children:"The providers are different implementations of the Booster runtime to allow Booster applications run on different cloud providers or services. They all implement the same interface, and the main idea behind the providers is that no matter what the developer chooses as backend, they won't need to know anything about the underlying infrastructure."}),"\n",(0,r.jsx)(n.p,{children:"Currently, the Booster framework provides three provider packages:"}),"\n",(0,r.jsxs)(n.ul,{children:["\n",(0,r.jsx)(n.li,{children:(0,r.jsx)(n.strong,{children:"framework-provider-azure-*"})}),"\n",(0,r.jsxs)(n.li,{children:[(0,r.jsx)(n.strong,{children:"framework-provider-aws-*"})," (deprecated - See ",(0,r.jsx)(n.a,{href:"https://github.com/boostercloud/booster/pull/1477",children:"PR #1477"}),")"]}),"\n",(0,r.jsxs)(n.li,{children:[(0,r.jsx)(n.strong,{children:"framework-provider-local-*"})," (for local testing)"]}),"\n"]}),"\n",(0,r.jsxs)(n.p,{children:["Booster uses sensible defaults, convention over configuration, and code inference to reduce dramatically the amount of configuration needed. However, there are some aspects that can't be inferred (like the application name) or the provider library used for each ",(0,r.jsx)(n.a,{href:"environment-configuration",children:"environment"}),"."]}),"\n",(0,r.jsx)(n.h2,{id:"booster-configuration",children:"Booster configuration"}),"\n",(0,r.jsxs)(n.p,{children:["You configure your application by calling the ",(0,r.jsx)(n.code,{children:"Booster.configure()"})," method. There are no restrictions about where you should do this call, but the convention is to do it in your configuration files located in the ",(0,r.jsx)(n.code,{children:"src/config"})," folder. This folder will get automatically generated for you after running the ",(0,r.jsx)(n.code,{children:"boost new:project <project-name>"})," CLI command."]}),"\n",(0,r.jsx)(n.p,{children:"This is an example of a possible configuration:"}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-typescript",children:"import { Booster } from '@boostercloud/framework-core'\nimport { BoosterConfig } from '@boostercloud/framework-types'\n\nBooster.configure('pre-production', (config: BoosterConfig): void => {\n  config.appName = 'my-app-name'\n  config.providerPackage = '@boostercloud/framework-provider-aws'\n})\n"})}),"\n",(0,r.jsx)(n.p,{children:"The following is the list of the fields you can configure:"}),"\n",(0,r.jsxs)(n.ul,{children:["\n",(0,r.jsxs)(n.li,{children:["\n",(0,r.jsxs)(n.p,{children:[(0,r.jsx)(n.strong,{children:"appName:"})," This is the name that identifies your application. It will be used for many things, such us prefixing the resources created by the provider. There are certain restrictions regarding the characters you can use: all of them must be lower-cased and can't contain spaces. Two apps with different names are completely independent."]}),"\n"]}),"\n",(0,r.jsxs)(n.li,{children:["\n",(0,r.jsxs)(n.p,{children:[(0,r.jsx)(n.strong,{children:"providerPackage:"})," This field contains the name of the provider package that Booster will use when deploying or running your application."]}),"\n"]}),"\n",(0,r.jsxs)(n.li,{children:["\n",(0,r.jsxs)(n.p,{children:[(0,r.jsx)(n.strong,{children:"enableGraphQLIntrospection"})," This field allows to enable/disable get information about the GraphQL schema of your application from client side. By default is enabled but it is recommended to disable for security reasons in production applications."]}),"\n"]}),"\n",(0,r.jsxs)(n.li,{children:["\n",(0,r.jsxs)(n.p,{children:[(0,r.jsx)(n.strong,{children:"assets"}),": This is an array of ",(0,r.jsx)(n.em,{children:"relative"})," paths from the root of the project pointing to files and folders with static assets. They will be included among the deployed files to the cloud provider.\nFor example, imagine you are using the ",(0,r.jsx)(n.code,{children:"dotenv"})," module so that all the environment variables you have in your ",(0,r.jsx)(n.code,{children:".env"})," files are loaded into memory in runtime. In order for this to work, you need to include your ",(0,r.jsx)(n.code,{children:".env"})," files as assets of your project, so that they are included when deploying. Assuming you only have a ",(0,r.jsx)(n.code,{children:".env"})," file in the root of your project, you should add the following to your configuration:"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-typescript",children:"config.assets = ['.env']\n"})}),"\n"]}),"\n"]}),"\n",(0,r.jsx)(n.h2,{id:"providers-setup",children:"Providers setup"}),"\n",(0,r.jsx)(n.h3,{id:"aws-provider-setup",children:"AWS Provider setup"}),"\n",(0,r.jsx)(n.p,{children:"In order to dpeloy your app to AWS you only need to provide Booster with the credentials of an AWS account and the\nframework will take care of the rest."}),"\n",(0,r.jsxs)(n.admonition,{type:"caution",children:[(0,r.jsx)(n.p,{children:"Booster is free to use, but remember that the resources deployed to your cloud provider\nmight generate some expenses."}),(0,r.jsxs)(n.p,{children:["In AWS, all the resources generated by Booster are part of the ",(0,r.jsx)(n.a,{href:"https://aws.amazon.com/free",children:"AWS free tier"}),".\nWhen you're not eligible for the free tier, resources are charged on-demand. Deploying a\nBooster project and sending a few commands and queries should cost just a few cents."]}),(0,r.jsxs)(n.p,{children:["In any case, make sure to un-deploy your application with the command ",(0,r.jsx)(n.code,{children:"boost nuke -e production"}),"\nif you're not planning to keep using it."]})]}),"\n",(0,r.jsx)(n.h3,{id:"creating-an-aws-account",children:"Creating an AWS account"}),"\n",(0,r.jsxs)(n.p,{children:["If you don't have an AWS account, you can create one by following the ",(0,r.jsx)(n.a,{href:"https://aws.amazon.com/premiumsupport/knowledge-center/create-and-activate-aws-account/",children:"instructions in the AWS documentation"}),"."]}),"\n",(0,r.jsx)(n.h3,{id:"getting-the-aws-credentials",children:"Getting the AWS credentials"}),"\n",(0,r.jsxs)(n.p,{children:["Once you have an AWS account, you need to get the credentials that Booster needs to deploy your application. You can follow the ",(0,r.jsx)(n.a,{href:"https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_access-keys.html#Using_CreateAccessKey",children:"instructions in the AWS documentation"})," to get them."]}),"\n",(0,r.jsx)(n.p,{children:"Booster needs you to get the following credentials:"}),"\n",(0,r.jsxs)(n.ul,{children:["\n",(0,r.jsx)(n.li,{children:(0,r.jsx)(n.strong,{children:"Access Key ID"})}),"\n",(0,r.jsx)(n.li,{children:(0,r.jsx)(n.strong,{children:"Secret Access Key"})}),"\n"]}),"\n",(0,r.jsx)(n.p,{children:"Make sure you get them, as they will be needed in the next step."}),"\n",(0,r.jsx)(n.h4,{id:"setting-the-aws-credentials-on-booster",children:"Setting the AWS credentials on Booster"}),"\n",(0,r.jsxs)(n.p,{children:["Booster needs to know how to authenticate against your AWS account. For that reason, create a folder called ",(0,r.jsx)(n.code,{children:".aws"})," under your home folder, and a file inside called ",(0,r.jsx)(n.code,{children:"credentials"})," with this syntax:"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-ini",metastring:"title=~/.aws/credentials",children:"[default]\naws_access_key_id=<YOUR ACCESS KEY ID>\naws_secret_access_key=<YOUR SECRET ACCESS KEY>\n"})}),"\n",(0,r.jsx)(n.h4,{id:"aws-provider-configuration",children:"AWS Provider configuration"}),"\n",(0,r.jsx)(n.p,{children:"To configure AWS as a provider you need to meet certain prerequisites:"}),"\n",(0,r.jsxs)(n.ul,{children:["\n",(0,r.jsxs)(n.li,{children:["Check ",(0,r.jsx)(n.code,{children:"@boostercloud/framework-provider-aws"})," is listed in your app ",(0,r.jsx)(n.code,{children:"package.json"})," dependencies."]}),"\n",(0,r.jsxs)(n.li,{children:["Check ",(0,r.jsx)(n.code,{children:"@boostercloud/framework-provider-aws-infrastructure"})," is listed in your app ",(0,r.jsx)(n.code,{children:"package.json"})," devDependencies."]}),"\n",(0,r.jsxs)(n.li,{children:["Check both dependencies are installed, otherwise use ",(0,r.jsx)(n.code,{children:"npm install"})," in the root of your project."]}),"\n"]}),"\n",(0,r.jsxs)(n.p,{children:["Now go to your ",(0,r.jsx)(n.code,{children:"config.ts"})," file, import the aws provider library and set up your app environment."]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-typescript",children:"import { Booster } from '@boostercloud/framework-core'\nimport { BoosterConfig } from '@boostercloud/framework-types'\nimport { Provider as AWSProvider } from\n\nBooster.configure('production', (config: BoosterConfig): void => {\n  config.appName = 'my-app-name'\n  config.providePackage = '@boostercloud/framework-provider-aws'\n})\n"})}),"\n",(0,r.jsx)(n.p,{children:"Open your terminal and run the deployment command"}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-sh",children:"boost deploy -e production\n"})}),"\n",(0,r.jsx)(n.p,{children:"Now just let the magic happen, Booster will create everything for you and give you back your app ready to use URL. \ud83d\ude80"}),"\n",(0,r.jsx)(n.h2,{id:"azure-provider-setup",children:"Azure Provider Setup"}),"\n",(0,r.jsx)(n.p,{children:"Booster applications can be deployed to Microsoft Azure. To do so, you need to have an Azure account and to have the Azure CLI installed on your computer."}),"\n",(0,r.jsxs)(n.admonition,{type:"caution",children:[(0,r.jsx)(n.p,{children:"Booster is free to use, but remember that the resources deployed to your cloud provider\nmight generate some expenses."}),(0,r.jsx)(n.p,{children:"In Azure, when you're not eligible for the free tier, resources are charged on-demand. Deploying a\nBooster project and sending a few commands and queries should cost just a few cents."}),(0,r.jsxs)(n.p,{children:["In any case, make sure to un-deploy your application with the command ",(0,r.jsx)(n.code,{children:"boost nuke -e production"}),"\nif you're not planning to keep using it."]})]}),"\n",(0,r.jsx)(n.h3,{id:"creating-an-azure-account",children:"Creating an Azure account"}),"\n",(0,r.jsxs)(n.p,{children:["As mentioned, you need to have an Azure account. If you don't have one, you can create one from ",(0,r.jsx)(n.a,{href:"https://azure.microsoft.com/free/",children:"the Microsoft SignUp page"}),". You can also use your existing Microsoft account to create an Azure account."]}),"\n",(0,r.jsx)(n.h3,{id:"installing-the-azure-cli",children:"Installing the Azure CLI"}),"\n",(0,r.jsxs)(n.p,{children:["Once you have created the Azure account, you need to install the Azure CLI on your computer. You can do it by following the instructions on ",(0,r.jsx)(n.a,{href:"https://docs.microsoft.com/es-es/cli/azure/install-azure-cli",children:"the official documentation"}),". You may also need to install ",(0,r.jsx)(n.a,{href:"https://stedolan.github.io/jq/download/",children:"jq"})," on your system."]}),"\n",(0,r.jsx)(n.h3,{id:"azure-provider-configuration",children:"Azure Provider configuration"}),"\n",(0,r.jsx)(n.p,{children:"To configure Azure as a provider you need to meet certain prerequisites:"}),"\n",(0,r.jsxs)(n.ul,{children:["\n",(0,r.jsxs)(n.li,{children:["Install ",(0,r.jsx)(n.a,{href:"https://stedolan.github.io/jq/download/",children:"jq"})," in your system."]}),"\n",(0,r.jsxs)(n.li,{children:["Install ",(0,r.jsx)(n.a,{href:"https://developer.hashicorp.com/terraform/tutorials/azure-get-started/install-cli",children:"the terraform CLI"}),"."]}),"\n",(0,r.jsxs)(n.li,{children:["Check ",(0,r.jsx)(n.code,{children:"@boostercloud/framework-provider-azure"})," is listed in your app ",(0,r.jsx)(n.code,{children:"package.json"})," dependencies."]}),"\n",(0,r.jsxs)(n.li,{children:["Check ",(0,r.jsx)(n.code,{children:"@boostercloud/framework-provider-azure-infrastructure"})," is listed in your app ",(0,r.jsx)(n.code,{children:"package.json"})," devDependencies."]}),"\n",(0,r.jsxs)(n.li,{children:["Check both dependencies are installed, otherwise use ",(0,r.jsx)(n.code,{children:"npm install"})," in the root of your project."]}),"\n"]}),"\n",(0,r.jsx)(n.p,{children:"At this moment you have to log in you Azure account using the Azure CLI with the following command."}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-bash",children:"az login\n"})}),"\n",(0,r.jsx)(n.p,{children:"You will get something like this:"}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-bash",children:'[\n  {\n    "cloudName": "AzureCloud",\n    "homeTenantId": "00000000-0000-0000-0000-000000000000",\n    "id": "00000000-0000-0000-0000-000000000000",\n    "isDefault": true,\n    "managedByTenants": [],\n    "name": "Azure subscription name",\n    "state": "Enabled",\n    "tenantId": "00000000-0000-0000-0000-000000000000",\n    "user": {\n      "name": "boosteduser@boosteddomain.com",\n      "type": "user"\n    }\n  }\n]\n'})}),"\n",(0,r.jsxs)(n.p,{children:["Keep the ",(0,r.jsx)(n.code,{children:"id"})," from the login output around."]}),"\n",(0,r.jsx)(n.p,{children:"Then create a service pricipal that is a contributor to a chosen subscription running the following command."}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-bash",children:'az ad sp create-for-rbac --name <service-principal-name> --role="Contributor" --scopes="/subscriptions/<the-id-from-the-login-output>"\n'})}),"\n",(0,r.jsx)(n.admonition,{type:"note",children:(0,r.jsxs)(n.p,{children:["Remember to change ",(0,r.jsx)(n.code,{children:"<service-principal-name>"})," for a custom one."]})}),"\n",(0,r.jsx)(n.p,{children:"After the service principal is created, create a bash script with the following content. It will set up the necessary environment variables required by the provider in order to work:"}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-bash",children:"#!/usr/bin/env bash\n\nSP_DISPLAY_NAME=\"<service-principal-name>\" #replace <service-principal-name> with the name of your own SP\nREGION=\"East US\" #replace with a region of your choice, see full list here: https://azure.microsoft.com/en-us/global-infrastructure/locations/\nAZURE_MAX_CONTAINER_THROUGHPUT=1000 #replace with a desired value for CosmosDB container throughput\nAZURE_MAX_DATABASE_THROUGHPUT=1000 #replace with a desired value for CosmosDB database throughput\n\nexport AZURE_APP_ID=$(az ad sp list --display-name ${SP_DISPLAY_NAME} | jq -r '.[].appId')\nexport AZURE_TENANT_ID=$(az ad sp list --display-name ${SP_DISPLAY_NAME} | jq -r '.[].appOwnerOrganizationId')\nexport AZURE_SECRET=$(az ad sp credential reset --id ${AZURE_APP_ID} | jq -r '.password')\nexport AZURE_SUBSCRIPTION_ID=$(az account show | jq -r '.id')\nexport REGION=$REGION\nexport AZURE_MAX_CONTAINER_THROUGHPUT=$AZURE_MAX_CONTAINER_THROUGHPUT\nexport AZURE_MAX_DATABASE_THROUGHPUT=$AZURE_MAX_DATABASE_THROUGHPUT\n"})}),"\n",(0,r.jsx)(n.admonition,{type:"note",children:(0,r.jsxs)(n.p,{children:["Remember to have ",(0,r.jsx)(n.a,{href:"https://stedolan.github.io/jq/download/",children:"jq"})," installed in your system."]})}),"\n",(0,r.jsxs)(n.p,{children:["Now go to your ",(0,r.jsx)(n.code,{children:"config.ts"})," file, import the aws provider library and set up your app environment."]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-typescript",children:"import { Booster } from '@boostercloud/framework-core'\nimport { BoosterConfig } from '@boostercloud/framework-types'\n\nBooster.configure('production', (config: BoosterConfig): void => {\n  config.appName = 'my-app-name'\n  config.providerPackage = '@boostercloud/framework-provider-azure'\n})\n"})}),"\n",(0,r.jsx)(n.p,{children:"Open your terminal and run the bash file to export you env variables and the deploy command"}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-bash",children:"source <path-to-your-bash-file> && boost deploy -e production\n"})}),"\n",(0,r.jsx)(n.admonition,{type:"note",children:(0,r.jsxs)(n.p,{children:["Remember to have ",(0,r.jsx)(n.a,{href:"https://developer.hashicorp.com/terraform/tutorials/azure-get-started/install-cli",children:"the terraform CLI"})," installed in your system."]})}),"\n",(0,r.jsx)(n.p,{children:"Now just let the magic happen, Booster will create everything for you and give you back your app ready to use URL. \ud83d\ude80"}),"\n",(0,r.jsx)(n.h3,{id:"azure-synth-command",children:"Azure synth command"}),"\n",(0,r.jsxs)(n.p,{children:["Azure provider implements the experimental ",(0,r.jsx)(n.strong,{children:"Booster"})," ",(0,r.jsx)(n.code,{children:"synth"})," command. This command will generate ",(0,r.jsx)(n.a,{href:"https://www.terraform.io/",children:"Terraform"})," templates from your code. It will also generate needed files to deploy your Booster application using ",(0,r.jsx)(n.a,{href:"https://learn.hashicorp.com/tutorials/terraform/cdktf",children:"cdktf"}),"."]}),"\n",(0,r.jsxs)(n.p,{children:["Running ",(0,r.jsx)(n.code,{children:"synth"})," command, for example ",(0,r.jsx)(n.code,{children:"boost synth -e production"})," will generate following files:"]}),"\n",(0,r.jsxs)(n.ul,{children:["\n",(0,r.jsxs)(n.li,{children:["A file ",(0,r.jsx)(n.code,{children:"cdktf.json"}),": A basic json file to deploy your application using ",(0,r.jsx)(n.code,{children:"cdktf"})]}),"\n",(0,r.jsxs)(n.li,{children:["A folder ",(0,r.jsx)(n.code,{children:"cdktf.out"}),": with the Terraform templates."]}),"\n"]}),"\n",(0,r.jsxs)(n.p,{children:["Booster deploy command for Azure will deploy your application using the generated templates.  You don't need to run the ",(0,r.jsx)(n.code,{children:"synth"})," command for deploy your application, the ",(0,r.jsx)(n.code,{children:"deploy"})," command will generate the templates before deploy for you."]}),"\n",(0,r.jsxs)(n.p,{children:["Once you have the new files and folders generates you could use ",(0,r.jsx)(n.code,{children:"cdktf"})," to deploy your application if you want to."]}),"\n",(0,r.jsx)(n.h3,{id:"azure-and-cicd-environments",children:"Azure and CI/CD environments"}),"\n",(0,r.jsxs)(n.p,{children:["It is possible to deploy your Azure infrastructure using the ",(0,r.jsx)(n.a,{href:"https://www.terraform.io/",children:"Terraform"})," templates generated by the ",(0,r.jsx)(n.code,{children:"synth"})," command using Terraform executable."]}),"\n",(0,r.jsxs)(n.p,{children:["To deploy a ",(0,r.jsx)(n.strong,{children:"Booster"})," application using the ",(0,r.jsx)(n.a,{href:"https://www.terraform.io/",children:"Terraform"})," templates generated by the ",(0,r.jsx)(n.strong,{children:"Booster"})," ",(0,r.jsx)(n.code,{children:"synth"})," command:"]}),"\n",(0,r.jsxs)(n.ol,{children:["\n",(0,r.jsx)(n.li,{children:"Navigate to"}),"\n"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"> cd cdktf.out/stacks/<application name><environment name>\n"})}),"\n",(0,r.jsxs)(n.ol,{start:"2",children:["\n",(0,r.jsx)(n.li,{children:"Run (only the first time)"}),"\n"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"> terraform init\n"})}),"\n",(0,r.jsxs)(n.ol,{start:"3",children:["\n",(0,r.jsx)(n.li,{children:"Run"}),"\n"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"> terraform plan --out plan\n"})}),"\n",(0,r.jsxs)(n.ol,{start:"4",children:["\n",(0,r.jsx)(n.li,{children:"Run"}),"\n"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:'> terraform apply "plan"\n'})}),"\n",(0,r.jsxs)(n.p,{children:["You could follow similar steps to integrate the Azure ",(0,r.jsx)(n.strong,{children:"Booster"})," deploys in your CI/CD environment."]}),"\n",(0,r.jsxs)(n.ol,{children:["\n",(0,r.jsx)(n.li,{children:"Navigate to"}),"\n"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"> cd cdktf.out/stacks/<application name><environment name>\n"})}),"\n",(0,r.jsxs)(n.ol,{start:"2",children:["\n",(0,r.jsxs)(n.li,{children:["Copy ",(0,r.jsx)(n.code,{children:"functionApp.zip"})," to the destination folder"]}),"\n"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"> cp functionApp.zip <destination>\n"})}),"\n",(0,r.jsx)(n.p,{children:"After copying the files you should have the following structure:"}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-text",children:"<application>\n\u251c\u2500\u2500 cdktf.out\n\u2502   \u2514\u2500\u2500 stacks\n\u2502       \u2514\u2500\u2500 <application name><environment name>\n\u2502           \u2514\u2500\u2500 cdk.tf.json\n"})}),"\n",(0,r.jsx)(n.p,{children:"Now deploy the template:"}),"\n",(0,r.jsxs)(n.ol,{children:["\n",(0,r.jsx)(n.li,{children:"Run (only the first time)"}),"\n"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"> terraform init\n"})}),"\n",(0,r.jsxs)(n.ol,{start:"2",children:["\n",(0,r.jsx)(n.li,{children:"Run"}),"\n"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"> terraform plan --out plan\n"})}),"\n",(0,r.jsxs)(n.ol,{start:"3",children:["\n",(0,r.jsx)(n.li,{children:"Run"}),"\n"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:'> terraform apply "plan"\n'})}),"\n",(0,r.jsxs)(n.p,{children:["Finally, you need to upload the source code. The main options are (",(0,r.jsx)(n.a,{href:"https://docs.microsoft.com/en-us/azure/azure-functions/deployment-zip-push",children:"more info"}),"):"]}),"\n",(0,r.jsxs)(n.ol,{children:["\n",(0,r.jsx)(n.li,{children:"Using az-cli. Run"}),"\n"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"> az functionapp deployment source config-zip -g <resource_group> -n \\\n   <app_name> --src ./functionApp.json\n"})}),"\n",(0,r.jsxs)(n.ol,{start:"2",children:["\n",(0,r.jsxs)(n.li,{children:["Using REST APIs. Send a POST request to ",(0,r.jsx)(n.code,{children:"https://<app_name>.scm.azurewebsites.net/api/zipdeploy"}),". Example:"]}),"\n"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:'>  curl -X POST -u <deployment_user> --data-binary @"<zip_file_path>" https://<app_name>.scm.azurewebsites.net/api/zipdeploy\n'})}),"\n",(0,r.jsx)(n.admonition,{type:"note",children:(0,r.jsxs)(n.p,{children:["Remember to follow the ",(0,r.jsx)(n.strong,{children:"Azure Provider"})," steps in this page to set up your credentials correctly"]})}),"\n",(0,r.jsx)(n.h3,{id:"azure-hostjson-file",children:"Azure host.json file"}),"\n",(0,r.jsxs)(n.p,{children:["Azure Provider will generate a default ",(0,r.jsx)(n.code,{children:"host.json"})," file if there is not a ",(0,r.jsx)(n.code,{children:"host.json"})," entry in the ",(0,r.jsx)(n.code,{children:"config.assets"})," array."]}),"\n",(0,r.jsxs)(n.p,{children:["If you want to use your own ",(0,r.jsx)(n.code,{children:"host.json"})," file just add it to ",(0,r.jsx)(n.code,{children:"config.assets"})," array and Booster will use yours."]}),"\n",(0,r.jsx)(n.h2,{id:"local-provider",children:"Local Provider"}),"\n",(0,r.jsx)(n.p,{children:"All Booster projects come with a local development environment configured by default, so you can test your app before deploying it to the cloud."}),"\n",(0,r.jsxs)(n.p,{children:["You can see the configured local environment in your ",(0,r.jsx)(n.code,{children:"src/config/config.ts"})," file:"]}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-typescript",children:"Booster.configure('local', (config: BoosterConfig): void => {\n  config.appName = 'my-store'\n  config.providerPackage = '@boostercloud/framework-provider-local'\n})\n"})}),"\n",(0,r.jsx)(n.p,{children:"In order to start your application using the local provider, use the following command:"}),"\n",(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-bash",children:"boost start -e local\n"})}),"\n",(0,r.jsxs)(n.p,{children:["Where ",(0,r.jsx)(n.code,{children:"local"})," is one of your defined environments with the ",(0,r.jsx)(n.code,{children:"Booster.configure"})," call."]}),"\n",(0,r.jsx)(n.h3,{id:"cleaning-local-data-for-new-changes",children:"Cleaning Local Data for New Changes"}),"\n",(0,r.jsxs)(n.p,{children:["When making changes to your entities and events, you might need to reset the local environment to accommodate these changes. The application creates a ",(0,r.jsx)(n.code,{children:".booster"})," folder to store relevant information. To clean the local data and reset the environment:"]}),"\n",(0,r.jsxs)(n.ol,{children:["\n",(0,r.jsxs)(n.li,{children:["Locate the ",(0,r.jsx)(n.code,{children:".booster"})," folder in your project directory."]}),"\n",(0,r.jsxs)(n.li,{children:["Delete the contents of the ",(0,r.jsx)(n.code,{children:".booster"})," folder or the folder itself."]}),"\n"]}),"\n",(0,r.jsx)(n.p,{children:"This action will clear the local data and allow you to proceed with your new changes effectively."})]})}function h(e={}){const{wrapper:n}={...(0,t.a)(),...e.components};return n?(0,r.jsx)(n,{...e,children:(0,r.jsx)(d,{...e})}):d(e)}},5163:(e,n,o)=>{o.d(n,{Z:()=>i});o(7294);const r={terminalWindow:"terminalWindow_wGrl",terminalWindowHeader:"terminalWindowHeader_o9Cs",row:"row_Rn7G",buttons:"buttons_IGLB",right:"right_fWp9",terminalWindowAddressBar:"terminalWindowAddressBar_X8fO",dot:"dot_fGZE",terminalWindowMenuIcon:"terminalWindowMenuIcon_rtOE",bar:"bar_Ck8N",terminalWindowBody:"terminalWindowBody_tzdS"};var t=o(5893);function i(e){let{children:n}=e;return(0,t.jsxs)("div",{className:r.terminalWindow,children:[(0,t.jsx)("div",{className:r.terminalWindowHeader,children:(0,t.jsxs)("div",{className:r.buttons,children:[(0,t.jsx)("span",{className:r.dot,style:{background:"#f25f58"}}),(0,t.jsx)("span",{className:r.dot,style:{background:"#fbbe3c"}}),(0,t.jsx)("span",{className:r.dot,style:{background:"#58cb42"}})]})}),(0,t.jsx)("div",{className:r.terminalWindowBody,children:n})]})}},1151:(e,n,o)=>{o.d(n,{Z:()=>a,a:()=>s});var r=o(7294);const t={},i=r.createContext(t);function s(e){const n=r.useContext(i);return r.useMemo((function(){return"function"==typeof e?e(n):{...n,...e}}),[n,e])}function a(e){let n;return n=e.disableParentContext?"function"==typeof e.components?e.components(t):e.components||t:s(e.components),r.createElement(i.Provider,{value:n},e.children)}}}]);