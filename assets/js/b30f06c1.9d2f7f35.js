"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[7557],{3235:(e,n,o)=>{o.r(n),o.d(n,{assets:()=>c,contentTitle:()=>s,default:()=>p,frontMatter:()=>i,metadata:()=>a,toc:()=>d});var t=o(5893),r=o(1151);const i={},s="Environments",a={id:"going-deeper/environment-configuration",title:"Environments",description:"You can create multiple environments calling the Booster.configure function several times using different environment names as the first argument. You can create one file for each environment, but it is not required. In this example we set all environments in a single file:",source:"@site/docs/10_going-deeper/environment-configuration.mdx",sourceDirName:"10_going-deeper",slug:"/going-deeper/environment-configuration",permalink:"/going-deeper/environment-configuration",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/environment-configuration.mdx",tags:[],version:"current",lastUpdatedBy:"heavylemon1",lastUpdatedAt:1707990703,formattedLastUpdatedAt:"Feb 15, 2024",frontMatter:{},sidebar:"docs",previous:{title:"Going deeper with Booster",permalink:"/category/going-deeper-with-booster"},next:{title:"Advanced uses of the Register object",permalink:"/going-deeper/register"}},c={},d=[];function l(e){const n={code:"code",h1:"h1",p:"p",pre:"pre",...(0,r.a)(),...e.components};return(0,t.jsxs)(t.Fragment,{children:[(0,t.jsx)(n.h1,{id:"environments",children:"Environments"}),"\n",(0,t.jsxs)(n.p,{children:["You can create multiple environments calling the ",(0,t.jsx)(n.code,{children:"Booster.configure"})," function several times using different environment names as the first argument. You can create one file for each environment, but it is not required. In this example we set all environments in a single file:"]}),"\n",(0,t.jsx)(n.pre,{children:(0,t.jsx)(n.code,{className:"language-typescript",children:"// Here we use a single file called src/config.ts, but you can use separate files for each environment too.\nimport { Booster } from '@boostercloud/framework-core'\nimport { BoosterConfig } from '@boostercloud/framework-types'\n// A provider that deploys your app to AWS:\n\nBooster.configure('stage', (config: BoosterConfig): void => {\n  config.appName = 'fruit-store-stage'\n  config.providerPackage = '@boostercloud/framework-provider-aws'\n})\n\nBooster.configure('prod', (config: BoosterConfig): void => {\n  config.appName = 'fruit-store-prod'\n  config.providerPackage = '@boostercloud/framework-provider-aws'\n})\n"})}),"\n",(0,t.jsxs)(n.p,{children:['It is also possible to place an environment configuration in a separated file. Let\'s say that a developer called "John" created its own configuration file ',(0,t.jsx)(n.code,{children:"src/config/john.ts"}),". The content would be the following:"]}),"\n",(0,t.jsx)(n.pre,{children:(0,t.jsx)(n.code,{className:"language-typescript",children:"import { Booster } from '@boostercloud/framework-core'\nimport { BoosterConfig } from '@boostercloud/framework-types'\nimport * as AWS from\n\nBooster.configure('john', (config: BoosterConfig): void => {\n  config.appName = 'john-fruit-store'\n  config.providerPackage = '@boostercloud/framework-provider-aws'\n})\n"})}),"\n",(0,t.jsx)(n.p,{children:"The environment name will be required by any command from the Booster CLI that depends on the provider. For instance, when you deploy your application, you'll need to specify on which environment you want to deploy it:"}),"\n",(0,t.jsx)(n.pre,{children:(0,t.jsx)(n.code,{className:"language-sh",children:"boost deploy -e prod\n"})}),"\n",(0,t.jsx)(n.p,{children:"This way, you can have different configurations depending on your needs."}),"\n",(0,t.jsx)(n.p,{children:"Booster environments are extremely flexible. As shown in the first example, your 'fruit-store' app can have three team-wide environments: 'dev', 'stage', and 'prod', each of them with different app names or providers, that are deployed by your CI/CD processes. Developers, like \"John\" in the second example, can create their own private environments in separate config files to test their changes in realistic environments before committing them. Likewise, CI/CD processes could generate separate production-like environments to test different branches to perform QA in separate environments without interferences from other features under test."}),"\n",(0,t.jsxs)(n.p,{children:["The only thing you need to do to deploy a whole new completely-independent copy of your application is to use a different name. Also, Booster uses the credentials available in the machine (",(0,t.jsx)(n.code,{children:"~/.aws/credentials"})," in AWS) that performs the deployment process, so developers can even work on separate accounts than production or staging environments."]})]})}function p(e={}){const{wrapper:n}={...(0,r.a)(),...e.components};return n?(0,t.jsx)(n,{...e,children:(0,t.jsx)(l,{...e})}):l(e)}},1151:(e,n,o)=>{o.d(n,{Z:()=>a,a:()=>s});var t=o(7294);const r={},i=t.createContext(r);function s(e){const n=t.useContext(i);return t.useMemo((function(){return"function"==typeof e?e(n):{...n,...e}}),[n,e])}function a(e){let n;return n=e.disableParentContext?"function"==typeof e.components?e.components(r):e.components||r:s(e.components),t.createElement(i.Provider,{value:n},e.children)}}}]);