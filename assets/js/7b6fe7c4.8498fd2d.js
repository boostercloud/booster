"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[7290],{4759:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>d,contentTitle:()=>o,default:()=>m,frontMatter:()=>c,metadata:()=>a,toc:()=>u});var r=n(5893),i=n(1151),s=n(2991);const c={},o="Security",a={id:"security/security",title:"Security",description:"Booster accepts standard JWT tokens to authenticate incoming requests.",source:"@site/docs/04_security/00_security.mdx",sourceDirName:"04_security",slug:"/security/security",permalink:"/security/security",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/04_security/00_security.mdx",tags:[],version:"current",lastUpdatedBy:"gonzalojaubert",lastUpdatedAt:1717081592,formattedLastUpdatedAt:"May 30, 2024",sidebarPosition:0,frontMatter:{},sidebar:"docs",previous:{title:"Error handling",permalink:"/features/error-handling"},next:{title:"Authentication",permalink:"/security/authentication"}},d={},u=[];function l(e){const t={a:"a",h1:"h1",p:"p",...(0,i.a)(),...e.components};return(0,r.jsxs)(r.Fragment,{children:[(0,r.jsx)(t.h1,{id:"security",children:"Security"}),"\n",(0,r.jsxs)(t.p,{children:["Booster accepts standard ",(0,r.jsx)(t.a,{href:"https://jwt.io/",children:"JWT tokens"})," to authenticate incoming requests.\nLikewise, you can use the claims included in these tokens to authorize access to commands\nor read models by using the provided simple role-based authorization or writing your own\nauthorizer functions."]}),"\n",(0,r.jsx)(s.Z,{})]})}function m(e={}){const{wrapper:t}={...(0,i.a)(),...e.components};return t?(0,r.jsx)(t,{...e,children:(0,r.jsx)(l,{...e})}):l(e)}},2991:(e,t,n)=>{n.d(t,{Z:()=>x});n(7294);var r=n(512),i=n(3438),s=n(3692),c=n(3919),o=n(5999),a=n(2503);const d={cardContainer:"cardContainer_fWXF",cardTitle:"cardTitle_rnsV",cardDescription:"cardDescription_PWke"};var u=n(5893);function l(e){let{href:t,children:n}=e;return(0,u.jsx)(s.Z,{href:t,className:(0,r.Z)("card padding--lg",d.cardContainer),children:n})}function m(e){let{href:t,icon:n,title:i,description:s}=e;return(0,u.jsxs)(l,{href:t,children:[(0,u.jsxs)(a.Z,{as:"h2",className:(0,r.Z)("text--truncate",d.cardTitle),title:i,children:[n," ",i]}),s&&(0,u.jsx)("p",{className:(0,r.Z)("text--truncate",d.cardDescription),title:s,children:s})]})}function h(e){let{item:t}=e;const n=(0,i.LM)(t);return n?(0,u.jsx)(m,{href:n,icon:"\ud83d\uddc3\ufe0f",title:t.label,description:t.description??(0,o.I)({message:"{count} items",id:"theme.docs.DocCard.categoryDescription",description:"The default description for a category card in the generated index about how many items this category includes"},{count:t.items.length})}):null}function p(e){let{item:t}=e;const n=(0,c.Z)(t.href)?"\ud83d\udcc4\ufe0f":"\ud83d\udd17",r=(0,i.xz)(t.docId??void 0);return(0,u.jsx)(m,{href:t.href,icon:n,title:t.label,description:t.description??r?.description})}function f(e){let{item:t}=e;switch(t.type){case"link":return(0,u.jsx)(p,{item:t});case"category":return(0,u.jsx)(h,{item:t});default:throw new Error(`unknown item type ${JSON.stringify(t)}`)}}function y(e){let{className:t}=e;const n=(0,i.jA)();return(0,u.jsx)(x,{items:n.items,className:t})}function x(e){const{items:t,className:n}=e;if(!t)return(0,u.jsx)(y,{...e});const s=(0,i.MN)(t);return(0,u.jsx)("section",{className:(0,r.Z)("row",n),children:s.map(((e,t)=>(0,u.jsx)("article",{className:"col col--6 margin-bottom--lg",children:(0,u.jsx)(f,{item:e})},t)))})}},1151:(e,t,n)=>{n.d(t,{Z:()=>o,a:()=>c});var r=n(7294);const i={},s=r.createContext(i);function c(e){const t=r.useContext(s);return r.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function o(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(i):e.components||i:c(e.components),r.createElement(s.Provider,{value:t},e.children)}}}]);