"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[3881],{4323:(e,s,n)=>{n.r(s),n.d(s,{assets:()=>d,contentTitle:()=>r,default:()=>h,frontMatter:()=>i,metadata:()=>c,toc:()=>l});var o=n(5893),t=n(1151),a=n(5163);const i={description:"Booster way to schedule an action to be performed at a specific moment in time"},r="Schedule actions",c={id:"features/schedule-actions",title:"Schedule actions",description:"Booster way to schedule an action to be performed at a specific moment in time",source:"@site/docs/03_features/02_schedule-actions.mdx",sourceDirName:"03_features",slug:"/features/schedule-actions",permalink:"/features/schedule-actions",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_features/02_schedule-actions.mdx",tags:[],version:"current",lastUpdatedBy:"Jorge Rodr\xedguez",lastUpdatedAt:1708603384,formattedLastUpdatedAt:"Feb 22, 2024",sidebarPosition:2,frontMatter:{description:"Booster way to schedule an action to be performed at a specific moment in time"},sidebar:"docs",previous:{title:"The event stream",permalink:"/features/event-stream"},next:{title:"Logging in Booster",permalink:"/features/logging"}},d={},l=[{value:"Scheduled command",id:"scheduled-command",level:2},{value:"Creating a scheduled command",id:"creating-a-scheduled-command",level:2}];function m(e){const s={code:"code",h1:"h1",h2:"h2",li:"li",p:"p",pre:"pre",strong:"strong",ul:"ul",...(0,t.a)(),...e.components};return(0,o.jsxs)(o.Fragment,{children:[(0,o.jsx)(s.h1,{id:"schedule-actions",children:"Schedule actions"}),"\n",(0,o.jsx)(s.p,{children:"There are many cases in which you want to trigger some action periodically. For example, you may want to send a reminder email to a user every day at 10:00 AM. For this, you can use scheduled commands."}),"\n",(0,o.jsx)(s.h2,{id:"scheduled-command",children:"Scheduled command"}),"\n",(0,o.jsxs)(s.p,{children:["Commands represent an action. Therefore, the way to trigger an action periodically is by scheduling a command. Scheduled commands are the way to add automated tasks to your application, like cron jobs in other frameworks. Booster scheduled commands are TypeScript classes decorated with ",(0,o.jsx)(s.code,{children:"@ScheduledCommand"}),". Unlike conventional commands, ",(0,o.jsx)(s.strong,{children:"the handle function doesn't have any parameters"}),"."]}),"\n",(0,o.jsx)(s.p,{children:"In Booster, a scheduled command looks like this:"}),"\n",(0,o.jsx)(s.pre,{children:(0,o.jsx)(s.code,{className:"language-typescript",children:"@ScheduledCommand({\n  minute: '0/5', // runs every 5 minutes\n})\nexport class CheckCartCount {\n  public static async handle(): Promise<void> {\n    /* YOUR CODE HERE */\n  }\n}\n"})}),"\n",(0,o.jsxs)(s.p,{children:["You can pass the following parameters to the ",(0,o.jsx)(s.code,{children:"@ScheduledCommand"})," decorator:"]}),"\n",(0,o.jsxs)(s.ul,{children:["\n",(0,o.jsxs)(s.li,{children:[(0,o.jsx)(s.code,{children:"minute"}),": A cron expression to specify the minute(s) in which the command will be triggered. For example, ",(0,o.jsx)(s.code,{children:"0/5"}),' means "every 5 minutes". You can also use a comma-separated list of values, like ',(0,o.jsx)(s.code,{children:"1,5,10,15,20,25,30,35,40,45,50,55"})," to specify a list of minutes."]}),"\n",(0,o.jsxs)(s.li,{children:[(0,o.jsx)(s.code,{children:"hour"}),": A cron expression to specify the hour(s) in which the command will be triggered. For example, ",(0,o.jsx)(s.code,{children:"0/2"}),' means "every 2 hours". You can also use a comma-separated list of values, like ',(0,o.jsx)(s.code,{children:"1,3,5,7,9,11,13,15,17,19,21,23"})," to specify a list of hours."]}),"\n",(0,o.jsxs)(s.li,{children:[(0,o.jsx)(s.code,{children:"day"}),": A cron expression to specify the day(s) in which the command will be triggered. For example, ",(0,o.jsx)(s.code,{children:"1/2"}),' means "every 2 days". You can also use a comma-separated list of values, like ',(0,o.jsx)(s.code,{children:"1,3,5,7,9,11,13,15,17,19,21,23,25,27,29,31"})," to specify a list of days."]}),"\n",(0,o.jsxs)(s.li,{children:[(0,o.jsx)(s.code,{children:"month"}),": A cron expression to specify the month(s) in which the command will be triggered. For example, ",(0,o.jsx)(s.code,{children:"1/2"}),' means "every 2 months". You can also use a comma-separated list of values, like ',(0,o.jsx)(s.code,{children:"1,3,5,7,9,11"})," to specify a list of months."]}),"\n",(0,o.jsxs)(s.li,{children:[(0,o.jsx)(s.code,{children:"weekDay"}),": A cron expression to specify the day(s) of the week in which the command will be triggered. For example, ",(0,o.jsx)(s.code,{children:"1/2"}),' means "every 2 days of the week". You can also use a comma-separated list of values, like ',(0,o.jsx)(s.code,{children:"1,3,5"})," to specify a list of days of the week."]}),"\n",(0,o.jsxs)(s.li,{children:[(0,o.jsx)(s.code,{children:"year"}),": A cron expression to specify the year(s) in which the command will be triggered. For example, ",(0,o.jsx)(s.code,{children:"2020/2"}),' means "every 2 years". You can also use a comma-separated list of values, like ',(0,o.jsx)(s.code,{children:"2020,2022,2024,2026,2028,2030"})," to specify a list of years."]}),"\n"]}),"\n",(0,o.jsx)(s.p,{children:"By default, if no paramaters are passed, the scheduled command will not be triggered."}),"\n",(0,o.jsx)(s.h2,{id:"creating-a-scheduled-command",children:"Creating a scheduled command"}),"\n",(0,o.jsx)(s.p,{children:"The preferred way to create a scheduled command is by using the generator, e.g."}),"\n",(0,o.jsx)(a.Z,{children:(0,o.jsx)(s.pre,{children:(0,o.jsx)(s.code,{className:"language-shell",children:"boost new:scheduled-command CheckCartCount\n"})})})]})}function h(e={}){const{wrapper:s}={...(0,t.a)(),...e.components};return s?(0,o.jsx)(s,{...e,children:(0,o.jsx)(m,{...e})}):m(e)}},5163:(e,s,n)=>{n.d(s,{Z:()=>a});n(7294);const o={terminalWindow:"terminalWindow_wGrl",terminalWindowHeader:"terminalWindowHeader_o9Cs",row:"row_Rn7G",buttons:"buttons_IGLB",right:"right_fWp9",terminalWindowAddressBar:"terminalWindowAddressBar_X8fO",dot:"dot_fGZE",terminalWindowMenuIcon:"terminalWindowMenuIcon_rtOE",bar:"bar_Ck8N",terminalWindowBody:"terminalWindowBody_tzdS"};var t=n(5893);function a(e){let{children:s}=e;return(0,t.jsxs)("div",{className:o.terminalWindow,children:[(0,t.jsx)("div",{className:o.terminalWindowHeader,children:(0,t.jsxs)("div",{className:o.buttons,children:[(0,t.jsx)("span",{className:o.dot,style:{background:"#f25f58"}}),(0,t.jsx)("span",{className:o.dot,style:{background:"#fbbe3c"}}),(0,t.jsx)("span",{className:o.dot,style:{background:"#58cb42"}})]})}),(0,t.jsx)("div",{className:o.terminalWindowBody,children:s})]})}},1151:(e,s,n)=>{n.d(s,{Z:()=>r,a:()=>i});var o=n(7294);const t={},a=o.createContext(t);function i(e){const s=o.useContext(a);return o.useMemo((function(){return"function"==typeof e?e(s):{...s,...e}}),[s,e])}function r(e){let s;return s=e.disableParentContext?"function"==typeof e.components?e.components(t):e.components||t:i(e.components),o.createElement(a.Provider,{value:s},e.children)}}}]);