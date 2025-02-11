"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[4454],{4606:(e,n,t)=>{t.r(n),t.d(n,{assets:()=>c,contentTitle:()=>a,default:()=>u,frontMatter:()=>l,metadata:()=>d,toc:()=>h});var s=t(5893),o=t(1151),i=t(5163),r=t(2735);const l={description:"How to have the backend up and running for a blog application in a few minutes"},a="Build a Booster app in minutes",d={id:"getting-started/coding",title:"Build a Booster app in minutes",description:"How to have the backend up and running for a blog application in a few minutes",source:"@site/docs/02_getting-started/coding.mdx",sourceDirName:"02_getting-started",slug:"/getting-started/coding",permalink:"/getting-started/coding",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/02_getting-started/coding.mdx",tags:[],version:"current",lastUpdatedBy:"Mario Castro Squella",lastUpdatedAt:1739295071,formattedLastUpdatedAt:"Feb 11, 2025",frontMatter:{description:"How to have the backend up and running for a blog application in a few minutes"},sidebar:"docs",previous:{title:"Installation",permalink:"/getting-started/installation"},next:{title:"Booster architecture",permalink:"/architecture/event-driven"}},c={},h=[{value:"1. Create the project",id:"1-create-the-project",level:3},{value:"2. First command",id:"2-first-command",level:3},{value:"3. First event",id:"3-first-event",level:3},{value:"4. First entity",id:"4-first-entity",level:3},{value:"5. First read model",id:"5-first-read-model",level:3},{value:"6. Deployment",id:"6-deployment",level:3},{value:"6.1 Running your application locally",id:"61-running-your-application-locally",level:4},{value:"6.2 Deploying to the cloud",id:"62-deploying-to-the-cloud",level:4},{value:"7. Testing",id:"7-testing",level:3},{value:"7.1 Creating posts",id:"71-creating-posts",level:4},{value:"7.2 Retrieving all posts",id:"72-retrieving-all-posts",level:4},{value:"7.3 Retrieving specific post",id:"73-retrieving-specific-post",level:4},{value:"8. Removing the stack",id:"8-removing-the-stack",level:3},{value:"9. More functionalities",id:"9-more-functionalities",level:3},{value:"Examples and walkthroughs",id:"examples-and-walkthroughs",level:2},{value:"Creation of a question-asking application backend",id:"creation-of-a-question-asking-application-backend",level:3},{value:"All the guides and examples",id:"all-the-guides-and-examples",level:3}];function p(e){const n={a:"a",admonition:"admonition",blockquote:"blockquote",code:"code",em:"em",h1:"h1",h2:"h2",h3:"h3",h4:"h4",img:"img",li:"li",ol:"ol",p:"p",pre:"pre",table:"table",tbody:"tbody",td:"td",th:"th",thead:"thead",tr:"tr",ul:"ul",...(0,o.a)(),...e.components};return(0,s.jsxs)(s.Fragment,{children:[(0,s.jsx)(n.h1,{id:"build-a-booster-app-in-minutes",children:"Build a Booster app in minutes"}),"\n",(0,s.jsx)(n.p,{children:"In this section, we will go through all the necessary steps to have the backend up and\nrunning for a blog application in just a few minutes."}),"\n",(0,s.jsxs)(n.p,{children:["Before starting, make sure to ",(0,s.jsx)(n.a,{href:"/getting-started/installation",children:"have Booster CLI installed"}),". If you also want to deploy your application to your cloud provider, check out the ",(0,s.jsx)(n.a,{href:"../going-deeper/infrastructure-providers",children:"Provider configuration"})," section."]}),"\n",(0,s.jsx)(n.h3,{id:"1-create-the-project",children:"1. Create the project"}),"\n",(0,s.jsx)(n.p,{children:"First of all, we will use the Booster CLI tool generators to create a project."}),"\n",(0,s.jsxs)(n.p,{children:["In your favourite terminal, run this command ",(0,s.jsx)(n.code,{children:"boost new:project boosted-blog"})," and follow\nthe instructions. After some prompted questions, the CLI will ask you to select one of the available providers to set up as the main provider that will be used."]}),"\n",(0,s.jsx)(i.Z,{children:(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-shell",children:"? What's the package name of your provider infrastructure library? (Use arrow keys)\n  @boostercloud/framework-provider-azure (Azure)\n\u276f @boostercloud/framework-provider-aws (AWS) - Deprecated\n  Other\n"})})}),"\n",(0,s.jsxs)(n.p,{children:["When asked for the provider, select AWS as that is what we have\nconfigured ",(0,s.jsx)(n.a,{href:"../going-deeper/infrastructure-providers#aws-provider-setup",children:"here"})," for the example. You can use another provider if you want, or add more providers once you have created the project."]}),"\n",(0,s.jsx)(n.p,{children:"If you don't know what provider you are going to use, and you just want to execute your Booster application locally, you can select one and change it later!"}),"\n",(0,s.jsx)(n.p,{children:"After choosing your provider, you will see your project generated!:"}),"\n",(0,s.jsx)(i.Z,{children:(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-shell",children:"> boost new:project boosted-blog\n\n...\n\n\u2139 boost new \ud83d\udea7\n\u2714 Creating project root\n\u2714 Generating config files\n\u2714 Installing dependencies\n\u2139 Project generated!\n"})})}),"\n",(0,s.jsxs)(n.admonition,{type:"tip",children:[(0,s.jsxs)(n.p,{children:["If you prefer to create the project with default parameters, you can run the command as ",(0,s.jsx)(n.code,{children:"boost new:project booster-blog --default"}),". The default\nparameters are as follows:"]}),(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:'Project name: The one provided when running the command, in this case "booster-blog"'}),"\n",(0,s.jsx)(n.li,{children:"Provider: AWS"}),"\n",(0,s.jsx)(n.li,{children:'Description, author, homepage and repository: ""'}),"\n",(0,s.jsx)(n.li,{children:"License: MIT"}),"\n",(0,s.jsx)(n.li,{children:"Version: 0.1.0"}),"\n"]})]}),"\n",(0,s.jsxs)(n.p,{children:["In case you want to specify each parameter without following the instructions, you can use the following flags with this structure ",(0,s.jsx)(n.code,{children:"<flag>=<parameter>"}),"."]}),"\n",(0,s.jsxs)(n.table,{children:[(0,s.jsx)(n.thead,{children:(0,s.jsxs)(n.tr,{children:[(0,s.jsx)(n.th,{style:{textAlign:"left"},children:"Flag"}),(0,s.jsx)(n.th,{style:{textAlign:"left"},children:"Short version"}),(0,s.jsx)(n.th,{style:{textAlign:"left"},children:"Description"})]})}),(0,s.jsxs)(n.tbody,{children:[(0,s.jsxs)(n.tr,{children:[(0,s.jsx)(n.td,{style:{textAlign:"left"},children:(0,s.jsx)(n.code,{children:"--homepage"})}),(0,s.jsx)(n.td,{style:{textAlign:"left"},children:(0,s.jsx)(n.code,{children:"-H"})}),(0,s.jsx)(n.td,{style:{textAlign:"left"},children:"The website of this project"})]}),(0,s.jsxs)(n.tr,{children:[(0,s.jsx)(n.td,{style:{textAlign:"left"},children:(0,s.jsx)(n.code,{children:"--author"})}),(0,s.jsx)(n.td,{style:{textAlign:"left"},children:(0,s.jsx)(n.code,{children:"-a"})}),(0,s.jsx)(n.td,{style:{textAlign:"left"},children:"Author of this project"})]}),(0,s.jsxs)(n.tr,{children:[(0,s.jsx)(n.td,{style:{textAlign:"left"},children:(0,s.jsx)(n.code,{children:"--description"})}),(0,s.jsx)(n.td,{style:{textAlign:"left"},children:(0,s.jsx)(n.code,{children:"-d"})}),(0,s.jsx)(n.td,{style:{textAlign:"left"},children:"A short description"})]}),(0,s.jsxs)(n.tr,{children:[(0,s.jsx)(n.td,{style:{textAlign:"left"},children:(0,s.jsx)(n.code,{children:"--license"})}),(0,s.jsx)(n.td,{style:{textAlign:"left"},children:(0,s.jsx)(n.code,{children:"-l"})}),(0,s.jsx)(n.td,{style:{textAlign:"left"},children:"License used in this project"})]}),(0,s.jsxs)(n.tr,{children:[(0,s.jsx)(n.td,{style:{textAlign:"left"},children:(0,s.jsx)(n.code,{children:"--providerPackageName"})}),(0,s.jsx)(n.td,{style:{textAlign:"left"},children:(0,s.jsx)(n.code,{children:"-p"})}),(0,s.jsx)(n.td,{style:{textAlign:"left"},children:"Package name implementing the cloud provider integration where the application will be deployed"})]}),(0,s.jsxs)(n.tr,{children:[(0,s.jsx)(n.td,{style:{textAlign:"left"},children:(0,s.jsx)(n.code,{children:"--repository"})}),(0,s.jsx)(n.td,{style:{textAlign:"left"},children:(0,s.jsx)(n.code,{children:"-r"})}),(0,s.jsx)(n.td,{style:{textAlign:"left"},children:"The URL of the repository"})]}),(0,s.jsxs)(n.tr,{children:[(0,s.jsx)(n.td,{style:{textAlign:"left"},children:(0,s.jsx)(n.code,{children:"--version"})}),(0,s.jsx)(n.td,{style:{textAlign:"left"},children:(0,s.jsx)(n.code,{children:"-v"})}),(0,s.jsx)(n.td,{style:{textAlign:"left"},children:"The initial version"})]})]})]}),"\n",(0,s.jsxs)(n.p,{children:["Additionally, you can use the ",(0,s.jsx)(n.code,{children:"--skipInstall"})," flag if you want to skip installing dependencies and the ",(0,s.jsx)(n.code,{children:"--skipGit"})," flag in case you want to skip git initialization."]}),"\n",(0,s.jsxs)(n.blockquote,{children:["\n",(0,s.jsxs)(n.p,{children:["Booster CLI commands follow this structure: ",(0,s.jsx)(n.code,{children:"boost <subcommand> [<flags>] [<parameters>]"}),".\nLet's break down the command we have just executed:"]}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsxs)(n.li,{children:[(0,s.jsx)(n.code,{children:"boost"})," is the Booster CLI executable"]}),"\n",(0,s.jsxs)(n.li,{children:[(0,s.jsx)(n.code,{children:"new:project"}),' is the "subcommand" part. In this case, it is composed of two parts separated by a colon. The first part, ',(0,s.jsx)(n.code,{children:"new"}),", means that we want to generate a new resource. The second part, ",(0,s.jsx)(n.code,{children:"project"}),", indicates which kind of resource we are interested in. Other examples are ",(0,s.jsx)(n.code,{children:"new:command"}),", ",(0,s.jsx)(n.code,{children:"new:event"}),", etc. We'll see a bunch of them later."]}),"\n",(0,s.jsxs)(n.li,{children:[(0,s.jsx)(n.code,{children:"boosted-blog"}),' is a "parameter" for the subcommand ',(0,s.jsx)(n.code,{children:"new:project"}),". Flags and parameters are optional and their meaning and shape depend on the subcommand you used. In this case, we are specifying the name of the project we are creating."]}),"\n"]}),"\n"]}),"\n",(0,s.jsx)(n.admonition,{type:"tip",children:(0,s.jsxs)(n.p,{children:["You can always use the ",(0,s.jsx)(n.code,{children:"--help"})," flag to get all the available options for each cli command."]})}),"\n",(0,s.jsxs)(n.p,{children:["When finished, you'll see some scaffolding that has been generated. The project name will be the\nproject's root so ",(0,s.jsx)(n.code,{children:"cd"})," into it:"]}),"\n",(0,s.jsx)(i.Z,{children:(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-shell",children:"cd boosted-blog\n"})})}),"\n",(0,s.jsx)(n.p,{children:"There you should have these files and directories already generated:"}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-text",children:"boosted-blog\n\u251c\u2500\u2500 .eslintignore\n\u251c\u2500\u2500 .gitignore\n\u251c\u2500\u2500 .eslintrc.js\n\u251c\u2500\u2500 .prettierrc.yaml\n\u251c\u2500\u2500 package-lock.json\n\u251c\u2500\u2500 package.json\n\u251c\u2500\u2500 src\n\u2502   \u251c\u2500\u2500 commands\n\u2502   \u251c\u2500\u2500 common\n\u2502   \u251c\u2500\u2500 config\n\u2502   \u2502   \u2514\u2500\u2500 config.ts\n\u2502   \u251c\u2500\u2500 entities\n\u2502   \u251c\u2500\u2500 events\n\u2502   \u251c\u2500\u2500 event-handlers\n\u2502   \u251c\u2500\u2500 read-models\n\u2502   \u2514\u2500\u2500 index.ts\n\u251c\u2500\u2500 tsconfig.eslint.json\n\u2514\u2500\u2500 tsconfig.json\n"})}),"\n",(0,s.jsxs)(n.p,{children:["Now open the project in your favorite editor, e.g. ",(0,s.jsx)(n.a,{href:"https://code.visualstudio.com/",children:"Visual Studio Code"}),"."]}),"\n",(0,s.jsx)(n.h3,{id:"2-first-command",children:"2. First command"}),"\n",(0,s.jsxs)(n.p,{children:["Commands define the input to our system, so we'll start by generating our first\n",(0,s.jsx)(n.a,{href:"/architecture/command",children:"command"})," to create posts. Use the command generator, while in the project's root\ndirectory, as follows:"]}),"\n",(0,s.jsx)(i.Z,{children:(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-bash",children:"boost new:command CreatePost --fields postId:UUID title:string content:string author:string\n"})})}),"\n",(0,s.jsxs)(n.p,{children:["The ",(0,s.jsx)(n.code,{children:"new:command"})," generator creates a ",(0,s.jsx)(n.code,{children:"create-post.ts"})," file in the ",(0,s.jsx)(n.code,{children:"commands"})," folder:"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-text",children:"boosted-blog\n\u2514\u2500\u2500 src\n    \u2514\u2500\u2500 commands\n        \u2514\u2500\u2500 create-post.ts\n"})}),"\n",(0,s.jsx)(n.p,{children:"As we mentioned before, commands are the input of our system. They're sent\nby the users of our application. When they are received you can validate its data,\nexecute some business logic, and register one or more events. Therefore, we have to define two more things:"}),"\n",(0,s.jsxs)(n.ol,{children:["\n",(0,s.jsx)(n.li,{children:"Who is authorized to run this command."}),"\n",(0,s.jsx)(n.li,{children:"The events that it will trigger."}),"\n"]}),"\n",(0,s.jsxs)(n.p,{children:["Booster allows you to define authorization strategies (we will cover that\nlater). Let's start by allowing anyone to send this command to our application.\nTo do that, open the file we have just generated and add the string ",(0,s.jsx)(n.code,{children:"'all'"})," to the\n",(0,s.jsx)(n.code,{children:"authorize"})," parameter of the ",(0,s.jsx)(n.code,{children:"@Command"})," decorator. Your ",(0,s.jsx)(n.code,{children:"CreatePost"})," command should look like this:"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"@Command({\n  authorize: 'all', // Specify authorized roles here. Use 'all' to authorize anyone\n})\nexport class CreatePost {\n  public constructor(\n    readonly postId: UUID,\n    readonly title: string,\n    readonly content: string,\n    readonly author: string\n  ) {}\n\n  public static async handle(command: CreatePost, register: Register): Promise<void> {\n    register.events(/* YOUR EVENT HERE */)\n  }\n}\n"})}),"\n",(0,s.jsx)(n.h3,{id:"3-first-event",children:"3. First event"}),"\n",(0,s.jsxs)(n.p,{children:["Instead of creating, updating, or deleting objects, Booster stores data in the form of events.\nThey are records of facts and represent the source of truth. Let's generate an event called ",(0,s.jsx)(n.code,{children:"PostCreated"}),"\nthat will contain the initial post info:"]}),"\n",(0,s.jsx)(i.Z,{children:(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-bash",children:"boost new:event PostCreated --fields postId:UUID title:string content:string author:string\n"})})}),"\n",(0,s.jsxs)(n.p,{children:["The ",(0,s.jsx)(n.code,{children:"new:event"})," generator creates a new file under the ",(0,s.jsx)(n.code,{children:"src/events"})," directory.\nThe name of the file is the name of the event:"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-text",children:"boosted-blog\n\u2514\u2500\u2500 src\n    \u2514\u2500\u2500 events\n        \u2514\u2500\u2500 post-created.ts\n"})}),"\n",(0,s.jsxs)(n.p,{children:["All events in Booster must target an entity, so we need to implement an ",(0,s.jsx)(n.code,{children:"entityID"}),"\nmethod. From there, we'll return the identifier of the post created, the field\n",(0,s.jsx)(n.code,{children:"postID"}),". This identifier will be used later by Booster to build the final state\nof the ",(0,s.jsx)(n.code,{children:"Post"})," automatically. Edit the ",(0,s.jsx)(n.code,{children:"entityID"})," method in ",(0,s.jsx)(n.code,{children:"events/post-created.ts"}),"\nto return our ",(0,s.jsx)(n.code,{children:"postID"}),":"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"// src/events/post-created.ts\n\n@Event\nexport class PostCreated {\n  public constructor(\n    readonly postId: UUID,\n    readonly title: string,\n    readonly content: string,\n    readonly author: string\n  ) {}\n\n  public entityID(): UUID {\n    return this.postId\n  }\n}\n"})}),"\n",(0,s.jsxs)(n.p,{children:["Now that we have an event, we can edit the ",(0,s.jsx)(n.code,{children:"CreatePost"})," command to emit it. Let's change\nthe command's ",(0,s.jsx)(n.code,{children:"handle"})," method to look like this:"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"// src/commands/create-post.ts::handle\npublic static async handle(command: CreatePost, register: Register): Promise<void> {\n  register.events(new PostCreated(command.postId, command.title, command.content, command.author))\n}\n"})}),"\n",(0,s.jsx)(n.p,{children:"Remember to import the event class correctly on the top of the file:"}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"import { PostCreated } from '../events/post-created'\n"})}),"\n",(0,s.jsxs)(n.p,{children:["We can do any validation in the command handler before storing the event, for our\nexample, we'll just save the received data in the ",(0,s.jsx)(n.code,{children:"PostCreated"})," event."]}),"\n",(0,s.jsx)(n.h3,{id:"4-first-entity",children:"4. First entity"}),"\n",(0,s.jsxs)(n.p,{children:["So far, our ",(0,s.jsx)(n.code,{children:"PostCreated"})," event suggests we need a ",(0,s.jsx)(n.code,{children:"Post"})," entity. Entities are a\nrepresentation of our system internal state. They are in charge of reducing (combining) all the events\nwith the same ",(0,s.jsx)(n.code,{children:"entityID"}),". Let's generate our ",(0,s.jsx)(n.code,{children:"Post"})," entity:"]}),"\n",(0,s.jsx)(i.Z,{children:(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-bash",children:"boost new:entity Post --fields title:string content:string author:string --reduces PostCreated\n"})})}),"\n",(0,s.jsxs)(n.p,{children:["You should see now a new file called ",(0,s.jsx)(n.code,{children:"post.ts"})," in the ",(0,s.jsx)(n.code,{children:"src/entities"})," directory."]}),"\n",(0,s.jsxs)(n.p,{children:["This time, besides using the ",(0,s.jsx)(n.code,{children:"--fields"})," flag, we use the ",(0,s.jsx)(n.code,{children:"--reduces"})," flag to specify the events the entity will reduce and, this way, produce the Post current state. The generator will create one ",(0,s.jsx)(n.em,{children:"reducer function"})," for each event we have specified (only one in this case)."]}),"\n",(0,s.jsxs)(n.p,{children:["Reducer functions in Booster work similarly to the ",(0,s.jsx)(n.code,{children:"reduce"})," callbacks in Javascript: they receive an event\nand the current state of the entity, and returns the next version of the same entity.\nIn this case, when we receive a ",(0,s.jsx)(n.code,{children:"PostCreated"})," event, we can just return a new ",(0,s.jsx)(n.code,{children:"Post"})," entity copying the fields\nfrom the event. There is no previous state of the Post as we are creating it for the first time:"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"// src/entities/post.ts\n@Entity\nexport class Post {\n  public constructor(public id: UUID, readonly title: string, readonly content: string, readonly author: string) {}\n\n  @Reduces(PostCreated)\n  public static reducePostCreated(event: PostCreated, currentPost?: Post): Post {\n    return new Post(event.postId, event.title, event.content, event.author)\n  }\n}\n"})}),"\n",(0,s.jsx)(n.p,{children:"Entities represent our domain model and can be queried from command or\nevent handlers to make business decisions or enforcing business rules."}),"\n",(0,s.jsx)(n.h3,{id:"5-first-read-model",children:"5. First read model"}),"\n",(0,s.jsxs)(n.p,{children:["In a real application, we rarely want to make public our entire domain model (entities)\nincluding all their fields. What is more, different users may have different views of the data depending\non their permissions or their use cases. That's the goal of ",(0,s.jsx)(n.code,{children:"ReadModels"}),". Client applications can query or\nsubscribe to them."]}),"\n",(0,s.jsxs)(n.p,{children:["Read models are ",(0,s.jsx)(n.em,{children:"projections"})," of one or more entities into a new object that is reachable through the query and subscriptions APIs. Let's generate a ",(0,s.jsx)(n.code,{children:"PostReadModel"})," that projects our\n",(0,s.jsx)(n.code,{children:"Post"})," entity:"]}),"\n",(0,s.jsx)(i.Z,{children:(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-bash",children:"boost new:read-model PostReadModel --fields title:string author:string --projects Post:id\n"})})}),"\n",(0,s.jsxs)(n.p,{children:["We have used a new flag, ",(0,s.jsx)(n.code,{children:"--projects"}),", that allow us to specify the entities (can be many) the read model will\nwatch for changes. You might be wondering what is the ",(0,s.jsx)(n.code,{children:":id"})," after the entity name. That's the ",(0,s.jsx)(n.a,{href:"/architecture/read-model#the-projection-function",children:"joinKey"}),",\nbut you can forget about it now."]}),"\n",(0,s.jsxs)(n.p,{children:["As you might guess, the read-model generator will create a file called\n",(0,s.jsx)(n.code,{children:"post-read-model.ts"})," under ",(0,s.jsx)(n.code,{children:"src/read-models"}),":"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-text",children:"boosted-blog\n\u2514\u2500\u2500 src\n    \u2514\u2500\u2500 read-models\n        \u2514\u2500\u2500 post-read-model.ts\n"})}),"\n",(0,s.jsx)(n.p,{children:"There are two things to do when creating a read model:"}),"\n",(0,s.jsxs)(n.ol,{children:["\n",(0,s.jsx)(n.li,{children:"Define who is authorized to query or subscribe it"}),"\n",(0,s.jsx)(n.li,{children:"Add the logic of the projection functions, where you can filter, combine, etc., the entities fields."}),"\n"]}),"\n",(0,s.jsxs)(n.p,{children:["While commands define the input to our system, read models define the output, and together they compound\nthe public API of a Booster application. Let's do the same we did in the command and authorize ",(0,s.jsx)(n.code,{children:"all"})," to\nquery/subscribe the ",(0,s.jsx)(n.code,{children:"PostReadModel"}),". Also, and for learning purposes, we will exclude the ",(0,s.jsx)(n.code,{children:"content"})," field\nfrom the ",(0,s.jsx)(n.code,{children:"Post"})," entity, so it won't be returned when users request the read model."]}),"\n",(0,s.jsxs)(n.p,{children:["Edit the ",(0,s.jsx)(n.code,{children:"post-read-model.ts"})," file to look like this:"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-typescript",children:"// src/read-models/post-read-model.ts\n@ReadModel({\n  authorize: 'all', // Specify authorized roles here. Use 'all' to authorize anyone\n})\nexport class PostReadModel {\n  public constructor(public id: UUID, readonly title: string, readonly author: string) {}\n\n  @Projects(Post, 'id')\n  public static projectPost(entity: Post, currentPostReadModel?: PostReadModel): ProjectionResult<PostReadModel> {\n    return new PostReadModel(entity.id, entity.title, entity.author)\n  }\n}\n"})}),"\n",(0,s.jsx)(n.h3,{id:"6-deployment",children:"6. Deployment"}),"\n",(0,s.jsx)(n.p,{children:"At this point, we've:"}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"Created a publicly accessible command"}),"\n",(0,s.jsx)(n.li,{children:"Emitted an event as a mechanism to store data"}),"\n",(0,s.jsx)(n.li,{children:"Reduced the event into an entity to have a representation of our internal state"}),"\n",(0,s.jsx)(n.li,{children:"Projected the entity into a read model that is also publicly accessible."}),"\n"]}),"\n",(0,s.jsx)(n.p,{children:"With this, you already know the basics to build event-driven, CQRS-based applications\nwith Booster."}),"\n",(0,s.jsx)(n.p,{children:"You can check that code compiles correctly by running the build command:"}),"\n",(0,s.jsx)(i.Z,{children:(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-bash",children:"boost build\n"})})}),"\n",(0,s.jsx)(n.p,{children:"You can also clean the compiled code by running:"}),"\n",(0,s.jsx)(i.Z,{children:(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-bash",children:"boost clean\n"})})}),"\n",(0,s.jsx)(n.h4,{id:"61-running-your-application-locally",children:"6.1 Running your application locally"}),"\n",(0,s.jsx)(n.p,{children:"Now, let's run our application to see it working. It is as simple as running:"}),"\n",(0,s.jsx)(i.Z,{children:(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-bash",children:"boost start -e local\n"})})}),"\n",(0,s.jsxs)(n.p,{children:["This will execute a local ",(0,s.jsx)(n.code,{children:"Express.js"})," server and will try to expose it in port ",(0,s.jsx)(n.code,{children:"3000"}),". You can change the port by using the ",(0,s.jsx)(n.code,{children:"-p"})," option:"]}),"\n",(0,s.jsx)(i.Z,{children:(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-bash",children:"boost start -e local -p 8080\n"})})}),"\n",(0,s.jsx)(n.h4,{id:"62-deploying-to-the-cloud",children:"6.2 Deploying to the cloud"}),"\n",(0,s.jsx)(n.p,{children:"Also, we can deploy our application to the cloud with no additional changes by running\nthe deploy command:"}),"\n",(0,s.jsx)(i.Z,{children:(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-bash",children:"boost deploy -e production\n"})})}),"\n",(0,s.jsxs)(n.p,{children:["This is the Booster magic! \u2728 When running the start or the deploy commands, Booster will handle the creation of all the resources, ",(0,s.jsx)(n.em,{children:"like Lambdas, API Gateway,"}),' and the "glue" between them; ',(0,s.jsx)(n.em,{children:"permissions, events, triggers, etc."})," It even creates a fully functional GraphQL API!"]}),"\n",(0,s.jsx)(n.admonition,{type:"note",children:(0,s.jsx)(n.p,{children:"Deploy command automatically builds the project for you before performing updates in the cloud provider, so, build command it's not required beforehand."})}),"\n",(0,s.jsxs)(n.blockquote,{children:["\n",(0,s.jsxs)(n.p,{children:["With ",(0,s.jsx)(n.code,{children:"-e production"})," we are specifying which environment we want to deploy. We'll talk about them later."]}),"\n"]}),"\n",(0,s.jsx)(n.admonition,{type:"tip",children:(0,s.jsx)(n.p,{children:"If at this point you still don\u2019t believe everything is done, feel free to check in your provider\u2019s console. You should see, as in the AWS example below, that the stack and all the services are up and running! It will be the same for other providers. \ud83d\ude80"})}),"\n",(0,s.jsx)(n.p,{children:(0,s.jsx)(n.img,{alt:"resources",src:t(2822).Z+"",width:"2726",height:"1276"})}),"\n",(0,s.jsxs)(n.p,{children:["When deploying, it will take a couple of minutes to deploy all the resources. Once finished, you will see\ninformation about your application endpoints and other outputs. For this example, we will\nonly need to pick the output ending in ",(0,s.jsx)(n.code,{children:"httpURL"}),", e.g.:"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-text",children:"https://<some random name>.execute-api.us-east-1.amazonaws.com/production\n"})}),"\n",(0,s.jsx)(n.admonition,{type:"note",children:(0,s.jsxs)(n.p,{children:["By default, the full error stack trace is send to a local file, ",(0,s.jsx)(n.code,{children:"./errors.log"}),". To see the full error stack trace directly from the console, use the ",(0,s.jsx)(n.code,{children:"--verbose"})," flag."]})}),"\n",(0,s.jsx)(n.h3,{id:"7-testing",children:"7. Testing"}),"\n",(0,s.jsx)(n.p,{children:"Let's get started testing the project. We will perform three actions:"}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"Add a couple of posts"}),"\n",(0,s.jsx)(n.li,{children:"Retrieve all posts"}),"\n",(0,s.jsx)(n.li,{children:"Retrieve a specific post"}),"\n"]}),"\n",(0,s.jsxs)(n.p,{children:["Booster applications provide you with a GraphQL API out of the box. You send commands using\n",(0,s.jsx)(n.em,{children:"mutations"})," and get read models data using ",(0,s.jsx)(n.em,{children:"queries"})," or ",(0,s.jsx)(n.em,{children:"subscriptions"}),"."]}),"\n",(0,s.jsxs)(n.p,{children:["In this section, we will be sending requests by hand using the free ",(0,s.jsx)(n.a,{href:"https://altair.sirmuel.design/",children:"Altair"})," GraphQL client,\nwhich is very simple and straightforward for this guide. However, you can use any client you want. Your endpoint URL should look like this:"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-text",children:"<httpURL>/graphql\n"})}),"\n",(0,s.jsx)(n.h4,{id:"71-creating-posts",children:"7.1 Creating posts"}),"\n",(0,s.jsxs)(n.p,{children:["Let's use two mutations to send two ",(0,s.jsx)(n.code,{children:"CreatePost"})," commands."]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-graphql",children:'mutation {\n  CreatePost(\n    input: {\n      postId: "95ddb544-4a60-439f-a0e4-c57e806f2f6e"\n      title: "Build a blog in 10 minutes with Booster"\n      content: "I am so excited to write my first post"\n      author: "Boosted developer"\n    }\n  )\n}\n'})}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-graphql",children:'mutation {\n  CreatePost(\n    input: {\n      postId: "05670e55-fd31-490e-b585-3a0096db0412"\n      title: "Booster framework rocks"\n      content: "I am so excited for writing the second post"\n      author: "Another boosted developer"\n    }\n  )\n}\n'})}),"\n",(0,s.jsx)(n.p,{children:"The expected response for each of those requests should be:"}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-json",children:'{\n  "data": {\n    "CreatePost": true\n  }\n}\n'})}),"\n",(0,s.jsx)(n.admonition,{type:"note",children:(0,s.jsxs)(n.p,{children:["In this example, the IDs are generated on the client-side. When running production applications consider adding validation for ID uniqueness. For this example, we have used ",(0,s.jsx)(n.a,{href:"https://www.uuidgenerator.net/version4",children:"a UUID generator"})]})}),"\n",(0,s.jsx)(n.h4,{id:"72-retrieving-all-posts",children:"7.2 Retrieving all posts"}),"\n",(0,s.jsxs)(n.p,{children:["Let's perform a GraphQL ",(0,s.jsx)(n.code,{children:"query"})," that will be hitting our ",(0,s.jsx)(n.code,{children:"PostReadModel"}),":"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-graphql",children:"query {\n  PostReadModels {\n    id\n    title\n    author\n  }\n}\n"})}),"\n",(0,s.jsx)(n.p,{children:"It should respond with something like:"}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-json",children:'{\n  "data": {\n    "PostReadModels": [\n      {\n        "id": "05670e55-fd31-490e-b585-3a0096db0412",\n        "title": "Booster framework rocks",\n        "author": "Another boosted developer"\n      },\n      {\n        "id": "95ddb544-4a60-439f-a0e4-c57e806f2f6e",\n        "title": "Build a blog in 10 minutes with Booster",\n        "author": "Boosted developer"\n      }\n    ]\n  }\n}\n'})}),"\n",(0,s.jsx)(n.h4,{id:"73-retrieving-specific-post",children:"7.3 Retrieving specific post"}),"\n",(0,s.jsxs)(n.p,{children:["It is also possible to retrieve specific a ",(0,s.jsx)(n.code,{children:"Post"})," by adding the ",(0,s.jsx)(n.code,{children:"id"})," as input, e.g.:"]}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-graphql",children:'query {\n  PostReadModel(id: "95ddb544-4a60-439f-a0e4-c57e806f2f6e") {\n    id\n    title\n    author\n  }\n}\n'})}),"\n",(0,s.jsx)(n.p,{children:"You should get a response similar to this:"}),"\n",(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-json",children:'{\n  "data": {\n    "PostReadModel": {\n      "id": "95ddb544-4a60-439f-a0e4-c57e806f2f6e",\n      "title": "Build a blog in 10 minutes with Booster",\n      "author": "Boosted developer"\n    }\n  }\n}\n'})}),"\n",(0,s.jsx)(n.h3,{id:"8-removing-the-stack",children:"8. Removing the stack"}),"\n",(0,s.jsxs)(n.p,{children:["It is convenient to destroy all the infrastructure created after you stop using\nit to avoid generating cloud resource costs. Execute the following command from\nthe root of the project. For safety reasons, you have to confirm this action by\nwriting the project's name, in our case ",(0,s.jsx)(n.code,{children:"boosted-blog"})," that is the same used when\nwe run ",(0,s.jsx)(n.code,{children:"new:project"})," CLI command."]}),"\n",(0,s.jsx)(i.Z,{children:(0,s.jsx)(n.pre,{children:(0,s.jsx)(n.code,{className:"language-bash",children:"> boost nuke -e production\n\n? Please, enter the app name to confirm deletion of all resources: boosted-blog\n"})})}),"\n",(0,s.jsxs)(n.blockquote,{children:["\n",(0,s.jsx)(n.p,{children:"Congratulations! You've built a serverless backend in less than 10 minutes. We hope you have enjoyed discovering the magic of the Booster Framework."}),"\n"]}),"\n",(0,s.jsx)(n.h3,{id:"9-more-functionalities",children:"9. More functionalities"}),"\n",(0,s.jsx)(n.p,{children:"This is a really basic example of a Booster application. The are many other features Booster provides like:"}),"\n",(0,s.jsxs)(n.ul,{children:["\n",(0,s.jsx)(n.li,{children:"Use a more complex authorization schema for commands and read models based on user roles"}),"\n",(0,s.jsx)(n.li,{children:"Use GraphQL subscriptions to get updates in real-time"}),"\n",(0,s.jsx)(n.li,{children:"Make events trigger other events"}),"\n",(0,s.jsx)(n.li,{children:"Deploy static content"}),"\n",(0,s.jsx)(n.li,{children:"Reading entities within command handlers to apply domain-driven decisions"}),"\n",(0,s.jsx)(n.li,{children:"And much more..."}),"\n"]}),"\n",(0,s.jsx)(n.p,{children:"Continue reading to dig more. You've just scratched the surface of all the Booster\ncapabilities!"}),"\n",(0,s.jsx)(n.h2,{id:"examples-and-walkthroughs",children:"Examples and walkthroughs"}),"\n",(0,s.jsx)(n.h3,{id:"creation-of-a-question-asking-application-backend",children:"Creation of a question-asking application backend"}),"\n",(0,s.jsxs)(n.p,{children:["In the following video, you will find how to create a backend for a question-asking application from scratch. This application would allow\nusers to create questions and like them. This video goes from creating the project to incrementally deploying features in the application.\nYou can find the code both for the frontend and the backend in ",(0,s.jsx)(r.do,{children:(0,s.jsx)(n.a,{href:"https://github.com/boostercloud/examples/tree/master/askme",children:"this GitHub repo"})}),"."]}),"\n",(0,s.jsx)("div",{align:"center",children:(0,s.jsx)("iframe",{width:"560",height:"315",src:"https://www.youtube.com/embed/C4K2M-orT8k",title:"YouTube video player",frameBorder:"0",allow:"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture",allowFullScreen:!0})}),"\n",(0,s.jsx)(n.h3,{id:"all-the-guides-and-examples",children:"All the guides and examples"}),"\n",(0,s.jsxs)(n.p,{children:["Check out the ",(0,s.jsx)(r.dM,{children:(0,s.jsx)(n.a,{href:"https://github.com/boostercloud/examples",children:"example apps repository"})})," to see Booster in use."]})]})}function u(e={}){const{wrapper:n}={...(0,o.a)(),...e.components};return n?(0,s.jsx)(n,{...e,children:(0,s.jsx)(p,{...e})}):p(e)}},2735:(e,n,t)=>{t.d(n,{do:()=>a,dM:()=>l,Dh:()=>d});var s=t(7294),o=t(719),i=t(5893);const r=e=>{let{href:n,onClick:t,children:s}=e;return(0,i.jsx)("a",{href:n,target:"_blank",rel:"noopener noreferrer",onClick:e=>{t&&t()},children:s})},l=e=>{let{children:n}=e;return c(n,"YY7T3ZSZ")},a=e=>{let{children:n}=e;return c(n,"NE1EADCK")},d=e=>{let{children:n}=e;return c(n,"AXTW7ICE")};function c(e,n){const{text:t,href:l}=function(e){if(s.isValidElement(e)&&e.props.href)return{text:e.props.children,href:e.props.href};return{text:"",href:""}}(e);return(0,i.jsx)(r,{href:l,onClick:()=>o.R.startAndTrackEvent(n),children:t})}},5163:(e,n,t)=>{t.d(n,{Z:()=>i});t(7294);const s={terminalWindow:"terminalWindow_wGrl",terminalWindowHeader:"terminalWindowHeader_o9Cs",row:"row_Rn7G",buttons:"buttons_IGLB",right:"right_fWp9",terminalWindowAddressBar:"terminalWindowAddressBar_X8fO",dot:"dot_fGZE",terminalWindowMenuIcon:"terminalWindowMenuIcon_rtOE",bar:"bar_Ck8N",terminalWindowBody:"terminalWindowBody_tzdS"};var o=t(5893);function i(e){let{children:n}=e;return(0,o.jsxs)("div",{className:s.terminalWindow,children:[(0,o.jsx)("div",{className:s.terminalWindowHeader,children:(0,o.jsxs)("div",{className:s.buttons,children:[(0,o.jsx)("span",{className:s.dot,style:{background:"#f25f58"}}),(0,o.jsx)("span",{className:s.dot,style:{background:"#fbbe3c"}}),(0,o.jsx)("span",{className:s.dot,style:{background:"#58cb42"}})]})}),(0,o.jsx)("div",{className:s.terminalWindowBody,children:n})]})}},2822:(e,n,t)=>{t.d(n,{Z:()=>s});const s=t.p+"assets/images/aws-resources-e620ed48140a022aae2ca68d0c52b496.png"},1151:(e,n,t)=>{t.d(n,{Z:()=>l,a:()=>r});var s=t(7294);const o={},i=s.createContext(o);function r(e){const n=s.useContext(i);return s.useMemo((function(){return"function"==typeof e?e(n):{...n,...e}}),[n,e])}function l(e){let n;return n=e.disableParentContext?"function"==typeof e.components?e.components(o):e.components||o:r(e.components),s.createElement(i.Provider,{value:n},e.children)}}}]);