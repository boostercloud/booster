"use strict";
var __awaiter = (this && this.__awaiter) || function (thisArg, _arguments, P, generator) {
    function adopt(value) { return value instanceof P ? value : new P(function (resolve) { resolve(value); }); }
    return new (P || (P = Promise))(function (resolve, reject) {
        function fulfilled(value) { try { step(generator.next(value)); } catch (e) { reject(e); } }
        function rejected(value) { try { step(generator["throw"](value)); } catch (e) { reject(e); } }
        function step(result) { result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected); }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
};
var __generator = (this && this.__generator) || function (thisArg, body) {
    var _ = { label: 0, sent: function() { if (t[0] & 1) throw t[1]; return t[1]; }, trys: [], ops: [] }, f, y, t, g;
    return g = { next: verb(0), "throw": verb(1), "return": verb(2) }, typeof Symbol === "function" && (g[Symbol.iterator] = function() { return this; }), g;
    function verb(n) { return function (v) { return step([n, v]); }; }
    function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (g && (g = 0, op[0] && (_ = 0)), _) try {
            if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
            if (y = 0, t) op = [op[0] & 2, t.value];
            switch (op[0]) {
                case 0: case 1: t = op; break;
                case 4: _.label++; return { value: op[1], done: false };
                case 5: _.label++; y = op[1]; op = [0]; continue;
                case 7: op = _.ops.pop(); _.trys.pop(); continue;
                default:
                    if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) { _ = 0; continue; }
                    if (op[0] === 3 && (!t || (op[1] > t[0] && op[1] < t[3]))) { _.label = op[1]; break; }
                    if (op[0] === 6 && _.label < t[1]) { _.label = t[1]; t = op; break; }
                    if (t && _.label < t[2]) { _.label = t[2]; _.ops.push(op); break; }
                    if (t[2]) _.ops.pop();
                    _.trys.pop(); continue;
            }
            op = body.call(thisArg, _);
        } catch (e) { op = [6, e]; y = 0; } finally { f = t = 0; }
        if (op[0] & 5) throw op[1]; return { value: op[0] ? op[1] : void 0, done: true };
    }
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.AskAIDisclaimer = exports.ResetSearchButton = exports.AskAIBar = exports.ASK_AI_ERROR = void 0;
var ChatService = require("".concat(__dirname, "/../../theme/chat-service")).ChatService;
var privateGptIcon_svg_1 = require("@site/static/img/privateGptIcon.svg");
var react_1 = require("react");
var analytics_client_1 = require("../Analytics/analytics-client");
var ChatResponse_1 = require("./ChatResponse");
exports.ASK_AI_ERROR = 'There was an unexpected error. Please try again 🙏';
function BoosterChat() {
    var _this = this;
    var _a = (0, react_1.useState)(null), response = _a[0], setResponse = _a[1];
    var _b = (0, react_1.useState)(null), loading = _b[0], setLoading = _b[1];
    var _c = (0, react_1.useState)(false), hasFinished = _c[0], setHasFinished = _c[1];
    var _d = (0, react_1.useState)(false), hasSearched = _d[0], setHasSearched = _d[1];
    var _e = (0, react_1.useState)(null), questionId = _e[0], setQuestionId = _e[1];
    (0, react_1.useEffect)(function () {
        analytics_client_1.AnalyticsClient.start();
    }, []);
    var handleResponseUpdated = function (_questionId, _question, newResponseFragment, _hasFinished) {
        setResponse(function (prev) { return "".concat(prev).concat(newResponseFragment); });
        setHasFinished(_hasFinished);
        setQuestionId(_questionId);
    };
    var handleSearch = function (query) { return __awaiter(_this, void 0, void 0, function () {
        return __generator(this, function (_a) {
            if ((query === null || query === void 0 ? void 0 : query.trim()) === '') {
                return [2 /*return*/];
            }
            analytics_client_1.AnalyticsClient.trackEvent('CEJF3EH1');
            setHasSearched(true);
            setLoading(true);
            setResponse('');
            setHasFinished(false);
            ChatService.answerBoosterQuestion(query, handleResponseUpdated)
                .catch(function (error) {
                setResponse(exports.ASK_AI_ERROR);
                analytics_client_1.AnalyticsClient.trackEvent('SFWQOOY0');
                console.error(error);
            })
                .finally(function () {
                setLoading(false);
            });
            return [2 /*return*/];
        });
    }); };
    var handleKeyDown = function (event) {
        if (!loading && event.key === 'Enter') {
            handleSearch(event.target.value);
        }
    };
    var onQuickQuestionClick = function (query) { return __awaiter(_this, void 0, void 0, function () {
        var searchInput;
        return __generator(this, function (_a) {
            searchInput = document.getElementsByClassName('bc-input')[0];
            if (searchInput) {
                searchInput.value = query;
            }
            handleSearch(query);
            return [2 /*return*/];
        });
    }); };
    var resetSearchResponse = function () {
        setResponse(null);
        setLoading(null);
        setHasFinished(false);
    };
    return (<div className="bc-layout">
      <AskAIBar handleKeyDown={handleKeyDown} loading={loading} isModalStyle={false} hasFinished={hasFinished} resetSearchResponse={resetSearchResponse}/>
      <AskAIDisclaimer />
      {!hasSearched && (<div className="bc-quick-questions-panel">
          <button className="bc-quick-question" onClick={function () { return onQuickQuestionClick('What is Booster Framework?'); }}>
            What is Booster Framework?
          </button>
          <button className="bc-quick-question" onClick={function () { return onQuickQuestionClick("Summary of Booster's components"); }}>
            Summary of Booster's components
          </button>
          <button className="bc-quick-question" onClick={function () { return onQuickQuestionClick("What's the difference between an entity and a read-model?"); }}>
            What's the difference between an entity and a read-model?
          </button>
          <button className="bc-quick-question" onClick={function () { return onQuickQuestionClick('How to know when a reducer failed?'); }}>
            How to know when a reducer failed?
          </button>
          <button className="bc-quick-question" onClick={function () { return onQuickQuestionClick('Create a read-model and subscribe to it using websockets in Bash'); }}>
            Create a read-model and subscribe to it using websockets in Bash
          </button>
        </div>)}
      <ChatResponse_1.ChatResponse questionId={questionId} response={response} loading={loading} hasFinished={hasFinished}/>
    </div>);
}
exports.default = BoosterChat;
function AskAIBar(_a) {
    var handleKeyDown = _a.handleKeyDown, loading = _a.loading, isModalStyle = _a.isModalStyle, hasFinished = _a.hasFinished, resetSearchResponse = _a.resetSearchResponse;
    var inputRef = (0, react_1.useRef)(null);
    var handleResetClick = function () {
        inputRef.current.value = '';
        resetSearchResponse();
        inputRef.current.focus();
    };
    return (<div className={isModalStyle ? 'bc-searchbar max-width-100' : 'bc-searchbar'}>
      <privateGptIcon_svg_1.default className="bc-searchbar-icon"/>
      <input placeholder="Ask PrivateGPT about Booster" className="bc-input" type="text" onKeyDown={handleKeyDown} disabled={loading} ref={inputRef}/>
      {hasFinished ? <ResetSearchButton resetSearchResponse={handleResetClick}/> : null}
    </div>);
}
exports.AskAIBar = AskAIBar;
function ResetSearchButton(_a) {
    var resetSearchResponse = _a.resetSearchResponse;
    return (<button className="bc-reset-button" onClick={resetSearchResponse}>
      <img src="/img/cancel.svg" className="bc-reset-icon" alt="Reset"/>
    </button>);
}
exports.ResetSearchButton = ResetSearchButton;
function AskAIDisclaimer() {
    return <div className="bc-beta-disclaimer"> PrivateGPT · Free beta version</div>;
}
exports.AskAIDisclaimer = AskAIDisclaimer;
