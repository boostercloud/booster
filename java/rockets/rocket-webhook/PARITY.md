# rocket-webhook parity ledger

Audit of the Java port of the webhook rocket (module `rockets/rocket-webhook`) against the TypeScript rocket
`boostercloud/rocket-webhook` at `07aa8a806ff6ae5acbdb1ba06ebdc068f6650cbb` (release 2.1.0: the packages
`rocket-webhook-types`, `-core`, `-local-infrastructure` and `-azure-infrastructure`), item by item, following the
reader notes `14-rocket-webhook.md` ("Behaviour", "Data shapes", "Behaviours pinned by tests", porting hazards) and
every TypeScript source file of the rocket. Where a note and the code disagreed, the code won. Audited on 2026-11-10
on the branch `port/rocket-webhook`, created from `93d1b8865`.

The TypeScript rocket has **no tests** (`git ls-files | grep -i test` is empty in every package), so every row below is
pinned by a characterisation test derived from the source. Values that depend on JavaScript built-ins (WHATWG `URL`,
`URLSearchParams`, `String.prototype.replace`, `http.ServerResponse#setHeader`) were computed with Node 24 and copied
into the tests.

Tests are `Class#method` under `src/test/java/com/boostercloud/rocket/webhook` (nested classes are left out of the
name). Framework tests are named with their module.

**Status legend**

| Status | Meaning |
| --- | --- |
| covered | Implemented, and the named test pins it (checked by reading the test). |
| fixed `sha` | A gap this audit found: the code or the test was changed in that commit. |
| differs | Intentionally different; the reason is given and is in the README's "Differences from TypeScript". The test pins the Java behaviour. |
| elsewhere | Owned by a framework module; the owning test is named. |
| not implemented | Listed in the README's "Not implemented", with the reason. |
| n/a | Nothing to port (documentation text, TypeScript-only typing). |

## Summary

Counts are ledger rows; a row is "fixed" when any part of it was changed by this audit.

| Part | Rows | covered | fixed | differs | elsewhere | not implemented | n/a |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1. Behaviour (spec §3) | 93 | 60 | 12 | 15 | 5 | 1 | 0 |
| 2. Data shapes (spec §4) | 13 | 12 | 0 | 1 | 0 | 0 | 0 |
| 3. Behaviours pinned by tests (spec §5) | 42 | 35 | 1 | 0 | 6 | 0 | 0 |
| 4. Porting hazards (spec §6) | 21 | 13 | 3 | 4 | 1 | 0 | 0 |
| 5. TypeScript sources (file by file) | 33 | 28 | 2 | 1 | 0 | 1 | 1 |

Nothing is left missing: every gap found was fixed or is a listed difference, except Busboy's urlencoded parser
(not implemented, README).

### Commits of this audit

| Commit | Finding | Pinned by |
| --- | --- | --- |
| `979597e85` fix(framework-provider-local-infrastructure) | Node's `res.setHeader` rejects a header name that is not an HTTP token (`Header name must be a valid HTTP token ["Bad Header"]`) and values with CR, LF, other controls or characters above `U+00FF` (`Invalid character in header content ["Content-Disposition"]`), both `TypeError`s. `HttpResponse.setHeader` accepted anything and left the encoding to Jetty, so a handler header or a file name such as `名.txt` went out altered instead of failing the webhook call with 500. | `HttpResponseTest#setHeaderValidatesNamesAndValuesLikeNode` (framework), `LocalEndToEndTest#headers_node_would_reject_fail_the_request_and_latin1_values_are_sent` |
| `f60bef5f0` | `getRouteFromRequest` is `new URL(req.url, 'http://' + host + '/')`: the base must parse even for absolute URLs, so an invalid `Host` header, or an absolute or scheme-relative URL with an invalid authority, throws `TypeError: Invalid URL` (500). The Java `Helper` ignored the host and the authority. Also `http:/h/x` (the base's scheme, one slash) is the relative path `/h/x`, not host `h`. | `HelperTest#the_base_built_from_the_host_header_must_parse_as_with_new_url` (125 hosts), `HelperTest#absolute_and_scheme_relative_urls_need_a_valid_authority` (21 URLs), `HelperTest#a_missing_host_header_is_the_valid_base_http_undefined`, `HelperTest#punycode_decodes_rfc_3492_labels` |
| `77b2dad53` | `endpoint.replace(/[^a-zA-Z0-9_]/g, '_')` replaces UTF-16 units (an emoji gives `__`); the Java regex replaced code points. Two routes with one function name (`a/b`, `a_b`): TypeScript registers `app.http("a_b")` twice and the Node worker refuses the second at start-up (no webhook works); the Java package silently kept the first `function.json`. Now the synthesis fails with the worker's message. | `FunctionsTest#function_names_replace_every_utf16_unit_outside_the_name_characters_as_javascript`, `FunctionsTest#colliding_function_names_fail_the_synthesis_with_the_node_worker_message` |
| `f3708560d` | Tests only: the dispatcher's `Invalid URL` failure and a failed asynchronous result; no `unmountStack` on either infrastructure rocket; the Cosmos DB master-key token re-derived from Microsoft's documented string-to-sign. | `WebhookDispatcherTest#a_host_or_url_the_url_parser_rejects_is_a_500_invalid_url_failure`, `WebhookDispatcherTest#a_failed_asynchronous_result_is_mapped_by_its_cause_and_stores_nothing`, `AzureWebhookSynthTest#the_rocket_is_loaded_from_the_descriptor_package_and_defines_the_v4_hooks`, `LocalWebhookTest#the_local_webhook_mounts_through_the_spi_and_a_webhook_without_route_aborts_the_mount`, `AzureRuntimeContractTest#a_webhook_call_stores_its_events_in_cosmos_and_answers_the_handler_result`, `AzureRuntimeContractTest#multipart_uploads_reach_the_handler_byte_for_byte` |
| `a388c743f` | Tests only, on the real local server: restarts over the same project directory, concurrent calls on one entity, `HEAD`, the `express.raw()` limit, an odd `Host`, header validation, the rockets health indicator. | the `LocalEndToEndTest` rows of section 6 |
| `dd1047109` | Tests only: a webhook named `graphql` answers `GET`; `POST /graphql` stays GraphQL. | `LocalEndToEndTest#a_webhook_named_graphql_is_shadowed_for_post_and_answers_get` |

---

## 1. Behaviour (spec §3)

### 3.1 The generic rocket mechanism

| Item | Java | Test | Status |
| --- | --- | --- | --- |
| 3.1.1 `registerRocketFunction` / `getRegisteredRocketFunction`, per-config map, duplicate id message with the registered function's name (`""` for the webhook's anonymous function) | `BoosterConfig.registerRocketFunction` | `ConfigTest#registersAndFindsRocketFunctions` (framework-types); `BoosterWebhookTest#a_second_webhook_on_the_same_configuration_fails_with_the_typescript_message` | elsewhere / covered |
| 3.1.2 `boosterRocketDispatcher(raw)` → `BoosterRocketDispatcher.dispatch`; `Attempt to execute a rocket function but the ID is missing.`; `Rocket function with ID "x" not found. Did you forget registering the function with "config.registerRocketFunction()"?`; raw request passed unchanged | `BoosterRocketDispatcher` | `BoosterRocketDispatcherTest#callsTheRegisteredFunctionWithTheRawRequest`, `#failsWithoutAnId`, `#failsForUnknownIds` (framework-core); `AzureRuntimeContractTest#without_the_rocket_id_app_setting_the_dispatcher_fails_and_the_functions_host_answers_500` | elsewhere / covered |
| 3.1.3 rocket id: local request property wins over the env var (`??`); Azure env var only | provider rocket adapters | `SmallAdaptersTest#theRequestPropertyWinsOverTheEnvironment` (framework-provider-local), `ScheduledRocketAndSearcherAdapterTest#theRocketIdComesFromTheFunctionAppSetting` (framework-provider-azure); `WebhookControllerTest#post_and_get_reach_the_dispatcher_with_the_rocket_id_and_the_request` | elsewhere / covered |
| 3.1.4 provider created lazily with `config.rockets`; infrastructure rockets loaded eagerly by `Infrastructure(rockets)` | framework | `RocketMountingTest` (framework-provider-local-infrastructure), `InfrastructureTest` (framework-provider-azure-infrastructure) | elsewhere |
| 3.1.5 `RocketLoader.loadRocket`: the three messages, builder called with `parameters` by reference | `RocketLoader` (`<packageName>.Rocket`) | `RocketLoaderTest` (5 tests, framework-common-helpers); `LocalWebhookTest#the_rocket_loader_builds_the_local_webhook_from_the_descriptor_package`, `AzureWebhookSynthTest#the_rocket_is_loaded_from_the_descriptor_package_and_defines_the_v4_hooks` | elsewhere / covered |
| 3.1.6 local start order: rockets mounted on the router before the parsers, the router used after JSON (6 MB, `rawBody`), urlencoded, CORS, CORS headers | `LocalServer` | `LocalEndToEndTest#json_bodies_come_parsed_with_their_raw_bytes`, `#forms_are_parsed_octet_streams_are_bytes_and_other_bodies_are_not_read`, `#preflights_are_answered_by_cors_and_graphql_keeps_working_next_to_the_webhooks` | covered |
| 3.1.7 Azure: rockets synthesised after the base stack; `ROCKET_FUNCTION_APP_NAMES` = `<rg>webhookfunc`, `ROCKET_PACKAGE_MAPPING` empty for the webhook; rocket zip `rocket_<app>.zip`; no `unmountStack` | `ApplicationBuilder`, `AzureWebhook` | `AzureWebhookSynthTest#synthesizes_the_function_app_and_the_gateway_rules_of_every_webhook`, `#packages_one_http_function_per_webhook_bound_to_the_rocket_http_entry_point`, `#the_rocket_is_loaded_from_the_descriptor_package_and_defines_the_v4_hooks` | covered (no `unmountStack`: fixed `f3708560d`) |
| 3.1.7 zip contents: `.deploy/**` + `functions.js` + default `host.json` + generated `package.json` | the Java package: application jar, libraries, one `function.json` per webhook, default `host.json` | golden `webhook-rocket-package.txt` | differs: Java worker (no `functions.js`, no `package.json`) |
| 3.1.8 an app whose only rocket is the webhook reports `UNKNOWN` / `No Rockets found` | `HealthAdapter.areRocketFunctionsUp` keys on `rocketProviderPackage` | `LocalEndToEndTest#the_webhook_rocket_has_no_entry_in_the_rockets_health_indicator`, `RocketsHealthIndicatorTest` (framework-core) | fixed `a388c743f` (was not pinned for the webhook) |

