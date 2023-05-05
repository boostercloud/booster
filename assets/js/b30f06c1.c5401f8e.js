"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[7557],{3905:(e,n,t)=>{t.d(n,{Zo:()=>l,kt:()=>d});var o=t(7294);function r(e,n,t){return n in e?Object.defineProperty(e,n,{value:t,enumerable:!0,configurable:!0,writable:!0}):e[n]=t,e}function i(e,n){var t=Object.keys(e);if(Object.getOwnPropertySymbols){var o=Object.getOwnPropertySymbols(e);n&&(o=o.filter((function(n){return Object.getOwnPropertyDescriptor(e,n).enumerable}))),t.push.apply(t,o)}return t}function a(e){for(var n=1;n<arguments.length;n++){var t=null!=arguments[n]?arguments[n]:{};n%2?i(Object(t),!0).forEach((function(n){r(e,n,t[n])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(t)):i(Object(t)).forEach((function(n){Object.defineProperty(e,n,Object.getOwnPropertyDescriptor(t,n))}))}return e}function s(e,n){if(null==e)return{};var t,o,r=function(e,n){if(null==e)return{};var t,o,r={},i=Object.keys(e);for(o=0;o<i.length;o++)t=i[o],n.indexOf(t)>=0||(r[t]=e[t]);return r}(e,n);if(Object.getOwnPropertySymbols){var i=Object.getOwnPropertySymbols(e);for(o=0;o<i.length;o++)t=i[o],n.indexOf(t)>=0||Object.prototype.propertyIsEnumerable.call(e,t)&&(r[t]=e[t])}return r}var c=o.createContext({}),p=function(e){var n=o.useContext(c),t=n;return e&&(t="function"==typeof e?e(n):a(a({},n),e)),t},l=function(e){var n=p(e.components);return o.createElement(c.Provider,{value:n},e.children)},u={inlineCode:"code",wrapper:function(e){var n=e.children;return o.createElement(o.Fragment,{},n)}},f=o.forwardRef((function(e,n){var t=e.components,r=e.mdxType,i=e.originalType,c=e.parentName,l=s(e,["components","mdxType","originalType","parentName"]),f=p(t),d=r,m=f["".concat(c,".").concat(d)]||f[d]||u[d]||i;return t?o.createElement(m,a(a({ref:n},l),{},{components:t})):o.createElement(m,a({ref:n},l))}));function d(e,n){var t=arguments,r=n&&n.mdxType;if("string"==typeof e||r){var i=t.length,a=new Array(i);a[0]=f;var s={};for(var c in n)hasOwnProperty.call(n,c)&&(s[c]=n[c]);s.originalType=e,s.mdxType="string"==typeof e?e:r,a[1]=s;for(var p=2;p<i;p++)a[p]=t[p];return o.createElement.apply(null,a)}return o.createElement.apply(null,t)}f.displayName="MDXCreateElement"},6635:(e,n,t)=>{t.r(n),t.d(n,{assets:()=>c,contentTitle:()=>a,default:()=>u,frontMatter:()=>i,metadata:()=>s,toc:()=>p});var o=t(7462),r=(t(7294),t(3905));const i={},a="Environments",s={unversionedId:"going-deeper/environment-configuration",id:"going-deeper/environment-configuration",title:"Environments",description:"You can create multiple environments calling the Booster.configure function several times using different environment names as the first argument. You can create one file for each environment, but it is not required. In this example we set all environments in a single file:",source:"@site/docs/10_going-deeper/environment-configuration.mdx",sourceDirName:"10_going-deeper",slug:"/going-deeper/environment-configuration",permalink:"/going-deeper/environment-configuration",draft:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/environment-configuration.mdx",tags:[],version:"current",lastUpdatedBy:"gonzalojaubert",lastUpdatedAt:1683285609,formattedLastUpdatedAt:"May 5, 2023",frontMatter:{},sidebar:"docs",previous:{title:"Going deeper with Booster",permalink:"/category/going-deeper-with-booster"},next:{title:"Advanced uses of the Register object",permalink:"/going-deeper/register"}},c={},p=[],l={toc:p};function u(e){let{components:n,...t}=e;return(0,r.kt)("wrapper",(0,o.Z)({},l,t,{components:n,mdxType:"MDXLayout"}),(0,r.kt)("h1",{id:"environments"},"Environments"),(0,r.kt)("p",null,"You can create multiple environments calling the ",(0,r.kt)("inlineCode",{parentName:"p"},"Booster.configure")," function several times using different environment names as the first argument. You can create one file for each environment, but it is not required. In this example we set all environments in a single file:"),(0,r.kt)("pre",null,(0,r.kt)("code",{parentName:"pre",className:"language-typescript"},"// Here we use a single file called src/config.ts, but you can use separate files for each environment too.\nimport { Booster } from '@boostercloud/framework-core'\nimport { BoosterConfig } from '@boostercloud/framework-types'\n// A provider that deploys your app to AWS:\n\nBooster.configure('stage', (config: BoosterConfig): void => {\n  config.appName = 'fruit-store-stage'\n  config.providerPackage = '@boostercloud/framework-provider-aws'\n})\n\nBooster.configure('prod', (config: BoosterConfig): void => {\n  config.appName = 'fruit-store-prod'\n  config.providerPackage = '@boostercloud/framework-provider-aws'\n})\n")),(0,r.kt)("p",null,'It is also possible to place an environment configuration in a separated file. Let\'s say that a developer called "John" created its own configuration file ',(0,r.kt)("inlineCode",{parentName:"p"},"src/config/john.ts"),". The content would be the following:"),(0,r.kt)("pre",null,(0,r.kt)("code",{parentName:"pre",className:"language-typescript"},"import { Booster } from '@boostercloud/framework-core'\nimport { BoosterConfig } from '@boostercloud/framework-types'\nimport * as AWS from\n\nBooster.configure('john', (config: BoosterConfig): void => {\n  config.appName = 'john-fruit-store'\n  config.providerPackage = '@boostercloud/framework-provider-aws'\n})\n")),(0,r.kt)("p",null,"The environment name will be required by any command from the Booster CLI that depends on the provider. For instance, when you deploy your application, you'll need to specify on which environment you want to deploy it:"),(0,r.kt)("pre",null,(0,r.kt)("code",{parentName:"pre",className:"language-sh"},"boost deploy -e prod\n")),(0,r.kt)("p",null,"This way, you can have different configurations depending on your needs."),(0,r.kt)("p",null,"Booster environments are extremely flexible. As shown in the first example, your 'fruit-store' app can have three team-wide environments: 'dev', 'stage', and 'prod', each of them with different app names or providers, that are deployed by your CI/CD processes. Developers, like \"John\" in the second example, can create their own private environments in separate config files to test their changes in realistic environments before committing them. Likewise, CI/CD processes could generate separate production-like environments to test different branches to perform QA in separate environments without interferences from other features under test."),(0,r.kt)("p",null,"The only thing you need to do to deploy a whole new completely-independent copy of your application is to use a different name. Also, Booster uses the credentials available in the machine (",(0,r.kt)("inlineCode",{parentName:"p"},"~/.aws/credentials")," in AWS) that performs the deployment process, so developers can even work on separate accounts than production or staging environments."))}u.isMDXComponent=!0}}]);