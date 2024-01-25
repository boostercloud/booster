"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[4514],{3033:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>u,contentTitle:()=>i,default:()=>p,frontMatter:()=>l,metadata:()=>c,toc:()=>d});var r=n(5893),o=n(1151),a=n(5162),s=n(4866);const l={},i="Webhook Rocket",c={id:"going-deeper/rockets/rocket-webhook",title:"Webhook Rocket",description:"This rocket adds a Webhook to your Booster application. When the webhook is called, a function will be executed in your Booster application with request as a parameter.",source:"@site/docs/10_going-deeper/rockets/rocket-webhook.md",sourceDirName:"10_going-deeper/rockets",slug:"/going-deeper/rockets/rocket-webhook",permalink:"/going-deeper/rockets/rocket-webhook",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/rockets/rocket-webhook.md",tags:[],version:"current",lastUpdatedBy:"Nick Seagull",lastUpdatedAt:1706186680,formattedLastUpdatedAt:"Jan 25, 2024",frontMatter:{},sidebar:"docs",previous:{title:"Static Sites Rocket",permalink:"/going-deeper/rockets/rocket-static-sites"},next:{title:"Sensor",permalink:"/going-deeper/sensor"}},u={},d=[{value:"Supported Providers",id:"supported-providers",level:2},{value:"Usage",id:"usage",level:2},{value:"Return type",id:"return-type",level:2},{value:"Demo",id:"demo",level:2}];function h(e){const t={a:"a",admonition:"admonition",code:"code",h1:"h1",h2:"h2",li:"li",p:"p",pre:"pre",strong:"strong",ul:"ul",...(0,o.a)(),...e.components};return(0,r.jsxs)(r.Fragment,{children:[(0,r.jsx)(t.h1,{id:"webhook-rocket",children:"Webhook Rocket"}),"\n",(0,r.jsx)(t.p,{children:"This rocket adds a Webhook to your Booster application. When the webhook is called, a function will be executed in your Booster application with request as a parameter."}),"\n",(0,r.jsx)(t.admonition,{type:"info",children:(0,r.jsx)(t.p,{children:(0,r.jsx)(t.a,{href:"https://github.com/boostercloud/rocket-webhook",children:"GitHub Repo"})})}),"\n",(0,r.jsx)(t.h2,{id:"supported-providers",children:"Supported Providers"}),"\n",(0,r.jsxs)(t.ul,{children:["\n",(0,r.jsx)(t.li,{children:"Azure Provider"}),"\n",(0,r.jsx)(t.li,{children:"Local Provider"}),"\n"]}),"\n",(0,r.jsx)(t.h2,{id:"usage",children:"Usage"}),"\n",(0,r.jsx)(t.p,{children:"Add your rocket to your application in the Booster configuration file:"}),"\n",(0,r.jsxs)(s.Z,{groupId:"providers-usage",children:[(0,r.jsx)(a.Z,{value:"azure-provider",label:"Azure Provider",default:!0,children:(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"config.rockets = [buildBoosterWebhook(config).rocketForAzure()]\n"})})}),(0,r.jsx)(a.Z,{value:"local-provider",label:"Local Provider",default:!0,children:(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"config.rockets = [buildBoosterWebhook(config).rocketForLocal()]\n"})})})]}),"\n",(0,r.jsxs)(t.p,{children:["Then declare the function to initialize the ",(0,r.jsx)(t.code,{children:"BoosterWebhook"}),":"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"function buildBoosterWebhook(config: BoosterConfig): BoosterWebhook {\n    return new BoosterWebhook(config, [\n        {\n            origin: 'test',\n            handlerClass: TestHandler,\n        },\n        {\n            origin: 'other',\n            handlerClass: FacebookHandler,\n        },\n    ])\n}\n"})}),"\n",(0,r.jsxs)(t.admonition,{type:"info",children:[(0,r.jsx)(t.p,{children:"Parameters:"}),(0,r.jsxs)(t.ul,{children:["\n",(0,r.jsxs)(t.li,{children:[(0,r.jsx)(t.strong,{children:"origin"}),": Identify the webhook. It will be also the name of the endpoint that will be created."]}),"\n",(0,r.jsxs)(t.li,{children:[(0,r.jsx)(t.strong,{children:"handlerClass"}),": A class with a ",(0,r.jsx)(t.code,{children:"handle"})," method to handle the request."]}),"\n"]})]}),"\n",(0,r.jsxs)(t.p,{children:["The ",(0,r.jsx)(t.code,{children:"handle"})," method should be like this one:"]}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:'export class TestHandler {\n\n  constructor() {\n  }\n\n  public static async handle(webhookEventInterface: WebhookEvent, register: Register): Promise<WebhookHandlerReturnType> {\n    if (validationFails()) {\n      throw new InvalidParameterError("Error message");\n    }\n    return Promise.resolve({\n      body: { name: "my_name" }\n    });\n  }\n}\n'})}),"\n",(0,r.jsx)(t.h2,{id:"return-type",children:"Return type"}),"\n",(0,r.jsx)(t.p,{children:"Handle methods return a promise of WebhookHandlerReturnType or void. This object contains the headers and body to be returned as response."}),"\n",(0,r.jsx)(t.p,{children:"Example:"}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"  public static async handle(webhookEventInterface: WebhookEvent, register: Register): Promise<WebhookHandlerReturnType> {\n    return Promise.resolve({\n      body: 'ok',\n      headers: {\n        Test: 'test header',\n      },\n    })\n  }\n"})}),"\n",(0,r.jsx)(t.h2,{id:"demo",children:"Demo"}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-bash",children:"curl --request POST 'http://localhost:3000/webhook/command?param1=testvalue'\n"})}),"\n",(0,r.jsx)(t.p,{children:"The webhookEventInterface object will be similar to this one:"}),"\n",(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-typescript",children:"{\n  origin: 'test',\n  method: 'POST',\n  url: '/test?param1=testvalue',\n  originalUrl: '/webhook/test?param1=testvalue',\n  headers: {\n    accept: '*/*',\n    'cache-control': 'no-cache',\n    host: 'localhost:3000',\n    'accept-encoding': 'gzip, deflate, br',\n    connection: 'keep-alive',\n    'content-length': '0'\n  },\n  query: { param1: 'testvalue' },\n  params: {},\n  rawBody: undefined,\n  body: {}\n}\n"})})]})}function p(e={}){const{wrapper:t}={...(0,o.a)(),...e.components};return t?(0,r.jsx)(t,{...e,children:(0,r.jsx)(h,{...e})}):h(e)}},5162:(e,t,n)=>{n.d(t,{Z:()=>s});n(7294);var r=n(512);const o={tabItem:"tabItem_Ymn6"};var a=n(5893);function s(e){let{children:t,hidden:n,className:s}=e;return(0,a.jsx)("div",{role:"tabpanel",className:(0,r.Z)(o.tabItem,s),hidden:n,children:t})}},4866:(e,t,n)=>{n.d(t,{Z:()=>y});var r=n(7294),o=n(512),a=n(2466),s=n(6550),l=n(469),i=n(1980),c=n(7392),u=n(12);function d(e){return r.Children.toArray(e).filter((e=>"\n"!==e)).map((e=>{if(!e||(0,r.isValidElement)(e)&&function(e){const{props:t}=e;return!!t&&"object"==typeof t&&"value"in t}(e))return e;throw new Error(`Docusaurus error: Bad <Tabs> child <${"string"==typeof e.type?e.type:e.type.name}>: all children of the <Tabs> component should be <TabItem>, and every <TabItem> should have a unique "value" prop.`)}))?.filter(Boolean)??[]}function h(e){const{values:t,children:n}=e;return(0,r.useMemo)((()=>{const e=t??function(e){return d(e).map((e=>{let{props:{value:t,label:n,attributes:r,default:o}}=e;return{value:t,label:n,attributes:r,default:o}}))}(n);return function(e){const t=(0,c.l)(e,((e,t)=>e.value===t.value));if(t.length>0)throw new Error(`Docusaurus error: Duplicate values "${t.map((e=>e.value)).join(", ")}" found in <Tabs>. Every value needs to be unique.`)}(e),e}),[t,n])}function p(e){let{value:t,tabValues:n}=e;return n.some((e=>e.value===t))}function b(e){let{queryString:t=!1,groupId:n}=e;const o=(0,s.k6)(),a=function(e){let{queryString:t=!1,groupId:n}=e;if("string"==typeof t)return t;if(!1===t)return null;if(!0===t&&!n)throw new Error('Docusaurus error: The <Tabs> component groupId prop is required if queryString=true, because this value is used as the search param name. You can also provide an explicit value such as queryString="my-search-param".');return n??null}({queryString:t,groupId:n});return[(0,i._X)(a),(0,r.useCallback)((e=>{if(!a)return;const t=new URLSearchParams(o.location.search);t.set(a,e),o.replace({...o.location,search:t.toString()})}),[a,o])]}function m(e){const{defaultValue:t,queryString:n=!1,groupId:o}=e,a=h(e),[s,i]=(0,r.useState)((()=>function(e){let{defaultValue:t,tabValues:n}=e;if(0===n.length)throw new Error("Docusaurus error: the <Tabs> component requires at least one <TabItem> children component");if(t){if(!p({value:t,tabValues:n}))throw new Error(`Docusaurus error: The <Tabs> has a defaultValue "${t}" but none of its children has the corresponding value. Available values are: ${n.map((e=>e.value)).join(", ")}. If you intend to show no default tab, use defaultValue={null} instead.`);return t}const r=n.find((e=>e.default))??n[0];if(!r)throw new Error("Unexpected error: 0 tabValues");return r.value}({defaultValue:t,tabValues:a}))),[c,d]=b({queryString:n,groupId:o}),[m,f]=function(e){let{groupId:t}=e;const n=function(e){return e?`docusaurus.tab.${e}`:null}(t),[o,a]=(0,u.Nk)(n);return[o,(0,r.useCallback)((e=>{n&&a.set(e)}),[n,a])]}({groupId:o}),g=(()=>{const e=c??m;return p({value:e,tabValues:a})?e:null})();(0,l.Z)((()=>{g&&i(g)}),[g]);return{selectedValue:s,selectValue:(0,r.useCallback)((e=>{if(!p({value:e,tabValues:a}))throw new Error(`Can't select invalid tab value=${e}`);i(e),d(e),f(e)}),[d,f,a]),tabValues:a}}var f=n(2389);const g={tabList:"tabList__CuJ",tabItem:"tabItem_LNqP"};var k=n(5893);function v(e){let{className:t,block:n,selectedValue:r,selectValue:s,tabValues:l}=e;const i=[],{blockElementScrollPositionUntilNextRender:c}=(0,a.o5)(),u=e=>{const t=e.currentTarget,n=i.indexOf(t),o=l[n].value;o!==r&&(c(t),s(o))},d=e=>{let t=null;switch(e.key){case"Enter":u(e);break;case"ArrowRight":{const n=i.indexOf(e.currentTarget)+1;t=i[n]??i[0];break}case"ArrowLeft":{const n=i.indexOf(e.currentTarget)-1;t=i[n]??i[i.length-1];break}}t?.focus()};return(0,k.jsx)("ul",{role:"tablist","aria-orientation":"horizontal",className:(0,o.Z)("tabs",{"tabs--block":n},t),children:l.map((e=>{let{value:t,label:n,attributes:a}=e;return(0,k.jsx)("li",{role:"tab",tabIndex:r===t?0:-1,"aria-selected":r===t,ref:e=>i.push(e),onKeyDown:d,onClick:u,...a,className:(0,o.Z)("tabs__item",g.tabItem,a?.className,{"tabs__item--active":r===t}),children:n??t},t)}))})}function x(e){let{lazy:t,children:n,selectedValue:o}=e;const a=(Array.isArray(n)?n:[n]).filter(Boolean);if(t){const e=a.find((e=>e.props.value===o));return e?(0,r.cloneElement)(e,{className:"margin-top--md"}):null}return(0,k.jsx)("div",{className:"margin-top--md",children:a.map(((e,t)=>(0,r.cloneElement)(e,{key:t,hidden:e.props.value!==o})))})}function j(e){const t=m(e);return(0,k.jsxs)("div",{className:(0,o.Z)("tabs-container",g.tabList),children:[(0,k.jsx)(v,{...e,...t}),(0,k.jsx)(x,{...e,...t})]})}function y(e){const t=(0,f.Z)();return(0,k.jsx)(j,{...e,children:d(e.children)},String(t))}},1151:(e,t,n)=>{n.d(t,{Z:()=>l,a:()=>s});var r=n(7294);const o={},a=r.createContext(o);function s(e){const t=r.useContext(a);return r.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function l(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(o):e.components||o:s(e.components),r.createElement(a.Provider,{value:t},e.children)}}}]);