### 3.2 `BoosterWebhook`

| Item | Test | Status |
| --- | --- | --- |
| constructing registers `rocket-webhook`; no validation of `params` | `BoosterWebhookTest#registers_the_rocket_function_under_rocket_webhook`, `#parameters_are_not_validated_when_the_rocket_is_created` | covered |
| a second `BoosterWebhook` on the same config throws `... registered under the same ID, ""` | `BoosterWebhookTest#a_second_webhook_on_the_same_configuration_fails_with_the_typescript_message` | covered |
| the function uses the dispatcher's `config`; `params` captured by reference (later changes visible) | `BoosterWebhookTest#the_registered_function_uses_the_config_it_is_called_with_and_sees_later_changes_of_the_params` | covered |
| `rocketForAzure()` / `rocketForLocal()` pure, same `params` instance | `BoosterWebhookTest#the_descriptors_name_the_java_infrastructure_packages_and_share_the_params_instance` | differs: package names are the Java packages `com.boostercloud.rocket.webhook.local` / `.azure` |

### 3.3 `getRoute`

| Item | Test | Status |
| --- | --- | --- |
| `route ?? origin`; `route: ''` does not fall back and throws; `Undefined route parameter [object Object]`; no normalisation | `WebhookParamsTest#the_route_wins_over_the_deprecated_origin`, `#a_webhook_without_route_fails_and_an_empty_route_does_not_fall_back_to_the_origin`, `#routes_are_not_normalised` | covered |

### 3.4 `Helper.getRouteFromRequest`

| Item | Test | Status |
| --- | --- | --- |
| relative and absolute URLs; pathname only; percent-encoding kept; dot segments; `/api/` or `/` stripped once | `HelperTest#extracts_the_route` (21 Node cases) | covered |
| missing `Host` → base `http://undefined/` | `HelperTest#a_missing_host_header_is_the_valid_base_http_undefined`, `LocalEndToEndTest#a_host_header_jetty_accepts_but_the_url_parser_rejects_is_a_500_invalid_url` (HTTP/1.0) | fixed `f60bef5f0` |
| an invalid `Host` makes `new URL` throw `Invalid URL`, also for absolute URLs; an absolute or `//` URL with an invalid authority throws too | `HelperTest#the_base_built_from_the_host_header_must_parse_as_with_new_url`, `#absolute_and_scheme_relative_urls_need_a_valid_authority`, `WebhookDispatcherTest#a_host_or_url_the_url_parser_rejects_is_a_500_invalid_url_failure` | fixed `f60bef5f0` (title `Illegal Argument Exception`: differs, §7) |
| `http:/h/x` (base scheme, one slash) is a relative path | `HelperTest#absolute_and_scheme_relative_urls_need_a_valid_authority` | fixed `f60bef5f0` |
| non-ASCII host names go through UTS 46 | `HelperTest` (ASCII, `xn--`, `café`, `名.com`, `ä`, soft hyphen, `U+FFFD` cases) | differs: approximated with Punycode + `java.net.IDN` (joiner/bidi rules, `ß`/`ς`, non-ASCII labels over 63 ASCII characters) |
| locally, Jetty answers `400 Bad HostPort` / `Blank Host` / `No Host` / `Authority!=Host` itself for most invalid hosts and for absolute-form targets with another authority (Express passes them) | the local server (Jetty) | differs (transport of framework-provider-local-infrastructure; probed by hand, see section 7) |

### 3.5 `dispatch`

