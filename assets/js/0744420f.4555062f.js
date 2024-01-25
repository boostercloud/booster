"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[4971],{9593:(e,n,t)=>{t.r(n),t.d(n,{assets:()=>a,contentTitle:()=>r,default:()=>h,frontMatter:()=>s,metadata:()=>l,toc:()=>c});var i=t(5893),o=t(1151);const s={title:"BEEP 6 - Remote Imports",authors:["NickSeagull"],date:"2024-01-26T00:00"},r=void 0,l={permalink:"/blog/0006-remote-imports",editUrl:"https://github.com/boostercloud/booster/edit/main/website/proposals/0006-remote-imports/index.mdx",source:"@site/proposals/0006-remote-imports/index.mdx",title:"BEEP 6 - Remote Imports",description:"Introduction",date:"2024-01-26T00:00:00.000Z",formattedDate:"January 26, 2024",tags:[],readingTime:2.92,hasTruncateMarker:!1,authors:[{name:"Nick Tchayka",title:"Booster Core Team",url:"https://github.com/NickSeagull",imageURL:"https://github.com/NickSeagull.png",key:"NickSeagull"}],frontMatter:{title:"BEEP 6 - Remote Imports",authors:["NickSeagull"],date:"2024-01-26T00:00"},unlisted:!1,prevItem:{title:"BEEP 5 - Agent-Based Codebase Structure",permalink:"/blog/0005-agent-codebase"}},a={authorsImageUrls:[void 0]},c=[{value:"Introduction",id:"introduction",level:2},{value:"Context and Challenges",id:"context-and-challenges",level:2},{value:"Proposed Solution",id:"proposed-solution",level:2},{value:"Introduction of <code>/inspect</code> Endpoint",id:"introduction-of-inspect-endpoint",level:3},{value:"Extension of TypeScript Compiler",id:"extension-of-typescript-compiler",level:3},{value:"Addressing Potential Concerns",id:"addressing-potential-concerns",level:2},{value:"Security and Access Control for <code>/inspect</code>",id:"security-and-access-control-for-inspect",level:3},{value:"Integration with Existing Workflow",id:"integration-with-existing-workflow",level:3},{value:"Naming Convention and Format",id:"naming-convention-and-format",level:3},{value:"Conclusion",id:"conclusion",level:2}];function d(e){const n={a:"a",admonition:"admonition",code:"code",h2:"h2",h3:"h3",li:"li",p:"p",strong:"strong",ul:"ul",...(0,o.a)(),...e.components};return(0,i.jsxs)(i.Fragment,{children:[(0,i.jsx)(n.admonition,{title:"STATUS - DRAFT",type:"tip"}),"\n",(0,i.jsx)(n.h2,{id:"introduction",children:"Introduction"}),"\n",(0,i.jsxs)(n.p,{children:["This document proposes a novel solution to address the challenges encountered by the team following the restructuring of Booster Framework codebase into ",(0,i.jsx)(n.a,{href:"/blog/0005-agent-codebase",children:"distinct agents"})," and separate repositories. The primary challenge involves enabling easy importation of types and classes from other agents without relying on traditional package managers. The proposed solution introduces a new endpoint, ",(0,i.jsx)(n.code,{children:"/inspect"}),", in Booster services, coupled with an extension to the TypeScript compiler through a specialized plugin (transformer). This approach aims to streamline development, ensure access to up-to-date type definitions, and facilitate seamless agent interaction."]}),"\n",(0,i.jsx)(n.h2,{id:"context-and-challenges",children:"Context and Challenges"}),"\n",(0,i.jsx)(n.p,{children:"After splitting the project codebase into individual agents and repositories, the team faces the challenge of efficiently importing types and classes across these divided entities. Traditional methods, such as package managers, are not optimal in this scenario due to their complexity and the additional maintenance they require. There is a need for a more agile and integrated solution that aligns with the dynamic nature of Booster Framework."}),"\n",(0,i.jsx)(n.h2,{id:"proposed-solution",children:"Proposed Solution"}),"\n",(0,i.jsxs)(n.h3,{id:"introduction-of-inspect-endpoint",children:["Introduction of ",(0,i.jsx)(n.code,{children:"/inspect"})," Endpoint"]}),"\n",(0,i.jsxs)(n.ul,{children:["\n",(0,i.jsxs)(n.li,{children:[(0,i.jsx)(n.strong,{children:"Functionality"}),": The ",(0,i.jsx)(n.code,{children:"/inspect"})," endpoint in Booster services will provide direct access to ",(0,i.jsx)(n.code,{children:".d.ts"})," files of all project components, organized by agents. This will include types for commands, events, read-models, and other relevant components."]}),"\n",(0,i.jsxs)(n.li,{children:[(0,i.jsx)(n.strong,{children:"Benefits"}),": This endpoint simplifies development by offering an organized and up-to-date source of type definitions, ensuring consistency across different agents."]}),"\n"]}),"\n",(0,i.jsx)(n.h3,{id:"extension-of-typescript-compiler",children:"Extension of TypeScript Compiler"}),"\n",(0,i.jsxs)(n.ul,{children:["\n",(0,i.jsxs)(n.li,{children:[(0,i.jsx)(n.strong,{children:"Plugin Development"}),": A custom plugin for the TypeScript compiler will be developed to recognize a special import syntax, such as ",(0,i.jsx)(n.code,{children:"import FooEvent from 'booster://agent-name/events/FooEvent'"}),"."]}),"\n",(0,i.jsxs)(n.li,{children:[(0,i.jsx)(n.strong,{children:"Dynamic Importing"}),": This plugin will dynamically fetch the corresponding ",(0,i.jsx)(n.code,{children:".d.ts"})," file from the ",(0,i.jsx)(n.code,{children:"/inspect"})," endpoint, streamlining the import process."]}),"\n",(0,i.jsxs)(n.li,{children:[(0,i.jsx)(n.strong,{children:"Advantages"}),": This system allows seamless interaction between agents, enhancing the developer experience and negating the need for traditional package management complexities."]}),"\n"]}),"\n",(0,i.jsx)(n.h2,{id:"addressing-potential-concerns",children:"Addressing Potential Concerns"}),"\n",(0,i.jsxs)(n.h3,{id:"security-and-access-control-for-inspect",children:["Security and Access Control for ",(0,i.jsx)(n.code,{children:"/inspect"})]}),"\n",(0,i.jsxs)(n.ul,{children:["\n",(0,i.jsxs)(n.li,{children:[(0,i.jsx)(n.strong,{children:"Implementation of Security Measures"}),": Appropriate security measures and access controls will be implemented to protect sensitive information within the ",(0,i.jsx)(n.code,{children:".d.ts"})," files accessible via the ",(0,i.jsx)(n.code,{children:"/inspect"})," endpoint."]}),"\n",(0,i.jsxs)(n.li,{children:[(0,i.jsx)(n.strong,{children:"Restricted Access"}),": Access to the endpoint may be restricted based on user roles or project settings, ensuring that only authorized personnel can retrieve type definitions."]}),"\n"]}),"\n",(0,i.jsxs)(n.p,{children:["As the first version of this feature, these security concerns will be mitigated through making this feature opt-in. ",(0,i.jsx)(n.strong,{children:"It would be only available for environments where the flag has been enabled."})," For example, it should be enabled in the local development, testing, and staging environments, but disabled in production."]}),"\n",(0,i.jsx)(n.h3,{id:"integration-with-existing-workflow",children:"Integration with Existing Workflow"}),"\n",(0,i.jsxs)(n.ul,{children:["\n",(0,i.jsxs)(n.li,{children:[(0,i.jsx)(n.strong,{children:"Seamless Integration"}),": The proposed solution is designed to integrate smoothly with the existing development workflow of the Booster Framework."]}),"\n",(0,i.jsxs)(n.li,{children:[(0,i.jsx)(n.strong,{children:"Addressing Challenges and Dependencies"}),": Potential challenges, such as network latency or downtime, will be addressed through robust error handling and fallback mechanisms."]}),"\n"]}),"\n",(0,i.jsx)(n.h3,{id:"naming-convention-and-format",children:"Naming Convention and Format"}),"\n",(0,i.jsxs)(n.ul,{children:["\n",(0,i.jsxs)(n.li,{children:[(0,i.jsx)(n.strong,{children:"Import Syntax Convention"}),": The ",(0,i.jsx)(n.code,{children:"booster://agent-name"})," import syntax will follow a clear and consistent naming convention, aligning with the overall structure and nomenclature of the Booster Framework."]}),"\n",(0,i.jsxs)(n.li,{children:[(0,i.jsx)(n.strong,{children:"Agent Naming Guidelines"}),": Specific guidelines and constraints for naming agents will be established to ensure clarity and avoid conflicts in the import process."]}),"\n"]}),"\n",(0,i.jsx)(n.h2,{id:"conclusion",children:"Conclusion"}),"\n",(0,i.jsxs)(n.p,{children:["The introduction of the ",(0,i.jsx)(n.code,{children:"/inspect"})," endpoint and the extension of the TypeScript compiler with a custom plugin represent a significant step forward in addressing the challenges of remote imports in Booster Framework. This solution not only simplifies the development process but also enhances the modularity and flexibility of the framework, ensuring a smooth and efficient experience for developers working across different agents and repositories."]})]})}function h(e={}){const{wrapper:n}={...(0,o.a)(),...e.components};return n?(0,i.jsx)(n,{...e,children:(0,i.jsx)(d,{...e})}):d(e)}},1151:(e,n,t)=>{t.d(n,{Z:()=>l,a:()=>r});var i=t(7294);const o={},s=i.createContext(o);function r(e){const n=i.useContext(s);return i.useMemo((function(){return"function"==typeof e?e(n):{...n,...e}}),[n,e])}function l(e){let n;return n=e.disableParentContext?"function"==typeof e.components?e.components(o):e.components||o:r(e.components),i.createElement(s.Provider,{value:n},e.children)}}}]);