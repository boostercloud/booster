"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[4514],{3905:(e,t,r)=>{r.d(t,{Zo:()=>c,kt:()=>m});var n=r(7294);function o(e,t,r){return t in e?Object.defineProperty(e,t,{value:r,enumerable:!0,configurable:!0,writable:!0}):e[t]=r,e}function a(e,t){var r=Object.keys(e);if(Object.getOwnPropertySymbols){var n=Object.getOwnPropertySymbols(e);t&&(n=n.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),r.push.apply(r,n)}return r}function l(e){for(var t=1;t<arguments.length;t++){var r=null!=arguments[t]?arguments[t]:{};t%2?a(Object(r),!0).forEach((function(t){o(e,t,r[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(r)):a(Object(r)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(r,t))}))}return e}function i(e,t){if(null==e)return{};var r,n,o=function(e,t){if(null==e)return{};var r,n,o={},a=Object.keys(e);for(n=0;n<a.length;n++)r=a[n],t.indexOf(r)>=0||(o[r]=e[r]);return o}(e,t);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);for(n=0;n<a.length;n++)r=a[n],t.indexOf(r)>=0||Object.prototype.propertyIsEnumerable.call(e,r)&&(o[r]=e[r])}return o}var s=n.createContext({}),u=function(e){var t=n.useContext(s),r=t;return e&&(r="function"==typeof e?e(t):l(l({},t),e)),r},c=function(e){var t=u(e.components);return n.createElement(s.Provider,{value:t},e.children)},p={inlineCode:"code",wrapper:function(e){var t=e.children;return n.createElement(n.Fragment,{},t)}},d=n.forwardRef((function(e,t){var r=e.components,o=e.mdxType,a=e.originalType,s=e.parentName,c=i(e,["components","mdxType","originalType","parentName"]),d=u(r),m=o,b=d["".concat(s,".").concat(m)]||d[m]||p[m]||a;return r?n.createElement(b,l(l({ref:t},c),{},{components:r})):n.createElement(b,l({ref:t},c))}));function m(e,t){var r=arguments,o=t&&t.mdxType;if("string"==typeof e||o){var a=r.length,l=new Array(a);l[0]=d;var i={};for(var s in t)hasOwnProperty.call(t,s)&&(i[s]=t[s]);i.originalType=e,i.mdxType="string"==typeof e?e:o,l[1]=i;for(var u=2;u<a;u++)l[u]=r[u];return n.createElement.apply(null,l)}return n.createElement.apply(null,r)}d.displayName="MDXCreateElement"},5162:(e,t,r)=>{r.d(t,{Z:()=>l});var n=r(7294),o=r(6010);const a="tabItem_Ymn6";function l(e){let{children:t,hidden:r,className:l}=e;return n.createElement("div",{role:"tabpanel",className:(0,o.Z)(a,l),hidden:r},t)}},4866:(e,t,r)=>{r.d(t,{Z:()=>T});var n=r(7462),o=r(7294),a=r(6010),l=r(2466),i=r(6550),s=r(1980),u=r(7392),c=r(12);function p(e){return function(e){var t;return(null==(t=o.Children.map(e,(e=>{if(!e||(0,o.isValidElement)(e)&&function(e){const{props:t}=e;return!!t&&"object"==typeof t&&"value"in t}(e))return e;throw new Error(`Docusaurus error: Bad <Tabs> child <${"string"==typeof e.type?e.type:e.type.name}>: all children of the <Tabs> component should be <TabItem>, and every <TabItem> should have a unique "value" prop.`)})))?void 0:t.filter(Boolean))??[]}(e).map((e=>{let{props:{value:t,label:r,attributes:n,default:o}}=e;return{value:t,label:r,attributes:n,default:o}}))}function d(e){const{values:t,children:r}=e;return(0,o.useMemo)((()=>{const e=t??p(r);return function(e){const t=(0,u.l)(e,((e,t)=>e.value===t.value));if(t.length>0)throw new Error(`Docusaurus error: Duplicate values "${t.map((e=>e.value)).join(", ")}" found in <Tabs>. Every value needs to be unique.`)}(e),e}),[t,r])}function m(e){let{value:t,tabValues:r}=e;return r.some((e=>e.value===t))}function b(e){let{queryString:t=!1,groupId:r}=e;const n=(0,i.k6)(),a=function(e){let{queryString:t=!1,groupId:r}=e;if("string"==typeof t)return t;if(!1===t)return null;if(!0===t&&!r)throw new Error('Docusaurus error: The <Tabs> component groupId prop is required if queryString=true, because this value is used as the search param name. You can also provide an explicit value such as queryString="my-search-param".');return r??null}({queryString:t,groupId:r});return[(0,s._X)(a),(0,o.useCallback)((e=>{if(!a)return;const t=new URLSearchParams(n.location.search);t.set(a,e),n.replace({...n.location,search:t.toString()})}),[a,n])]}function h(e){const{defaultValue:t,queryString:r=!1,groupId:n}=e,a=d(e),[l,i]=(0,o.useState)((()=>function(e){let{defaultValue:t,tabValues:r}=e;if(0===r.length)throw new Error("Docusaurus error: the <Tabs> component requires at least one <TabItem> children component");if(t){if(!m({value:t,tabValues:r}))throw new Error(`Docusaurus error: The <Tabs> has a defaultValue "${t}" but none of its children has the corresponding value. Available values are: ${r.map((e=>e.value)).join(", ")}. If you intend to show no default tab, use defaultValue={null} instead.`);return t}const n=r.find((e=>e.default))??r[0];if(!n)throw new Error("Unexpected error: 0 tabValues");return n.value}({defaultValue:t,tabValues:a}))),[s,u]=b({queryString:r,groupId:n}),[p,h]=function(e){let{groupId:t}=e;const r=function(e){return e?`docusaurus.tab.${e}`:null}(t),[n,a]=(0,c.Nk)(r);return[n,(0,o.useCallback)((e=>{r&&a.set(e)}),[r,a])]}({groupId:n}),k=(()=>{const e=s??p;return m({value:e,tabValues:a})?e:null})();(0,o.useLayoutEffect)((()=>{k&&i(k)}),[k]);return{selectedValue:l,selectValue:(0,o.useCallback)((e=>{if(!m({value:e,tabValues:a}))throw new Error(`Can't select invalid tab value=${e}`);i(e),u(e),h(e)}),[u,h,a]),tabValues:a}}var k=r(2389);const f="tabList__CuJ",g="tabItem_LNqP";function v(e){let{className:t,block:r,selectedValue:i,selectValue:s,tabValues:u}=e;const c=[],{blockElementScrollPositionUntilNextRender:p}=(0,l.o5)(),d=e=>{const t=e.currentTarget,r=c.indexOf(t),n=u[r].value;n!==i&&(p(t),s(n))},m=e=>{var t;let r=null;switch(e.key){case"Enter":d(e);break;case"ArrowRight":{const t=c.indexOf(e.currentTarget)+1;r=c[t]??c[0];break}case"ArrowLeft":{const t=c.indexOf(e.currentTarget)-1;r=c[t]??c[c.length-1];break}}null==(t=r)||t.focus()};return o.createElement("ul",{role:"tablist","aria-orientation":"horizontal",className:(0,a.Z)("tabs",{"tabs--block":r},t)},u.map((e=>{let{value:t,label:r,attributes:l}=e;return o.createElement("li",(0,n.Z)({role:"tab",tabIndex:i===t?0:-1,"aria-selected":i===t,key:t,ref:e=>c.push(e),onKeyDown:m,onClick:d},l,{className:(0,a.Z)("tabs__item",g,null==l?void 0:l.className,{"tabs__item--active":i===t})}),r??t)})))}function y(e){let{lazy:t,children:r,selectedValue:n}=e;const a=(Array.isArray(r)?r:[r]).filter(Boolean);if(t){const e=a.find((e=>e.props.value===n));return e?(0,o.cloneElement)(e,{className:"margin-top--md"}):null}return o.createElement("div",{className:"margin-top--md"},a.map(((e,t)=>(0,o.cloneElement)(e,{key:t,hidden:e.props.value!==n}))))}function w(e){const t=h(e);return o.createElement("div",{className:(0,a.Z)("tabs-container",f)},o.createElement(v,(0,n.Z)({},e,t)),o.createElement(y,(0,n.Z)({},e,t)))}function T(e){const t=(0,k.Z)();return o.createElement(w,(0,n.Z)({key:String(t)},e))}},6520:(e,t,r)=>{r.r(t),r.d(t,{assets:()=>c,contentTitle:()=>s,default:()=>m,frontMatter:()=>i,metadata:()=>u,toc:()=>p});var n=r(7462),o=(r(7294),r(3905)),a=r(5162),l=r(4866);const i={},s="Webhook Rocket",u={unversionedId:"going-deeper/rockets/rocket-webhook",id:"going-deeper/rockets/rocket-webhook",title:"Webhook Rocket",description:"This rocket adds a Webhook to your Booster application. When the webhook is called, a function will be executed in your Booster application with request as a parameter.",source:"@site/docs/10_going-deeper/rockets/rocket-webhook.md",sourceDirName:"10_going-deeper/rockets",slug:"/going-deeper/rockets/rocket-webhook",permalink:"/going-deeper/rockets/rocket-webhook",draft:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/rockets/rocket-webhook.md",tags:[],version:"current",lastUpdatedBy:"gonzalojaubert",lastUpdatedAt:1683285609,formattedLastUpdatedAt:"May 5, 2023",frontMatter:{},sidebar:"docs",previous:{title:"Static Sites Rocket",permalink:"/going-deeper/rockets/rocket-static-sites"},next:{title:"Testing",permalink:"/going-deeper/testing"}},c={},p=[{value:"Supported Providers",id:"supported-providers",level:2},{value:"Usage",id:"usage",level:2},{value:"Return type",id:"return-type",level:2},{value:"Demo",id:"demo",level:2}],d={toc:p};function m(e){let{components:t,...r}=e;return(0,o.kt)("wrapper",(0,n.Z)({},d,r,{components:t,mdxType:"MDXLayout"}),(0,o.kt)("h1",{id:"webhook-rocket"},"Webhook Rocket"),(0,o.kt)("p",null,"This rocket adds a Webhook to your Booster application. When the webhook is called, a function will be executed in your Booster application with request as a parameter."),(0,o.kt)("admonition",{type:"info"},(0,o.kt)("p",{parentName:"admonition"},(0,o.kt)("a",{parentName:"p",href:"https://github.com/boostercloud/rocket-webhook"},"GitHub Repo"))),(0,o.kt)("h2",{id:"supported-providers"},"Supported Providers"),(0,o.kt)("ul",null,(0,o.kt)("li",{parentName:"ul"},"Azure Provider"),(0,o.kt)("li",{parentName:"ul"},"Local Provider")),(0,o.kt)("h2",{id:"usage"},"Usage"),(0,o.kt)("p",null,"Add your rocket to your application in the Booster configuration file:"),(0,o.kt)(l.Z,{groupId:"providers-usage",mdxType:"Tabs"},(0,o.kt)(a.Z,{value:"azure-provider",label:"Azure Provider",default:!0,mdxType:"TabItem"},(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"config.rockets = [buildBoosterWebhook(config).rocketForAzure()]\n"))),(0,o.kt)(a.Z,{value:"local-provider",label:"Local Provider",default:!0,mdxType:"TabItem"},(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"config.rockets = [buildBoosterWebhook(config).rocketForLocal()]\n")))),(0,o.kt)("p",null,"Then declare the function to initialize the ",(0,o.kt)("inlineCode",{parentName:"p"},"BoosterWebhook"),":"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"function buildBoosterWebhook(config: BoosterConfig): BoosterWebhook {\n    return new BoosterWebhook(config, [\n        {\n            origin: 'test',\n            handlerClass: TestHandler,\n        },\n        {\n            origin: 'other',\n            handlerClass: FacebookHandler,\n        },\n    ])\n}\n")),(0,o.kt)("admonition",{type:"info"},(0,o.kt)("p",{parentName:"admonition"},"Parameters:"),(0,o.kt)("ul",{parentName:"admonition"},(0,o.kt)("li",{parentName:"ul"},(0,o.kt)("strong",{parentName:"li"},"origin"),": Identify the webhook. It will be also the name of the endpoint that will be created."),(0,o.kt)("li",{parentName:"ul"},(0,o.kt)("strong",{parentName:"li"},"handlerClass"),": A class with a ",(0,o.kt)("inlineCode",{parentName:"li"},"handle")," method to handle the request."))),(0,o.kt)("p",null,"The ",(0,o.kt)("inlineCode",{parentName:"p"},"handle")," method should be like this one:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},'export class TestHandler {\n\n  constructor() {\n  }\n\n  public static async handle(webhookEventInterface: WebhookEvent, register: Register): Promise<WebhookHandlerReturnType> {\n    if (validationFails()) {\n      throw new InvalidParameterError("Error message");\n    }\n    return Promise.resolve({\n      body: { name: "my_name" }\n    });\n  }\n}\n')),(0,o.kt)("h2",{id:"return-type"},"Return type"),(0,o.kt)("p",null,"Handle methods return a promise of WebhookHandlerReturnType or void. This object contains the headers and body to be returned as response. "),(0,o.kt)("p",null,"Example:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"  public static async handle(webhookEventInterface: WebhookEvent, register: Register): Promise<WebhookHandlerReturnType> {\n    return Promise.resolve({\n      body: 'ok',\n      headers: {\n        Test: 'test header',\n      },\n    })\n  }\n")),(0,o.kt)("h2",{id:"demo"},"Demo"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-bash"},"curl --request POST 'http://localhost:3000/webhook/command?param1=testvalue'\n")),(0,o.kt)("p",null,"The webhookEventInterface object will be similar to this one:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"{\n  origin: 'test',\n  method: 'POST',\n  url: '/test?param1=testvalue',\n  originalUrl: '/webhook/test?param1=testvalue',\n  headers: {\n    accept: '*/*',\n    'cache-control': 'no-cache',\n    host: 'localhost:3000',\n    'accept-encoding': 'gzip, deflate, br',\n    connection: 'keep-alive',\n    'content-length': '0'\n  },\n  query: { param1: 'testvalue' },\n  params: {},\n  rawBody: undefined,\n  body: {}\n}\n")))}m.isMDXComponent=!0}}]);