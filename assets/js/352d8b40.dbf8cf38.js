"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[6502],{3905:(t,e,n)=>{n.d(e,{Zo:()=>p,kt:()=>u});var i=n(7294);function a(t,e,n){return e in t?Object.defineProperty(t,e,{value:n,enumerable:!0,configurable:!0,writable:!0}):t[e]=n,t}function o(t,e){var n=Object.keys(t);if(Object.getOwnPropertySymbols){var i=Object.getOwnPropertySymbols(t);e&&(i=i.filter((function(e){return Object.getOwnPropertyDescriptor(t,e).enumerable}))),n.push.apply(n,i)}return n}function r(t){for(var e=1;e<arguments.length;e++){var n=null!=arguments[e]?arguments[e]:{};e%2?o(Object(n),!0).forEach((function(e){a(t,e,n[e])})):Object.getOwnPropertyDescriptors?Object.defineProperties(t,Object.getOwnPropertyDescriptors(n)):o(Object(n)).forEach((function(e){Object.defineProperty(t,e,Object.getOwnPropertyDescriptor(n,e))}))}return t}function c(t,e){if(null==t)return{};var n,i,a=function(t,e){if(null==t)return{};var n,i,a={},o=Object.keys(t);for(i=0;i<o.length;i++)n=o[i],e.indexOf(n)>=0||(a[n]=t[n]);return a}(t,e);if(Object.getOwnPropertySymbols){var o=Object.getOwnPropertySymbols(t);for(i=0;i<o.length;i++)n=o[i],e.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(t,n)&&(a[n]=t[n])}return a}var s=i.createContext({}),l=function(t){var e=i.useContext(s),n=e;return t&&(n="function"==typeof t?t(e):r(r({},e),t)),n},p=function(t){var e=l(t.components);return i.createElement(s.Provider,{value:e},t.children)},d={inlineCode:"code",wrapper:function(t){var e=t.children;return i.createElement(i.Fragment,{},e)}},f=i.forwardRef((function(t,e){var n=t.components,a=t.mdxType,o=t.originalType,s=t.parentName,p=c(t,["components","mdxType","originalType","parentName"]),f=l(n),u=a,m=f["".concat(s,".").concat(u)]||f[u]||d[u]||o;return n?i.createElement(m,r(r({ref:e},p),{},{components:n})):i.createElement(m,r({ref:e},p))}));function u(t,e){var n=arguments,a=e&&e.mdxType;if("string"==typeof t||a){var o=n.length,r=new Array(o);r[0]=f;var c={};for(var s in e)hasOwnProperty.call(e,s)&&(c[s]=e[s]);c.originalType=t,c.mdxType="string"==typeof t?t:a,r[1]=c;for(var l=2;l<o;l++)r[l]=n[l];return i.createElement.apply(null,r)}return i.createElement.apply(null,n)}f.displayName="MDXCreateElement"},5163:(t,e,n)=>{n.d(e,{Z:()=>l});var i=n(7294);const a="terminalWindow_wGrl",o="terminalWindowHeader_o9Cs",r="buttons_IGLB",c="dot_fGZE",s="terminalWindowBody_tzdS";function l(t){let{children:e}=t;return i.createElement("div",{className:a},i.createElement("div",{className:o},i.createElement("div",{className:r},i.createElement("span",{className:c,style:{background:"#f25f58"}}),i.createElement("span",{className:c,style:{background:"#fbbe3c"}}),i.createElement("span",{className:c,style:{background:"#58cb42"}}))),i.createElement("div",{className:s},e))}},9741:(t,e,n)=>{n.r(e),n.d(e,{assets:()=>l,contentTitle:()=>c,default:()=>f,frontMatter:()=>r,metadata:()=>s,toc:()=>p});var i=n(7462),a=(n(7294),n(3905)),o=n(5163);const r={description:"Documentation for defining notifications in the Booster Framework using the @Notification and @partitionKey decorators."},c="Notifications",s={unversionedId:"architecture/notifications",id:"architecture/notifications",title:"Notifications",description:"Documentation for defining notifications in the Booster Framework using the @Notification and @partitionKey decorators.",source:"@site/docs/03_architecture/07_notifications.mdx",sourceDirName:"03_architecture",slug:"/architecture/notifications",permalink:"/architecture/notifications",draft:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_architecture/07_notifications.mdx",tags:[],version:"current",lastUpdatedBy:"Javier Toledo",lastUpdatedAt:1698840899,formattedLastUpdatedAt:"Nov 1, 2023",sidebarPosition:7,frontMatter:{description:"Documentation for defining notifications in the Booster Framework using the @Notification and @partitionKey decorators."},sidebar:"docs",previous:{title:"Read model",permalink:"/architecture/read-model"},next:{title:"Queries",permalink:"/architecture/queries"}},l={},p=[{value:"Declaring a notification",id:"declaring-a-notification",level:2},{value:"Separating by topic",id:"separating-by-topic",level:2},{value:"Separating by partition key",id:"separating-by-partition-key",level:2},{value:"Reacting to notifications",id:"reacting-to-notifications",level:2}],d={toc:p};function f(t){let{components:e,...n}=t;return(0,a.kt)("wrapper",(0,i.Z)({},d,n,{components:e,mdxType:"MDXLayout"}),(0,a.kt)("h1",{id:"notifications"},"Notifications"),(0,a.kt)("p",null,"Notifications are an important concept in event-driven architecture, and they play a crucial role in informing interested parties about certain events that take place within an application."),(0,a.kt)("h2",{id:"declaring-a-notification"},"Declaring a notification"),(0,a.kt)("p",null,"In Booster, notifications are defined as classes decorated with the ",(0,a.kt)("inlineCode",{parentName:"p"},"@Notification")," decorator. Here's a minimal example to illustrate this:"),(0,a.kt)(o.Z,{mdxType:"TerminalWindow"},(0,a.kt)("pre",null,(0,a.kt)("code",{parentName:"pre",className:"language-typescript",metastring:'title="src/notifications/cart-abandoned.ts"',title:'"src/notifications/cart-abandoned.ts"'},"import { Notification } from '@boostercloud/framework-core'\n\n@Notification()\nexport class CartAbandoned {}\n"))),(0,a.kt)("p",null,"As you can see, to define a notification you simply need to import the ",(0,a.kt)("inlineCode",{parentName:"p"},"@Notification")," decorator from the @boostercloud/framework-core library and use it to decorate a class. In this case, the class ",(0,a.kt)("inlineCode",{parentName:"p"},"CartAbandoned")," represents a notification that informs interested parties that a cart has been abandoned."),(0,a.kt)("h2",{id:"separating-by-topic"},"Separating by topic"),(0,a.kt)("p",null,"By default, all notifications in the application will be sent to the same topic called ",(0,a.kt)("inlineCode",{parentName:"p"},"defaultTopic"),". To configure this, you can specify a different topic name in the ",(0,a.kt)("inlineCode",{parentName:"p"},"@Notification")," decorator:"),(0,a.kt)(o.Z,{mdxType:"TerminalWindow"},(0,a.kt)("pre",null,(0,a.kt)("code",{parentName:"pre",className:"language-typescript",metastring:'title="src/notifications/cart-abandoned-topic.ts"',title:'"src/notifications/cart-abandoned-topic.ts"'},"import { Notification } from '@boostercloud/framework-core'\n\n@Notification({ topic: 'cart-abandoned' })\nexport class CartAbandoned {}\n"))),(0,a.kt)("p",null,"In this example, the ",(0,a.kt)("inlineCode",{parentName:"p"},"CartAbandoned")," notification will be sent to the ",(0,a.kt)("inlineCode",{parentName:"p"},"cart-abandoned")," topic, instead of the default topic."),(0,a.kt)("h2",{id:"separating-by-partition-key"},"Separating by partition key"),(0,a.kt)("p",null,"By default, all the notifications in the application will share a partition key called ",(0,a.kt)("inlineCode",{parentName:"p"},"default"),". This means that, by default, all the notifications in the application will be processed in order, which may not be as performant."),(0,a.kt)("p",null,"To change this, you can use the @partitionKey decorator to specify a field that will be used as a partition key for each notification:"),(0,a.kt)(o.Z,{mdxType:"TerminalWindow"},(0,a.kt)("pre",null,(0,a.kt)("code",{parentName:"pre",className:"language-typescript",metastring:'title="src/notifications/cart-abandoned-partition-key.ts"',title:'"src/notifications/cart-abandoned-partition-key.ts"'},"import { Notification, partitionKey } from '@boostercloud/framework-core'\n\n@Notification({ topic: 'cart-abandoned' })\nexport class CartAbandoned {\n  public constructor(@partitionKey readonly key: string) {}\n}\n"))),(0,a.kt)("p",null,"In this example, each ",(0,a.kt)("inlineCode",{parentName:"p"},"CartAbandoned")," notification will have its own partition key, which is specified in the constructor as the field ",(0,a.kt)("inlineCode",{parentName:"p"},"key"),", it can be called in any way you want. This will allow for parallel processing of notifications, making the system more performant."),(0,a.kt)("h2",{id:"reacting-to-notifications"},"Reacting to notifications"),(0,a.kt)("p",null,"Just like events, notifications can be handled by event handlers in order to trigger other processes. Event handlers are responsible for listening to events and notifications, and then performing specific actions in response to them."),(0,a.kt)("p",null,"In conclusion, defining notifications in the Booster Framework is a simple and straightforward process that can be done using the ",(0,a.kt)("inlineCode",{parentName:"p"},"@Notification")," and ",(0,a.kt)("inlineCode",{parentName:"p"},"@partitionKey")," decorators."))}f.isMDXComponent=!0}}]);