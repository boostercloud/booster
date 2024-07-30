"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[8032],{7120:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>c,contentTitle:()=>r,default:()=>d,frontMatter:()=>i,metadata:()=>o,toc:()=>l});var s=n(5893),a=n(1151);const i={describe:"Exposing and accessing the internal stream of events generated by your application."},r="The event stream",o={id:"features/event-stream",title:"The event stream",description:"The event stream API is a read-only API that allows you to fetch the events that have been generated by your application. It's useful for debugging purposes, but also for building your own analytics tools. The access to this API is disabled by default, but you can enable it by configuring the authorizeReadEvents policy in your entities. You can also use the authorizeReadEvents policy to restrict access to the events of certain entities.",source:"@site/docs/03_features/01_event-stream.mdx",sourceDirName:"03_features",slug:"/features/event-stream",permalink:"/features/event-stream",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_features/01_event-stream.mdx",tags:[],version:"current",lastUpdatedBy:"gonzalojaubert",lastUpdatedAt:1722356865,formattedLastUpdatedAt:"Jul 30, 2024",sidebarPosition:1,frontMatter:{describe:"Exposing and accessing the internal stream of events generated by your application."},sidebar:"docs",previous:{title:"Features",permalink:"/category/features"},next:{title:"Schedule actions",permalink:"/features/schedule-actions"}},c={},l=[{value:"Accessing the event streams API",id:"accessing-the-event-streams-api",level:2}];function u(e){const t={a:"a",admonition:"admonition",code:"code",h1:"h1",h2:"h2",li:"li",p:"p",pre:"pre",ul:"ul",...(0,a.a)(),...e.components};return(0,s.jsxs)(s.Fragment,{children:[(0,s.jsx)(t.h1,{id:"the-event-stream",children:"The event stream"}),"\n",(0,s.jsxs)(t.p,{children:["The event stream API is a read-only API that allows you to fetch the events that have been generated by your application. It's useful for debugging purposes, but also for building your own analytics tools. The access to this API is disabled by default, but you can enable it by configuring the ",(0,s.jsx)(t.code,{children:"authorizeReadEvents"})," policy in your entities. You can also use the ",(0,s.jsx)(t.code,{children:"authorizeReadEvents"})," policy to restrict access to the events of certain entities."]}),"\n",(0,s.jsx)(t.h2,{id:"accessing-the-event-streams-api",children:"Accessing the event streams API"}),"\n",(0,s.jsxs)(t.p,{children:["The ",(0,s.jsx)(t.code,{children:"authorizeReadEvents"})," policy can be configured with any of the supported authorization mechanisms:"]}),"\n",(0,s.jsxs)(t.ul,{children:["\n",(0,s.jsxs)(t.li,{children:[(0,s.jsx)(t.code,{children:"'all'"})," to make them public."]}),"\n",(0,s.jsx)(t.li,{children:"an array of roles."}),"\n",(0,s.jsxs)(t.li,{children:["An ",(0,s.jsx)(t.a,{href:"/security/authorization#event-stream-authorizers",children:"authorizer function"})," that matches the ",(0,s.jsx)(t.code,{children:"EventStreamAuthorizer"})," type signature. For example"]}),"\n"]}),"\n",(0,s.jsx)(t.pre,{children:(0,s.jsx)(t.code,{className:"language-typescript",children:"@Entity({\n  authorizeReadEvents: 'all', // Anyone can read any Cart's event\n})\nexport class Cart {\n  public constructor(\n    readonly id: UUID,\n    readonly cartItems: Array<CartItem>,\n    public shippingAddress?: Address,\n    public checks = 0\n  ) {}\n  // <reducers...>\n}\n"})}),"\n",(0,s.jsx)(t.admonition,{type:"note",children:(0,s.jsxs)(t.p,{children:["Be careful when exposing events data, as this data is likely to hold internal system state. Pay special attention when authorizing public access with the ",(0,s.jsx)(t.code,{children:"'all'"})," option, it's always recommended to look for alternate solutions that limit access."]})}),"\n",(0,s.jsxs)(t.p,{children:["To read more about how to restrict the access to the event stream API, check out the ",(0,s.jsx)(t.a,{href:"/security/authorization",children:"authorization guide"}),"."]})]})}function d(e={}){const{wrapper:t}={...(0,a.a)(),...e.components};return t?(0,s.jsx)(t,{...e,children:(0,s.jsx)(u,{...e})}):u(e)}},1151:(e,t,n)=>{n.d(t,{Z:()=>o,a:()=>r});var s=n(7294);const a={},i=s.createContext(a);function r(e){const t=s.useContext(i);return s.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function o(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(a):e.components||a:r(e.components),s.createElement(i.Provider,{value:t},e.children)}}}]);