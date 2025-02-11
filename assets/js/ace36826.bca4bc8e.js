"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[7348],{5007:(e,t,r)=>{r.r(t),r.d(t,{assets:()=>d,contentTitle:()=>c,default:()=>m,frontMatter:()=>o,metadata:()=>a,toc:()=>l});var n=r(5893),i=r(1151),s=r(2991);const o={},c="Booster architecture",a={id:"architecture/event-driven",title:"Booster architecture",description:"Booster is a highly opinionated framework that provides a complete toolset to build production-ready event-driven serverless applications.",source:"@site/docs/03_architecture/01_event-driven.mdx",sourceDirName:"03_architecture",slug:"/architecture/event-driven",permalink:"/architecture/event-driven",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_architecture/01_event-driven.mdx",tags:[],version:"current",lastUpdatedBy:"Mario Castro Squella",lastUpdatedAt:1739295071,formattedLastUpdatedAt:"Feb 11, 2025",sidebarPosition:1,frontMatter:{},sidebar:"docs",previous:{title:"Build a Booster app in minutes",permalink:"/getting-started/coding"},next:{title:"Command",permalink:"/architecture/command"}},d={},l=[];function h(e){const t={a:"a",code:"code",em:"em",h1:"h1",img:"img",p:"p",strong:"strong",...(0,i.a)(),...e.components};return(0,n.jsxs)(n.Fragment,{children:[(0,n.jsx)(t.h1,{id:"booster-architecture",children:"Booster architecture"}),"\n",(0,n.jsx)(t.p,{children:"Booster is a highly opinionated framework that provides a complete toolset to build production-ready event-driven serverless applications."}),"\n",(0,n.jsxs)(t.p,{children:["Two patterns influence the Booster's event-driven architecture: Command-Query Responsibility Segregation (",(0,n.jsx)(t.a,{href:"https://www.martinfowler.com/bliki/CQRS.html",children:"CQRS"}),") and ",(0,n.jsx)(t.a,{href:"https://martinfowler.com/eaaDev/EventSourcing.html",children:"Event Sourcing"}),". They're complex techniques to implement from scratch with lower-level frameworks, but Booster makes them feel natural and very easy to use."]}),"\n",(0,n.jsx)(t.p,{children:(0,n.jsx)(t.img,{alt:"architecture",src:r(8437).Z+"",width:"1272",height:"715"})}),"\n",(0,n.jsxs)(t.p,{children:["As you can see in the diagram, Booster applications consist of four main building blocks: ",(0,n.jsx)(t.code,{children:"Commands"}),", ",(0,n.jsx)(t.code,{children:"Events"}),", ",(0,n.jsx)(t.code,{children:"Entities"}),", and ",(0,n.jsx)(t.code,{children:"Read Models"}),". ",(0,n.jsx)(t.code,{children:"Commands"})," and ",(0,n.jsx)(t.code,{children:"Read Models"})," are the public interface of the application, while ",(0,n.jsx)(t.code,{children:"Events"})," and ",(0,n.jsx)(t.code,{children:"Entities"})," are private implementation details. With Booster, clients submit ",(0,n.jsx)(t.code,{children:"Commands"}),", query the ",(0,n.jsx)(t.code,{children:"Read Models"}),", or subscribe to them for receiving real-time updates thanks to the out of the box ",(0,n.jsx)(t.a,{href:"/graphql",children:"GraphQL API"})]}),"\n",(0,n.jsxs)(t.p,{children:["Booster applications are event-driven and event-sourced so, ",(0,n.jsx)(t.strong,{children:"the source of truth is the whole history of events"}),". When a client submits a command, Booster ",(0,n.jsx)(t.em,{children:"wakes up"})," and handles it throght ",(0,n.jsx)(t.code,{children:"Command Handlers"}),". As part of the process, some ",(0,n.jsx)(t.code,{children:"Events"})," may be ",(0,n.jsx)(t.em,{children:"registered"})," as needed."]}),"\n",(0,n.jsxs)(t.p,{children:["On the other side, the framework caches the current state by automatically ",(0,n.jsx)(t.em,{children:"reducing"})," all the registered events into ",(0,n.jsx)(t.code,{children:"Entities"}),". You can also ",(0,n.jsx)(t.em,{children:"react"})," to events via ",(0,n.jsx)(t.code,{children:"Event Handlers"}),", triggering side effect actions to certain events. Finally, ",(0,n.jsx)(t.code,{children:"Entities"})," are not directly exposed, they are transformed or ",(0,n.jsx)(t.em,{children:"projected"})," into ",(0,n.jsx)(t.code,{children:"ReadModels"}),", which are exposed to the public."]}),"\n",(0,n.jsx)(t.p,{children:"In this chapter you'll walk through these concepts in detail."}),"\n",(0,n.jsx)(s.Z,{})]})}function m(e={}){const{wrapper:t}={...(0,i.a)(),...e.components};return t?(0,n.jsx)(t,{...e,children:(0,n.jsx)(h,{...e})}):h(e)}},2991:(e,t,r)=>{r.d(t,{Z:()=>j});r(7294);var n=r(512),i=r(3438),s=r(3692),o=r(3919),c=r(5999),a=r(2503);const d={cardContainer:"cardContainer_fWXF",cardTitle:"cardTitle_rnsV",cardDescription:"cardDescription_PWke"};var l=r(5893);function h(e){let{href:t,children:r}=e;return(0,l.jsx)(s.Z,{href:t,className:(0,n.Z)("card padding--lg",d.cardContainer),children:r})}function m(e){let{href:t,icon:r,title:i,description:s}=e;return(0,l.jsxs)(h,{href:t,children:[(0,l.jsxs)(a.Z,{as:"h2",className:(0,n.Z)("text--truncate",d.cardTitle),title:i,children:[r," ",i]}),s&&(0,l.jsx)("p",{className:(0,n.Z)("text--truncate",d.cardDescription),title:s,children:s})]})}function u(e){let{item:t}=e;const r=(0,i.LM)(t);return r?(0,l.jsx)(m,{href:r,icon:"\ud83d\uddc3\ufe0f",title:t.label,description:t.description??(0,c.I)({message:"{count} items",id:"theme.docs.DocCard.categoryDescription",description:"The default description for a category card in the generated index about how many items this category includes"},{count:t.items.length})}):null}function p(e){let{item:t}=e;const r=(0,o.Z)(t.href)?"\ud83d\udcc4\ufe0f":"\ud83d\udd17",n=(0,i.xz)(t.docId??void 0);return(0,l.jsx)(m,{href:t.href,icon:r,title:t.label,description:t.description??n?.description})}function x(e){let{item:t}=e;switch(t.type){case"link":return(0,l.jsx)(p,{item:t});case"category":return(0,l.jsx)(u,{item:t});default:throw new Error(`unknown item type ${JSON.stringify(t)}`)}}function f(e){let{className:t}=e;const r=(0,i.jA)();return(0,l.jsx)(j,{items:r.items,className:t})}function j(e){const{items:t,className:r}=e;if(!t)return(0,l.jsx)(f,{...e});const s=(0,i.MN)(t);return(0,l.jsx)("section",{className:(0,n.Z)("row",r),children:s.map(((e,t)=>(0,l.jsx)("article",{className:"col col--6 margin-bottom--lg",children:(0,l.jsx)(x,{item:e})},t)))})}},8437:(e,t,r)=>{r.d(t,{Z:()=>n});const n=r.p+"assets/images/booster-arch-6688b589969d52c4534238dab61b2ccc.jpg"},1151:(e,t,r)=>{r.d(t,{Z:()=>c,a:()=>o});var n=r(7294);const i={},s=n.createContext(i);function o(e){const t=n.useContext(s);return n.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function c(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(i):e.components||i:o(e.components),n.createElement(s.Provider,{value:t},e.children)}}}]);