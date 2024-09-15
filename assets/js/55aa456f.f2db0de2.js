"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[695],{4663:(e,n,t)=>{t.r(n),t.d(n,{assets:()=>d,contentTitle:()=>l,default:()=>v,frontMatter:()=>s,metadata:()=>o,toc:()=>c});var a=t(5893),r=t(1151),i=t(5163);const s={description:"Learn how to react to events and trigger side effects in Booster by defining event handlers."},l="Event handler",o={id:"architecture/event-handler",title:"Event handler",description:"Learn how to react to events and trigger side effects in Booster by defining event handlers.",source:"@site/docs/03_architecture/04_event-handler.mdx",sourceDirName:"03_architecture",slug:"/architecture/event-handler",permalink:"/architecture/event-handler",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/03_architecture/04_event-handler.mdx",tags:[],version:"current",lastUpdatedBy:"\xc1ngel Guzm\xe1n Maeso",lastUpdatedAt:1726405461,formattedLastUpdatedAt:"Sep 15, 2024",sidebarPosition:4,frontMatter:{description:"Learn how to react to events and trigger side effects in Booster by defining event handlers."},sidebar:"docs",previous:{title:"Event",permalink:"/architecture/event"},next:{title:"Entity",permalink:"/architecture/entity"}},d={},c=[{value:"Creating an event handler",id:"creating-an-event-handler",level:2},{value:"Declaring an event handler",id:"declaring-an-event-handler",level:2},{value:"Creating an event handler",id:"creating-an-event-handler-1",level:2},{value:"Registering events from an event handler",id:"registering-events-from-an-event-handler",level:2},{value:"Reading entities from event handlers",id:"reading-entities-from-event-handlers",level:2},{value:"Creating a global event handler",id:"creating-a-global-event-handler",level:2}];function h(e){const n={code:"code",h1:"h1",h2:"h2",p:"p",pre:"pre",strong:"strong",...(0,r.a)(),...e.components};return(0,a.jsxs)(a.Fragment,{children:[(0,a.jsx)(n.h1,{id:"event-handler",children:"Event handler"}),"\n",(0,a.jsx)(n.p,{children:"An event handler is a class that reacts to events. They are commonly used to trigger side effects in case of a new event. For instance, if a new event is registered in the system, an event handler could send an email to the user."}),"\n",(0,a.jsx)(n.h2,{id:"creating-an-event-handler",children:"Creating an event handler"}),"\n",(0,a.jsx)(n.p,{children:"The Booster CLI will help you to create new event handlers. You just need to run the following command and the CLI will generate all the boilerplate for you:"}),"\n",(0,a.jsx)(i.Z,{children:(0,a.jsx)(n.pre,{children:(0,a.jsx)(n.code,{className:"language-shell",children:"boost new:event-handler HandleAvailability --event StockMoved\n"})})}),"\n",(0,a.jsxs)(n.p,{children:["This will generate a new file called ",(0,a.jsx)(n.code,{children:"handle-availability.ts"})," in the ",(0,a.jsx)(n.code,{children:"src/event-handlers"})," directory. You can also create the file manually, but you will need to create the class and decorate it, so we recommend using the CLI."]}),"\n",(0,a.jsx)(n.h2,{id:"declaring-an-event-handler",children:"Declaring an event handler"}),"\n",(0,a.jsxs)(n.p,{children:["In Booster, event handlers are classes decorated with the ",(0,a.jsx)(n.code,{children:"@EventHandler"})," decorator. The parameter of the decorator is the event that the handler will react to. The logic to be triggered after an event is registered is defined in the ",(0,a.jsx)(n.code,{children:"handle"})," method of the class. This ",(0,a.jsx)(n.code,{children:"handle"})," function will receive the event that triggered the handler."]}),"\n",(0,a.jsx)(n.pre,{children:(0,a.jsx)(n.code,{className:"language-typescript",metastring:'title="src/event-handlers/handle-availability.ts"',children:"// highlight-next-line\n@EventHandler(StockMoved)\nexport class HandleAvailability {\n  // highlight-start\n  public static async handle(event: StockMoved): Promise<void> {\n    // Do something here\n  }\n  // highlight-end\n}\n"})}),"\n",(0,a.jsx)(n.h2,{id:"creating-an-event-handler-1",children:"Creating an event handler"}),"\n",(0,a.jsxs)(n.p,{children:["Event handlers can be easily created using the Booster CLI command ",(0,a.jsx)(n.code,{children:"boost new:event-handler"}),". There are two mandatory arguments: the event handler name, and the name of the event it will react to. For instance:"]}),"\n",(0,a.jsx)(i.Z,{children:(0,a.jsx)(n.pre,{children:(0,a.jsx)(n.code,{className:"language-typescript",children:"boost new:event-handler HandleAvailability --event StockMoved\n"})})}),"\n",(0,a.jsxs)(n.p,{children:["Once the creation is completed, there will be a new file in the event handlers directory ",(0,a.jsx)(n.code,{children:"<project-root>/src/event-handlers/handle-availability.ts"}),"."]}),"\n",(0,a.jsx)(n.pre,{children:(0,a.jsx)(n.code,{className:"language-text",children:"<project-root>\n\u251c\u2500\u2500 src\n\u2502   \u251c\u2500\u2500 commands\n\u2502   \u251c\u2500\u2500 common\n\u2502   \u251c\u2500\u2500 config\n\u2502   \u251c\u2500\u2500 entities\n\u2502   \u251c\u2500\u2500 events\n\u2502   \u251c\u2500\u2500 event-handlers <------ put them here\n\u2502   \u2514\u2500\u2500 read-models\n"})}),"\n",(0,a.jsx)(n.h2,{id:"registering-events-from-an-event-handler",children:"Registering events from an event handler"}),"\n",(0,a.jsx)(n.p,{children:"Event handlers can also register new events. This is useful when you want to trigger a new event after a certain condition is met. For example, if you want to send an email to the user when a product is out of stock."}),"\n",(0,a.jsxs)(n.p,{children:["In order to register new events, Booster injects the ",(0,a.jsx)(n.code,{children:"register"})," instance in the ",(0,a.jsx)(n.code,{children:"handle"})," method as a second parameter. This ",(0,a.jsx)(n.code,{children:"register"})," instance has a ",(0,a.jsx)(n.code,{children:"events(...)"})," method that allows you to store any side effect events, you can specify as many as you need separated by commas as arguments of the function."]}),"\n",(0,a.jsx)(n.pre,{children:(0,a.jsx)(n.code,{className:"language-typescript",metastring:'title="src/event-handlers/handle-availability.ts"',children:"@EventHandler(StockMoved)\nexport class HandleAvailability {\n  public static async handle(event: StockMoved, register: Register): Promise<void> {\n    if (event.quantity < 0) {\n      // highlight-next-line\n      register.events([new ProductOutOfStock(event.productID)])\n    }\n  }\n}\n"})}),"\n",(0,a.jsx)(n.h2,{id:"reading-entities-from-event-handlers",children:"Reading entities from event handlers"}),"\n",(0,a.jsx)(n.p,{children:"There are cases where you need to read an entity to make a decision based on its current state. Different side effects can be triggered depending on the current state of the entity. Given the previous example, if a user does not want to receive emails when a product is out of stock, we should be able check the user preferences before sending the email."}),"\n",(0,a.jsxs)(n.p,{children:["For that reason, Booster provides the ",(0,a.jsx)(n.code,{children:"Booster.entity"})," function. This function allows you to retrieve the current state of an entity. Let's say that we want to check the status of a product before we trigger its availability update. In that case we would call the ",(0,a.jsx)(n.code,{children:"Booster.entity"})," function, which will return information about the entity."]}),"\n",(0,a.jsx)(n.pre,{children:(0,a.jsx)(n.code,{className:"language-typescript",metastring:'title="src/event-handlers/handle-availability.ts"',children:"@EventHandler(StockMoved)\nexport class HandleAvailability {\n  public static async handle(event: StockMoved, register: Register): Promise<void> {\n    // highlight-next-line\n    const product = await Booster.entity(Product, event.productID)\n    if (product.stock < 0) {\n      register.events([new ProductOutOfStock(event.productID)])\n    }\n  }\n}\n"})}),"\n",(0,a.jsx)(n.h2,{id:"creating-a-global-event-handler",children:"Creating a global event handler"}),"\n",(0,a.jsxs)(n.p,{children:[(0,a.jsx)(n.strong,{children:"Booster"})," includes a ",(0,a.jsx)(n.code,{children:"Global event handler"}),". This feature allows you to react to any event that occurs within the system.\nBy annotating a class with the @GlobalEventHandler decorator, the handle method within that class will be automatically called for any event that is generated"]}),"\n",(0,a.jsx)(n.pre,{children:(0,a.jsx)(n.code,{className:"language-typescript",children:"@GlobalEventHandler\nexport class GlobalHandler {\n  public static async handle(event: EventInterface | NotificationInterface, register: Register): Promise<void> {\n    if (event instanceof LogEventReceived) {\n      register.events(new LogEventReceivedTest(event.entityID(), event.value))\n    }\n  }\n"})})]})}function v(e={}){const{wrapper:n}={...(0,r.a)(),...e.components};return n?(0,a.jsx)(n,{...e,children:(0,a.jsx)(h,{...e})}):h(e)}},5163:(e,n,t)=>{t.d(n,{Z:()=>i});t(7294);const a={terminalWindow:"terminalWindow_wGrl",terminalWindowHeader:"terminalWindowHeader_o9Cs",row:"row_Rn7G",buttons:"buttons_IGLB",right:"right_fWp9",terminalWindowAddressBar:"terminalWindowAddressBar_X8fO",dot:"dot_fGZE",terminalWindowMenuIcon:"terminalWindowMenuIcon_rtOE",bar:"bar_Ck8N",terminalWindowBody:"terminalWindowBody_tzdS"};var r=t(5893);function i(e){let{children:n}=e;return(0,r.jsxs)("div",{className:a.terminalWindow,children:[(0,r.jsx)("div",{className:a.terminalWindowHeader,children:(0,r.jsxs)("div",{className:a.buttons,children:[(0,r.jsx)("span",{className:a.dot,style:{background:"#f25f58"}}),(0,r.jsx)("span",{className:a.dot,style:{background:"#fbbe3c"}}),(0,r.jsx)("span",{className:a.dot,style:{background:"#58cb42"}})]})}),(0,r.jsx)("div",{className:a.terminalWindowBody,children:n})]})}},1151:(e,n,t)=>{t.d(n,{Z:()=>l,a:()=>s});var a=t(7294);const r={},i=a.createContext(r);function s(e){const n=a.useContext(i);return a.useMemo((function(){return"function"==typeof e?e(n):{...n,...e}}),[n,e])}function l(e){let n;return n=e.disableParentContext?"function"==typeof e.components?e.components(r):e.components||r:s(e.components),a.createElement(i.Provider,{value:n},e.children)}}}]);