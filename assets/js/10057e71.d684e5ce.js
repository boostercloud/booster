"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[4274],{3897:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>c,contentTitle:()=>i,default:()=>j,frontMatter:()=>s,metadata:()=>a,toc:()=>l});var r=n(5893),d=n(1151),o=n(5163);const s={},i="Booster CLI",a={id:"booster-cli",title:"Booster CLI",description:"Booster CLI is a command line interface that helps you to create, develop, and deploy your Booster applications. It is built with Node.js and published to NPM through the package @boostercloud/cli . You can install it using any compatible package manager. If you want to contribute to the project, you will also need to clone the GitHub repository and compile the source code.",source:"@site/docs/05_booster-cli.mdx",sourceDirName:".",slug:"/booster-cli",permalink:"/booster-cli",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/05_booster-cli.mdx",tags:[],version:"current",lastUpdatedBy:"Gonzalo Garcia Jaubert",lastUpdatedAt:1706192347,formattedLastUpdatedAt:"Jan 25, 2024",sidebarPosition:5,frontMatter:{},sidebar:"docs",previous:{title:"GraphQL API",permalink:"/graphql"},next:{title:"Going deeper with Booster",permalink:"/category/going-deeper-with-booster"}},c={},l=[{value:"Installation",id:"installation",level:2},{value:"Usage",id:"usage",level:2},{value:"Command Overview",id:"command-overview",level:2}];function h(e){const t={a:"a",admonition:"admonition",code:"code",h1:"h1",h2:"h2",p:"p",pre:"pre",table:"table",tbody:"tbody",td:"td",th:"th",thead:"thead",tr:"tr",...(0,d.a)(),...e.components};return(0,r.jsxs)(r.Fragment,{children:[(0,r.jsx)(t.h1,{id:"booster-cli",children:"Booster CLI"}),"\n",(0,r.jsxs)(t.p,{children:["Booster CLI is a command line interface that helps you to create, develop, and deploy your Booster applications. It is built with Node.js and published to NPM through the package ",(0,r.jsx)(t.code,{children:"@boostercloud/cli"})," . You can install it using any compatible package manager. If you want to contribute to the project, you will also need to clone the GitHub repository and compile the source code."]}),"\n",(0,r.jsx)(t.h2,{id:"installation",children:"Installation"}),"\n",(0,r.jsxs)(t.p,{children:["The preferred way to install the Booster CLI is through NPM. You can install it following the instructions in the ",(0,r.jsx)(t.a,{href:"https://nodejs.org/en/download/",children:"Node.js website"}),"."]}),"\n",(0,r.jsx)(t.p,{children:"Once you have NPM installed, you can install the Booster CLI by running this command:"}),"\n",(0,r.jsx)(o.Z,{children:(0,r.jsx)(t.pre,{children:(0,r.jsx)(t.code,{className:"language-bash",children:"npm install -g @boostercloud/cli\n"})})}),"\n",(0,r.jsx)(t.h2,{id:"usage",children:"Usage"}),"\n",(0,r.jsxs)(t.p,{children:["Once the installation is finished, you will have the ",(0,r.jsx)(t.code,{children:"boost"})," command available in your terminal. You can run it to see the help message."]}),"\n",(0,r.jsx)(t.admonition,{type:"tip",children:(0,r.jsxs)(t.p,{children:["You can also run ",(0,r.jsx)(t.code,{children:"boost --help"})," to get the same output."]})}),"\n",(0,r.jsx)(t.h2,{id:"command-overview",children:"Command Overview"}),"\n",(0,r.jsxs)(t.table,{children:[(0,r.jsx)(t.thead,{children:(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.th,{children:"Command"}),(0,r.jsx)(t.th,{children:"Description"})]})}),(0,r.jsxs)(t.tbody,{children:[(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{children:(0,r.jsx)(t.a,{href:"#new",children:(0,r.jsx)(t.code,{children:"new:project"})})}),(0,r.jsx)(t.td,{children:"Creates a new Booster project in a new directory"})]}),(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{children:(0,r.jsx)(t.a,{href:"/architecture/command#creating-a-command",children:(0,r.jsx)(t.code,{children:"new:command"})})}),(0,r.jsx)(t.td,{children:"Creates a new command in the project"})]}),(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{children:(0,r.jsx)(t.a,{href:"/architecture/entity#creating-an-entity",children:(0,r.jsx)(t.code,{children:"new:entity"})})}),(0,r.jsx)(t.td,{children:"Creates a new entity in the project"})]}),(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{children:(0,r.jsx)(t.a,{href:"/architecture/event#creating-an-event",children:(0,r.jsx)(t.code,{children:"new:event"})})}),(0,r.jsx)(t.td,{children:"Creates a new event in the project"})]}),(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{children:(0,r.jsx)(t.a,{href:"/architecture/event-handler#creating-an-event-handler",children:(0,r.jsx)(t.code,{children:"new:event-handler"})})}),(0,r.jsx)(t.td,{children:"Creates a new event handler in the project"})]}),(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{children:(0,r.jsx)(t.a,{href:"/architecture/read-model#creating-a-read-model",children:(0,r.jsx)(t.code,{children:"new:read-model"})})}),(0,r.jsx)(t.td,{children:"Creates a new read model in the project"})]}),(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{children:(0,r.jsx)(t.a,{href:"/features/schedule-actions#creating-a-scheduled-command",children:(0,r.jsx)(t.code,{children:"new:scheduled-command"})})}),(0,r.jsx)(t.td,{children:"Creates a new scheduled command in the project"})]}),(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{}),(0,r.jsx)(t.td,{})]}),(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{children:(0,r.jsx)(t.a,{href:"/getting-started/coding#6-deployment",children:(0,r.jsx)(t.code,{children:"start -e <environment>"})})}),(0,r.jsx)(t.td,{children:"Starts the project in development mode"})]}),(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{children:(0,r.jsx)(t.a,{href:"/getting-started/coding#6-deployment",children:(0,r.jsx)(t.code,{children:"build"})})}),(0,r.jsx)(t.td,{children:"Builds the project"})]}),(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{children:(0,r.jsx)(t.a,{href:"/getting-started/coding#6-deployment",children:(0,r.jsx)(t.code,{children:"deploy -e <environment>"})})}),(0,r.jsx)(t.td,{children:"Deploys the project to the cloud"})]}),(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{children:(0,r.jsx)(t.code,{children:"nuke"})}),(0,r.jsx)(t.td,{children:"Deletes all the resources created by the deploy command"})]}),(0,r.jsxs)(t.tr,{children:[(0,r.jsx)(t.td,{}),(0,r.jsx)(t.td,{})]})]})]})]})}function j(e={}){const{wrapper:t}={...(0,d.a)(),...e.components};return t?(0,r.jsx)(t,{...e,children:(0,r.jsx)(h,{...e})}):h(e)}},5163:(e,t,n)=>{n.d(t,{Z:()=>o});n(7294);const r={terminalWindow:"terminalWindow_wGrl",terminalWindowHeader:"terminalWindowHeader_o9Cs",row:"row_Rn7G",buttons:"buttons_IGLB",right:"right_fWp9",terminalWindowAddressBar:"terminalWindowAddressBar_X8fO",dot:"dot_fGZE",terminalWindowMenuIcon:"terminalWindowMenuIcon_rtOE",bar:"bar_Ck8N",terminalWindowBody:"terminalWindowBody_tzdS"};var d=n(5893);function o(e){let{children:t}=e;return(0,d.jsxs)("div",{className:r.terminalWindow,children:[(0,d.jsx)("div",{className:r.terminalWindowHeader,children:(0,d.jsxs)("div",{className:r.buttons,children:[(0,d.jsx)("span",{className:r.dot,style:{background:"#f25f58"}}),(0,d.jsx)("span",{className:r.dot,style:{background:"#fbbe3c"}}),(0,d.jsx)("span",{className:r.dot,style:{background:"#58cb42"}})]})}),(0,d.jsx)("div",{className:r.terminalWindowBody,children:t})]})}},1151:(e,t,n)=>{n.d(t,{Z:()=>i,a:()=>s});var r=n(7294);const d={},o=r.createContext(d);function s(e){const t=r.useContext(o);return r.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function i(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(d):e.components||d:s(e.components),r.createElement(o.Provider,{value:t},e.children)}}}]);