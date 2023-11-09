"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[3881],{3905:(e,t,a)=>{a.d(t,{Zo:()=>d,kt:()=>p});var n=a(7294);function r(e,t,a){return t in e?Object.defineProperty(e,t,{value:a,enumerable:!0,configurable:!0,writable:!0}):e[t]=a,e}function o(e,t){var a=Object.keys(e);if(Object.getOwnPropertySymbols){var n=Object.getOwnPropertySymbols(e);t&&(n=n.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),a.push.apply(a,n)}return a}function i(e){for(var t=1;t<arguments.length;t++){var a=null!=arguments[t]?arguments[t]:{};t%2?o(Object(a),!0).forEach((function(t){r(e,t,a[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(a)):o(Object(a)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(a,t))}))}return e}function l(e,t){if(null==e)return{};var a,n,r=function(e,t){if(null==e)return{};var a,n,r={},o=Object.keys(e);for(n=0;n<o.length;n++)a=o[n],t.indexOf(a)>=0||(r[a]=e[a]);return r}(e,t);if(Object.getOwnPropertySymbols){var o=Object.getOwnPropertySymbols(e);for(n=0;n<o.length;n++)a=o[n],t.indexOf(a)>=0||Object.prototype.propertyIsEnumerable.call(e,a)&&(r[a]=e[a])}return r}var s=n.createContext({}),c=function(e){var t=n.useContext(s),a=t;return e&&(a="function"==typeof e?e(t):i(i({},t),e)),a},d=function(e){var t=c(e.components);return n.createElement(s.Provider,{value:t},e.children)},m={inlineCode:"code",wrapper:function(e){var t=e.children;return n.createElement(n.Fragment,{},t)}},u=n.forwardRef((function(e,t){var a=e.components,r=e.mdxType,o=e.originalType,s=e.parentName,d=l(e,["components","mdxType","originalType","parentName"]),u=c(a),p=r,h=u["".concat(s,".").concat(p)]||u[p]||m[p]||o;return a?n.createElement(h,i(i({ref:t},d),{},{components:a})):n.createElement(h,i({ref:t},d))}));function p(e,t){var a=arguments,r=t&&t.mdxType;if("string"==typeof e||r){var o=a.length,i=new Array(o);i[0]=u;var l={};for(var s in t)hasOwnProperty.call(t,s)&&(l[s]=t[s]);l.originalType=e,l.mdxType="string"==typeof e?e:r,i[1]=l;for(var c=2;c<o;c++)i[c]=a[c];return n.createElement.apply(null,i)}return n.createElement.apply(null,a)}u.displayName="MDXCreateElement"},5163:(e,t,a)=>{a.d(t,{Z:()=>c});var n=a(7294);const r="terminalWindow_wGrl",o="terminalWindowHeader_o9Cs",i="buttons_IGLB",l="dot_fGZE",s="terminalWindowBody_tzdS";function c(e){let{children:t}=e;return n.createElement("div",{className:r},n.createElement("div",{className:o},n.createElement("div",{className:i},n.createElement("span",{className:l,style:{background:"#f25f58"}}),n.createElement("span",{className:l,style:{background:"#fbbe3c"}}),n.createElement("span",{className:l,style:{background:"#58cb42"}}))),n.createElement("div",{className:s},t))}},6332:(e,t,a)=>{a.r(t),a.d(t,{assets:()=>c,contentTitle:()=>l,default:()=>u,frontMatter:()=>i,metadata:()=>s,toc:()=>d});var n=a(7462),r=(a(7294),a(3905)),o=a(5163);const i={description:"Booster way to schedule an action to be performed at a specific moment in time"},l="Schedule actions",s={unversionedId:"features/schedule-actions",id:"features/schedule-actions",title:"Schedule actions",description:"Booster way to schedule an action to be performed at a specific moment in time",source:"@site/docs/03_features/02_schedule-actions.mdx",sourceDirName:"03_features",slug:"/features/schedule-actions",permalink:"/features/schedule-actions",draft:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_features/02_schedule-actions.mdx",tags:[],version:"current",lastUpdatedBy:"gonzalojaubert",lastUpdatedAt:1699531616,formattedLastUpdatedAt:"Nov 9, 2023",sidebarPosition:2,frontMatter:{description:"Booster way to schedule an action to be performed at a specific moment in time"},sidebar:"docs",previous:{title:"The event stream",permalink:"/features/event-stream"},next:{title:"Logging in Booster",permalink:"/features/logging"}},c={},d=[{value:"Scheduled command",id:"scheduled-command",level:2},{value:"Creating a scheduled command",id:"creating-a-scheduled-command",level:2}],m={toc:d};function u(e){let{components:t,...a}=e;return(0,r.kt)("wrapper",(0,n.Z)({},m,a,{components:t,mdxType:"MDXLayout"}),(0,r.kt)("h1",{id:"schedule-actions"},"Schedule actions"),(0,r.kt)("p",null,"There are many cases in which you want to trigger some action periodically. For example, you may want to send a reminder email to a user every day at 10:00 AM. For this, you can use scheduled commands."),(0,r.kt)("h2",{id:"scheduled-command"},"Scheduled command"),(0,r.kt)("p",null,"Commands represent an action. Therefore, the way to trigger an action periodically is by scheduling a command. Scheduled commands are the way to add automated tasks to your application, like cron jobs in other frameworks. Booster scheduled commands are TypeScript classes decorated with ",(0,r.kt)("inlineCode",{parentName:"p"},"@ScheduledCommand"),". Unlike conventional commands, ",(0,r.kt)("strong",{parentName:"p"},"the handle function doesn't have any parameters"),"."),(0,r.kt)("p",null,"In Booster, a scheduled command looks like this:"),(0,r.kt)("pre",null,(0,r.kt)("code",{parentName:"pre",className:"language-typescript"},"@ScheduledCommand({\n  minute: '0/5', // runs every 5 minutes\n})\nexport class CheckCartCount {\n  public static async handle(): Promise<void> {\n    /* YOUR CODE HERE */\n  }\n}\n")),(0,r.kt)("p",null,"You can pass the following parameters to the ",(0,r.kt)("inlineCode",{parentName:"p"},"@ScheduledCommand")," decorator:"),(0,r.kt)("ul",null,(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("inlineCode",{parentName:"li"},"minute"),": A cron expression to specify the minute(s) in which the command will be triggered. For example, ",(0,r.kt)("inlineCode",{parentName:"li"},"0/5"),' means "every 5 minutes". You can also use a comma-separated list of values, like ',(0,r.kt)("inlineCode",{parentName:"li"},"1,5,10,15,20,25,30,35,40,45,50,55")," to specify a list of minutes."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("inlineCode",{parentName:"li"},"hour"),": A cron expression to specify the hour(s) in which the command will be triggered. For example, ",(0,r.kt)("inlineCode",{parentName:"li"},"0/2"),' means "every 2 hours". You can also use a comma-separated list of values, like ',(0,r.kt)("inlineCode",{parentName:"li"},"1,3,5,7,9,11,13,15,17,19,21,23")," to specify a list of hours."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("inlineCode",{parentName:"li"},"day"),": A cron expression to specify the day(s) in which the command will be triggered. For example, ",(0,r.kt)("inlineCode",{parentName:"li"},"1/2"),' means "every 2 days". You can also use a comma-separated list of values, like ',(0,r.kt)("inlineCode",{parentName:"li"},"1,3,5,7,9,11,13,15,17,19,21,23,25,27,29,31")," to specify a list of days."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("inlineCode",{parentName:"li"},"month"),": A cron expression to specify the month(s) in which the command will be triggered. For example, ",(0,r.kt)("inlineCode",{parentName:"li"},"1/2"),' means "every 2 months". You can also use a comma-separated list of values, like ',(0,r.kt)("inlineCode",{parentName:"li"},"1,3,5,7,9,11")," to specify a list of months."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("inlineCode",{parentName:"li"},"weekDay"),": A cron expression to specify the day(s) of the week in which the command will be triggered. For example, ",(0,r.kt)("inlineCode",{parentName:"li"},"1/2"),' means "every 2 days of the week". You can also use a comma-separated list of values, like ',(0,r.kt)("inlineCode",{parentName:"li"},"1,3,5")," to specify a list of days of the week."),(0,r.kt)("li",{parentName:"ul"},(0,r.kt)("inlineCode",{parentName:"li"},"year"),": A cron expression to specify the year(s) in which the command will be triggered. For example, ",(0,r.kt)("inlineCode",{parentName:"li"},"2020/2"),' means "every 2 years". You can also use a comma-separated list of values, like ',(0,r.kt)("inlineCode",{parentName:"li"},"2020,2022,2024,2026,2028,2030")," to specify a list of years.")),(0,r.kt)("p",null,"By default, if no paramaters are passed, the scheduled command will not be triggered."),(0,r.kt)("h2",{id:"creating-a-scheduled-command"},"Creating a scheduled command"),(0,r.kt)("p",null,"The preferred way to create a scheduled command is by using the generator, e.g."),(0,r.kt)(o.Z,{mdxType:"TerminalWindow"},(0,r.kt)("pre",null,(0,r.kt)("code",{parentName:"pre",className:"language-shell"},"boost new:scheduled-command CheckCartCount\n"))))}u.isMDXComponent=!0}}]);