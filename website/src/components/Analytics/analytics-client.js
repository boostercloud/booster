"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.AnalyticsClient = void 0;
var Fathom = require("fathom-client");
var AnalyticsClient = /** @class */ (function () {
    function AnalyticsClient() {
    }
    AnalyticsClient.start = function () {
        Fathom.load('LHRTIPFZ', { url: 'https://tl1.boosterframework.com/script.js' });
    };
    AnalyticsClient.trackEvent = function (event) {
        Fathom.trackGoal(event, 0);
    };
    AnalyticsClient.startAndTrackEvent = function (event) {
        this.start();
        this.trackEvent(event);
    };
    return AnalyticsClient;
}());
exports.AnalyticsClient = AnalyticsClient;
