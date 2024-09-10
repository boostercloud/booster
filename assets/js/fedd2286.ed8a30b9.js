"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[2289],{642:(e,n,t)=>{t.r(n),t.d(n,{assets:()=>h,contentTitle:()=>d,default:()=>g,frontMatter:()=>c,metadata:()=>u,toc:()=>m});var r=t(5893),l=t(1151),s=t(5163),a=t(2735),i=t(5162),o=t(4866);const c={description:"How to install Booster locally, and start a Booster project in no time."},d="Installation",u={id:"getting-started/installation",title:"Installation",description:"How to install Booster locally, and start a Booster project in no time.",source:"@site/docs/02_getting-started/installation.mdx",sourceDirName:"02_getting-started",slug:"/getting-started/installation",permalink:"/getting-started/installation",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/02_getting-started/installation.mdx",tags:[],version:"current",lastUpdatedBy:"Mario Castro Squella",lastUpdatedAt:1725973309,formattedLastUpdatedAt:"Sep 10, 2024",frontMatter:{description:"How to install Booster locally, and start a Booster project in no time."},sidebar:"docs",previous:{title:"Getting Started",permalink:"/category/getting-started"},next:{title:"Build a Booster app in minutes",permalink:"/getting-started/coding"}},h={},m=[{value:"Booster Prerequisites",id:"booster-prerequisites",level:2},{value:"Install Node.js",id:"install-nodejs",level:3},{value:"Install Git",id:"install-git",level:3},{value:"Git configuration variables",id:"git-configuration-variables",level:4},{value:"Installing the Booster CLI",id:"installing-the-booster-cli",level:2}];function p(e){const n={a:"a",blockquote:"blockquote",code:"code",h1:"h1",h2:"h2",h3:"h3",h4:"h4",li:"li",p:"p",pre:"pre",ul:"ul",...(0,l.a)(),...e.components};return(0,r.jsxs)(r.Fragment,{children:[(0,r.jsx)(n.h1,{id:"installation",children:"Installation"}),"\n",(0,r.jsx)(n.p,{children:"You can develop with Booster using any of the following operating systems:"}),"\n",(0,r.jsxs)(n.ul,{children:["\n",(0,r.jsx)(n.li,{children:"Linux"}),"\n",(0,r.jsx)(n.li,{children:"macOS"}),"\n",(0,r.jsx)(n.li,{children:"Windows (Native and WSL)"}),"\n"]}),"\n",(0,r.jsx)(n.h2,{id:"booster-prerequisites",children:"Booster Prerequisites"}),"\n",(0,r.jsx)(n.h3,{id:"install-nodejs",children:"Install Node.js"}),"\n",(0,r.jsxs)(n.p,{children:["The minimal required Node.js version is ",(0,r.jsx)(n.code,{children:"v14.14"}),". Download the installer\n",(0,r.jsx)(n.a,{href:"https://nodejs.org/en/",children:"from nodejs website"}),", or install it using your system's package\nmanager."]}),"\n",(0,r.jsxs)(o.Z,{groupId:"os",children:[(0,r.jsxs)(i.Z,{value:"windows",label:"Windows",children:[(0,r.jsxs)(n.p,{children:["Using ",(0,r.jsx)(n.a,{href:"https://chocolatey.org/",children:"Chocolatey"})," package manager, run the following command in your PowerShell"]}),(0,r.jsx)(s.Z,{children:(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"choco install nodejs\n"})})})]}),(0,r.jsxs)(i.Z,{value:"macos",label:"macOS",children:[(0,r.jsxs)(n.p,{children:["Using ",(0,r.jsx)(n.a,{href:"https://brew.sh",children:"Homebrew"})," package manager, run the following command on the terminal"]}),(0,r.jsx)(s.Z,{children:(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"brew install node\n"})})})]}),(0,r.jsxs)(i.Z,{value:"ubuntu",label:"Ubuntu",children:[(0,r.jsx)(n.p,{children:"Just run the following commands on the terminal:"}),(0,r.jsx)(s.Z,{children:(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"curl -sL https://deb.nodesource.com/setup_14.x | sudo -E bash -\nsudo apt install nodejs\n"})})})]})]}),"\n",(0,r.jsx)(n.p,{children:"Verify that it was installed properly by checking so from your terminal:"}),"\n",(0,r.jsxs)(s.Z,{children:[(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"node -v\n"})}),(0,r.jsxs)(n.blockquote,{children:["\n",(0,r.jsx)(n.p,{children:"v14.14.0"}),"\n"]}),(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"npm -v\n"})}),(0,r.jsxs)(n.blockquote,{children:["\n",(0,r.jsx)(n.p,{children:"7.0.0"}),"\n"]})]}),"\n",(0,r.jsxs)(n.p,{children:["As soon as you have a Node.js version higher than ",(0,r.jsx)(n.code,{children:"v14.14"}),", and an ",(0,r.jsx)(n.code,{children:"npm"})," version higher than\n",(0,r.jsx)(n.code,{children:"7"}),", you are good to go. Just note that ",(0,r.jsx)(n.code,{children:"npm"})," comes with node, you don't have to install\nit apart."]}),"\n",(0,r.jsx)(n.p,{children:"Alternatively, we recommend you to use a version manager for dealing with different Node.js\nversions:"}),"\n",(0,r.jsxs)(n.ul,{children:["\n",(0,r.jsxs)(n.li,{children:[(0,r.jsx)(n.a,{href:"https://github.com/nvm-sh/nvm",children:(0,r.jsx)(n.code,{children:"nvm"})})," - Works with macOS, Linux, and Windows Subsystem\nfor Linux"]}),"\n",(0,r.jsxs)(n.li,{children:[(0,r.jsx)(n.a,{href:"https://github.com/coreybutler/nvm-windows",children:(0,r.jsx)(n.code,{children:"nvm-windows"})})," - Works with native Windows"]}),"\n"]}),"\n",(0,r.jsx)(n.h3,{id:"install-git",children:"Install Git"}),"\n",(0,r.jsxs)(n.p,{children:["Booster will initialize a Git repository when you create a new project (unless you use the ",(0,r.jsx)(n.code,{children:"--skipGit"})," flag), so it is required that you have it already installed in your system."]}),"\n",(0,r.jsxs)(o.Z,{groupId:"os",children:[(0,r.jsx)(i.Z,{value:"windows",label:"Windows",children:(0,r.jsx)(s.Z,{children:(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"choco install git\n"})})})}),(0,r.jsx)(i.Z,{value:"macos",label:"macOS",default:!0,children:(0,r.jsx)(s.Z,{children:(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"brew install git\n"})})})}),(0,r.jsx)(i.Z,{value:"ubuntu",label:"Ubuntu",children:(0,r.jsx)(s.Z,{children:(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"sudo apt install git-all\n"})})})})]}),"\n",(0,r.jsx)(n.h4,{id:"git-configuration-variables",children:"Git configuration variables"}),"\n",(0,r.jsxs)(n.p,{children:["After installing git in your machine, make sure that ",(0,r.jsx)(n.code,{children:"user.name"})," and ",(0,r.jsx)(n.code,{children:"user.email"})," are properly configured.\nTake a look at the ",(0,r.jsx)(n.a,{href:"https://git-scm.com/docs/git-config",children:"Git configuration page"})," for more info."]}),"\n",(0,r.jsx)(n.p,{children:"To configure them, run in your terminal:"}),"\n",(0,r.jsx)(s.Z,{children:(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:'git config --global user.name "Your Name Here"\ngit config --global user.email "your_email@youremail.com"\n'})})}),"\n",(0,r.jsx)(n.h2,{id:"installing-the-booster-cli",children:"Installing the Booster CLI"}),"\n",(0,r.jsxs)(n.p,{children:["Booster comes with a command-line tool that helps you generating boilerplate code,\ntesting and deploying the application, and deleting all the resources in the cloud. All\nthe stable versions are published to ",(0,r.jsx)(a.Dh,{children:(0,r.jsx)(n.a,{href:"https://www.npmjs.com/package/@boostercloud/cli",children:(0,r.jsx)(n.code,{children:"npm"})})}),",\nthese versions are the recommended ones, as they are well documented, and the changes are\nstated in the release notes."]}),"\n",(0,r.jsx)(n.p,{children:"To install the Booster CLI run this:"}),"\n",(0,r.jsx)(s.Z,{children:(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"npm install --global @boostercloud/cli\n"})})}),"\n",(0,r.jsxs)(n.p,{children:["Verify the Booster CLI installation with the ",(0,r.jsx)(n.code,{children:"boost version"})," command. You should get back\nsomething like"]}),"\n",(0,r.jsxs)(s.Z,{children:[(0,r.jsx)(n.pre,{children:(0,r.jsx)(n.code,{className:"language-shell",children:"boost version\n"})}),(0,r.jsxs)(n.blockquote,{children:["\n",(0,r.jsx)(n.p,{children:"@boostercloud/cli/0.16.1 darwin-x64 node-v14.14.0"}),"\n"]})]})]})}function g(e={}){const{wrapper:n}={...(0,l.a)(),...e.components};return n?(0,r.jsx)(n,{...e,children:(0,r.jsx)(p,{...e})}):p(e)}},5162:(e,n,t)=>{t.d(n,{Z:()=>a});t(7294);var r=t(512);const l={tabItem:"tabItem_Ymn6"};var s=t(5893);function a(e){let{children:n,hidden:t,className:a}=e;return(0,s.jsx)("div",{role:"tabpanel",className:(0,r.Z)(l.tabItem,a),hidden:t,children:n})}},4866:(e,n,t)=>{t.d(n,{Z:()=>y});var r=t(7294),l=t(512),s=t(2466),a=t(6550),i=t(469),o=t(1980),c=t(7392),d=t(12);function u(e){return r.Children.toArray(e).filter((e=>"\n"!==e)).map((e=>{if(!e||(0,r.isValidElement)(e)&&function(e){const{props:n}=e;return!!n&&"object"==typeof n&&"value"in n}(e))return e;throw new Error(`Docusaurus error: Bad <Tabs> child <${"string"==typeof e.type?e.type:e.type.name}>: all children of the <Tabs> component should be <TabItem>, and every <TabItem> should have a unique "value" prop.`)}))?.filter(Boolean)??[]}function h(e){const{values:n,children:t}=e;return(0,r.useMemo)((()=>{const e=n??function(e){return u(e).map((e=>{let{props:{value:n,label:t,attributes:r,default:l}}=e;return{value:n,label:t,attributes:r,default:l}}))}(t);return function(e){const n=(0,c.l)(e,((e,n)=>e.value===n.value));if(n.length>0)throw new Error(`Docusaurus error: Duplicate values "${n.map((e=>e.value)).join(", ")}" found in <Tabs>. Every value needs to be unique.`)}(e),e}),[n,t])}function m(e){let{value:n,tabValues:t}=e;return t.some((e=>e.value===n))}function p(e){let{queryString:n=!1,groupId:t}=e;const l=(0,a.k6)(),s=function(e){let{queryString:n=!1,groupId:t}=e;if("string"==typeof n)return n;if(!1===n)return null;if(!0===n&&!t)throw new Error('Docusaurus error: The <Tabs> component groupId prop is required if queryString=true, because this value is used as the search param name. You can also provide an explicit value such as queryString="my-search-param".');return t??null}({queryString:n,groupId:t});return[(0,o._X)(s),(0,r.useCallback)((e=>{if(!s)return;const n=new URLSearchParams(l.location.search);n.set(s,e),l.replace({...l.location,search:n.toString()})}),[s,l])]}function g(e){const{defaultValue:n,queryString:t=!1,groupId:l}=e,s=h(e),[a,o]=(0,r.useState)((()=>function(e){let{defaultValue:n,tabValues:t}=e;if(0===t.length)throw new Error("Docusaurus error: the <Tabs> component requires at least one <TabItem> children component");if(n){if(!m({value:n,tabValues:t}))throw new Error(`Docusaurus error: The <Tabs> has a defaultValue "${n}" but none of its children has the corresponding value. Available values are: ${t.map((e=>e.value)).join(", ")}. If you intend to show no default tab, use defaultValue={null} instead.`);return n}const r=t.find((e=>e.default))??t[0];if(!r)throw new Error("Unexpected error: 0 tabValues");return r.value}({defaultValue:n,tabValues:s}))),[c,u]=p({queryString:t,groupId:l}),[g,x]=function(e){let{groupId:n}=e;const t=function(e){return e?`docusaurus.tab.${e}`:null}(n),[l,s]=(0,d.Nk)(t);return[l,(0,r.useCallback)((e=>{t&&s.set(e)}),[t,s])]}({groupId:l}),j=(()=>{const e=c??g;return m({value:e,tabValues:s})?e:null})();(0,i.Z)((()=>{j&&o(j)}),[j]);return{selectedValue:a,selectValue:(0,r.useCallback)((e=>{if(!m({value:e,tabValues:s}))throw new Error(`Can't select invalid tab value=${e}`);o(e),u(e),x(e)}),[u,x,s]),tabValues:s}}var x=t(2389);const j={tabList:"tabList__CuJ",tabItem:"tabItem_LNqP"};var b=t(5893);function f(e){let{className:n,block:t,selectedValue:r,selectValue:a,tabValues:i}=e;const o=[],{blockElementScrollPositionUntilNextRender:c}=(0,s.o5)(),d=e=>{const n=e.currentTarget,t=o.indexOf(n),l=i[t].value;l!==r&&(c(n),a(l))},u=e=>{let n=null;switch(e.key){case"Enter":d(e);break;case"ArrowRight":{const t=o.indexOf(e.currentTarget)+1;n=o[t]??o[0];break}case"ArrowLeft":{const t=o.indexOf(e.currentTarget)-1;n=o[t]??o[o.length-1];break}}n?.focus()};return(0,b.jsx)("ul",{role:"tablist","aria-orientation":"horizontal",className:(0,l.Z)("tabs",{"tabs--block":t},n),children:i.map((e=>{let{value:n,label:t,attributes:s}=e;return(0,b.jsx)("li",{role:"tab",tabIndex:r===n?0:-1,"aria-selected":r===n,ref:e=>o.push(e),onKeyDown:u,onClick:d,...s,className:(0,l.Z)("tabs__item",j.tabItem,s?.className,{"tabs__item--active":r===n}),children:t??n},n)}))})}function v(e){let{lazy:n,children:t,selectedValue:l}=e;const s=(Array.isArray(t)?t:[t]).filter(Boolean);if(n){const e=s.find((e=>e.props.value===l));return e?(0,r.cloneElement)(e,{className:"margin-top--md"}):null}return(0,b.jsx)("div",{className:"margin-top--md",children:s.map(((e,n)=>(0,r.cloneElement)(e,{key:n,hidden:e.props.value!==l})))})}function w(e){const n=g(e);return(0,b.jsxs)("div",{className:(0,l.Z)("tabs-container",j.tabList),children:[(0,b.jsx)(f,{...e,...n}),(0,b.jsx)(v,{...e,...n})]})}function y(e){const n=(0,x.Z)();return(0,b.jsx)(w,{...e,children:u(e.children)},String(n))}},2735:(e,n,t)=>{t.d(n,{do:()=>o,dM:()=>i,Dh:()=>c});var r=t(7294),l=t(719),s=t(5893);const a=e=>{let{href:n,onClick:t,children:r}=e;return(0,s.jsx)("a",{href:n,target:"_blank",rel:"noopener noreferrer",onClick:e=>{t&&t()},children:r})},i=e=>{let{children:n}=e;return d(n,"YY7T3ZSZ")},o=e=>{let{children:n}=e;return d(n,"NE1EADCK")},c=e=>{let{children:n}=e;return d(n,"AXTW7ICE")};function d(e,n){const{text:t,href:i}=function(e){if(r.isValidElement(e)&&e.props.href)return{text:e.props.children,href:e.props.href};return{text:"",href:""}}(e);return(0,s.jsx)(a,{href:i,onClick:()=>l.R.startAndTrackEvent(n),children:t})}},5163:(e,n,t)=>{t.d(n,{Z:()=>s});t(7294);const r={terminalWindow:"terminalWindow_wGrl",terminalWindowHeader:"terminalWindowHeader_o9Cs",row:"row_Rn7G",buttons:"buttons_IGLB",right:"right_fWp9",terminalWindowAddressBar:"terminalWindowAddressBar_X8fO",dot:"dot_fGZE",terminalWindowMenuIcon:"terminalWindowMenuIcon_rtOE",bar:"bar_Ck8N",terminalWindowBody:"terminalWindowBody_tzdS"};var l=t(5893);function s(e){let{children:n}=e;return(0,l.jsxs)("div",{className:r.terminalWindow,children:[(0,l.jsx)("div",{className:r.terminalWindowHeader,children:(0,l.jsxs)("div",{className:r.buttons,children:[(0,l.jsx)("span",{className:r.dot,style:{background:"#f25f58"}}),(0,l.jsx)("span",{className:r.dot,style:{background:"#fbbe3c"}}),(0,l.jsx)("span",{className:r.dot,style:{background:"#58cb42"}})]})}),(0,l.jsx)("div",{className:r.terminalWindowBody,children:n})]})}},1151:(e,n,t)=>{t.d(n,{Z:()=>i,a:()=>a});var r=t(7294);const l={},s=r.createContext(l);function a(e){const n=r.useContext(s);return r.useMemo((function(){return"function"==typeof e?e(n):{...n,...e}}),[n,e])}function i(e){let n;return n=e.disableParentContext?"function"==typeof e.components?e.components(l):e.components||l:a(e.components),r.createElement(s.Provider,{value:n},e.children)}}}]);