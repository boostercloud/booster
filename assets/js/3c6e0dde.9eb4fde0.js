"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[4454],{3905:(e,t,n)=>{n.d(t,{Zo:()=>d,kt:()=>m});var a=n(7294);function o(e,t,n){return t in e?Object.defineProperty(e,t,{value:n,enumerable:!0,configurable:!0,writable:!0}):e[t]=n,e}function r(e,t){var n=Object.keys(e);if(Object.getOwnPropertySymbols){var a=Object.getOwnPropertySymbols(e);t&&(a=a.filter((function(t){return Object.getOwnPropertyDescriptor(e,t).enumerable}))),n.push.apply(n,a)}return n}function i(e){for(var t=1;t<arguments.length;t++){var n=null!=arguments[t]?arguments[t]:{};t%2?r(Object(n),!0).forEach((function(t){o(e,t,n[t])})):Object.getOwnPropertyDescriptors?Object.defineProperties(e,Object.getOwnPropertyDescriptors(n)):r(Object(n)).forEach((function(t){Object.defineProperty(e,t,Object.getOwnPropertyDescriptor(n,t))}))}return e}function l(e,t){if(null==e)return{};var n,a,o=function(e,t){if(null==e)return{};var n,a,o={},r=Object.keys(e);for(a=0;a<r.length;a++)n=r[a],t.indexOf(n)>=0||(o[n]=e[n]);return o}(e,t);if(Object.getOwnPropertySymbols){var r=Object.getOwnPropertySymbols(e);for(a=0;a<r.length;a++)n=r[a],t.indexOf(n)>=0||Object.prototype.propertyIsEnumerable.call(e,n)&&(o[n]=e[n])}return o}var s=a.createContext({}),p=function(e){var t=a.useContext(s),n=t;return e&&(n="function"==typeof e?e(t):i(i({},t),e)),n},d=function(e){var t=p(e.components);return a.createElement(s.Provider,{value:t},e.children)},c={inlineCode:"code",wrapper:function(e){var t=e.children;return a.createElement(a.Fragment,{},t)}},u=a.forwardRef((function(e,t){var n=e.components,o=e.mdxType,r=e.originalType,s=e.parentName,d=l(e,["components","mdxType","originalType","parentName"]),u=p(n),m=o,h=u["".concat(s,".").concat(m)]||u[m]||c[m]||r;return n?a.createElement(h,i(i({ref:t},d),{},{components:n})):a.createElement(h,i({ref:t},d))}));function m(e,t){var n=arguments,o=t&&t.mdxType;if("string"==typeof e||o){var r=n.length,i=new Array(r);i[0]=u;var l={};for(var s in t)hasOwnProperty.call(t,s)&&(l[s]=t[s]);l.originalType=e,l.mdxType="string"==typeof e?e:o,i[1]=l;for(var p=2;p<r;p++)i[p]=n[p];return a.createElement.apply(null,i)}return a.createElement.apply(null,n)}u.displayName="MDXCreateElement"},5163:(e,t,n)=>{n.d(t,{Z:()=>p});var a=n(7294);const o="terminalWindow_wGrl",r="terminalWindowHeader_o9Cs",i="buttons_IGLB",l="dot_fGZE",s="terminalWindowBody_tzdS";function p(e){let{children:t}=e;return a.createElement("div",{className:o},a.createElement("div",{className:r},a.createElement("div",{className:i},a.createElement("span",{className:l,style:{background:"#f25f58"}}),a.createElement("span",{className:l,style:{background:"#fbbe3c"}}),a.createElement("span",{className:l,style:{background:"#58cb42"}}))),a.createElement("div",{className:s},t))}},4792:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>p,contentTitle:()=>l,default:()=>u,frontMatter:()=>i,metadata:()=>s,toc:()=>d});var a=n(7462),o=(n(7294),n(3905)),r=n(5163);const i={description:"How to have the backend up and running for a blog application in a few minutes"},l="Build a Booster app in minutes",s={unversionedId:"getting-started/coding",id:"getting-started/coding",title:"Build a Booster app in minutes",description:"How to have the backend up and running for a blog application in a few minutes",source:"@site/docs/02_getting-started/coding.mdx",sourceDirName:"02_getting-started",slug:"/getting-started/coding",permalink:"/getting-started/coding",draft:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/02_getting-started/coding.mdx",tags:[],version:"current",lastUpdatedBy:"Javier Toledo",lastUpdatedAt:1677152638,formattedLastUpdatedAt:"Feb 23, 2023",frontMatter:{description:"How to have the backend up and running for a blog application in a few minutes"},sidebar:"docs",previous:{title:"Installation",permalink:"/getting-started/installation"},next:{title:"Booster architecture",permalink:"/architecture/event-driven"}},p={},d=[{value:"1. Create the project",id:"1-create-the-project",level:3},{value:"2. First command",id:"2-first-command",level:3},{value:"3. First event",id:"3-first-event",level:3},{value:"4. First entity",id:"4-first-entity",level:3},{value:"5. First read model",id:"5-first-read-model",level:3},{value:"6. Deployment",id:"6-deployment",level:3},{value:"6.1 Running your application locally",id:"61-running-your-application-locally",level:4},{value:"6.2 Deploying to the cloud",id:"62-deploying-to-the-cloud",level:4},{value:"7. Testing",id:"7-testing",level:3},{value:"7.1 Creating posts",id:"71-creating-posts",level:4},{value:"7.2 Retrieving all posts",id:"72-retrieving-all-posts",level:4},{value:"7.3 Retrieving specific post",id:"73-retrieving-specific-post",level:4},{value:"8. Removing the stack",id:"8-removing-the-stack",level:3},{value:"9. More functionalities",id:"9-more-functionalities",level:3},{value:"Examples and walkthroughs",id:"examples-and-walkthroughs",level:2},{value:"Creation of a question-asking application backend",id:"creation-of-a-question-asking-application-backend",level:3},{value:"All the guides and examples",id:"all-the-guides-and-examples",level:3}],c={toc:d};function u(e){let{components:t,...i}=e;return(0,o.kt)("wrapper",(0,a.Z)({},c,i,{components:t,mdxType:"MDXLayout"}),(0,o.kt)("h1",{id:"build-a-booster-app-in-minutes"},"Build a Booster app in minutes"),(0,o.kt)("p",null,"In this section, we will go through all the necessary steps to have the backend up and\nrunning for a blog application in just a few minutes."),(0,o.kt)("p",null,"Before starting, make sure to ",(0,o.kt)("a",{parentName:"p",href:"/getting-started/installation"},"have Booster CLI installed"),". If you also want to deploy your application to your cloud provider, check out the ",(0,o.kt)("a",{parentName:"p",href:"../going-deeper/infrastructure-providers"},"Provider configuration")," section."),(0,o.kt)("h3",{id:"1-create-the-project"},"1. Create the project"),(0,o.kt)("p",null,"First of all, we will use the Booster CLI tool generators to create a project."),(0,o.kt)("p",null,"In your favourite terminal, run this command ",(0,o.kt)("inlineCode",{parentName:"p"},"boost new:project boosted-blog")," and follow\nthe instructions. After some prompted questions, the CLI will ask you to select one of the available providers to set up as the main provider that will be used."),(0,o.kt)(r.Z,{mdxType:"TerminalWindow"},(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-shell"},"? What's the package name of your provider infrastructure library? (Use arrow keys)\n\u276f @boostercloud/framework-provider-aws (AWS)\n  @boostercloud/framework-provider-azure (Azure)\n  @boostercloud/framework-provider-kubernetes (Kubernetes) [Experimental]\n  Other\n"))),(0,o.kt)("p",null,"When asked for the provider, select AWS as that is what we have\nconfigured ",(0,o.kt)("a",{parentName:"p",href:"../going-deeper/infrastructure-providers#aws-provider-setup"},"here")," for the example. You can use another provider if you want, or add more providers once you have created the project."),(0,o.kt)("p",null,"If you don't know what provider you are going to use, and you just want to execute your Booster application locally, you can select one and change it later!"),(0,o.kt)("p",null,"After choosing your provider, you will see your project generated!:"),(0,o.kt)(r.Z,{mdxType:"TerminalWindow"},(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-shell"},"> boost new:project boosted-blog\n\n...\n\n\u2139 boost new \ud83d\udea7\n\u2714 Creating project root\n\u2714 Generating config files\n\u2714 Installing dependencies\n\u2139 Project generated!\n"))),(0,o.kt)("admonition",{type:"tip"},(0,o.kt)("p",{parentName:"admonition"},"If you prefer to create the project with default parameters, you can run the command as ",(0,o.kt)("inlineCode",{parentName:"p"},"boost new:project booster-blog --default"),". The default\nparameters are as follows:"),(0,o.kt)("ul",{parentName:"admonition"},(0,o.kt)("li",{parentName:"ul"},'Project name: The one provided when running the command, in this case "booster-blog"'),(0,o.kt)("li",{parentName:"ul"},"Provider: AWS"),(0,o.kt)("li",{parentName:"ul"},'Description, author, homepage and repository: ""'),(0,o.kt)("li",{parentName:"ul"},"License: MIT"),(0,o.kt)("li",{parentName:"ul"},"Version: 0.1.0"))),(0,o.kt)("p",null,"In case you want to specify each parameter without following the instructions, you can use the following flags with this structure ",(0,o.kt)("inlineCode",{parentName:"p"},"<flag>=<parameter>"),"."),(0,o.kt)("table",null,(0,o.kt)("thead",{parentName:"table"},(0,o.kt)("tr",{parentName:"thead"},(0,o.kt)("th",{parentName:"tr",align:"left"},"Flag"),(0,o.kt)("th",{parentName:"tr",align:"left"},"Short version"),(0,o.kt)("th",{parentName:"tr",align:"left"},"Description"))),(0,o.kt)("tbody",{parentName:"table"},(0,o.kt)("tr",{parentName:"tbody"},(0,o.kt)("td",{parentName:"tr",align:"left"},(0,o.kt)("inlineCode",{parentName:"td"},"--homepage")),(0,o.kt)("td",{parentName:"tr",align:"left"},(0,o.kt)("inlineCode",{parentName:"td"},"-H")),(0,o.kt)("td",{parentName:"tr",align:"left"},"The website of this project")),(0,o.kt)("tr",{parentName:"tbody"},(0,o.kt)("td",{parentName:"tr",align:"left"},(0,o.kt)("inlineCode",{parentName:"td"},"--author")),(0,o.kt)("td",{parentName:"tr",align:"left"},(0,o.kt)("inlineCode",{parentName:"td"},"-a")),(0,o.kt)("td",{parentName:"tr",align:"left"},"Author of this project")),(0,o.kt)("tr",{parentName:"tbody"},(0,o.kt)("td",{parentName:"tr",align:"left"},(0,o.kt)("inlineCode",{parentName:"td"},"--description")),(0,o.kt)("td",{parentName:"tr",align:"left"},(0,o.kt)("inlineCode",{parentName:"td"},"-d")),(0,o.kt)("td",{parentName:"tr",align:"left"},"A short description")),(0,o.kt)("tr",{parentName:"tbody"},(0,o.kt)("td",{parentName:"tr",align:"left"},(0,o.kt)("inlineCode",{parentName:"td"},"--license")),(0,o.kt)("td",{parentName:"tr",align:"left"},(0,o.kt)("inlineCode",{parentName:"td"},"-l")),(0,o.kt)("td",{parentName:"tr",align:"left"},"License used in this project")),(0,o.kt)("tr",{parentName:"tbody"},(0,o.kt)("td",{parentName:"tr",align:"left"},(0,o.kt)("inlineCode",{parentName:"td"},"--providerPackageName")),(0,o.kt)("td",{parentName:"tr",align:"left"},(0,o.kt)("inlineCode",{parentName:"td"},"-p")),(0,o.kt)("td",{parentName:"tr",align:"left"},"Package name implementing the cloud provider integration where the application will be deployed")),(0,o.kt)("tr",{parentName:"tbody"},(0,o.kt)("td",{parentName:"tr",align:"left"},(0,o.kt)("inlineCode",{parentName:"td"},"--repository")),(0,o.kt)("td",{parentName:"tr",align:"left"},(0,o.kt)("inlineCode",{parentName:"td"},"-r")),(0,o.kt)("td",{parentName:"tr",align:"left"},"The URL of the repository")),(0,o.kt)("tr",{parentName:"tbody"},(0,o.kt)("td",{parentName:"tr",align:"left"},(0,o.kt)("inlineCode",{parentName:"td"},"--version")),(0,o.kt)("td",{parentName:"tr",align:"left"},(0,o.kt)("inlineCode",{parentName:"td"},"-v")),(0,o.kt)("td",{parentName:"tr",align:"left"},"The initial version")))),(0,o.kt)("p",null,"Additionally, you can use the ",(0,o.kt)("inlineCode",{parentName:"p"},"--skipInstall")," flag if you want to skip installing dependencies and the ",(0,o.kt)("inlineCode",{parentName:"p"},"--skipGit")," flag in case you want to skip git initialization."),(0,o.kt)("blockquote",null,(0,o.kt)("p",{parentName:"blockquote"},"Booster CLI commands follow this structure: ",(0,o.kt)("inlineCode",{parentName:"p"},"boost <subcommand> [<flags>] [<parameters>]"),".\nLet's break down the command we have just executed:"),(0,o.kt)("ul",{parentName:"blockquote"},(0,o.kt)("li",{parentName:"ul"},(0,o.kt)("inlineCode",{parentName:"li"},"boost")," is the Booster CLI executable"),(0,o.kt)("li",{parentName:"ul"},(0,o.kt)("inlineCode",{parentName:"li"},"new:project"),' is the "subcommand" part. In this case, it is composed of two parts separated by a colon. The first part, ',(0,o.kt)("inlineCode",{parentName:"li"},"new"),", means that we want to generate a new resource. The second part, ",(0,o.kt)("inlineCode",{parentName:"li"},"project"),", indicates which kind of resource we are interested in. Other examples are ",(0,o.kt)("inlineCode",{parentName:"li"},"new:command"),", ",(0,o.kt)("inlineCode",{parentName:"li"},"new:event"),", etc. We'll see a bunch of them later."),(0,o.kt)("li",{parentName:"ul"},(0,o.kt)("inlineCode",{parentName:"li"},"boosted-blog"),' is a "parameter" for the subcommand ',(0,o.kt)("inlineCode",{parentName:"li"},"new:project"),". Flags and parameters are optional and their meaning and shape depend on the subcommand you used. In this case, we are specifying the name of the project we are creating."))),(0,o.kt)("admonition",{type:"tip"},(0,o.kt)("p",{parentName:"admonition"},"You can always use the ",(0,o.kt)("inlineCode",{parentName:"p"},"--help")," flag to get all the available options for each cli command.")),(0,o.kt)("p",null,"When finished, you'll see some scaffolding that has been generated. The project name will be the\nproject's root so ",(0,o.kt)("inlineCode",{parentName:"p"},"cd")," into it:"),(0,o.kt)(r.Z,{mdxType:"TerminalWindow"},(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-shell"},"cd boosted-blog\n"))),(0,o.kt)("p",null,"There you should have these files and directories already generated:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-text"},"boosted-blog\n\u251c\u2500\u2500 .eslintignore\n\u251c\u2500\u2500 .gitignore\n\u251c\u2500\u2500 .eslintrc.js\n\u251c\u2500\u2500 .prettierrc.yaml\n\u251c\u2500\u2500 package-lock.json\n\u251c\u2500\u2500 package.json\n\u251c\u2500\u2500 src\n\u2502   \u251c\u2500\u2500 commands\n\u2502   \u251c\u2500\u2500 common\n\u2502   \u251c\u2500\u2500 config\n\u2502   \u2502   \u2514\u2500\u2500 config.ts\n\u2502   \u251c\u2500\u2500 entities\n\u2502   \u251c\u2500\u2500 events\n\u2502   \u251c\u2500\u2500 event-handlers\n\u2502   \u251c\u2500\u2500 read-models\n\u2502   \u2514\u2500\u2500 index.ts\n\u251c\u2500\u2500 tsconfig.eslint.json\n\u2514\u2500\u2500 tsconfig.json\n")),(0,o.kt)("p",null,"Now open the project in your favorite editor, e.g. ",(0,o.kt)("a",{parentName:"p",href:"https://code.visualstudio.com/"},"Visual Studio Code"),"."),(0,o.kt)("h3",{id:"2-first-command"},"2. First command"),(0,o.kt)("p",null,"Commands define the input to our system, so we'll start by generating our first\n",(0,o.kt)("a",{parentName:"p",href:"/architecture/command"},"command")," to create posts. Use the command generator, while in the project's root\ndirectory, as follows:"),(0,o.kt)(r.Z,{mdxType:"TerminalWindow"},(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-bash"},"boost new:command CreatePost --fields postId:UUID title:string content:string author:string\n"))),(0,o.kt)("p",null,"The ",(0,o.kt)("inlineCode",{parentName:"p"},"new:command")," generator creates a ",(0,o.kt)("inlineCode",{parentName:"p"},"create-post.ts")," file in the ",(0,o.kt)("inlineCode",{parentName:"p"},"commands")," folder:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-text"},"boosted-blog\n\u2514\u2500\u2500 src\n    \u2514\u2500\u2500 commands\n        \u2514\u2500\u2500 create-post.ts\n")),(0,o.kt)("p",null,"As we mentioned before, commands are the input of our system. They're sent\nby the users of our application. When they are received you can validate its data,\nexecute some business logic, and register one or more events. Therefore, we have to define two more things:"),(0,o.kt)("ol",null,(0,o.kt)("li",{parentName:"ol"},"Who is authorized to run this command."),(0,o.kt)("li",{parentName:"ol"},"The events that it will trigger.")),(0,o.kt)("p",null,"Booster allows you to define authorization strategies (we will cover that\nlater). Let's start by allowing anyone to send this command to our application.\nTo do that, open the file we have just generated and add the string ",(0,o.kt)("inlineCode",{parentName:"p"},"'all'")," to the\n",(0,o.kt)("inlineCode",{parentName:"p"},"authorize")," parameter of the ",(0,o.kt)("inlineCode",{parentName:"p"},"@Command")," decorator. Your ",(0,o.kt)("inlineCode",{parentName:"p"},"CreatePost")," command should look like this:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"@Command({\n  authorize: 'all', // Specify authorized roles here. Use 'all' to authorize anyone\n})\nexport class CreatePost {\n  public constructor(\n    readonly postId: UUID,\n    readonly title: string,\n    readonly content: string,\n    readonly author: string\n  ) {}\n\n  public static async handle(command: CreatePost, register: Register): Promise<void> {\n    register.events(/* YOUR EVENT HERE */)\n  }\n}\n")),(0,o.kt)("h3",{id:"3-first-event"},"3. First event"),(0,o.kt)("p",null,"Instead of creating, updating, or deleting objects, Booster stores data in the form of events.\nThey are records of facts and represent the source of truth. Let's generate an event called ",(0,o.kt)("inlineCode",{parentName:"p"},"PostCreated"),"\nthat will contain the initial post info:"),(0,o.kt)(r.Z,{mdxType:"TerminalWindow"},(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-bash"},"boost new:event PostCreated --fields postId:UUID title:string content:string author:string\n"))),(0,o.kt)("p",null,"The ",(0,o.kt)("inlineCode",{parentName:"p"},"new:event")," generator creates a new file under the ",(0,o.kt)("inlineCode",{parentName:"p"},"src/events")," directory.\nThe name of the file is the name of the event:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-text"},"boosted-blog\n\u2514\u2500\u2500 src\n    \u2514\u2500\u2500 events\n        \u2514\u2500\u2500 post-created.ts\n")),(0,o.kt)("p",null,"All events in Booster must target an entity, so we need to implement an ",(0,o.kt)("inlineCode",{parentName:"p"},"entityID"),"\nmethod. From there, we'll return the identifier of the post created, the field\n",(0,o.kt)("inlineCode",{parentName:"p"},"postID"),". This identifier will be used later by Booster to build the final state\nof the ",(0,o.kt)("inlineCode",{parentName:"p"},"Post")," automatically. Edit the ",(0,o.kt)("inlineCode",{parentName:"p"},"entityID")," method in ",(0,o.kt)("inlineCode",{parentName:"p"},"events/post-created.ts"),"\nto return our ",(0,o.kt)("inlineCode",{parentName:"p"},"postID"),":"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"// src/events/post-created.ts\n\n@Event\nexport class PostCreated {\n  public constructor(\n    readonly postId: UUID,\n    readonly title: string,\n    readonly content: string,\n    readonly author: string\n  ) {}\n\n  public entityID(): UUID {\n    return this.postId\n  }\n}\n")),(0,o.kt)("p",null,"Now that we have an event, we can edit the ",(0,o.kt)("inlineCode",{parentName:"p"},"CreatePost")," command to emit it. Let's change\nthe command's ",(0,o.kt)("inlineCode",{parentName:"p"},"handle")," method to look like this:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"// src/commands/create-post.ts::handle\npublic static async handle(command: CreatePost, register: Register): Promise<void> {\n  register.events(new PostCreated(command.postId, command.title, command.content, command.author))\n}\n")),(0,o.kt)("p",null,"Remember to import the event class correctly on the top of the file:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"import { PostCreated } from '../events/post-created'\n")),(0,o.kt)("p",null,"We can do any validation in the command handler before storing the event, for our\nexample, we'll just save the received data in the ",(0,o.kt)("inlineCode",{parentName:"p"},"PostCreated")," event."),(0,o.kt)("h3",{id:"4-first-entity"},"4. First entity"),(0,o.kt)("p",null,"So far, our ",(0,o.kt)("inlineCode",{parentName:"p"},"PostCreated")," event suggests we need a ",(0,o.kt)("inlineCode",{parentName:"p"},"Post")," entity. Entities are a\nrepresentation of our system internal state. They are in charge of reducing (combining) all the events\nwith the same ",(0,o.kt)("inlineCode",{parentName:"p"},"entityID"),". Let's generate our ",(0,o.kt)("inlineCode",{parentName:"p"},"Post")," entity:"),(0,o.kt)(r.Z,{mdxType:"TerminalWindow"},(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-bash"},"boost new:entity Post --fields title:string content:string author:string --reduces PostCreated\n"))),(0,o.kt)("p",null,"You should see now a new file called ",(0,o.kt)("inlineCode",{parentName:"p"},"post.ts")," in the ",(0,o.kt)("inlineCode",{parentName:"p"},"src/entities")," directory."),(0,o.kt)("p",null,"This time, besides using the ",(0,o.kt)("inlineCode",{parentName:"p"},"--fields")," flag, we use the ",(0,o.kt)("inlineCode",{parentName:"p"},"--reduces")," flag to specify the events the entity will reduce and, this way, produce the Post current state. The generator will create one ",(0,o.kt)("em",{parentName:"p"},"reducer function")," for each event we have specified (only one in this case)."),(0,o.kt)("p",null,"Reducer functions in Booster work similarly to the ",(0,o.kt)("inlineCode",{parentName:"p"},"reduce")," callbacks in Javascript: they receive an event\nand the current state of the entity, and returns the next version of the same entity.\nIn this case, when we receive a ",(0,o.kt)("inlineCode",{parentName:"p"},"PostCreated")," event, we can just return a new ",(0,o.kt)("inlineCode",{parentName:"p"},"Post")," entity copying the fields\nfrom the event. There is no previous state of the Post as we are creating it for the first time:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"// src/entities/post.ts\n@Entity\nexport class Post {\n  public constructor(public id: UUID, readonly title: string, readonly content: string, readonly author: string) {}\n\n  @Reduces(PostCreated)\n  public static reducePostCreated(event: PostCreated, currentPost?: Post): Post {\n    return new Post(event.postId, event.title, event.content, event.author)\n  }\n}\n")),(0,o.kt)("p",null,"Entities represent our domain model and can be queried from command or\nevent handlers to make business decisions or enforcing business rules."),(0,o.kt)("h3",{id:"5-first-read-model"},"5. First read model"),(0,o.kt)("p",null,"In a real application, we rarely want to make public our entire domain model (entities)\nincluding all their fields. What is more, different users may have different views of the data depending\non their permissions or their use cases. That's the goal of ",(0,o.kt)("inlineCode",{parentName:"p"},"ReadModels"),". Client applications can query or\nsubscribe to them."),(0,o.kt)("p",null,"Read models are ",(0,o.kt)("em",{parentName:"p"},"projections")," of one or more entities into a new object that is reachable through the query and subscriptions APIs. Let's generate a ",(0,o.kt)("inlineCode",{parentName:"p"},"PostReadModel")," that projects our\n",(0,o.kt)("inlineCode",{parentName:"p"},"Post")," entity:"),(0,o.kt)(r.Z,{mdxType:"TerminalWindow"},(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-bash"},"boost new:read-model PostReadModel --fields title:string author:string --projects Post:id\n"))),(0,o.kt)("p",null,"We have used a new flag, ",(0,o.kt)("inlineCode",{parentName:"p"},"--projects"),", that allow us to specify the entities (can be many) the read model will\nwatch for changes. You might be wondering what is the ",(0,o.kt)("inlineCode",{parentName:"p"},":id")," after the entity name. That's the ",(0,o.kt)("a",{parentName:"p",href:"/architecture/read-model#the-projection-function"},"joinKey"),",\nbut you can forget about it now."),(0,o.kt)("p",null,"As you might guess, the read-model generator will create a file called\n",(0,o.kt)("inlineCode",{parentName:"p"},"post-read-model.ts")," under ",(0,o.kt)("inlineCode",{parentName:"p"},"src/read-models"),":"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-text"},"boosted-blog\n\u2514\u2500\u2500 src\n    \u2514\u2500\u2500 read-models\n        \u2514\u2500\u2500 post-read-model.ts\n")),(0,o.kt)("p",null,"There are two things to do when creating a read model:"),(0,o.kt)("ol",null,(0,o.kt)("li",{parentName:"ol"},"Define who is authorized to query or subscribe it"),(0,o.kt)("li",{parentName:"ol"},"Add the logic of the projection functions, where you can filter, combine, etc., the entities fields.")),(0,o.kt)("p",null,"While commands define the input to our system, read models define the output, and together they compound\nthe public API of a Booster application. Let's do the same we did in the command and authorize ",(0,o.kt)("inlineCode",{parentName:"p"},"all")," to\nquery/subscribe the ",(0,o.kt)("inlineCode",{parentName:"p"},"PostReadModel"),". Also, and for learning purposes, we will exclude the ",(0,o.kt)("inlineCode",{parentName:"p"},"content")," field\nfrom the ",(0,o.kt)("inlineCode",{parentName:"p"},"Post")," entity, so it won't be returned when users request the read model."),(0,o.kt)("p",null,"Edit the ",(0,o.kt)("inlineCode",{parentName:"p"},"post-read-model.ts")," file to look like this:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-typescript"},"// src/read-models/post-read-model.ts\n@ReadModel({\n  authorize: 'all', // Specify authorized roles here. Use 'all' to authorize anyone\n})\nexport class PostReadModel {\n  public constructor(public id: UUID, readonly title: string, readonly author: string) {}\n\n  @Projects(Post, 'id')\n  public static projectPost(entity: Post, currentPostReadModel?: PostReadModel): ProjectionResult<PostReadModel> {\n    return new PostReadModel(entity.id, entity.title, entity.author)\n  }\n}\n")),(0,o.kt)("h3",{id:"6-deployment"},"6. Deployment"),(0,o.kt)("p",null,"At this point, we've:"),(0,o.kt)("ul",null,(0,o.kt)("li",{parentName:"ul"},"Created a publicly accessible command"),(0,o.kt)("li",{parentName:"ul"},"Emitted an event as a mechanism to store data"),(0,o.kt)("li",{parentName:"ul"},"Reduced the event into an entity to have a representation of our internal state"),(0,o.kt)("li",{parentName:"ul"},"Projected the entity into a read model that is also publicly accessible.")),(0,o.kt)("p",null,"With this, you already know the basics to build event-driven, CQRS-based applications\nwith Booster."),(0,o.kt)("p",null,"You can check that code compiles correctly by running the build command:"),(0,o.kt)(r.Z,{mdxType:"TerminalWindow"},(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-bash"},"boost build\n"))),(0,o.kt)("p",null,"You can also clean the compiled code by running:"),(0,o.kt)(r.Z,{mdxType:"TerminalWindow"},(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-bash"},"boost clean\n"))),(0,o.kt)("h4",{id:"61-running-your-application-locally"},"6.1 Running your application locally"),(0,o.kt)("p",null,"Now, let's run our application to see it working. It is as simple as running:"),(0,o.kt)(r.Z,{mdxType:"TerminalWindow"},(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-bash"},"boost start -e local\n"))),(0,o.kt)("p",null,"This will execute a local ",(0,o.kt)("inlineCode",{parentName:"p"},"Express.js")," server and will try to expose it in port ",(0,o.kt)("inlineCode",{parentName:"p"},"3000"),". You can change the port by using the ",(0,o.kt)("inlineCode",{parentName:"p"},"-p")," option:"),(0,o.kt)(r.Z,{mdxType:"TerminalWindow"},(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-bash"},"boost start -e local -p 8080\n"))),(0,o.kt)("h4",{id:"62-deploying-to-the-cloud"},"6.2 Deploying to the cloud"),(0,o.kt)("p",null,"Also, we can deploy our application to the cloud with no additional changes by running\nthe deploy command:"),(0,o.kt)(r.Z,{mdxType:"TerminalWindow"},(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-bash"},"boost deploy -e production\n"))),(0,o.kt)("p",null,"This is the Booster magic! \u2728 When running the start or the deploy commands, Booster will handle the creation of all the resources, ",(0,o.kt)("em",{parentName:"p"},"like Lambdas, API Gateway,"),' and the "glue" between them; ',(0,o.kt)("em",{parentName:"p"},"permissions, events, triggers, etc.")," It even creates a fully functional GraphQL API!"),(0,o.kt)("admonition",{type:"note"},(0,o.kt)("p",{parentName:"admonition"},"Deploy command automatically builds the project for you before performing updates in the cloud provider, so, build command it's not required beforehand.")),(0,o.kt)("blockquote",null,(0,o.kt)("p",{parentName:"blockquote"},"With ",(0,o.kt)("inlineCode",{parentName:"p"},"-e production")," we are specifying which environment we want to deploy. We'll talk about them later.")),(0,o.kt)("admonition",{type:"tip"},(0,o.kt)("p",{parentName:"admonition"},"If at this point you still don\u2019t believe everything is done, feel free to check in your provider\u2019s console. You should see, as in the AWS example below, that the stack and all the services are up and running! It will be the same for other providers. \ud83d\ude80")),(0,o.kt)("p",null,(0,o.kt)("img",{alt:"resources",src:n(2822).Z,width:"2726",height:"1276"})),(0,o.kt)("p",null,"When deploying, it will take a couple of minutes to deploy all the resources. Once finished, you will see\ninformation about your application endpoints and other outputs. For this example, we will\nonly need to pick the output ending in ",(0,o.kt)("inlineCode",{parentName:"p"},"httpURL"),", e.g.:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-text"},"https://<some random name>.execute-api.us-east-1.amazonaws.com/production\n")),(0,o.kt)("admonition",{type:"note"},(0,o.kt)("p",{parentName:"admonition"},"By default, the full error stack trace is send to a local file, ",(0,o.kt)("inlineCode",{parentName:"p"},"./errors.log"),". To see the full error stack trace directly from the console, use the ",(0,o.kt)("inlineCode",{parentName:"p"},"--verbose")," flag.")),(0,o.kt)("h3",{id:"7-testing"},"7. Testing"),(0,o.kt)("p",null,"Let's get started testing the project. We will perform three actions:"),(0,o.kt)("ul",null,(0,o.kt)("li",{parentName:"ul"},"Add a couple of posts"),(0,o.kt)("li",{parentName:"ul"},"Retrieve all posts"),(0,o.kt)("li",{parentName:"ul"},"Retrieve a specific post")),(0,o.kt)("p",null,"Booster applications provide you with a GraphQL API out of the box. You send commands using\n",(0,o.kt)("em",{parentName:"p"},"mutations")," and get read models data using ",(0,o.kt)("em",{parentName:"p"},"queries")," or ",(0,o.kt)("em",{parentName:"p"},"subscriptions"),"."),(0,o.kt)("p",null,"In this section, we will be sending requests by hand using the free ",(0,o.kt)("a",{parentName:"p",href:"https://altair.sirmuel.design/"},"Altair")," GraphQL client,\nwhich is very simple and straightforward for this guide. However, you can use any client you want. Your endpoint URL should look like this:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-text"},"<httpURL>/graphql\n")),(0,o.kt)("h4",{id:"71-creating-posts"},"7.1 Creating posts"),(0,o.kt)("p",null,"Let's use two mutations to send two ",(0,o.kt)("inlineCode",{parentName:"p"},"CreatePost")," commands."),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-graphql"},'mutation {\n  CreatePost(\n    input: {\n      postId: "95ddb544-4a60-439f-a0e4-c57e806f2f6e"\n      title: "Build a blog in 10 minutes with Booster"\n      content: "I am so excited to write my first post"\n      author: "Boosted developer"\n    }\n  )\n}\n')),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-graphql"},'mutation {\n  CreatePost(\n    input: {\n      postId: "05670e55-fd31-490e-b585-3a0096db0412"\n      title: "Booster framework rocks"\n      content: "I am so excited for writing the second post"\n      author: "Another boosted developer"\n    }\n  )\n}\n')),(0,o.kt)("p",null,"The expected response for each of those requests should be:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-json"},'{\n  "data": {\n    "CreatePost": true\n  }\n}\n')),(0,o.kt)("admonition",{type:"note"},(0,o.kt)("p",{parentName:"admonition"},"In this example, the IDs are generated on the client-side. When running production applications consider adding validation for ID uniqueness. For this example, we have used ",(0,o.kt)("a",{parentName:"p",href:"https://www.uuidgenerator.net/version4"},"a UUID generator"))),(0,o.kt)("h4",{id:"72-retrieving-all-posts"},"7.2 Retrieving all posts"),(0,o.kt)("p",null,"Let's perform a GraphQL ",(0,o.kt)("inlineCode",{parentName:"p"},"query")," that will be hitting our ",(0,o.kt)("inlineCode",{parentName:"p"},"PostReadModel"),":"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-graphql"},"query {\n  PostReadModels {\n    id\n    title\n    author\n  }\n}\n")),(0,o.kt)("p",null,"It should respond with something like:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-json"},'{\n  "data": {\n    "PostReadModels": [\n      {\n        "id": "05670e55-fd31-490e-b585-3a0096db0412",\n        "title": "Booster framework rocks",\n        "author": "Another boosted developer"\n      },\n      {\n        "id": "95ddb544-4a60-439f-a0e4-c57e806f2f6e",\n        "title": "Build a blog in 10 minutes with Booster",\n        "author": "Boosted developer"\n      }\n    ]\n  }\n}\n')),(0,o.kt)("h4",{id:"73-retrieving-specific-post"},"7.3 Retrieving specific post"),(0,o.kt)("p",null,"It is also possible to retrieve specific a ",(0,o.kt)("inlineCode",{parentName:"p"},"Post")," by adding the ",(0,o.kt)("inlineCode",{parentName:"p"},"id")," as input, e.g.:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-graphql"},'query {\n  PostReadModel(id: "95ddb544-4a60-439f-a0e4-c57e806f2f6e") {\n    id\n    title\n    author\n  }\n}\n')),(0,o.kt)("p",null,"You should get a response similar to this:"),(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-json"},'{\n  "data": {\n    "PostReadModel": {\n      "id": "95ddb544-4a60-439f-a0e4-c57e806f2f6e",\n      "title": "Build a blog in 10 minutes with Booster",\n      "author": "Boosted developer"\n    }\n  }\n}\n')),(0,o.kt)("h3",{id:"8-removing-the-stack"},"8. Removing the stack"),(0,o.kt)("p",null,"It is convenient to destroy all the infrastructure created after you stop using\nit to avoid generating cloud resource costs. Execute the following command from\nthe root of the project. For safety reasons, you have to confirm this action by\nwriting the project's name, in our case ",(0,o.kt)("inlineCode",{parentName:"p"},"boosted-blog")," that is the same used when\nwe run ",(0,o.kt)("inlineCode",{parentName:"p"},"new:project")," CLI command."),(0,o.kt)(r.Z,{mdxType:"TerminalWindow"},(0,o.kt)("pre",null,(0,o.kt)("code",{parentName:"pre",className:"language-bash"},"> boost nuke -e production\n\n? Please, enter the app name to confirm deletion of all resources: boosted-blog\n"))),(0,o.kt)("blockquote",null,(0,o.kt)("p",{parentName:"blockquote"},"Congratulations! You've built a serverless backend in less than 10 minutes. We hope you have enjoyed discovering the magic of the Booster Framework.")),(0,o.kt)("h3",{id:"9-more-functionalities"},"9. More functionalities"),(0,o.kt)("p",null,"This is a really basic example of a Booster application. The are many other features Booster provides like:"),(0,o.kt)("ul",null,(0,o.kt)("li",{parentName:"ul"},"Use a more complex authorization schema for commands and read models based on user roles"),(0,o.kt)("li",{parentName:"ul"},"Use GraphQL subscriptions to get updates in real-time"),(0,o.kt)("li",{parentName:"ul"},"Make events trigger other events"),(0,o.kt)("li",{parentName:"ul"},"Deploy static content"),(0,o.kt)("li",{parentName:"ul"},"Reading entities within command handlers to apply domain-driven decisions"),(0,o.kt)("li",{parentName:"ul"},"And much more...")),(0,o.kt)("p",null,"Continue reading to dig more. You've just scratched the surface of all the Booster\ncapabilities!"),(0,o.kt)("h2",{id:"examples-and-walkthroughs"},"Examples and walkthroughs"),(0,o.kt)("h3",{id:"creation-of-a-question-asking-application-backend"},"Creation of a question-asking application backend"),(0,o.kt)("p",null,"In the following video, you will find how to create a backend for a question-asking application from scratch. This application would allow\nusers to create questions and like them. This video goes from creating the project to incrementally deploying features in the application.\nYou can find the code both for the frontend and the backend in\n",(0,o.kt)("a",{parentName:"p",href:"https://github.com/boostercloud/examples/tree/master/askme"},"this GitHub repo"),"."),(0,o.kt)("div",{align:"center"},(0,o.kt)("iframe",{width:"560",height:"315",src:"https://www.youtube.com/embed/C4K2M-orT8k",title:"YouTube video player",frameBorder:"0",allow:"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture",allowFullScreen:!0})),(0,o.kt)("h3",{id:"all-the-guides-and-examples"},"All the guides and examples"),(0,o.kt)("p",null,"Check out the ",(0,o.kt)("a",{parentName:"p",href:"https://github.com/boostercloud/booster/tree/main/examples"},"step-by-step guides")," and the ",(0,o.kt)("a",{parentName:"p",href:"https://github.com/boostercloud/examples"},"example apps repository")," to see Booster in use."))}u.isMDXComponent=!0},2822:(e,t,n)=>{n.d(t,{Z:()=>a});const a=n.p+"assets/images/aws-resources-e620ed48140a022aae2ca68d0c52b496.png"}}]);