| Step | Test | Status |
| --- | --- | --- |
| 1 log `Webhook: request received` | `WebhookDispatcherTest#an_unknown_route_is_a_500_failure_and_logs_the_request` | differs: Booster logger at info (`[Booster]\|WebhookDispatcher#dispatch: ...`) instead of `console.log` |
| 2 fresh request id per call, no incoming header honoured | `WebhookDispatcherTest#every_request_gets_a_new_request_id` | covered |
| 3 route lookup: exact, case-sensitive, first match; a route-less entry before the match fails later routes; no match → 500 | `WebhookDispatcherTest#routes_match_exactly_and_case_sensitively`, `#the_first_of_two_webhooks_with_the_same_route_wins`, `#a_webhook_without_route_before_the_match_fails_every_later_route`, `#an_unknown_route_is_a_500_failure_and_logs_the_request` | covered |
| 4 `allowedMethods ?? [POST]`, `[]` rejects all, case-sensitive compare, returned (not thrown) 500 `Method X not allowed` | `WebhookDispatcherTest#only_post_is_allowed_by_default_and_the_handler_is_not_called_for_get`, `#an_empty_list_rejects_every_method_and_methods_are_case_sensitive` | covered |
| 5 token verified whenever `authorization` is present, even without `authorize` | `WebhookDispatcherTest#a_token_is_verified_also_on_public_webhooks`, `LocalEndToEndTest#tokens_are_verified_on_public_webhooks_too` | covered |
| 6 no `authorize` → public | `WebhookDispatcherTest#without_token_nor_authorize_the_webhook_is_public_and_anonymous` | covered |
| 7 event conversion (with multipart) after authorization | `WebhookDispatcherTest#authorization_runs_before_the_body_is_parsed` | covered |
| 8 `new Register(requestId, {}, flush, user)`; response headers never copied | `WebhookDispatcherTest#are_stored_after_the_handler_returns_with_the_request_id_and_the_user` | covered |
| 9 `handle` is static, class never instantiated | `WebhookDispatcherTest#a_handler_class_without_handle_or_without_class_is_a_failure` | covered |
| 10 events stored after the handler returns; discarded when it throws (also asynchronously); a storage failure (404) fails the call | `WebhookDispatcherTest#are_stored_after_the_handler_returns_with_the_request_id_and_the_user`, `#are_discarded_when_the_handler_fails`, `#a_failed_asynchronous_result_is_mapped_by_its_cause_and_stores_nothing`, `#a_storage_failure_fails_the_request_although_the_handler_succeeded`, `LocalEndToEndTest#invalid_input_is_a_400_and_events_of_a_failing_handler_are_not_stored` | covered (async: fixed `f3708560d`) |
| 11 truthy result → success with it; falsy → empty success | `WebhookDispatcherTest#falsy_results_are_empty_responses`, `#a_void_handler_answers_an_empty_text` | covered |
| 12 any exception → failure result; dispatch never throws | `WebhookDispatcherTest#checked_exceptions_of_handlers_are_failures_too`, `#a_request_without_req_is_a_failure_not_an_exception` | covered |
| a thrown non-object (`undefined`) makes `responseFailure` throw | — | differs: Java cannot throw non-`Throwable`s; JVM `Error`s propagate |
| order: route < method < auth; unknown route + bad token → 500 | `WebhookDispatcherTest#route_and_method_errors_come_before_token_errors` | covered |

### 3.6 `WebhookTokenVerifier`

| Item | Test | Status |
| --- | --- | --- |
| lower-case `authorization`; missing or empty → anonymous; raw value with `Bearer ` to `BoosterTokenVerifier` | `WebhookTokenVerifierTest#a_request_without_token_is_anonymous`, `#a_valid_bearer_token_gives_its_user`, `WebhookDispatcherTest#an_empty_authorization_header_is_no_token` | covered |
| no verifier accepts → `NotAuthorizedError` (`''` with zero verifiers), expired → `BoosterTokenExpiredError`, 401 | `WebhookTokenVerifierTest#invalid_and_expired_tokens_are_rejected_with_the_booster_errors`, `WebhookDispatcherTest#a_token_is_verified_also_on_public_webhooks` | covered |

### 3.7 Authorization

| `authorize` | Test | Status |
| --- | --- | --- |
| absent → no check | `WebhookAuthorizerBuilderTest#the_checker_does_nothing_without_authorize_and_rebuilds_the_authorizer_on_every_call` | covered |
| `'all'` | `WebhookAuthorizerBuilderTest#all_allows_everybody`, `WebhookDispatcherTest#authorize_all_lets_anonymous_requests_in` | covered |
| roles by class simple name; `[]` denies; request ignored | `WebhookAuthorizerBuilderTest#roles_match_the_simple_class_names_of_the_token_roles`, `WebhookDispatcherTest#roles_need_a_user_with_one_of_them`, `LocalEndToEndTest#role_protected_webhooks_need_a_token_with_the_role` | covered |
| function `(user, request)`; its error decides the status | `WebhookAuthorizerBuilderTest#a_custom_function_is_used_as_is`, `WebhookDispatcherTest#a_custom_authorizer_gets_the_user_and_the_request_and_its_error_decides_the_status`, `LocalEndToEndTest#a_custom_authorizer_reads_the_request` | covered |
| any other truthy value → deny | `WebhookAuthorizerBuilderTest#anything_else_denies_access` | covered |
| authorizer rejecting with a string (`{"title":"String"}`) | — | differs: Java cannot throw strings |

### 3.8 Request → `WebhookEvent`

