"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[1399],{380:(e,n,t)=>{t.r(n),t.d(n,{assets:()=>c,contentTitle:()=>r,default:()=>u,frontMatter:()=>s,metadata:()=>a,toc:()=>d});var o=t(5893),i=t(1151);const s={},r="Storing events in batches",a={id:"going-deeper/event-batches",title:"Storing events in batches",description:"This feature is only available for the Azure provider.",source:"@site/docs/10_going-deeper/event-batches.mdx",sourceDirName:"10_going-deeper",slug:"/going-deeper/event-batches",permalink:"/going-deeper/event-batches",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/event-batches.mdx",tags:[],version:"current",lastUpdatedBy:"Mario Castro Squella",lastUpdatedAt:1739295071,formattedLastUpdatedAt:"Feb 11, 2025",frontMatter:{},sidebar:"docs",previous:{title:"Scaling Booster Azure Functions",permalink:"/going-deeper/azure-scale"},next:{title:"Frequently Asked Questions",permalink:"/frequently-asked-questions"}},c={},d=[];function l(e){const n={a:"a",admonition:"admonition",code:"code",h1:"h1",p:"p",pre:"pre",...(0,i.a)(),...e.components};return(0,o.jsxs)(o.Fragment,{children:[(0,o.jsx)(n.h1,{id:"storing-events-in-batches",children:"Storing events in batches"}),"\n",(0,o.jsx)(n.admonition,{type:"warning",children:(0,o.jsx)(n.p,{children:"This feature is only available for the Azure provider."})}),"\n",(0,o.jsxs)(n.p,{children:["Booster will store events in batches by default, but this behavior can be disabled with some configuration. The\n",(0,o.jsx)(n.code,{children:"azureConfiguration"})," property of the ",(0,o.jsx)(n.code,{children:"BoosterConfig"})," object contains properties for enabling/disabling batch storage of\nevents, setting the size of chunks of each batch, as well as some other options that are applied when events are stored\nin batches."]}),"\n",(0,o.jsx)(n.pre,{children:(0,o.jsx)(n.code,{className:"language-typescript",children:"Booster.configure('azure', (config: BoosterConfig): void => {\n  // enable/disable event batching (default true)\n  config.azureConfiguration.enableEventBatching = false\n  config.azureConfiguration.cosmos = {\n    // Maximum number of operations in a single batch (default 100, max 100)\n    batchSize: 50,\n      requestOptions: {\n      // Override consistency level for specific operations\n      consistencyLevel: 'Strong',\n      // Enable/disable RU/minute usage when RU/second is exhausted\n      disableRUPerMinuteUsage: false,\n      // Specify indexing directives\n      indexingDirective: 'Include',\n    },\n  }\n\n  // Rest of the configuration\n})\n"})}),"\n",(0,o.jsxs)(n.p,{children:["For more details on the ",(0,o.jsx)(n.code,{children:"requestOptions"}),", we recommend reading ",(0,o.jsx)(n.a,{href:"https://learn.microsoft.com/en-us/javascript/api/%40azure/cosmos/requestoptions?view=azure-node-latest",children:"the official documentation from Microsoft"}),"."]})]})}function u(e={}){const{wrapper:n}={...(0,i.a)(),...e.components};return n?(0,o.jsx)(n,{...e,children:(0,o.jsx)(l,{...e})}):l(e)}},1151:(e,n,t)=>{t.d(n,{Z:()=>a,a:()=>r});var o=t(7294);const i={},s=o.createContext(i);function r(e){const n=o.useContext(s);return o.useMemo((function(){return"function"==typeof e?e(n):{...n,...e}}),[n,e])}function a(e){let n;return n=e.disableParentContext?"function"==typeof e.components?e.components(i):e.components||i:r(e.components),o.createElement(s.Provider,{value:n},e.children)}}}]);