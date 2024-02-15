"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[6502],{7041:(t,e,i)=>{i.r(e),i.d(e,{assets:()=>d,contentTitle:()=>c,default:()=>p,frontMatter:()=>r,metadata:()=>s,toc:()=>l});var n=i(5893),o=i(1151),a=i(5163);const r={description:"Documentation for defining notifications in the Booster Framework using the @Notification and @partitionKey decorators."},c="Notifications",s={id:"architecture/notifications",title:"Notifications",description:"Documentation for defining notifications in the Booster Framework using the @Notification and @partitionKey decorators.",source:"@site/docs/03_architecture/07_notifications.mdx",sourceDirName:"03_architecture",slug:"/architecture/notifications",permalink:"/architecture/notifications",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_architecture/07_notifications.mdx",tags:[],version:"current",lastUpdatedBy:"Gonzalo Garcia Jaubert",lastUpdatedAt:1707997370,formattedLastUpdatedAt:"Feb 15, 2024",sidebarPosition:7,frontMatter:{description:"Documentation for defining notifications in the Booster Framework using the @Notification and @partitionKey decorators."},sidebar:"docs",previous:{title:"Read model",permalink:"/architecture/read-model"},next:{title:"Queries",permalink:"/architecture/queries"}},d={},l=[{value:"Declaring a notification",id:"declaring-a-notification",level:2},{value:"Separating by topic",id:"separating-by-topic",level:2},{value:"Separating by partition key",id:"separating-by-partition-key",level:2},{value:"Reacting to notifications",id:"reacting-to-notifications",level:2}];function h(t){const e={code:"code",h1:"h1",h2:"h2",p:"p",pre:"pre",...(0,o.a)(),...t.components};return(0,n.jsxs)(n.Fragment,{children:[(0,n.jsx)(e.h1,{id:"notifications",children:"Notifications"}),"\n",(0,n.jsx)(e.p,{children:"Notifications are an important concept in event-driven architecture, and they play a crucial role in informing interested parties about certain events that take place within an application."}),"\n",(0,n.jsx)(e.h2,{id:"declaring-a-notification",children:"Declaring a notification"}),"\n",(0,n.jsxs)(e.p,{children:["In Booster, notifications are defined as classes decorated with the ",(0,n.jsx)(e.code,{children:"@Notification"})," decorator. Here's a minimal example to illustrate this:"]}),"\n",(0,n.jsx)(a.Z,{children:(0,n.jsx)(e.pre,{children:(0,n.jsx)(e.code,{className:"language-typescript",metastring:'title="src/notifications/cart-abandoned.ts"',children:"import { Notification } from '@boostercloud/framework-core'\n\n@Notification()\nexport class CartAbandoned {}\n"})})}),"\n",(0,n.jsxs)(e.p,{children:["As you can see, to define a notification you simply need to import the ",(0,n.jsx)(e.code,{children:"@Notification"})," decorator from the @boostercloud/framework-core library and use it to decorate a class. In this case, the class ",(0,n.jsx)(e.code,{children:"CartAbandoned"})," represents a notification that informs interested parties that a cart has been abandoned."]}),"\n",(0,n.jsx)(e.h2,{id:"separating-by-topic",children:"Separating by topic"}),"\n",(0,n.jsxs)(e.p,{children:["By default, all notifications in the application will be sent to the same topic called ",(0,n.jsx)(e.code,{children:"defaultTopic"}),". To configure this, you can specify a different topic name in the ",(0,n.jsx)(e.code,{children:"@Notification"})," decorator:"]}),"\n",(0,n.jsx)(a.Z,{children:(0,n.jsx)(e.pre,{children:(0,n.jsx)(e.code,{className:"language-typescript",metastring:'title="src/notifications/cart-abandoned-topic.ts"',children:"import { Notification } from '@boostercloud/framework-core'\n\n@Notification({ topic: 'cart-abandoned' })\nexport class CartAbandoned {}\n"})})}),"\n",(0,n.jsxs)(e.p,{children:["In this example, the ",(0,n.jsx)(e.code,{children:"CartAbandoned"})," notification will be sent to the ",(0,n.jsx)(e.code,{children:"cart-abandoned"})," topic, instead of the default topic."]}),"\n",(0,n.jsx)(e.h2,{id:"separating-by-partition-key",children:"Separating by partition key"}),"\n",(0,n.jsxs)(e.p,{children:["By default, all the notifications in the application will share a partition key called ",(0,n.jsx)(e.code,{children:"default"}),". This means that, by default, all the notifications in the application will be processed in order, which may not be as performant."]}),"\n",(0,n.jsx)(e.p,{children:"To change this, you can use the @partitionKey decorator to specify a field that will be used as a partition key for each notification:"}),"\n",(0,n.jsx)(a.Z,{children:(0,n.jsx)(e.pre,{children:(0,n.jsx)(e.code,{className:"language-typescript",metastring:'title="src/notifications/cart-abandoned-partition-key.ts"',children:"import { Notification, partitionKey } from '@boostercloud/framework-core'\n\n@Notification({ topic: 'cart-abandoned' })\nexport class CartAbandoned {\n  public constructor(@partitionKey readonly key: string) {}\n}\n"})})}),"\n",(0,n.jsxs)(e.p,{children:["In this example, each ",(0,n.jsx)(e.code,{children:"CartAbandoned"})," notification will have its own partition key, which is specified in the constructor as the field ",(0,n.jsx)(e.code,{children:"key"}),", it can be called in any way you want. This will allow for parallel processing of notifications, making the system more performant."]}),"\n",(0,n.jsx)(e.h2,{id:"reacting-to-notifications",children:"Reacting to notifications"}),"\n",(0,n.jsx)(e.p,{children:"Just like events, notifications can be handled by event handlers in order to trigger other processes. Event handlers are responsible for listening to events and notifications, and then performing specific actions in response to them."}),"\n",(0,n.jsxs)(e.p,{children:["In conclusion, defining notifications in the Booster Framework is a simple and straightforward process that can be done using the ",(0,n.jsx)(e.code,{children:"@Notification"})," and ",(0,n.jsx)(e.code,{children:"@partitionKey"})," decorators."]})]})}function p(t={}){const{wrapper:e}={...(0,o.a)(),...t.components};return e?(0,n.jsx)(e,{...t,children:(0,n.jsx)(h,{...t})}):h(t)}},5163:(t,e,i)=>{i.d(e,{Z:()=>a});i(7294);const n={terminalWindow:"terminalWindow_wGrl",terminalWindowHeader:"terminalWindowHeader_o9Cs",row:"row_Rn7G",buttons:"buttons_IGLB",right:"right_fWp9",terminalWindowAddressBar:"terminalWindowAddressBar_X8fO",dot:"dot_fGZE",terminalWindowMenuIcon:"terminalWindowMenuIcon_rtOE",bar:"bar_Ck8N",terminalWindowBody:"terminalWindowBody_tzdS"};var o=i(5893);function a(t){let{children:e}=t;return(0,o.jsxs)("div",{className:n.terminalWindow,children:[(0,o.jsx)("div",{className:n.terminalWindowHeader,children:(0,o.jsxs)("div",{className:n.buttons,children:[(0,o.jsx)("span",{className:n.dot,style:{background:"#f25f58"}}),(0,o.jsx)("span",{className:n.dot,style:{background:"#fbbe3c"}}),(0,o.jsx)("span",{className:n.dot,style:{background:"#58cb42"}})]})}),(0,o.jsx)("div",{className:n.terminalWindowBody,children:e})]})}},1151:(t,e,i)=>{i.d(e,{Z:()=>c,a:()=>r});var n=i(7294);const o={},a=n.createContext(o);function r(t){const e=n.useContext(a);return n.useMemo((function(){return"function"==typeof t?t(e):{...e,...t}}),[e,t])}function c(t){let e;return e=t.disableParentContext?"function"==typeof t.components?t.components(o):t.components||o:r(t.components),n.createElement(a.Provider,{value:e},t.children)}}}]);