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
var react_1 = require("react");
var react_modal_1 = require("react-modal");
var chat_service_1 = require("../chat-service");
var react_2 = require("react");
var ChatResponse_1 = require("../../components/BoosterChat/ChatResponse");
var BoosterChat_1 = require("../../components/BoosterChat/BoosterChat");
var analytics_client_1 = require("../../components/Analytics/analytics-client");
// see https://github.com/facebook/docusaurus/issues/7227
function CustomNavbarItem(props) {
    var _this = this;
    var _a = (0, react_2.useState)(false), isModalOpen = _a[0], setIsModalOpen = _a[1];
    var _b = (0, react_2.useState)(null), searchQuery = _b[0], setSearchQuery = _b[1];
    var _c = (0, react_2.useState)(null), response = _c[0], setResponse = _c[1];
    var _d = (0, react_2.useState)(null), loading = _d[0], setLoading = _d[1];
    var _e = (0, react_2.useState)(false), hasFinished = _e[0], setHasFinished = _e[1];
    var _f = (0, react_2.useState)(null), questionId = _f[0], setQuestionId = _f[1];
    var handleResponseUpdated = function (_questionId, question, newResponseFragment, hasFinished) {
        setResponse(function (prev) { return "".concat(prev).concat(newResponseFragment); });
        setHasFinished(hasFinished);
        setQuestionId(_questionId);
    };
    var handleSearch = function (query, controller) { return __awaiter(_this, void 0, void 0, function () {
        return __generator(this, function (_a) {
            if ((query === null || query === void 0 ? void 0 : query.trim()) === '') {
                return [2 /*return*/];
            }
            analytics_client_1.AnalyticsClient.trackEvent('UNOKECXW');
            setLoading(true);
            setResponse('');
            setSearchQuery(query);
            setHasFinished(false);
            chat_service_1.ChatService.answerBoosterQuestion(query, handleResponseUpdated, controller.signal)
                .catch(function (error) {
                setResponse(BoosterChat_1.ASK_AI_ERROR);
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
            setSearchQuery(event.target.value);
        }
    };
    var openModal = function () {
        setResponse(null);
        setIsModalOpen(true);
    };
    var closeModal = function () {
        setSearchQuery(null);
        setResponse(null);
        setLoading(null);
        setIsModalOpen(false);
    };
    (0, react_1.useEffect)(function () {
        analytics_client_1.AnalyticsClient.start();
    }, []);
    (0, react_1.useEffect)(function () {
        var abortController = new AbortController();
        handleSearch(searchQuery, abortController);
        var modalElement = document.querySelector('.modal');
        if (modalElement) {
            modalElement.addEventListener('Modal.afterClose', abortController.abort);
        }
        return function () {
            if (modalElement) {
                modalElement.removeEventListener('Modal.afterClose', abortController.abort);
            }
            abortController.abort();
        };
    }, [searchQuery]);
    (0, react_1.useEffect)(function () {
        document.body.style.overflow = isModalOpen ? 'hidden' : '';
        return function () {
            document.body.style.overflow = '';
        };
    }, [isModalOpen]);
    return (<>
      <a onClick={openModal} href="javascript:void(0)" className="navbar_custom_item--button">
        <img src={props.imageURL} alt={props.altText} className="navbar_custom_item--image"/>
      </a>
      <react_modal_1.default isOpen={isModalOpen} onRequestClose={closeModal} style={{
            overlay: {
                backgroundColor: '#656C85CC',
                zIndex: 1000,
            },
            content: {
                top: '50%',
                left: '50%',
                right: 'auto',
                bottom: 'auto',
                marginRight: '-50%',
                transform: 'translate(-50%, -50%)',
                padding: '2rem',
                borderRadius: '1rem',
                maxHeight: '60%',
                width: 'min(100vw, 600px)',
            },
        }}>
        <BoosterChat_1.AskAIBar handleKeyDown={handleKeyDown} loading={loading} isModalStyle={true} hasFinished={hasFinished} resetSearchResponse={null}/>
        <BoosterChat_1.AskAIDisclaimer />
        <ChatResponse_1.ChatResponse questionId={questionId} response={response} loading={loading} hasFinished={hasFinished}/>
      </react_modal_1.default>
    </>);
}
exports.default = CustomNavbarItem;
