"use strict";(self.webpackChunkwebsite=self.webpackChunkwebsite||[]).push([[8946],{4380:(e,t,n)=>{n.r(t),n.d(t,{assets:()=>c,contentTitle:()=>a,default:()=>h,frontMatter:()=>i,metadata:()=>r,toc:()=>l});var s=n(5893),o=n(1151);const i={},a="Testing",r={id:"going-deeper/testing",title:"Testing",description:"Booster applications are fully tested by default. This means that you can be sure that your application will work as expected. However, you can also write your own tests to check that your application behaves as you expect. In this section, we will leave some recommendations on how to test your Booster application.",source:"@site/docs/10_going-deeper/testing.md",sourceDirName:"10_going-deeper",slug:"/going-deeper/testing",permalink:"/going-deeper/testing",draft:!1,unlisted:!1,editUrl:"https://github.com/boostercloud/booster/tree/main/website/docs/10_going-deeper/testing.md",tags:[],version:"current",lastUpdatedBy:"Nick Seagull",lastUpdatedAt:1706186680,formattedLastUpdatedAt:"Jan 25, 2024",frontMatter:{},sidebar:"docs",previous:{title:"sensor-health",permalink:"/going-deeper/health/sensor-health"},next:{title:"Migrations",permalink:"/going-deeper/data-migrations"}},c={},l=[{value:"Testing Booster applications",id:"testing-booster-applications",level:2},{value:"Testing with <code>sinon-chai</code>",id:"testing-with-sinon-chai",level:3},{value:"Recommended files",id:"recommended-files",level:3},{value:"Framework integration tests",id:"framework-integration-tests",level:2}];function d(e){const t={a:"a",code:"code",h1:"h1",h2:"h2",h3:"h3",li:"li",p:"p",pre:"pre",ul:"ul",...(0,o.a)(),...e.components};return(0,s.jsxs)(s.Fragment,{children:[(0,s.jsx)(t.h1,{id:"testing",children:"Testing"}),"\n",(0,s.jsx)(t.p,{children:"Booster applications are fully tested by default. This means that you can be sure that your application will work as expected. However, you can also write your own tests to check that your application behaves as you expect. In this section, we will leave some recommendations on how to test your Booster application."}),"\n",(0,s.jsx)(t.h2,{id:"testing-booster-applications",children:"Testing Booster applications"}),"\n",(0,s.jsxs)(t.p,{children:["To properly test a Booster application, you should create a ",(0,s.jsx)(t.code,{children:"test"})," folder at the same level as the ",(0,s.jsx)(t.code,{children:"src"})," one. Apart from that, tests' names should have the ",(0,s.jsx)(t.code,{children:"<my_test>.test.ts"})," format."]}),"\n",(0,s.jsxs)(t.p,{children:["When a Booster application is generated, you will have a script in a ",(0,s.jsx)(t.code,{children:"package.json"})," like this:"]}),"\n",(0,s.jsx)(t.pre,{children:(0,s.jsx)(t.code,{className:"language-typescript",children:'"scripts": {\n  "test": "nyc --extension .ts mocha --forbid-only \\"test/**/*.test.ts\\""\n}\n'})}),"\n",(0,s.jsxs)(t.p,{children:["The only thing that you should add to this line are the ",(0,s.jsx)(t.code,{children:"AWS_SDK_LOAD_CONFIG=true"})," and ",(0,s.jsx)(t.code,{children:"BOOSTER_ENV=test"})," environment variables, so the script will look like this:"]}),"\n",(0,s.jsx)(t.pre,{children:(0,s.jsx)(t.code,{className:"language-typescript",children:'"scripts": {\n  "test": "AWS_SDK_LOAD_CONFIG=true BOOSTER_ENV=test nyc --extension .ts mocha --forbid-only \\"test/**/*.test.ts\\""\n}\n'})}),"\n",(0,s.jsxs)(t.h3,{id:"testing-with-sinon-chai",children:["Testing with ",(0,s.jsx)(t.code,{children:"sinon-chai"})]}),"\n",(0,s.jsxs)(t.p,{children:["The ",(0,s.jsx)(t.code,{children:"BoosterConfig"})," can be accessed through the ",(0,s.jsx)(t.code,{children:"Booster.config"})," on any part of a Booster application. To properly mock it for your objective, we really recommend to use sinon ",(0,s.jsx)(t.code,{children:"replace"})," method, after configuring your ",(0,s.jsx)(t.code,{children:"Booster.config"})," as desired."]}),"\n",(0,s.jsxs)(t.p,{children:['In the example below, we add 2 "empty" read-models, since we are iterating ',(0,s.jsx)(t.code,{children:"Booster.config.readModels"})," from a command handler:"]}),"\n",(0,s.jsx)(t.pre,{children:(0,s.jsx)(t.code,{className:"language-typescript",children:"// Test\nimport { replace } from 'sinon'\n\nconst config = new BoosterConfig('test')\nconfig.appName = 'testing-time'\nconfig.providerPackage = '@boostercloud/framework-provider-aws'\nconfig.readModels['WoW'] = {} as ReadModelMetadata\nconfig.readModels['Amazing'] = {} as ReadModelMetadata\nreplace(Booster, 'config', config)\n\nconst spyMyCall = spy(MyCommand, 'myCall')\nconst command = new MyCommand('1', true)\nconst register = new Register('request-id-1')\nconst registerSpy = spy(register, 'events')\nawait MyCommand.handle(command, register)\n\nexpect(spyMyCall).to.have.been.calledOnceWithExactly('WoW')\nexpect(spyMyCall).to.have.been.calledOnceWithExactly('Amazing')\nexpect(registerSpy).to.have.been.calledOnceWithExactly(new MyEvent('1', 'WoW'))\nexpect(registerSpy).to.have.been.calledOnceWithExactly(new MyEvent('1', 'Amazing'))\n"})}),"\n",(0,s.jsx)(t.pre,{children:(0,s.jsx)(t.code,{className:"language-typescript",children:"// Example code\npublic static async handle(command: MyCommand, register: Register): Promise<void> {\n  const readModels = Booster.config.readModels\n  for (const readModelName in readModels) {\n    myCall(readModelName)\n    register.events(new MyEvent(command.ID, readModelName))\n  }\n}\n"})}),"\n",(0,s.jsx)(t.h3,{id:"recommended-files",children:"Recommended files"}),"\n",(0,s.jsx)(t.p,{children:"These are some files that might help you speed up your testing with Booster."}),"\n",(0,s.jsx)(t.pre,{children:(0,s.jsx)(t.code,{className:"language-typescript",children:"// <root_dir>/test/expect.ts\nimport * as chai from 'chai'\n\nchai.use(require('sinon-chai'))\nchai.use(require('chai-as-promised'))\n\nexport const expect = chai.expect\n"})}),"\n",(0,s.jsxs)(t.p,{children:["This ",(0,s.jsx)(t.code,{children:"expect"})," method will help you with some more additional methods like ",(0,s.jsx)(t.code,{children:"expect(<my_stub>).to.have.been.calledOnceWithExactly(<my_params..>)"})]}),"\n",(0,s.jsx)(t.pre,{children:(0,s.jsx)(t.code,{className:"language-yaml",children:"# <root_dir>/.mocharc.yml\ndiff: true\nrequire: 'ts-node/register'\nextension:\n  - ts\npackage: './package.json'\nrecursive: true\nreporter: 'spec'\ntimeout: 5000\nfull-trace: true\nbail: true\n"})}),"\n",(0,s.jsx)(t.h2,{id:"framework-integration-tests",children:"Framework integration tests"}),"\n",(0,s.jsxs)(t.p,{children:["Booster framework integration tests package is used to test the Booster project itself, but it is also an example of how a Booster application could be tested. We encourage developers to have a look at our ",(0,s.jsx)(t.a,{href:"https://github.com/boostercloud/booster/tree/main/packages/framework-integration-tests",children:"Booster project repository"}),"."]}),"\n",(0,s.jsx)(t.p,{children:"Some integration tests highly depend on the provider chosen for the project, and the infrastructure is normally deployed in the cloud right before the tests run. Once tests are completed, the application is teared down."}),"\n",(0,s.jsx)(t.p,{children:"There are several types of integration tests in this package:"}),"\n",(0,s.jsxs)(t.ul,{children:["\n",(0,s.jsx)(t.li,{children:"Tests to ensure that different packages integrate as expected with each other."}),"\n",(0,s.jsx)(t.li,{children:"Tests to ensure that a Booster application behaves as expected when it is hit by a client (a GraphQL client)."}),"\n",(0,s.jsx)(t.li,{children:"Tests to ensure that the application behaves in the same way no matter what provider is selected."}),"\n"]})]})}function h(e={}){const{wrapper:t}={...(0,o.a)(),...e.components};return t?(0,s.jsx)(t,{...e,children:(0,s.jsx)(d,{...e})}):d(e)}},1151:(e,t,n)=>{n.d(t,{Z:()=>r,a:()=>a});var s=n(7294);const o={},i=s.createContext(o);function a(e){const t=s.useContext(i);return s.useMemo((function(){return"function"==typeof e?e(t):{...t,...e}}),[t,e])}function r(e){let t;return t=e.disableParentContext?"function"==typeof e.components?e.components(o):e.components||o:a(e.components),s.createElement(i.Provider,{value:t},e.children)}}}]);