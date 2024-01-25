"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[1614],{7848:(e,t,r)=>{r.r(t),r.d(t,{assets:()=>d,contentTitle:()=>c,default:()=>h,frontMatter:()=>a,metadata:()=>i,toc:()=>l});var o=r(5893),n=r(1151),s=r(2991);const a={},c="Extending Booster with Rockets!",i={id:"going-deeper/rockets",title:"Extending Booster with Rockets!",description:"You can extend Booster by creating rockets (Booster Framework extensions). A rocket is just a node package that implements the public Booster rocket interfaces. You can use them for:",source:"@site/docs/10_going-deeper/rockets.mdx",sourceDirName:"10_going-deeper",slug:"/going-deeper/rockets",permalink:"/going-deeper/rockets",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/rockets.mdx",tags:[],version:"current",lastUpdatedBy:"Nick Seagull",lastUpdatedAt:1706202233,formattedLastUpdatedAt:"Jan 25, 2024",frontMatter:{},sidebar:"docs",previous:{title:"Create custom providers",permalink:"/going-deeper/custom-providers"},next:{title:"File Uploads Rocket",permalink:"/going-deeper/rockets/rocket-file-uploads"}},d={},l=[{value:"Create an Infrastructure Rocket package to extend the default Booster-provided infrastructure",id:"create-an-infrastructure-rocket-package-to-extend-the-default-booster-provided-infrastructure",level:3},{value:"Provide new abtractions with custom decorators",id:"provide-new-abtractions-with-custom-decorators",level:3},{value:"Naming recommendations",id:"naming-recommendations",level:3},{value:"Booster Rockets list",id:"booster-rockets-list",level:3}];function u(e){const t={a:"a",code:"code",em:"em",h1:"h1",h3:"h3",li:"li",ol:"ol",p:"p",pre:"pre",strong:"strong",ul:"ul",...(0,n.a)(),...e.components};return(0,o.jsxs)(o.Fragment,{children:[(0,o.jsx)(t.h1,{id:"extending-booster-with-rockets",children:"Extending Booster with Rockets!"}),"\n",(0,o.jsx)(t.p,{children:"You can extend Booster by creating rockets (Booster Framework extensions). A rocket is just a node package that implements the public Booster rocket interfaces. You can use them for:"}),"\n",(0,o.jsxs)(t.ol,{children:["\n",(0,o.jsx)(t.li,{children:"Extend your infrastructure: You can write a rocket that adds provider resources to your application stack."}),"\n",(0,o.jsx)(t.li,{children:"Runtime extensions: Add new annotations and interfaces, which combined with infrastructure extensions, could implement new abstractions on top of highly requested use cases."}),"\n"]}),"\n",(0,o.jsxs)(t.p,{children:["If you want to create a rocket that supports several cloud providers or want to provide extra decorators and functionality on top of the infrastructure extensions, you'll probably need to distribute it as a set of separate packages. In this scenario we recommend using a monorepo management tool like ",(0,o.jsx)(t.a,{href:"https://rushjs.io",children:"Microsoft Rush"})," to maintail them all together in a single repository, but this is not a requirement. Your packages will work perfectly fine if you maintain them in separate repositories."]}),"\n",(0,o.jsx)(t.h3,{id:"create-an-infrastructure-rocket-package-to-extend-the-default-booster-provided-infrastructure",children:"Create an Infrastructure Rocket package to extend the default Booster-provided infrastructure"}),"\n",(0,o.jsxs)(t.p,{children:["A rocket is an npm package that extends your current Booster architecture. The structure is simple, and it mainly has 2 methods: ",(0,o.jsx)(t.code,{children:"mountStack"})," and ",(0,o.jsx)(t.code,{children:"unmountStack"}),". We'll explain what they are shortly."]}),"\n",(0,o.jsxs)(t.p,{children:[(0,o.jsx)(t.em,{children:"Infrastructure Rocket"})," interfaces are provider-dependant because each provider defines their own way to manage context, so ",(0,o.jsx)(t.em,{children:"Infrastructure Rockets"})," must import the corresponding booster infrastructure package for their chosen provider:"]}),"\n",(0,o.jsxs)(t.ul,{children:["\n",(0,o.jsxs)(t.li,{children:["For AWS: ",(0,o.jsx)(t.code,{children:"@boostercloud/framework-provider-aws-infrastructure"})]}),"\n",(0,o.jsxs)(t.li,{children:["For Azure: ",(0,o.jsx)(t.code,{children:"@boostercloud/framework-provider-azure-infrastructure"})]}),"\n",(0,o.jsxs)(t.li,{children:["For Local (dev environment): ",(0,o.jsx)(t.code,{children:"@boostercloud/framework-provider-local-infrastructure"})]}),"\n"]}),"\n",(0,o.jsxs)(t.p,{children:["Notice that, as the only thing you'll need from that package is the ",(0,o.jsx)(t.code,{children:"InfrastructureRocket"})," interface, it is preferable to import it as a dev dependency to avoid including such a big package in your deployed lambdas."]}),"\n",(0,o.jsxs)(t.p,{children:["So let's start by creating a new package and adding the provider-depdendent dependency as well as the ",(0,o.jsx)(t.code,{children:"typescript"})," and ",(0,o.jsx)(t.code,{children:"@boostercloud/framework-types"})," packages:"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-sh",children:"mkdir rocket-your-rocket-name-aws-infrastructure\ncd rocket-your-rocket-name-aws-infrastructure\nnpm init\n...\nnpm install --save-dev @boostercloud/framework-provider-aws-infrastructure @boostercloud/framework-types typescript\n"})}),"\n",(0,o.jsxs)(t.p,{children:["In the case of AWS we use the ",(0,o.jsx)(t.a,{href:"https://docs.aws.amazon.com/cdk/v2/guide/work-with-cdk-typescript.html",children:"AWS CDK for TypeScript"}),", so you'll also need to import the AWS CDK package:"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-sh",children:"npm install --save-dev @aws-cdk/core\n"})}),"\n",(0,o.jsxs)(t.p,{children:["The basic structure of an ",(0,o.jsx)(t.em,{children:"Infrastructure Rocket"})," project is quite simple as you can see here:"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-text",children:"rocket-your-rocket-name-aws-infrastructure\n\u251c\u2500\u2500 package.json\n\u251c\u2500\u2500 src\n    \u251c\u2500\u2500 index.ts\n    \u2514\u2500\u2500 your-main-class.ts\n\n"})}),"\n",(0,o.jsxs)(t.p,{children:[(0,o.jsx)(t.code,{children:"<your-main-class>.ts"}),"  can be named as you want and this is where we define the mountStack and unmount methods."]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"import { RocketUtils } from '@boostercloud/framework-provider-aws-infrastructure'\nimport { BoosterConfig } from '@boostercloud/framework-types'\nimport { Stack } from '@aws-cdk/core'\nimport { YourRocketParams } from '.'\n\nexport class YourMainClass {\n  public static mountStack(params: YourRocketParams, stack: Stack, config: BoosterConfig): void {\n    /* CDK code to expand your Booster infrastructure */\n  }\n  public static unmountStack(params: YourRocketParams, utils: RocketUtils): void {\n    /* Optional code that runs before removing the stack */\n  }\n}\n"})}),"\n",(0,o.jsx)(t.p,{children:"Let's look in more detail these two special functions:"}),"\n",(0,o.jsxs)(t.ul,{children:["\n",(0,o.jsxs)(t.li,{children:["\n",(0,o.jsxs)(t.p,{children:[(0,o.jsx)(t.strong,{children:"mountStack"}),": Whenever we are deploying our Booster application (",(0,o.jsx)(t.code,{children:"boost deploy"}),") this method will also be run.  It receives two params:"]}),"\n",(0,o.jsxs)(t.ul,{children:["\n",(0,o.jsxs)(t.li,{children:[(0,o.jsx)(t.code,{children:"stack"}),": An initialized AWS CDK stack that you can use to add new resources. Check out ",(0,o.jsx)(t.a,{href:"https://docs.aws.amazon.com/cdk/latest/guide/stacks.html#stack_api",children:"the Stack API in the official CDK documentation"}),". This is the same stack instance that Booster uses to deploy its resources, so your resources will automatically be deployed along with the Booster's ones on the same stack."]}),"\n",(0,o.jsxs)(t.li,{children:[(0,o.jsx)(t.code,{children:"config"}),": It includes properties of the Booster project (e.g. project name) that come in handy for your rocket."]}),"\n"]}),"\n"]}),"\n",(0,o.jsxs)(t.li,{children:["\n",(0,o.jsxs)(t.p,{children:[(0,o.jsx)(t.strong,{children:"unmountStack"}),": This function executes when you run the ",(0,o.jsx)(t.code,{children:"boost nuke"})," command, just before starting the deletion of the cloud resources. When you nuke your Booster application, resources added by your rocket are automatically destroyed with the rest of the application stack. However, in certain cases, you may need extra steps during the deletion process. The ",(0,o.jsx)(t.code,{children:"unmountStack"})," function serves this purpose. For example, in AWS, you must first empty any S3 buckets before deleting your stack. You can achieve this within the ",(0,o.jsx)(t.code,{children:"unmountStack"})," method."]}),"\n"]}),"\n"]}),"\n",(0,o.jsxs)(t.p,{children:["In addition to your main rocket class, you'll need an ",(0,o.jsx)(t.code,{children:"index.ts"})," file that default exports an object that conforms to the ",(0,o.jsx)(t.code,{children:"InfrastructureRocket"})," interface:"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"export interface InfrastructureRocket {\n  mountStack: (stack: Stack, config: BoosterConfig) => void\n  unmountStack?: (utils: RocketUtils) => void\n}\n"})}),"\n",(0,o.jsxs)(t.p,{children:["You'll have to implement a default exported function that accepts a parameters object and returns an initialized ",(0,o.jsx)(t.code,{children:"InfrastructureRocket"})," object:"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"import { InfrastructureRocket } from '@boostercloud/framework-provider-aws-infrastructure'\nimport { YourMainClass } from './your-main-class';\n\nexport interface YourRocketParams {\n  param1: string\n}\n\nconst YourRocketInitializator = (params: YourRocketParams): InfrastructureRocket => ({\n  mountStack: SomePrivateObject.mountStack.bind(null, params),\n  unmountStack: SomePrivateObject.unmountStack.bind(null, params),\n})\n\nexport default YourRocketInitializator\n"})}),"\n",(0,o.jsxs)(t.p,{children:["Note that ",(0,o.jsx)(t.em,{children:"Infrastructure Rockets"})," must not be part of the Booster application code to prevent including the CDK and other unnecessary dependencies in the deployed lambdas. This is due to strict code size restrictions on most platforms. To address this, ",(0,o.jsx)(t.em,{children:"Infrastructure Rockets"})," are dynamically loaded by Booster, using package names as strings in the application config file:"]}),"\n",(0,o.jsx)(t.p,{children:(0,o.jsx)(t.em,{children:"src/config/production.ts:"})}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"Booster.configure('development', (config: BoosterConfig): void => {\n  config.appName = 'my-store'\n  config.providerPackage = '@boostercloud/framework-provider-aws'\n  config.rockets = [\n    {\n      packageName: 'rocket-your-rocket-name-aws-infrastructure', // Your infrastructure rocket package name\n      parameters: {\n        // A custom object with the parameters needed by your infrastructure rocket initializer\n        hello: 'world',\n      },\n    },\n  ]\n})\n"})}),"\n",(0,o.jsx)(t.p,{children:"Your rocket implementation will have access to the stack (CDK in AWS or Terraform in Azure) just after Booster has finished to add all its default resources, so while the most common scenario to implement a rocket is to create additional resources, it's also possible to inspect or alter the Booster stack. If you're considering creating and maintaining your own fork of one of the default provider runtime implementations, it could be easier to create a rocket instead."}),"\n",(0,o.jsx)(t.h3,{id:"provide-new-abtractions-with-custom-decorators",children:"Provide new abtractions with custom decorators"}),"\n",(0,o.jsx)(t.p,{children:"Rockets can be utilized to extend the Booster framework by providing additional decorators that offer new abstractions. When creating a decorator as part of your rocket, you should deliver it as a package that, once compiled, does not have any infrastructure dependencies, so if your rocket provides both infrastructure and runtime extensions, it's advisable to deliver it as a pair of packages or more."}),"\n",(0,o.jsx)(t.p,{children:"A common pattern when creating decorators for Booster is to use a singleton object to store metadata about the decorated structures. This singleton object stores data generated during the decorator's execution, which can then be accessed from other parts of the user's project, the rocket's infrastructure package or even other rockets. This data can be used during deployment to generate extra tables, endpoints, or other resources."}),"\n",(0,o.jsx)(t.p,{children:"To create a new custom decorator for the Booster framework with singleton storage, follow these steps:"}),"\n",(0,o.jsxs)(t.ol,{children:["\n",(0,o.jsx)(t.li,{children:"Create a new npm package for your rocket. This package should not have any infrastructure dependencies once compiled."}),"\n"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-sh",children:"$ mkdir my-booster-rocket\n$ cd my-booster-rocket\n$ npm init\n"})}),"\n",(0,o.jsxs)(t.ol,{start:"2",children:["\n",(0,o.jsx)(t.li,{children:"Add typescript as a dependency"}),"\n"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-sh",children:"$ npm install typescript --save-dev\n"})}),"\n",(0,o.jsxs)(t.ol,{start:"3",children:["\n",(0,o.jsx)(t.li,{children:"Create a src directory to hold your decorator code:"}),"\n"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-sh",children:"$ mkdir src\n"})}),"\n",(0,o.jsxs)(t.ol,{start:"4",children:["\n",(0,o.jsxs)(t.li,{children:["Inside the src directory, create a new TypeScript file for your singleton object, e.g., ",(0,o.jsx)(t.code,{children:"RocketSingleton.ts"}),":"]}),"\n"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-sh",children:"$ touch src/RocketSingleton.ts\n"})}),"\n",(0,o.jsxs)(t.ol,{start:"5",children:["\n",(0,o.jsx)(t.li,{children:'Implement your singleton object to store your metadata, for instance, a list of special classes that we will "mark" for later:'}),"\n"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"// src/RocketSingleton.ts\nexport class RocketSingleton {\n  public static specialClasses: Function[] = [];\n\n  private constructor() {}\n\n  public static addSpecialClass(target: Function): void {\n    RocketSingleton.specialClasses.push(target)\n  }\n}\n"})}),"\n",(0,o.jsxs)(t.ol,{start:"6",children:["\n",(0,o.jsxs)(t.li,{children:["Create a new TypeScript file for your custom decorator, e.g., ",(0,o.jsx)(t.code,{children:"MyCustomDecorator.ts"}),":"]}),"\n"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-sh",children:"$ touch src/MyCustomDecorator.ts\n"})}),"\n",(0,o.jsxs)(t.ol,{start:"7",children:["\n",(0,o.jsx)(t.li,{children:"Implement your custom decorator using the singleton object:"}),"\n"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:'// src/MyCustomDecorator.ts\nimport { RocketSingleton } from "./RocketSingleton"\n\nexport function MyCustomDecorator(): (target: Function) => void {\n  return (target: Function) => {\n    // Implement your decorator logic here.\n    console.log(`MyCustomDecorator applied on ${target.name}`)\n    RocketSingleton.addSpecialClass(target)\n  }\n}\n'})}),"\n",(0,o.jsxs)(t.ol,{start:"8",children:["\n",(0,o.jsx)(t.li,{children:"Export your decorator from the package's entry point, e.g., index.ts:"}),"\n"]}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"// src/index.ts\nexport * from './MyCustomDecorator';\nexport * from './RocketSingleton';\n"})}),"\n",(0,o.jsx)(t.p,{children:"Now you have a custom decorator that can be used within the Booster framework. Users can install your rocket package and use the decorator in their Booster applications:"}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-sh",children:"$ npm install my-booster-rocket\n"})}),"\n",(0,o.jsx)(t.pre,{children:(0,o.jsx)(t.code,{className:"language-typescript",children:"// src/MySpecialClass.ts\nimport { MyCustomDecorator, RocketSingleton } from 'my-booster-rocket';\n\n@MyCustomDecorator()\nclass MySpecialClass {\n  // Application logic here\n}\n\nconsole.log(RocketSingleton.specialClasses) // [ [Function: MySpecialClass] ]\n"})}),"\n",(0,o.jsx)(t.p,{children:"This example demonstrates how to create a custom decorator with a singleton object for storing data and package it as a rocket for use with the Booster framework. Following this pattern will allow you to extend Booster with new abstractions and provide additional functionality for users. The singleton object can be used to store and retrieve data across different parts of the user's project, enabling features such as generating extra tables or endpoints during deployment. This approach ensures a consistent and flexible way to extend the Booster framework while maintaining ease of use for developers."}),"\n",(0,o.jsx)(t.h3,{id:"naming-recommendations",children:"Naming recommendations"}),"\n",(0,o.jsx)(t.p,{children:"There are no restrictions on how you name your rocket packages, but we propose the following naming convention to make it easier to find your extensions in the vast npm library and find related packages (code and infrastructure extensions cannot be distributed in the same package)."}),"\n",(0,o.jsxs)(t.ul,{children:["\n",(0,o.jsxs)(t.li,{children:[(0,o.jsx)(t.code,{children:"rocket-{rocket-name}-{provider}"}),": A rocket that adds runtime functionality or init scripts. This code will be deployed along with your application code to the lambdas."]}),"\n",(0,o.jsxs)(t.li,{children:[(0,o.jsx)(t.code,{children:"rocket-{rocket-name}-{provider}-infrastructure"}),": A rocket that provides infrastructure extensions or implements deploy hooks. This code will only be used on developer's or CI/CD systems machines and won't be deployed to lambda with the rest of the application code."]}),"\n"]}),"\n",(0,o.jsxs)(t.p,{children:["Notice that some functionalities, for instance an S3 uploader, might require both runtime and infrastructure extensions. In these cases, the convention is to use the same name ",(0,o.jsx)(t.code,{children:"rocket-name"})," and add the suffix ",(0,o.jsx)(t.code,{children:"-infrastructure"})," to the infrastructure rocket. It's recommended, but not required, to manage these dependent packages in a monorepo and ensure that the versions match on each release."]}),"\n",(0,o.jsxs)(t.p,{children:["If you want to support the same functionality in several providers, it could be handy to also have a package named ",(0,o.jsx)(t.code,{children:"rocket-{rocket-name}-{provider}-core"})," where you can have cross-provider code that you can use from all the provider-specific implementations. For instance, a file uploader rocket that supports both AWS and Azure could have an structure like this:"]}),"\n",(0,o.jsxs)(t.ul,{children:["\n",(0,o.jsxs)(t.li,{children:[(0,o.jsx)(t.code,{children:"rocket-file-uploader-core"}),": Defines abstract decorators and interfaces to handle uploaded files."]}),"\n",(0,o.jsxs)(t.li,{children:[(0,o.jsx)(t.code,{children:"rocket-file-uploader-aws"}),": Implements the API calls to S3 to get the uploaded files."]}),"\n",(0,o.jsxs)(t.li,{children:[(0,o.jsx)(t.code,{children:"rocket-file-uploader-aws-infrastructure"}),": Adds a dedicated S3 bucket."]}),"\n",(0,o.jsxs)(t.li,{children:[(0,o.jsx)(t.code,{children:"rocket-file-uploader-azure"}),": Implements the API calls to Azure Storage to get the uploaded files."]}),"\n",(0,o.jsxs)(t.li,{children:[(0,o.jsx)(t.code,{children:"rocket-file-uploader-azure-infrastructure"}),": Configures file storage."]}),"\n"]}),"\n",(0,o.jsx)(t.h3,{id:"booster-rockets-list",children:"Booster Rockets list"}),"\n",(0,o.jsx)(t.p,{children:"Here you can check out the official Booster Rockets developed at this time:"}),"\n",(0,o.jsx)(s.Z,{})]})}function h(e={}){const{wrapper:t}={...(0,n.a)(),...e.components};return t?(0,o.jsx)(t,{...e,children:(0,o.jsx)(u,{...e})}):u(e)}},2991:(e,t,r)=>{r.d(t,{Z:()=>x});r(7294);var o=r(512),n=r(3438),s=r(3692),a=r(3919),c=r(5999),i=r(2503);const d={cardContainer:"cardContainer_fWXF",cardTitle:"cardTitle_rnsV",cardDescription:"cardDescription_PWke"};var l=r(5893);function u(e){let{href:t,children:r}=e;return(0,l.jsx)(s.Z,{href:t,className:(0,o.Z)("card padding--lg",d.cardContainer),children:r})}function h(e){let{href:t,icon:r,title:n,description:s}=e;return(0,l.jsxs)(u,{href:t,children:[(0,l.jsxs)(i.Z,{as:"h2",className:(0,o.Z)("text--truncate",d.cardTitle),title:n,children:[r," ",n]}),s&&(0,l.jsx)("p",{className:(0,o.Z)("text--truncate",d.cardDescription),title:s,children:s})]})}function p(e){let{item:t}=e;const r=(0,n.LM)(t);return r?(0,l.jsx)(h,{href:r,icon:"\ud83d\uddc3\ufe0f",title:t.label,description:t.description??(0,c.I)({message:"{count} items",id:"theme.docs.DocCard.categoryDescription",description:"The default description for a category card in the generated index about how many items this category includes"},{count:t.items.length})}):null}function m(e){let{item:t}=e;const r=(0,a.Z)(t.href)?"\ud83d\udcc4\ufe0f":"\ud83d\udd17",o=(0,n.xz)(t.docId??void 0);return(0,l.jsx)(h,{href:t.href,icon:r,title:t.label,description:t.description??o?.description})}function f(e){let{item:t}=e;switch(t.type){case"link":return(0,l.jsx)(m,{item:t});case"category":return(0,l.jsx)(p,{item:t});default:throw new Error(`unknown item type ${JSON.stringify(t)}`)}}function k(e){let{className:t}=e;const r=(0,n.jA)();return(0,l.jsx)(x,{items:r.items,className:t})}function x(e){const{items:t,className:r}=e;if(!t)return(0,l.jsx)(k,{...e});const s=(0,n.MN)(t);return(0,l.jsx)("section",{className:(0,o.Z)("row",r),children:s.map(((e,t)=>(0,l.jsx)("article",{className:"col col--6 margin-bottom--lg",children:(0,l.jsx)(f,{item:e})},t)))})}},1151:(e,t,r)=>{r.d(t,{Z:()=>c,a:()=>a});var o=r(7294);const n={},s=o.createContext(n);function a(e){const t=o.useContext(s);return o.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function c(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(n):e.components||n:a(e.components),o.createElement(s.Provider,{value:t},e.children)}}}]);