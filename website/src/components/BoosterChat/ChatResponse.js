"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.ChatResponse = void 0;
var react_1 = require("react");
var react_markdown_1 = require("react-markdown");
var analytics_client_1 = require("../Analytics/analytics-client");
var ThumbsComponent_1 = require("./ThumbsComponent");
var ChatResponse = function (_a) {
    var questionId = _a.questionId, loading = _a.loading, response = _a.response, hasFinished = _a.hasFinished;
    var _b = (0, react_1.useState)(false), displayPopup = _b[0], setDisplayPopup = _b[1];
    (0, react_1.useEffect)(function () {
        analytics_client_1.AnalyticsClient.start();
    }, []);
    (0, react_1.useEffect)(function () {
        if (!(response === null || response === void 0 ? void 0 : response.length) || loading) {
            setDisplayPopup(false);
        }
        if ((response === null || response === void 0 ? void 0 : response.length) && !loading) {
            setTimeout(function () {
                setDisplayPopup(true);
            }, 500);
        }
    }, [response, loading]);
    if (response === null) {
        return <div></div>;
    }
    if (!response.length) {
        return (<div className="bc-chat --loading">
        <span className="bc-loader"></span>
      </div>);
    }
    return (<>
      <div className={"bc-chat-embedded"}>
        <react_markdown_1.default>{response}</react_markdown_1.default>
      </div>
      {!hasFinished ? null :
            <div>
          {!questionId ? null :
                    <ThumbsComponent_1.ThumbsComponent questionId={questionId}/>}
          <div className={"bc-chat-popup"} style={{ bottom: displayPopup ? '2rem' : '-20rem' }}>
            <p>
              Not the answer you expected? This is still a beta version, and the quality of the answers may vary depending on the language, spelling, and other factors. You can check out the documentation, and remember that you can also use our
              <a href={'https://discord.com/channels/763753198388510780/1019895895325675550'} target="_blank" onClick={function () { return analytics_client_1.AnalyticsClient.trackEvent('UESXT8VI'); }}> #booster-help </a>
              Discord channel 🤗
            </p>
          </div>
        </div>}
    </>);
};
exports.ChatResponse = ChatResponse;
