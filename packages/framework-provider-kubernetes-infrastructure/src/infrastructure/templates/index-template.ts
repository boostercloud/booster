export const indexTemplate = `"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
/* eslint-disable @typescript-eslint/no-explicit-any */
const framework_core_1 = require("@boostercloud/framework-core");
const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
require('isomorphic-fetch');
var framework_core_2 = require("@boostercloud/framework-core");
Object.defineProperty(exports, "Booster", { enumerable: true, get: function () { return framework_core_2.Booster; } });
Object.defineProperty(exports, "boosterEventDispatcher", { enumerable: true, get: function () { return framework_core_2.boosterEventDispatcher; } });
Object.defineProperty(exports, "boosterPreSignUpChecker", { enumerable: true, get: function () { return framework_core_2.boosterPreSignUpChecker; } });
Object.defineProperty(exports, "boosterServeGraphQL", { enumerable: true, get: function () { return framework_core_2.boosterServeGraphQL; } });
Object.defineProperty(exports, "boosterRequestAuthorizer", { enumerable: true, get: function () { return framework_core_2.boosterRequestAuthorizer; } });
Object.defineProperty(exports, "boosterNotifySubscribers", { enumerable: true, get: function () { return framework_core_2.boosterNotifySubscribers; } });
framework_core_1.Booster.start();
const app = express();

app.use(bodyParser.json());
app.use(cors());
app.use(function(req, res, next) {
    res.header("Access-Control-Allow-Origin", "*");
    res.header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
    next();
  });
const port = 3000;
app.post('/graphQL', async (req, res) => {
    const response = await framework_core_1.boosterServeGraphQL(req);
    res.send(response);
});
app.get('/ready', async (req, res) => {
  res.send('ok');
});
app.listen(port, () => console.log('Node App started!'));`