| Item | Test | Status |
| --- | --- | --- |
| every field copied; `route ?? origin`; `method` always `POST`; `multipart` key | `WebhookDispatcherTest#copies_the_request_into_the_event`, `#the_deprecated_origin_is_the_route_of_webhooks_without_one`, `#allowed_get_requests_reach_the_handler_with_method_post_as_in_typescript` | covered |
| multipart only when the content type matches `/multipart\/form-data/i` | `WebhookDispatcherTest#parses_multipart_bodies_from_the_stream_or_the_buffered_body` (mixed-case type) | covered |
| 3.8.1 local `req`: url, qs query, `{}` params, `rawBody` bytes for JSON only (empty bytes for an empty JSON body), body by type, `{}` otherwise | `LocalEndToEndTest#a_get_request_has_the_qs_parsed_query_and_the_post_method_as_in_typescript`, `#json_bodies_come_parsed_with_their_raw_bytes`, `#forms_are_parsed_octet_streams_are_bytes_and_other_bodies_are_not_read`, `BodyParsersTest` (framework) | covered |
| 3.8.1 Azure `req`: absolute url, sorted lower-cased headers, flat `URLSearchParams` query, multipart bytes / `JSON.parse` or text | `AzureRuntimeContractTest#the_query_comes_from_the_url_and_failures_carry_their_headers_on_azure`, `BoosterAzureFunctionsTest` (framework-provider-azure) | covered |
| headers are `string \| string[]` (Express) | `WebhookRequestTest#header_names_are_lower_cased_and_maps_never_null` | differs: single strings (Node's joining rules of the framework) |

### 3.9 `parseMultipartFormData`

| Item | Test | Status |
| --- | --- | --- |
| Busboy options = headers + `MultiPartConfig` (`defParamCharset`, `preservePath`, limits) | `ParseMultiPartTest#the_param_charset_and_preserve_path_options_reach_busboy` | covered |
| stream branch (`pipe`) and buffered branch (`bb.end(body)`, a string is UTF-8) | `ParseMultiPartTest#buffers_fields_and_files_of_a_streamed_body_in_chunks`, `#a_buffered_body_is_parsed_at_once_and_a_string_body_is_utf8` | covered |
| files fully buffered, in end order; fields in body order; field and file info defaults | `MultipartTest#produces_busboys_events` (Busboy's vectors, whole and byte by byte), `ParseMultiPartTest#buffers_fields_and_files_of_a_streamed_body_in_chunks` | covered |
| synchronous Busboy errors (`Missing Content-Type`, `Malformed content type`, `Multipart: Boundary not found`) → 500 | `ParseMultiPartTest#malformed_requests_fail_with_the_busboy_messages`, `WebhookDispatcherTest#malformed_multipart_requests_are_failures` | covered |
| asynchronous errors (`Unexpected end of form`, `Malformed part header`): no listener, uncaught in Node | same tests, `LocalEndToEndTest#files_over_the_limit_are_truncated_and_broken_forms_fail` | differs: 500 with Busboy's message |
| limits truncate files and drop parts silently | `ParseMultiPartTest#limits_truncate_files_and_drop_the_parts_over_them`, `WebhookDispatcherTest#files_over_the_size_limit_are_truncated_silently` | covered |
| content types containing `multipart/form-data` but of the urlencoded type run Busboy's urlencoded parser | `WebhookDispatcherTest#an_urlencoded_type_quoting_multipart_form_data_is_not_parsed_busboys_urlencoded_parser_is_not_ported` (500 `Unsupported content type: ...`) | not implemented (README) |

### 3.10 Responses

| Item | Test | Status |
| --- | --- | --- |
| success always 200 | `WebhookResponseTest#successes_are_200_with_the_builder_body_and_headers`, `#the_result_records_copy_their_headers_and_a_success_is_always_200` | covered |
| strategy selection; unknown type → file + `Unexpected response type. Using File` | `WebhookResponseBuilderTest#an_unknown_response_type_is_sent_as_a_file_and_logged` | covered (log through the Booster logger: differs) |
| empty / text (`?? ''`) / JSON (truthiness, `JSON.stringify`) / file (`mimeType \|\| ''`, unquoted `filename=`, `undefined` name) bodies and headers | `WebhookResponseBuilderTest#falsy_results_use_the_empty_strategy`, `#text_bodies_pass_through_and_only_null_becomes_empty`, `#json_bodies_are_stringified_and_falsy_ones_are_empty`, `#file_bodies_pass_through_with_the_attachment_headers`, `#file_names_are_neither_quoted_nor_encoded_and_missing_values_print_like_javascript` | covered |
| a result without `file` → `TypeError` → 500 | `WebhookResponseBuilderTest#a_file_result_without_file_fails_when_the_headers_are_built` | covered (title: §7) |
| header merge default < handler < type, case-sensitive keys, key order | `WebhookResponseBuilderTest#handler_headers_override_the_default_and_are_overridden_by_the_type_headers`, `#the_header_order_is_default_handler_then_type_keys` | covered |
| `DEFAULT_HEADERS` public mutable static | `WebhookResponseBuilderTest#the_default_headers_are_access_control_allow_origin_star_and_read_only` | differs: read-only |
| failure: `httpStatusCodeFor` by exact class name, `toClassTitle`, `{title, reason}`, JSON headers | `WebhookResponseTest#failures_map_the_error_class_to_status_and_title`, `#the_failure_body_serialises_as_title_then_reason` | covered |
| failure catalogue (plain `Error` → `Error` title) | `WebhookDispatcherTest` (every `assertFailure`) | differs: `Illegal State Exception` / `Illegal Argument Exception` (ARCHITECTURE §7); statuses and reasons equal |
| `null` header value of a handler result | `WebhookResponseBuilderTest#null_header_values_are_dropped_from_typed_and_raw_results` | differs: dropped (TypeScript sends `null`, and fails locally for `undefined`) |

### 3.11 `Infra.mountStack` / `LocalWebhook`

| Item | Test | Status |
| --- | --- | --- |
| one controller per webhook at `/`, in order; metadata ignored; route-less webhook aborts the mount | `LocalWebhookTest#mounts_every_webhook_at_its_route_without_prefix_and_ignores_the_metadata`, `#the_local_webhook_mounts_through_the_spi_and_a_webhook_without_route_aborts_the_mount` | covered |
| URL `http://localhost:<port>/<route>`, no `/webhook` prefix | `LocalEndToEndTest#other_methods_and_unknown_paths_get_the_express_404` (`/webhook/orders` is a 404) | covered |
| `/graphql` and `/sensor/health` mounted first; a webhook named `graphql` keeps `GET` only | `LocalEndToEndTest#a_webhook_named_graphql_is_shadowed_for_post_and_answers_get` | fixed `dd1047109` (was not pinned) |

### 3.12 `WebhookController`

| Item | Test | Status |
| --- | --- | --- |
| `POST` and `GET` always registered; other verbs Express 404 | `WebhookControllerTest#post_and_get_reach_the_dispatcher_with_the_rocket_id_and_the_request`, `LocalEndToEndTest#other_methods_and_unknown_paths_get_the_express_404` | covered |
| `HEAD` routed to `GET` → `Method HEAD not allowed`, no body | `LocalEndToEndTest#head_requests_reach_the_get_route_and_the_method_policy_answers_without_body` | fixed `a388c743f` (was not pinned end to end) |
| `OPTIONS` answered by `cors()` | `LocalEndToEndTest#preflights_are_answered_by_cors_and_graphql_keeps_working_next_to_the_webhooks` | covered |
| Express path rules (case-insensitive, trailing slash) then the dispatcher's exact match | `WebhookControllerTest#other_methods_and_paths_are_not_handled_but_express_path_rules_apply`, `LocalEndToEndTest#the_method_policy_and_the_exact_route_match_are_enforced_by_the_dispatcher` | covered |
| `express.raw()` (octet-stream, 100 kB); over the limit → `PayloadTooLargeError` reaches the server's handler → 500 | `WebhookControllerTest#octet_stream_bodies_arrive_as_bytes_through_express_raw`, `LocalEndToEndTest#octet_stream_bodies_over_the_100kb_of_express_raw_fail_before_the_webhook` | covered (limit: fixed `a388c743f`) |
| request `{ BOOSTER_ROCKET_FUNCTION_ID: 'rocket-webhook', req }` | `WebhookControllerTest#post_and_get_reach_the_dispatcher_with_the_rocket_id_and_the_request` | covered |
| success: every header set (`setHeader`, Node's validation), body through `res.send` (charset, bytes, empty `Content-Type` → octet-stream, objects as JSON) | `WebhookControllerTest#a_json_success_sends_every_header_and_the_body_as_utf8_text`, `#text_empty_and_object_bodies_go_through_res_send`, `#a_file_success_sends_the_bytes_as_they_are_with_its_headers`, `#a_file_without_mime_type_is_sent_as_octet_stream_like_express`, `LocalEndToEndTest#headers_node_would_reject_fail_the_request_and_latin1_values_are_sent` | covered (validation: fixed `979597e85`) |
| the `'Content-type'` typo branches (PassThrough for `file`, `res.json` double-encoding) | `WebhookControllerTest#a_handler_content_type_spelled_content_type_does_not_switch_to_res_json` | differs: dead in TypeScript; not ported as live code |
| a number as text body (Express's deprecated `res.send(status)`) | — | differs: sent as its JSON text |
| failure: status + `res.json(body)`, result headers not applied | `WebhookControllerTest#a_failure_sends_the_status_and_the_body_as_json_without_the_result_headers` | covered |
| dispatcher rejects → `requestFailed` + `next(e)` | `WebhookControllerTest#a_dispatcher_exception_is_answered_with_request_failed_and_passed_to_next` | covered |
| Express's weak `ETag` and `304` answers of `res.send` | — | differs (framework-provider-local-infrastructure README: not emitted) |

### 3.13 Azure functions

| Item | Test | Status |
| --- | --- | --- |
| one function app `<rg>webhookfunc`, one definition, no `hostJsonPath` | `FunctionsTest#one_definition_for_the_webhook_function_app_with_one_function_per_route_in_order`, `#no_webhooks_still_give_one_empty_function_app` | covered |
| `Webhook endpoint must be a non-empty string` | `FunctionsTest#an_empty_endpoint_and_a_webhook_without_route_fail` | covered |
| function name `replace(/[^a-zA-Z0-9_]/g, '_')` per UTF-16 unit | `FunctionsTest#function_names_replace_every_utf16_unit_outside_the_name_characters_as_javascript` | fixed `77b2dad53` |
| colliding names | `FunctionsTest#colliding_function_names_fail_the_synthesis_with_the_node_worker_message` | fixed `77b2dad53` (fails at synthesis instead of at function-app start: differs) |
| trigger `POST`+`GET`, anonymous, route verbatim, `$return` | `FunctionsTest#the_function_is_an_anonymous_get_and_post_trigger_on_the_route_bound_to_rocket_http`, golden `webhook-rocket-package.txt` | covered (`function.json` instead of `app.http`: differs, see section 8) |
| generated handler request (headers, query, multipart bytes, `JSON.parse` or text, `params`) and response mapping (object bodies JSON, strings/bytes as is, headers forwarded) | `AzureRuntimeContractTest` (5 tests), `BoosterAzureFunctionsTest` (framework-provider-azure) | covered |
| dispatcher rejection → host 500 | `AzureRuntimeContractTest#without_the_rocket_id_app_setting_the_dispatcher_fails_and_the_functions_host_answers_500` | covered |

### 3.14 Terraform

| Item | Test | Status |
| --- | --- | --- |
| `TerraformFunctionApp.build` guards in order | `AzureWebhookSynthTest#the_function_app_needs_the_database_the_storage_account_and_the_service_plan_in_that_order` | covered |
| every attribute of the function app | golden `webhook.cdk.tf.json`, `AzureWebhookSynthTest#synthesizes_the_function_app_and_the_gateway_rules_of_every_webhook`; hand walk in section 7 | covered (`java_version`, `BOOSTER_APP_CLASS`: differs) |
| `rocketStack ?? []` push lost when the bag has no list | `AzureWebhookSynthTest#without_application_gateway_only_the_function_app_is_added_and_the_stack_is_returned` | covered |
| gateway overrides replace `backend_address_pool`, `url_path_map.0.path_rule`, `rewrite_rule_set.0.rewrite_rule` (also the GraphQL/sensor rules) | golden `webhook.cdk.tf.json` | covered (as written in TypeScript; section 7) |
| rule names `toTerraformName(appPrefix, 'ru'/'rw' + uuid[0..23])`, random every synthesis | `AzureWebhookSynthTest#the_rule_names_come_from_the_app_prefix_and_23_characters_of_a_uuid`, `#the_published_rocket_uses_random_rule_names_on_every_synthesis` | covered |
| no gateway → only the function app | `AzureWebhookSynthTest#without_application_gateway_only_the_function_app_is_added_and_the_stack_is_returned` | covered |
| a route-less webhook fails the synthesis (`PromisesError`) | `AzureWebhookSynthTest#a_webhook_without_route_fails_the_synthesis` | covered |

### 3.15 Documentation vs code

| Item | Status |
| --- | --- |
| `origin` deprecated, results need `responseType`, no `/webhook` prefix, `method` always `POST`, key `multipart`, void results valid, local/Azure `mountStack` signatures | covered: the README documents the code's behaviour; each is pinned by the rows above |

## 2. Data shapes (spec §4)

| Shape | Test | Status |
| --- | --- | --- |
| 4.1 descriptor with live `parameters` | `BoosterWebhookTest#the_descriptors_name_the_java_infrastructure_packages_and_share_the_params_instance`, `WebhookParamsTest#reads_the_descriptor_parameters` | covered |
| 4.1 package names | same | differs: Java packages |
| 4.2 local rocket request | `WebhookRequestTest#reads_the_local_shape`, `WebhookControllerTest#post_and_get_reach_the_dispatcher_with_the_rocket_id_and_the_request` | covered |
| 4.2 Azure rocket request | `WebhookRequestTest#reads_the_azure_shape`, `AzureRuntimeContractTest#the_query_comes_from_the_url_and_failures_carry_their_headers_on_azure` | covered |
| 4.3 `WebhookEvent` (JSON POST) | `WebhookDispatcherTest#copies_the_request_into_the_event`, `LocalEndToEndTest#json_bodies_come_parsed_with_their_raw_bytes` | covered |
| 4.3 `multipart` fields and files | `WebhookDispatcherTest#parses_multipart_bodies_from_the_stream_or_the_buffered_body` | covered |
| 4.4 handler return values (JSON, text + header, file, nothing) | `WebhookResponseBuilderTest`, `WebhookDispatcherTest#a_raw_map_result_is_read_like_the_typescript_object_literal` | covered |
| 4.5 `WebhookAPIResult` success shapes | `WebhookResponseTest#successes_are_200_with_the_builder_body_and_headers`, `WebhookDispatcherTest#a_json_result`, `#a_void_handler_answers_an_empty_text` | covered |
| 4.5 failure shape | `WebhookResponseTest#failures_map_the_error_class_to_status_and_title` | covered |
| 4.6 wire: JSON success with `; charset=utf-8` | `LocalEndToEndTest#a_webhook_call_becomes_a_read_model_that_a_command_then_updates` | covered |
| 4.6 wire: `GET` on a POST route → 500; `/Test` → 500; `POST /unknown` → Express 404 | `LocalEndToEndTest#the_method_policy_and_the_exact_route_match_are_enforced_by_the_dispatcher`, `#other_methods_and_unknown_paths_get_the_express_404` | covered |
| 4.7 Azure artefacts (names, zip, app settings, overrides) | golden files, `AzureWebhookSynthTest#packages_one_http_function_per_webhook_bound_to_the_rocket_http_entry_point` | covered |
| 4.8 constants | `WebhookParamsTest#the_function_id_is_rocket_webhook`, `#enums_keep_the_typescript_values`, `AzureWebhookSynthTest` | covered |

## 3. Behaviours pinned by tests (spec §5)

### 5.1 Tests of the rocket repository

None (checked: `git ls-files | grep -ci test` is `0` at `07aa8a80`).

### 5.2 Framework tests of the rocket plumbing

| # | Behaviour | Java test | Status |
| --- | --- | --- | --- |
| 1-4 | `RocketLoader.loadRocket` messages and success | `RocketLoaderTest` (framework-common-helpers) | elsewhere |
| 5 | `boosterRocketDispatcher(raw)` dispatches once with the raw request | `BoosterUserAppTest#boosterRocketDispatcher`, `BoosterRocketDispatcherTest#callsTheRegisteredFunctionWithTheRawRequest` (framework-core) | elsewhere |
| 6-12 | rockets health indicator (`No Rockets found`, UP, DOWN, PARTIALLY_UP, one rocket, missing rocket) | `RocketsHealthIndicatorTest` (framework-core) | elsewhere |

### 5.3 Characterisation tests the port should add

| Item | Test | Status |
| --- | --- | --- |
| new `BoosterWebhook` registers `rocket-webhook` | `BoosterWebhookTest#registers_the_rocket_function_under_rocket_webhook` | covered |
| second `BoosterWebhook` throws the message | `BoosterWebhookTest#a_second_webhook_on_the_same_configuration_fails_with_the_typescript_message` | covered |
| descriptors: package name and same params | `BoosterWebhookTest#the_descriptors_name_the_java_infrastructure_packages_and_share_the_params_instance` | covered |
| no rocket id | `BoosterRocketDispatcherTest#failsWithoutAnId`, `AzureRuntimeContractTest#without_the_rocket_id_app_setting_the_dispatcher_fails_and_the_functions_host_answers_500` | elsewhere / covered |
| unknown rocket id | `BoosterRocketDispatcherTest#failsForUnknownIds` | elsewhere |
| local adapter precedence | `SmallAdaptersTest#theRequestPropertyWinsOverTheEnvironment` | elsewhere |
| `getRoute` route/origin/neither/`''` | `WebhookParamsTest#the_route_wins_over_the_deprecated_origin`, `#a_webhook_without_route_fails_and_an_empty_route_does_not_fall_back_to_the_origin` | covered |
| `/test?x=1`, `/api/test`, `https://h/api/clients/other?x=1` | `HelperTest#extracts_the_route` | covered |
| unconfigured path → 500 `Could not find route nope in parameters.` | `WebhookDispatcherTest#an_unknown_route_is_a_500_failure_and_logs_the_request` | covered |
| duplicate routes: first wins | `WebhookDispatcherTest#the_first_of_two_webhooks_with_the_same_route_wins` | covered |
| default methods: GET rejected, handler not called; POST called | `WebhookDispatcherTest#only_post_is_allowed_by_default_and_the_handler_is_not_called_for_get` | covered |
| `[GET, POST]` + GET → called, `method === 'POST'` | `WebhookDispatcherTest#allowed_get_requests_reach_the_handler_with_method_post_as_in_typescript` | covered |
| no token, no authorize → `currentUser` undefined | `WebhookDispatcherTest#without_token_nor_authorize_the_webhook_is_public_and_anonymous` | covered |
| bad token on a public route → 401, handler not called | `WebhookDispatcherTest#a_token_is_verified_also_on_public_webhooks` | covered |
| `'all'` without token | `WebhookDispatcherTest#authorize_all_lets_anonymous_requests_in` | covered |
| roles: none / wrong / right | `WebhookDispatcherTest#roles_need_a_user_with_one_of_them` | covered |
| custom authorizer gets `(user, request)`; `NotAuthorizedError` 401, `Error` 500 | `WebhookDispatcherTest#a_custom_authorizer_gets_the_user_and_the_request_and_its_error_decides_the_status` | covered |
| unknown route + bad token → 500 | `WebhookDispatcherTest#route_and_method_errors_come_before_token_errors` | covered |
| JSON POST event fields, `multipart` undefined | `WebhookDispatcherTest#copies_the_request_into_the_event` | covered |
| multipart field + file, stream and buffered | `WebhookDispatcherTest#parses_multipart_bodies_from_the_stream_or_the_buffered_body` | covered |
| `fileSize` truncates silently | `WebhookDispatcherTest#files_over_the_size_limit_are_truncated_silently` | covered |
| registered event stored once after the handler, with request id and user | `WebhookDispatcherTest#are_stored_after_the_handler_returns_with_the_request_id_and_the_user` | covered |
| event registered then throw → nothing stored | `WebhookDispatcherTest#are_discarded_when_the_handler_fails`, `#a_failed_asynchronous_result_is_mapped_by_its_cause_and_stores_nothing` | covered (async: fixed `f3708560d`) |
| no events → store not called | `WebhookDispatcherTest#the_store_is_not_touched_without_events` | covered |
| void result shape | `WebhookDispatcherTest#a_void_handler_answers_an_empty_text` | covered |
| text with headers, merge order | `WebhookResponseBuilderTest#the_header_order_is_default_handler_then_type_keys` | covered |
| JSON stringified; falsy → `''` | `WebhookResponseBuilderTest#json_bodies_are_stringified_and_falsy_ones_are_empty` | covered |
| file body and headers | `WebhookResponseBuilderTest#file_bodies_pass_through_with_the_attachment_headers` | covered |
| handler `Content-Type` overridden; `Access-Control-Allow-Origin` overridable | `WebhookResponseBuilderTest#handler_headers_override_the_default_and_are_overridden_by_the_type_headers` | covered |
| unknown type with/without `file` | `WebhookResponseBuilderTest#an_unknown_response_type_is_sent_as_a_file_and_logged` | covered |
| error mapping table | `WebhookResponseTest#failures_map_the_error_class_to_status_and_title` | covered |
| local: `POST` and `GET` reach the dispatcher with `{BOOSTER_ROCKET_FUNCTION_ID, req}` | `WebhookControllerTest#post_and_get_reach_the_dispatcher_with_the_rocket_id_and_the_request` | covered |
| local: success headers + body; failure status + JSON | `WebhookControllerTest#a_json_success_sends_every_header_and_the_body_as_utf8_text`, `#a_failure_sends_the_status_and_the_body_as_json_without_the_result_headers` | covered |
| local: dispatcher rejects → `requestFailed` + `next` | `WebhookControllerTest#a_dispatcher_exception_is_answered_with_request_failed_and_passed_to_next` | covered |
| Azure: code for `test` and `clients/other` | `FunctionsTest#one_definition_for_the_webhook_function_app_with_one_function_per_route_in_order`, golden package | covered |
| Azure: empty endpoint | `FunctionsTest#an_empty_endpoint_and_a_webhook_without_route_fail` | covered |
| Azure: `xrg` → `xrgwebhookfunc`, one definition | `FunctionsTest#one_definition_for_the_webhook_function_app_with_one_function_per_route_in_order` | covered |
| Azure: guards in order | `AzureWebhookSynthTest#the_function_app_needs_the_database_the_storage_account_and_the_service_plan_in_that_order` | covered |
| Azure: N path rules, N rewrites `1..N`, app settings | `AzureWebhookSynthTest#synthesizes_the_function_app_and_the_gateway_rules_of_every_webhook` | covered |

## 4. Porting hazards (spec §6)

| # | Hazard | Resolution | Status |
| --- | --- | --- | --- |
| 1 | dynamic package loading, three messages | `<packageName>.Rocket` + `RocketLoader` | elsewhere (`RocketLoaderTest`) |
| 2 | live `parameters` | the same `List<WebhookParamsEvent>`; `WebhookParams.from` | covered (`WebhookParamsTest#reads_the_descriptor_parameters`) |
| 3 | static `handle` on a class | `handlerClass` + `UserCode.invokeStatic`; may take only the event; `CompletionStage` awaited | covered (`WebhookDispatcherTest#asynchronous_results_are_awaited_and_handlers_may_take_only_the_event`) |
| 4 | unions | sealed `WebhookHandlerReturnType`, `Authorize<WebhookAuthorizer>`; raw maps still read | covered; unknown `responseType` only through raw maps (differs) |
| 5 | truthiness, `??` vs `\|\|` | `Js.isTruthy`; each branch pinned | covered (`JsTest`, `WebhookResponseBuilderTest`, `WebhookParamsTest`) |
| 6 | errors by constructor name | `BoosterErrors.httpStatusCodeFor` / `TypeLevel.toClassTitle` | covered; titles of plain errors differ (§7) |
| 7 | untyped `req` | `WebhookHttpRequest` + `WebhookRequest.from` | differs (neutral model; shapes per provider kept) |
| 8 | header maps, spread order, case-sensitive keys | `LinkedHashMap` + `putAll`; local `setHeader` case-insensitive like Node | covered |
| 9 | `'Content-type'` typo | not ported as live code | differs |
| 10 | `method: 'POST'`, 500 for method/route | kept | covered |
| 11 | sequential awaits; dispatch total | blocking virtual-thread code; never throws | covered |
| 12 | Busboy | port of `multipart.js` + `streamsearch`; errors answered | covered / differs (errors), urlencoded not implemented |
| 13 | Express specifics | framework web layer (Router, BodyParsers, `res.send`); `setHeader` validation | covered (fixed `979597e85`) |
| 14 | `url.URL` + regex | WHATWG pathname + host/port validation | fixed `f60bef5f0` |
| 15 | generated JavaScript | `function.json` for the Java worker; name sanitising per UTF-16 unit | differs / fixed `77b2dad53` |
| 16 | `addOverride` replace-vs-append, random names | framework `TerraformResource.addOverride` (cdktn `deepMerge` semantics); `Synth(Supplier<String>)` for tests | covered (section 7) |
| 17 | `bind` partial application | constructors capturing `params`; metadata and `request` declared and ignored | covered |
| 18 | globals, env vars, `console.log` | `UserAppRegistry`, `EnvironmentVariables`; logger | differs (logger) |
| 19 | `JSON.stringify` semantics | `BoosterJson` | covered for the rocket's values; `byte[]` inside a JSON body is base64 (`JSON.stringify(Buffer)` gives `{"type":"Buffer","data":[...]}`): framework JSON mapping, not changed |
| 20 | `WebhookEvent` not an event | plain record | differs (not an `EventInterface`) |
| 21 | health integration | kept absent | covered (`LocalEndToEndTest#the_webhook_rocket_has_no_entry_in_the_rockets_health_indicator`) |

## 5. TypeScript sources, file by file

| TypeScript | Java | Tests | Status |
| --- | --- | --- | --- |
| `T/index.ts` (barrel) | package `types` | — | n/a |
| `T/webhook-params.ts` (`functionID`, `WebhookAuthorizer`, `WebhookRoleAccess`, `AllowedHttpMethod`, `WebhookParamsEvent`, `getRoute`) | `WebhookParams`, `WebhookAuthorizer`, `AllowedHttpMethod`, `WebhookParamsEvent` | `WebhookParamsTest` | covered |
| `T/webhook-handler.ts` | `WebhookHandler*ReturnType`, `WebhookHandlerClassInterface` | `WebhookResponseBuilderTest`, `WebhookDispatcherTest` | covered |
| `T/webhook-event.ts` | `WebhookEvent` | `WebhookRequestTest#the_event_gives_the_raw_body_as_bytes_or_text` | covered |
| `T/webhook-request.ts` | `WebhookRequest`, `WebhookHttpRequest` | `WebhookRequestTest` | covered |
| `T/helper.ts` | `Helper`, `WhatwgHost` | `HelperTest` | fixed `f60bef5f0` |
| `T/webhook-multipart-form.ts` | `WebhookMultipartForm`, `WebhookParsedFile`, `WebhookParsedField`, `MultiPartConfig` | `WebhookRequestTest#parsed_files_compare_by_content`, `ParseMultiPartTest` | covered |
| `T/webhook-response-type.ts` | `WebhookResponseType` | `WebhookParamsTest#enums_keep_the_typescript_values` | covered |
| `T/webhook-api-result.ts` | `WebhookAPIResult`, `WebhookAPISuccessResult`, `WebhookAPIErrorResult` | `WebhookResponseTest#the_result_records_copy_their_headers_and_a_success_is_always_200` | covered |
| `C/index.ts` | `BoosterWebhook` | `BoosterWebhookTest` | covered |
| `C/webhook-dispatcher.ts` | `WebhookDispatcher` | `WebhookDispatcherTest` | covered |
| `C/parse-multi-part.ts` | `ParseMultiPart` | `ParseMultiPartTest` | covered |
| busboy 1.6.0 `lib/index.js`, `lib/utils.js`, `lib/types/multipart.js`, streamsearch | `core.busboy.*` | `MultipartTest`, `BusboyUtilsTest`, `StreamSearchTest` | covered |
| busboy 1.6.0 `lib/types/urlencoded.js` | — | `WebhookDispatcherTest#an_urlencoded_type_quoting_multipart_form_data_is_not_parsed_busboys_urlencoded_parser_is_not_ported` | not implemented |
| `C/webhook-token-verifier.ts` | `WebhookTokenVerifier` | `WebhookTokenVerifierTest` | covered |
| `C/webhook-authorization-checker.ts` | `WebhookAuthorizationChecker` | `WebhookAuthorizerBuilderTest#the_checker_does_nothing_without_authorize_and_rebuilds_the_authorizer_on_every_call` | covered |
| `C/webhook-authorizer-builder.ts` | `WebhookAuthorizerBuilder` | `WebhookAuthorizerBuilderTest` | covered |
| `C/response/webhook-response.ts` | `WebhookResponse` | `WebhookResponseTest` | covered |
| `C/response/webhook-response-builder.ts` | `WebhookResponseBuilder`, `WebhookResponseClass` | `WebhookResponseBuilderTest` | covered |
| `C/response/responses/webhook-empty-response.ts` | `WebhookEmptyResponse` | `WebhookResponseBuilderTest#falsy_results_use_the_empty_strategy` | covered |
| `C/response/responses/webhook-text-response.ts` | `WebhookTextResponse` | `WebhookResponseBuilderTest#text_bodies_pass_through_and_only_null_becomes_empty` | covered |
| `C/response/responses/webhook-json-response.ts` | `WebhookJsonResponse` | `WebhookResponseBuilderTest#json_bodies_are_stringified_and_falsy_ones_are_empty` | covered |
| `C/response/responses/webhook-file-response.ts` | `WebhookFileResponse` | `WebhookResponseBuilderTest#file_bodies_pass_through_with_the_attachment_headers` | covered |
| `L/index.ts` | `local.Rocket`, `LocalWebhook` | `LocalWebhookTest` | covered |
| `L/infra.ts` | `Infra` | `LocalWebhookTest#mounts_every_webhook_at_its_route_without_prefix_and_ignores_the_metadata` | covered |
| `L/controllers/webhook-controller.ts` | `WebhookController` | `WebhookControllerTest`, `LocalEndToEndTest` | covered |
| `L/http.ts` (`HttpCodes`, `requestFailed`) | framework `Http.requestFailed` | `WebhookControllerTest#a_dispatcher_exception_is_answered_with_request_failed_and_passed_to_next` | differs: not duplicated (a copy of the framework's) |
| `A/index.ts` | `azure.Rocket`, `AzureWebhook` | `AzureWebhookSynthTest#the_rocket_is_loaded_from_the_descriptor_package_and_defines_the_v4_hooks` | covered |
| `A/helper.ts` | `azure.Helper` | `FunctionsTest#one_definition_for_the_webhook_function_app_with_one_function_per_route_in_order` | covered |
| `A/functions/functions.ts` | `Functions` | `FunctionsTest` | fixed `77b2dad53` (collisions) |
| `A/functions/webhook-function.ts` | `WebhookFunction` | `FunctionsTest`, golden package | covered (`function.json`, section 8) |
| `A/synth/synth.ts` | `Synth` | `AzureWebhookSynthTest`, golden `webhook.cdk.tf.json` | covered |
| `A/synth/terraform-function-app.ts` | `TerraformFunctionApp` | `AzureWebhookSynthTest`, golden | covered |

---

## 6. Local end to end on the real server

`LocalEndToEndTest` starts `Booster.start(App.class)` with the local provider over a `@TempDir` project and the real
local server (Jetty) with the HTTP and websocket servers on port 0; plain HTTP clients call the webhooks.

| Scenario | Test |
| --- | --- |
| webhook call → event → entity → read model through GraphQL; a command updates it | `#a_webhook_call_becomes_a_read_model_that_a_command_then_updates` |
| 20 concurrent calls on different orders | `#concurrent_webhook_calls_all_reach_their_read_models` |
| restart over the same project directory (Booster reset and started again, new configuration and rocket registration, new free ports): the event and read model are read back and nothing is recovered; then 32 concurrent calls, 16 on that same order: 17 events with 17 distinct request ids and every quantity, the read model ends on the newest event; a second restart keeps all 33 events, and a new webhook call and a GraphQL mutation work | `#webhook_events_and_read_models_survive_restarts_and_concurrent_calls_lose_no_event` |
| `HEAD`, `graphql` shadowing, odd `Host`, `express.raw()` limit, header validation, health | the rows of sections 3.4, 3.11, 3.12, 3.1.8 |

After the runs, `jps -lm` showed no JVM of this worktree left running.

## 7. Hand walks against the TypeScript code

### Terraform of `TerraformFunctionApp.build` (golden `webhook.cdk.tf.json`, app `test`, environment `production`)

| TypeScript (cdktn) | Renders as | Golden value | Result |
| --- | --- | --- | --- |
| id `utils.toTerraformName('testproduction', 'webhookfunc')` (25 > 24 characters, name longer than suffix: drop 1 character of the name) | resource key | `estproductionwebhookfunc` | equal |
| `name: getFunctionAppName(resourceGroupName)` (a string) | literal | `testproductionrgwebhookfunc` | equal |
| `location: resourceGroup.location` | token | `${azurerm_resource_group.testproductionrg.location}` | equal |
| `resourceGroupName: resourceGroup.name` | token | `${azurerm_resource_group.testproductionrg.name}` | equal |
| `servicePlanId: applicationServicePlan.id` | token | `${azurerm_service_plan.testproductionpsp.id}` | equal |
| `appSettings.WEBSITE_RUN_FROM_PACKAGE: '1'`, `WEBSITE_CONTENTSHARE: id` | literals | `1`, `estproductionwebhookfunc` | equal |
| `...config.env` before the later keys | map entries | `CUSTOM_SETTING`; `BOOSTER_ROCKET_FUNCTION_ID` from `env` overridden | equal |
| `BOOSTER_ENV: config.environmentName` | literal | `production` | equal |
| `COSMOSDB_CONNECTION_STRING` from `cosmosdbDatabase.name` / `.primaryKey` (the account) | template | `AccountEndpoint=https://${azurerm_cosmosdb_account.testproductiondba.name}.documents.azure.com:443/;AccountKey=${azurerm_cosmosdb_account.testproductiondba.primary_key};` | equal |
| `WEBSITE_CONTENTAZUREFILECONNECTIONSTRING: storageAccount.primaryConnectionString` | token | `${azurerm_storage_account.testproductionsp.primary_connection_string}` | equal |
| `[rocketFunctionIDEnvVar]: functionID` | literal | `BOOSTER_ROCKET_FUNCTION_ID = rocket-webhook` | equal |
| — | — | `BOOSTER_APP_CLASS` | differs: Java worker boot class |
| `storageAccountName`, `storageAccountAccessKey` | tokens | `${...testproductionsp.name}`, `${...testproductionsp.primary_access_key}` | equal |
| `dependsOn: [resourceGroup]` | `depends_on` | `azurerm_resource_group.testproductionrg` | equal |
| `lifecycle.ignoreChanges` | `lifecycle.ignore_changes` | `app_settings["WEBSITE_RUN_FROM_PACKAGE"]` | equal |
| `provider: azureProvider` | `provider` | `azurerm` | equal |
| `siteConfig.applicationStack.nodeVersion: '~22'` | `site_config.application_stack` | `java_version: "21"` | differs: Java stack |
| `functionsExtensionVersion: '~4'` | literal | `~4` | equal |

Gateway (`Synth.mountStack`, cdktn `addOverride` writes `rawOverrides`, `deepMerge` at synthesis: a list value
replaces, the `0` segment merges into the first element of a list):

| TypeScript | Golden | Result |
| --- | --- | --- |
| `addOverride('backend_address_pool', [{ name: 'rocketWebHookBackendAddressPool', fqdns: [`${functionApp.name}.azurewebsites.net`] }])` (`functionApp.name` is the token) | the whole list replaced: one pool, fqdn `${azurerm_windows_function_app.estproductionwebhookfunc.name}.azurewebsites.net` | equal (the base `functionManagementBackendAddressPool` is gone while `url_path_map` and `request_routing_rule` still name it, as in TypeScript) |
| `addOverride('url_path_map.0.path_rule', [...])`: per route `{ name, paths: ['/r', '/r/*'], backend_address_pool_name, backend_http_settings_name: 'mainBackendHttpSettings', rewrite_rule_set_name: 'mainRewriteRuleSet' }` | `url_path_map[0]` keeps its name and defaults; `path_rule` = the two webhook rules (GraphQL and sensor rules replaced) | equal |
| `addOverride('rewrite_rule_set.0.rewrite_rule', [...])`: `{ name, rule_sequence: index + 1, condition: [{ pattern: '/r', variable: 'var_request_uri', ignore_case: true }], url: { path: '/api/r', components: 'path_only' } }` | `rewrite_rule_set[0]` keeps `mainRewriteRuleSet`; two rules, sequences 1 and 2 | equal |
| names `toTerraformName('testproduction', 'ru' + 'f47ac10b-58cc-4372-a567')`: suffix cleaned to 22 characters, name not longer than suffix → first 23 characters of the concatenation | `testproductionruf47ac10`, `testproductionrwf47ac10` | equal |

Main function app (framework `addAppSettingsToFunctionApp`): `ROCKET_FUNCTION_APP_NAMES = "testproductionrgwebhookfunc"`,
`ROCKET_PACKAGE_MAPPING = ""` (the webhook's parameters have no `rocketProviderPackage`): equal.

### `function.json` against the generated `functions.js`

| `app.http(name, options)` in TypeScript | `function.json` | Result |
| --- | --- | --- |
| `name`: `JSON.stringify(endpoint.replace(/[^a-zA-Z0-9_]/g, '_'))` | directory `<name>/` | equal (per UTF-16 unit since `77b2dad53`) |
| `methods: ['POST', 'GET']` | `"methods": ["POST", "GET"]` | equal |
| `authLevel: 'anonymous'` | `"authLevel": "anonymous"` | equal |
| `route: JSON.stringify(endpoint)` | `"route": "<endpoint>"` (default `host.json`, prefix `api`) | equal |
| HTTP trigger + `$return` HTTP output (the v4 model's `trigger.http` / `output.http`) | `httpTrigger` `request` in, `http` `$return` out | equal (binding names do not matter) |
| `handler` builds the rocket request and calls `boosterRocketDispatcher` from `./dist/index` | `entryPoint` `BoosterAzureFunctions.rocketHttp`, `scriptFile` `../<app>.jar`, `dataType: binary` | differs: Java worker; the request built is the same (`FunctionMappings.rocketHttpRequest`, pinned in framework-provider-azure) |
| default `host.json` `{"version":"2.0","extensionBundle":{"id":"Microsoft.Azure.Functions.ExtensionBundle","version":"[4.*, 5.0.0)"}}` | same | equal |

### Signatures

The rocket computes no signature itself. The Cosmos DB master-key token the Azure provider sends for the events the
webhook registers is re-derived in `AzureRuntimeContractTest` from Microsoft's documented rules ("Access control in the
Azure Cosmos DB SQL API": `verb\nresourceType\nresourceLink\ndate\n\n`, verb, type and date lower-cased,
HMAC-SHA256 with the base64-decoded key, `type=master&ver=1.0&sig=<base64>` URI-encoded) with `javax.crypto` only,
for `post` / `docs` / `dbs/webhook-e2e-app/colls/webhook-e2e-app-events-store`, and compared with the `authorization`
header of each batch; `x-ms-date` is RFC 1123 and current. Equal. The provider's own known-answer vectors (master key,
SAS, HS256) are in `SignatureKnownAnswerTest` (framework-provider-azure).

### Transport probes (local server, by hand, 2026-11-10)

`GET /status` with `Connection: close` on a raw socket: `Host: localhost` → 200; `a b`, `h:99999`, `[::1`, `a<b`,
`h:x`, `a@` → Jetty `400 Bad HostPort`; empty → `400 Blank Host`; HTTP/1.1 without `Host` → `400 No Host` (Node's
server also answers 400); `a%20b` → 500 `Invalid URL` (pinned); HTTP/1.0 without `Host` → 200 (pinned);
`GET http://other:1/status` with `Host: localhost` → `400 Authority!=Host`. In TypeScript, Node passes all of these
to Express except the HTTP/1.1 request without `Host`, and the rocket answers 500 `Invalid URL` (or, for the
absolute-form target, routes it with `event.url` absolute). Recorded as a difference of the local transport.
