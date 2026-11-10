# rocket-file-uploads parity ledger

Audit of the Java port of the file uploads rocket against (1) both TypeScript test files of the rocket, (2) every
item of `spec/15-rocket-file-uploads.md` ("Behaviour", "Data shapes", "Behaviours pinned by tests", porting notes and
hazards) and (3) every TypeScript source of the rocket repository (`rocket-file-uploads` 3.2.0, commit `cc6d646`, the
six `packages/*/src`). Audited on 2026-11-10 on `port/rocket-files`, starting from `513acb3c2`. Where the spec and the
TypeScript code disagree, the code wins (none found in this audit besides those the spec itself lists in §3.12).

Every row names the Java test that pins the behaviour, as `Class#method` under
`src/test/java/com/boostercloud/rocket/fileuploads` (package prefix shortened: `types.`, `core.`, `local.`, `azure.`,
`e2e.`).

**Status legend**

| Status | Meaning |
| --- | --- |
| covered | Implemented, and the named test pins it (checked by reading the test). |
| fixed `sha` | A gap found by this audit: the code and/or the test were changed in that commit. |
| differs | Intentionally different; the reason is given and README "Differences from TypeScript" lists it. The test pins the Java behaviour. |
| inherited | Behaviour of a framework module the rocket runs on, documented as a difference by that module. |
| n/a | Nothing to port (documentation defect, type-only declaration, AWS). |

## Summary

| Part | Rows | covered | fixed | differs | inherited | n/a |
| --- | --- | --- | --- | --- | --- | --- |
| 1. TypeScript tests (28 `it`) | 28 | 28 | 0 | 0 | 0 | 0 |
| 2. Behaviour (spec §3) | 98 | 59 | 14 | 21 | 3 | 1 |
| 3. Data shapes (spec §4) | 12 | 10 | 1 | 1 | 0 | 0 |
| 4. Characterisation tests (spec §5.3) | 16 | 14 | 1 | 1 | 0 | 0 |
| 5. Porting notes and hazards (spec §6) | 16 | 9 | 2 | 5 | 0 | 0 |
| 6. TypeScript sources (38 files) | 38 | 34 | 2 | 2 | 0 | 0 |

A row whose status names a commit counts as fixed (some also note a documented difference). Nothing is left
missing: every row is covered, fixed, a documented difference or inherited from a framework module.

Gaps found and fixed by this audit:

| Commit | Gap |
| --- | --- |
| `875d227d2` | Azure synth: the container and role assignment construct ids are computed from names (TypeScript: from token strings, always distinct); `toTerraformName` keeps only the end of long names, so storages whose names end alike (`firstcompanyfiles`, `secondcompanyfiles`) got duplicate ids and the synthesis failed with `There is already a Construct`. Now a counter is added on collision. |
| `b633079cc` | Local watcher: the JDK `WatchService` keeps 512 pending events per directory and reports the rest as `OVERFLOW`, which was ignored, so a burst of uploads into one folder lost notifications (and their `UploadedFileEvent`s). The directory is now rescanned on overflow. The test fails without the fix. |
| `d332b4ffe` | Local I/O errors: a `PUT` onto a directory / under a file answered the JDK's text (`<path>: Is a directory`) instead of Node's `EISDIR: illegal operation on a directory, open '<path>'` / `ENOTDIR: not a directory, open '<path>'`; `list` dropped the trailing separator Node prints in `scandir '<path>/'`. |
| `33c2b3788` | Not pinned before: Terraform and `function.json` compared with what the TypeScript rocket produces (hand walk of the synth code; `functions.js` from the TypeScript generator itself), the TypeScript name-validation messages, SAS tokens re-derived independently from the public specification. |
| `402dac5f6` | Not exercised before: the application as `boost start` runs it (a separate JVM over a real project layout) with concurrent clients, SIGTERM and a restart over the same project directory. |
| `0a3f90c46`, `c9f5cd0e0` | Not pinned before: Express routing details of the local routes (query string in `req.url`, raw folder vs decoded file name, trailing slash, case-insensitive match), the directory fall-through of `res.download` and `HEAD`. |

## Cross-checks made for this audit

* **SAS (user delegation, version 2018-11-09).** `src/test/resources/typescript/generate.mjs` implements the
  string-to-sign of the public "Create a user delegation SAS" specification (20 fields: `sp, st, se,
  /blob/<account>/<container>/<blob>, skoid, sktid, skt, ske, sks, skv, sip, spr, sv, sr, snapshot time, rscc, rscd,
  rsce, rscl, rsct`) in JavaScript, signs it with Node's `crypto` HMAC-SHA256 and encodes the query with
  `encodeURIComponent` in `SASQueryParameters.toString()` order (`sv, spr, st, se, skoid, sktid, skt, ske, sks, skv,
  sr, sp, sig`). `azure.storage.BlobSasSpecificationVectorsTest` compares the Java string-to-sign, signature, query
  string and complete `getBlobSasUrl` URL with 5 vectors (86 s and 87 s windows from the 86 400 ms lifetime, month and
  year boundaries, `wr` → `rw`, blob names with spaces, accents, `+`, `%`, `!*'()~`, astral characters, mixed-case
  default account names). `azure.storage.BlobSasTest` adds an OpenSSL known answer. The rocket uses no shared key
  (only Azure AD and user delegation keys), so there is no shared key signature to re-derive.
* **Terraform.** `typescript/file-uploads-resources.expected.json` is the hand walk of `synth.ts`,
  `terraform-storage-account.ts`, `terraform-storage-container.ts`, `terraform-function-app.ts` and
  `terraform-role-assignment.ts` for the README storages, every token written as the Terraform expression cdktn
  synthesizes. `azure.infrastructure.AzureRocketSynthTest#the_terraform_is_what_the_typescript_synthesis_writes_attribute_by_attribute`
  synthesizes the application with and without the rocket and requires: the added resources equal the walk attribute
  by attribute (13 resources; the 10 with opaque TypeScript ids matched by content), the application's function app
  gains only `identity` (rocket) and the `ROCKET_*` settings (framework), nothing else changes. The only normalised
  differences are the documented ones: literal `<account>__serviceUri` keys, the Java worker's `site_config`,
  `BOOSTER_APP_CLASS`.
* **Functions.** `typescript/functions.js` is the output of the rocket's own generator
  (`rocket-files-file-uploaded-function.ts`, run by Node with type stripping, joined as `Functions.mountFunctionsV4`
  does). `AzureRocketSynthTest#the_blob_trigger_functions_are_those_the_typescript_functions_js_registers` parses
  every `app.storageBlob(name, { path, connection })` and requires one `function.json` per registration with the same
  name, `blobTrigger` binding path and connection. `#names_are_validated_with_the_typescript_messages` replays the
  messages the TypeScript validation produced (`typescript/function-name-errors.json`).
* **Local end to end.** `e2e.LocalServerProcessEndToEndTest` (separate JVM, `java -cp target/classes:... Main start
  --port N` from the project directory, free HTTP and websocket ports): 40 concurrent presigned PUT mutations and
  uploads into both storages, 8 concurrent overwrites of one file, concurrent presigned GETs and listings, every
  read model / event / `UploadedFileEntity` snapshot checked, SIGTERM (exit 143, graceful), a second process over the
  same project directory on new ports (files, events, read models unchanged; start-up notifies nothing), 12 more
  concurrent uploads notified with the new port. `e2e.LocalEndToEndTest` keeps the in-process variant (delete, files
  dropped by hand, error messages).

## 1. TypeScript tests

### 1.1 `rocket-file-uploads-types/test/utils.test.ts` → `types.UtilsTest` (display name `checkDirectory`)

| # | `it` | Java | Status |
| --- | --- | --- | --- |
| 1 | invalid empty source | `#invalid_empty_source` | covered |
| 2 | valid exact root | `#valid_exact_root` | covered |
| 3 | invalid extra slash | `#invalid_extra_slash` | covered |
| 4 | valid exact root with ending slash | `#valid_exact_root_with_ending_slash` | covered |
| 5 | valid exact root/subdirectory | `#valid_exact_root_subdirectory` | covered |
| 6 | valid exact root/subdirectory with ending slash | `#valid_exact_root_subdirectory_with_ending_slash` | covered |
| 7 | only one star: valid any root | `only_one_star#valid_any_root` | covered |
| 8 | valid any root with ending slash | `#valid_any_root_with_ending_slash` | covered |
| 9 | invalid for root with subdirectories | `#invalid_for_root_with_subdirectories` | covered |
| 10 | valid any root and the exact subdirectory | `#valid_any_root_and_the_exact_subdirectory` | covered |
| 11 | invalid any root and the wrong subdirectory | `#invalid_any_root_and_the_wrong_subdirectory` | covered |
| 12 | invalid wrong root | `#invalid_wrong_root` | covered |
| 13 | valid the exact root and any subdirectory | `#valid_the_exact_root_and_any_subdirectory` | covered |
| 14 | invalid the exact root and more subdirectories than expected | `#invalid_the_exact_root_and_more_subdirectories_than_expected` | covered |
| 15 | only one GlobStart: valid any root | `only_one_GlobStart#valid_any_root` | covered |
| 16 | GlobStart at the end (6 assertions) | `#valid_the_exact_root_directory_and_any_number_of_subdirectories` | covered |
| 17 | GlobStart in the middle (9 assertions) | `#valid_the_exact_root_directory_and_any_number_of_subdirectories_ending_in_the_expected_one` | covered |
| 18 | more than one GlobStart (10 assertions) | `#valid_only_if_subdirectories_match` | covered |

Beyond the TypeScript tests: `types.MinimatchDifferentialTest` (~9 000 cases) and
`types.BraceExpansionDifferentialTest` (~1 500) against corpora generated with minimatch 3.1.5 / brace-expansion
1.1.16 (`differential/generate.js`), `types.MinimatchTest` (the §3.4 table, errors, BOM/whitespace trimming).

### 1.2 `rocket-file-uploads-azure/test/file-uploaded.test.ts` → `azure.FileUploadedTest`

| # | `it` | Java | Status |
| --- | --- | --- | --- |
| 1 | false when the container does not match, even with `**` | `#returns_false_when_container_name_does_not_match_even_with_wildcard_directory_pattern` | covered |
| 2 | false when the container is a substring | `#returns_false_when_container_name_is_a_substring_but_not_exact_match` | covered |
| 3 | true when container and directory match | `#returns_true_when_container_name_matches_and_directory_pattern_matches` | covered |
| 4 | true with container and `**` | `#returns_true_when_container_name_matches_with_wildcard_directory_pattern` | covered |
| 5 | false when the directory does not match | `#returns_false_when_container_matches_but_directory_does_not` | covered |
| 6 | true for nested directory with globstar | `#returns_true_for_nested_directory_with_globstar_pattern` | covered |
| 7 | true for a file in a root directory with `*` | `#returns_true_for_file_in_root_directory_with_single_star_pattern` | covered |
| 8 | duplicate prevention, data-processing upload | `#only_one_configuration_matches_for_data_processing_upload` | covered |
| 9 | duplicate prevention, media-assets upload | `#only_one_configuration_matches_for_media_assets_upload` | covered |
| 10 | `getMetadataFromRequest` returns `context.triggerMetadata` | `#extracts_triggerMetadata_from_Azure_Functions_v4_InvocationContext` | covered |

Beyond the TypeScript tests: `#ignored_blobs_are_logged_like_console_info`,
`#a_blob_at_the_container_root_only_matches_patterns_matching_the_empty_directory`,
`#a_missing_blob_trigger_fails_like_javascript`, `#reads_the_context_built_by_the_java_entry_point`,
`#fails_without_trigger_metadata`.

## 2. Behaviour (spec §3)

### §3.1 `BoosterRocketFiles`

| Item | Java test | Status |
| --- | --- | --- |
| Constructor keeps the configuration and one or several storages | `core.BoosterRocketFilesTest#azure_descriptor_with_a_single_configuration`, `#local_descriptor_with_lower_cased_storage_names` | covered |
| `rocketForAzure()` / `rocketForLocal()`: build parameters, register, descriptor whose parameters are the registered object | same tests, `#registers_the_rocket_function_and_the_concepts` | covered |
| Descriptor package names | same tests (`com.boostercloud.rocket.fileuploads.{azure,local}.infrastructure`) | differs: Java packages instead of npm names (loaded as `<package>.Rocket`); `rocketProviderPackage` keeps the npm names (`types.RocketFilesConfigurationTest#the_provider_packages_keep_the_npm_names_and_name_the_java_libraries`) |
| `buildParameters`: storage names lower-cased, single object wrapped in a list, containers/directories untouched, nothing validated | `#local_descriptor_with_lower_cased_storage_names`, `#azure_descriptor_with_a_single_configuration` | covered |
| `buildParameters` mutates the caller's objects | `#local_descriptor_with_lower_cased_storage_names` | differs: records are immutable; the descriptor holds lower-cased copies, every consumer reads the descriptor |
| Missing `storageName` → `Cannot read properties of undefined (reading 'toLowerCase')` | `#a_storage_without_name_fails_like_javascript` | covered |
| `register`: provider library loaded eagerly | `core.FileHandlerTest#the_provider_library_must_exist` (`RocketFilesProviders`) | covered |
| Rocket function `rocket-files` runs `fileUploaded` for every configuration with the same request, `Promise.all` (fail fast) | `#the_local_rocket_function_stores_one_event_per_configuration_with_the_request_as_metadata`, `#the_rocket_function_fails_with_the_first_extraction_error` | covered |
| Second registration on one configuration: `Error registering rocket function with id rocket-files: There is already a rocket function registered under the same ID, ""` | `#a_second_rocket_on_the_same_configuration_fails` | covered |
| Concepts registered by decorators on import | `types.RocketFilesConceptsTest#registers_the_event_the_entity_and_the_reducer_once`, `#another_class_with_the_same_name_fails_like_the_decorators` | differs: Java scans only the application package, so the constructor registers them (`RocketFilesConcepts`) |

### §3.2 `getUserConfiguration` → `types.RocketFilesParamsTest`

| Item | Java test | Status |
| --- | --- | --- |
| `[]` → `Missing userConfiguration` | `#without_configurations_it_fails` | covered |
| several and no name (`undefined`, `null`, `''`) → `Missing storageAccountName` | `#several_configurations_need_a_name_and_empty_counts_as_missing` | covered |
| one and no name → that one | `#a_single_configuration_is_used_without_name` | covered |
| case-insensitive match, first match wins | `#names_match_ignoring_case_and_the_first_match_wins` | covered |
| unknown → `Storage account name <name as given> not found` | `#an_unknown_name_is_reported_as_given` | covered |
| `functionID = 'rocket-files'` | `#the_function_id` | covered |

### §3.3 `FileHandler` → `core.FileHandlerTest`

| Item | Java test | Status |
| --- | --- | --- |
| First descriptor among the two infrastructure packages; none (or no `rockets`) → `Rocket not found. Please make sure you have setup the rocket packageName correctly` | `#without_the_rocket_it_fails`, `#the_first_rocket_descriptor_is_used` | covered |
| Parameters read as `RocketFilesConfiguration` (also raw JSON) | `types.RocketFilesConfigurationTest#raw_parameters_are_read_back` | covered |
| Library from `rocketProviderPackage`, storage from `getUserConfiguration` | `#the_storage_is_selected_by_name`, `azure.storage.BlobServiceContractTest#the_file_handler_uses_the_azure_library_of_the_descriptor` | covered |
| `presignedGet` / `presignedPut` / `list`: directory checked first (`Invalid directory <d>`, before any provider call), then delegated with the selected storage | `#every_method_checks_the_directory_first`, `#presigned_urls_of_the_local_library`, `#list_and_delete_go_to_the_library` | covered |
| File names never validated | `#presigned_urls_of_the_local_library`, `#delete_only_removes_a_file_of_the_checked_directory` | covered for the presigned URLs (`deleteFile`: next rows) |
| `presignedPut: Promise<string> \| Promise<any>` | `#presigned_urls_of_the_local_library` | differs: generic `<T> T` |
| No `deleteFile` | `#list_and_delete_go_to_the_library` | differs: Java addition `deleteFile(directory, fileName)` (documented as a TODO in TypeScript) |
| `deleteFile` file name (Java addition, so no TypeScript rule): only the directory is checked against the patterns | `#delete_only_removes_a_file_of_the_checked_directory`, `local.RocketFilesLibraryTest#delete_stays_in_the_given_directory`, `azure.storage.BlobServiceContractTest#delete_refuses_file_names_that_leave_the_directory` | fixed (security): a plain name is required (`Invalid file name <fileName>`), so `../admin/secret.txt` cannot delete outside the allowed folders |

### §3.4 `isValidDirectory`

| Item | Java test | Status |
| --- | --- | --- |
| minimatch 3.1.5 default options, `some` over patterns, empty list false | `types.UtilsTest#any_matching_pattern_is_enough`, `types.MinimatchDifferentialTest#matches_like_minimatch_3_1_5` | covered |
| The 15 rows of the §3.4 table | `types.MinimatchTest#behaves_like_minimatch` | covered |
| Windows `\` → `/` | `types.MinimatchDifferentialTest` (POSIX corpus) | differs: emulated on Windows only, as minimatch does |

### §3.5 `fileUploaded` → `core.FileUploadedTest`

| Item | Java test | Status |
| --- | --- | --- |
| metadata extracted, validated, event stored only when valid | `#stores_the_event_when_the_library_accepts_the_metadata`, `#stores_nothing_when_the_library_rejects_the_metadata` | covered |
| store failure logged with `[ROCKET#files] An error occurred while performing a PutItem operation: ` and swallowed | `#store_failures_are_logged_and_swallowed` | covered (logged on `config.logger`, the console by default, instead of `console.log`) |
| extraction errors propagate | `#metadata_errors_propagate` | covered |
| Envelope: random id for `entityID` and `requestID`, `kind event`, `superKind domain`, `version 1`, type names, `value {metadata}`, no user/context | `#the_envelope_is_an_uploaded_file_event_with_one_random_id` | covered |
| `createdAt` set before storing | same | differs: not set, every provider sets it when storing (TypeScript's value is overwritten) |
| `null` metadata | — | differs: stored as `{}` (same md5 `null` id) |
| `entityID()` = md5 of `JSON.stringify(metadata)` | `types.UploadedFileEventTest#the_entity_id_is_the_md5_of_the_metadata_json`, `#the_md5_uses_javascript_json_text` (Node-computed values) | covered |
| Reducer ignores the current state; entity id = md5 while the snapshot key is the random id | `#the_reducer_replaces_the_state_with_the_md5_id_and_the_metadata`, `e2e.LocalServerProcessEndToEndTest` (snapshots) | covered |
| Read model by md5 collapses repeated notifications of one file | `e2e.LocalEndToEndTest#upload_observe_download_list_delete_and_restart`, `e2e.LocalServerProcessEndToEndTest` (read model count = files) | covered |
| `@Entity` without `authorizeReadEvents` | `types.RocketFilesConceptsTest#the_entity_events_cannot_be_read_through_graphql` | covered |

### §3.6 Local runtime → `local.RocketFilesLibraryTest`

| Item | Java test | Status |
| --- | --- | --- |
| `presignedPut` = `presignedGet` = `http://localhost:<ROCKET_FILES_LOCAL_PORT or 3000>/<storage>/<container>/<dir>/<file>`, nothing encoded | `#presigned_urls_point_at_the_local_server`, `types.ConstantsTest#the_local_port_comes_from_the_rocket_variable_or_3000` | covered |
| `list`: entries (files, sub-directories, dot files), not recursive, `strcmp` order, `name = path.join(dir, file)`, only `lastModified = ctime` | `#list_returns_the_entries_sorted_with_their_change_time` | covered |
| missing directory → `ENOENT: no such file or directory, scandir '<path as joined>'` | `#listing_a_missing_directory_fails_like_node` | fixed `d332b4ffe` (trailing separator kept) |
| `getMetadataFromRequest` = identity, `validateMetadata` = true | `#the_metadata_is_the_request_and_is_always_valid` | covered |
| `process.cwd()` | `#without_project_root_the_working_directory_is_used` | differs: `config.userProjectRootPath()` (the project directory, = cwd under `boost start`), else the working directory |

### §3.7 Local infrastructure → `local.infrastructure.LocalFilesServerTest`, `FsWatchTest`

| Item | Java test | Status |
| --- | --- | --- |
| Mount: port from metadata (default 3000) into `ROCKET_FILES_LOCAL_PORT`, one watcher per storage, one controller per (storage, directory) at `/<storage>/<container>`, in order | `#mounting_sets_the_port_of_the_presigned_urls_and_creates_the_containers` | covered (in-process `EnvironmentVariables` override instead of `process.env`) |
| Container folder created eagerly, directory folders lazily | same, `#a_put_writes_the_raw_body_and_answers_the_typescript_result` | covered |
| `PUT` writes the raw body, truncates, answers 200 `{"status":"success","result":{"message":"File uploaded","data":{"name","mimeType":"unknown","size":0}}}` | `#a_put_writes_the_raw_body_and_answers_the_typescript_result`, `#a_second_put_replaces_the_file` | covered |
| Folder from the raw `req.url` (query string included), file name decoded | `#the_file_name_is_decoded_and_the_directory_comes_from_the_raw_url`, `#express_routing_details_are_kept` | fixed `0a3f90c46` (test) |
| Directory patterns are Express routes (`*` spans segments), case-insensitive, trailing slash tolerated, unmatched → 404 | `#a_star_directory_is_an_express_wildcard_over_several_segments`, `#express_routing_details_are_kept`, `#other_directories_and_methods_are_not_routed` | covered / fixed `0a3f90c46` |
| Glob characters other than `*` in route paths (`?`, `+`, `()`, `[]` are live regex in path-to-regexp 0.1) | — | inherited: the framework router treats them as literals (framework-provider-local-infrastructure README) |
| JSON / urlencoded bodies consumed by the server parsers first (empty file) | `#a_json_body_is_consumed_by_the_server_parser_first_as_in_typescript` | covered |
| Answer sent on the request `end` (maybe before the flush; twice on a write error) | `#a_put_writes_the_raw_body_and_answers_the_typescript_result` | differs: answered after the file is written, once |
| Write errors → `requestFailed` (status of the error class, `{title, reason}`) and `next(e)` | `#writing_onto_a_directory_or_under_a_file_fails_with_the_node_errors` | fixed `d332b4ffe` (Node's `EISDIR`/`ENOTDIR` reasons) |
| Error titles of native errors (`"Error"`) | same | inherited: Java errors are titled by class (`Unchecked IO Exception`), the framework's rule; status and reason as Node |
| Path traversal through a decoded `..%2F` | `#targets_outside_the_container_are_refused` | differs: hardening, `400 Invalid Parameter Error` |
| `GET` = `res.download`: attachment, type by extension | `#a_get_downloads_the_file_as_an_attachment` | covered |
| Missing file → 500 `ENOENT: no such file or directory, stat '<abs path>'` | `#downloading_a_missing_file_answers_500_with_the_node_error`, `e2e.LocalEndToEndTest#errors_reach_the_client_with_the_typescript_messages` | covered |
| Dot file → 404 `{"title":"Not Found Error","reason":"Not Found"}` | `#dot_files_are_not_served` | covered |
| Directory → `next()` → Express 404; `HEAD` | `#a_directory_falls_through_to_not_found_and_head_answers_the_headers_only` | fixed `c9f5cd0e0` (test) |
| `Range` requests and `304` of `send` | — | inherited: not emitted by the framework's `download` |
| No authentication | `#a_put_writes_the_raw_body_and_answers_the_typescript_result` | covered |
| Watcher: recursive, both event kinds, no debounce, deletions notified | `FsWatchTest#notifies_creations_modifications_and_deletions`, `#watches_directories_created_later_and_their_first_files` | covered |
| Hidden-segment regex `/(^\|[/\\])\../` and parent-directory filter | `FsWatchTest#ignores_hidden_segments_and_other_directories` | covered |
| Request `{BOOSTER_ROCKET_FUNCTION_ID, uri, name}` in that order, `uri` with the mount port | `FsWatchTest#dispatches_the_typescript_request`, `LocalFilesServerTest#uploads_are_notified_to_the_rocket_function` | covered |
| Every change of a burst notified | `FsWatchTest#a_burst_larger_than_the_jdk_event_queue_is_fully_notified` | fixed `b633079cc` |
| Unhandled rejection of a failing dispatch | `FsWatchTest#a_failing_dispatch_is_logged_and_the_watch_goes_on` | differs: logged, the watch goes on |
| Watchers never stop | `LocalFilesServerTest#the_watchers_stop_with_the_server` | differs: stopped by `unmountStack` when the server stops |
| macOS latency | `FsWatchTest` (timeouts) | differs: the JDK polls every 2 s on macOS |

### §3.8 Azure runtime → `azure.storage.*`, `azure.FileUploadedTest`

| Item | Java test | Status |
| --- | --- | --- |
| `azureStorageName`: configured name, else `<app>rf<env>` without `-`/`_`, cut to 24, not lower-cased | `types.ConstantsTest#the_azure_storage_name_is_the_configured_one`, `#without_name_it_is_app_rf_environment_without_dashes_and_underscores_cut_to_24` | covered |
| Service URL `https://<account>.blob.core.windows.net/` (host lower-cased) | `BlobServiceContractTest#the_public_endpoint_is_the_account_blob_host` | covered |
| `getBlobSasUrl`: `<service><container>/<dir>/<file>?<sas>`, path not encoded | `BlobSasSpecificationVectorsTest#blob_service_urls_carry_the_specification_sas`, `BlobServiceContractTest#presigned_put_asks_for_a_user_delegation_key_and_signs_a_write_sas` | covered / fixed `33c2b3788` (vectors) |
| User delegation key for `[now, now + 86 400 ms]`, `KeyInfo` body, dates truncated to seconds | `BlobServiceContractTest#presigned_put_asks_for_a_user_delegation_key_and_signs_a_write_sas` | covered |
| SAS: `sp` canonical (`BlobSASPermissions`), `spr=https,http`, `sv=2018-11-09`, `sr=b`, string-to-sign and query order | `BlobSasSpecificationVectorsTest#the_sas_is_the_one_of_the_specification`, `BlobSasTest` | covered / fixed `33c2b3788` (independent vectors) |
| `presignedGet` `r`, `presignedPut` `w` | `BlobServiceContractTest#presigned_get_signs_a_read_sas`, `#presigned_put_asks_for_a_user_delegation_key_and_signs_a_write_sas` | covered |
| New `BlobServiceClient` + `DefaultAzureCredential` per call | `DefaultAzureCredentialTest` | differs: one credential per process with a token cache |
| `DefaultAzureCredential` chain | `DefaultAzureCredentialTest` (8 tests) | differs: environment, managed identity, Azure CLI only (README "Not implemented") |
| REST `x-ms-version` | `BlobServiceContractTest` | differs: `2025-11-05` (the SDK's own version); the SAS keeps `2018-11-09` |
| REST `x-ms-date`: the SDK's `Date.toUTCString()` (`Tue, 03 Nov 2026 08:05:01 GMT`, two-digit day) | `BlobServiceContractTest#the_date_header_has_a_two_digit_day_like_the_sdk` | fixed: `RFC_1123_DATE_TIME` wrote a one-digit day (`Tue, 3 Nov`) on the 1st to the 9th of each month |
| `listBlobFolder`: prefix, flat, every page, `include=metadata`, item mapping | `BlobServiceContractTest#list_follows_the_markers_and_maps_the_blobs`, `#an_empty_directory_lists_the_whole_container` | covered |
| Storage failures | `#a_refused_key_request_is_a_storage_error`, `#transport_failures_are_storage_errors` | covered (`StorageError` for the SDK's `RestError`) |
| `getMetadataFromRequest`: `context.triggerMetadata`, else `InvocationContext.triggerMetadata is missing for the blob upload request` | `azure.FileUploadedTest#extracts_triggerMetadata_from_Azure_Functions_v4_InvocationContext`, `#fails_without_trigger_metadata` | covered |
| `validateMetadata`: `<container>/` prefix, `path.parse(rest).dir` against the patterns, `console.info` lines | §1.2 rows, `#ignored_blobs_are_logged_like_console_info` | covered |
| Missing `blobTrigger` → `TypeError` | `#a_missing_blob_trigger_fails_like_javascript` | covered |
| Storage account not checked | `core.BoosterRocketFilesTest#the_azure_rocket_function_stores_events_only_for_matching_configurations` | covered |
| Trigger metadata content (host-defined) | `azure.BlobTriggerEntryPointTest` | differs: the framework's Java entry point passes `blobTrigger` and `uri` only, so stored metadata and md5 ids differ from a Node deployment |

### §3.9 Azure infrastructure → `azure.infrastructure.AzureRocketSynthTest`, `FunctionsTest`

| Item | Java test | Status |
| --- | --- | --- |
| `getFunctionAppName` = `<rg>filesfunc`; `ROCKET_FUNCTION_APP_NAMES`, `ROCKET_PACKAGE_MAPPING=rocket-file-uploads:<app>` | `#synthesizes_the_storages_the_rocket_function_app_and_the_roles`, `#the_terraform_is_what_the_typescript_synthesis_writes_attribute_by_attribute` | covered |
| `mountFunctionsV4`: one package for `<rg>filesfunc`, one trigger per storage | `#packages_one_blob_trigger_function_per_storage_for_the_rocket_function_app`, `FunctionsTest#one_blob_trigger_function_per_storage` | covered |
| Trigger `fileupload_<storage>`, path `<container>/{name}`, connection `<storage>` | `#the_blob_trigger_functions_are_those_the_typescript_functions_js_registers` | fixed `33c2b3788` (diffed with the TypeScript output) |
| Generated JavaScript (`functions.js`, `nodeVersion ~22`) | same | differs: `function.json` for the Java worker on `BoosterAzureFunctions.rocketBlob`, `java_version` |
| `validateName` (`^[a-z0-9_-]+$`, container first, exact message) | `#names_are_validated_with_the_typescript_messages`, `FunctionsTest#names_are_validated_container_first`, `#invalid_names_fail_when_the_rocket_package_is_built` | covered / fixed `33c2b3788` |
| Synth order and resources (accounts, containers, connections, function app, 4 roles per storage, main app identity) | `#the_terraform_is_what_the_typescript_synthesis_writes_attribute_by_attribute` | fixed `33c2b3788` (attribute walk) |
| Construct ids `<storage>rfst`, `rffunc` (literal) | same | covered |
| Container and role ids (TypeScript: opaque token strings) | `#storages_whose_names_end_alike_get_distinct_construct_ids` | differs / fixed `875d227d2`: readable ids from names, made unique with a counter |
| `<account>__serviceUri` keys (TypeScript: name token) | `#the_terraform_is_what_the_typescript_synthesis_writes_attribute_by_attribute` | differs: literal account names (same value after apply) |
| `FunctionApp identity not found` check | — | differs: checks the identity attribute/override (TypeScript's check of a token list never fires) |
| `Error. Suffix could not be greater than 24` for long storage/container names | `#a_storage_name_too_long_for_its_construct_id_fails_the_synthesis`, `#a_container_name_too_long_for_its_construct_id_fails_the_synthesis` | covered |
| `rocketStack` push order | `Synth` (read) | covered |

### §3.10–3.12

| Item | Java test | Status |
| --- | --- | --- |
| §3.10 constraint table (every message) | rows above | covered |
| §3.11 quick reference | rows above | covered |
| §3.12 documentation vs code (AWS, `deleteFile`, paths, README examples) | — | n/a: the code is ported; AWS not ported, `deleteFile` added (see §3.3) |

## 3. Data shapes (spec §4)

| Item | Java test | Status |
| --- | --- | --- |
| §4.1 descriptor and parameters JSON (`userConfiguration`, `rocketProviderPackage` npm name) | `types.RocketFilesConfigurationTest#the_parameters_have_the_typescript_shape` | covered |
| §4.1 descriptor `packageName` | same | differs: Java packages (§3.1) |
| §4.2 local request object (key order) | `FsWatchTest#dispatches_the_typescript_request` | covered |
| §4.2 Azure request `{ blob, context }` | `azure.BlobTriggerEntryPointTest#the_rocket_blob_entry_point_request_carries_the_metadata_the_library_validates` | covered |
| §4.3 event envelope | `core.FileUploadedTest#the_envelope_is_an_uploaded_file_event_with_one_random_id`, `e2e.LocalServerProcessEndToEndTest` (stored documents) | covered |
| §4.4 entity snapshot `{id: md5, metadata}`, `2fbc2683…` | `types.UploadedFileEventTest#a_stored_event_value_binds_back_with_the_same_id`, `e2e.LocalServerProcessEndToEndTest` | covered |
| §4.5 local `ListItem` (only `lastModified`, ISO with ms) | `types.RocketFilesConfigurationTest#list_items_omit_absent_members_and_write_dates_like_javascript`, `e2e.LocalEndToEndTest` | covered |
| §4.5 Azure `ListItem` | `BlobServiceContractTest#list_follows_the_markers_and_maps_the_blobs` | covered |
| §4.6 local wire format (upload, failure, download) | `LocalFilesServerTest` rows of §3.7 | covered |
| §4.7 SAS URL shape | `BlobSasSpecificationVectorsTest` | fixed `33c2b3788` |
| §4.7 `functions.js` exact text / function app settings | `AzureRocketSynthTest#the_blob_trigger_functions_are_those_the_typescript_functions_js_registers`, `#the_terraform_is_what_the_typescript_synthesis_writes_attribute_by_attribute` | covered (Java emits `function.json`) |
| §4.8 constants | `RocketFilesParamsTest`, `ConstantsTest`, `FileController`/`BlobService`/`TerraformRoleAssignment` constants | covered |

## 4. Characterisation tests the spec asks for (§5.3)

| Item | Java test | Status |
| --- | --- | --- |
| `buildParameters` shapes and descriptors | `core.BoosterRocketFilesTest#local_descriptor_with_lower_cased_storage_names`, `#azure_descriptor_with_a_single_configuration` | covered (copies, not mutation: differs §3.1) |
| second `rocketForLocal()` message | `#a_second_rocket_on_the_same_configuration_fails` | covered |
| 2 configurations, local → 2 stores | `#the_local_rocket_function_stores_one_event_per_configuration_with_the_request_as_metadata` | covered |
| store errors swallowed, extraction errors reject | `core.FileUploadedTest#store_failures_are_logged_and_swallowed`, `#the_rocket_function_fails_with_the_first_extraction_error` | covered |
| envelope fields | `core.FileUploadedTest#the_envelope_is_an_uploaded_file_event_with_one_random_id` | covered |
| md5 `2fbc2683ad694ec4ecb387e886daf4e2` and reducer | `types.UploadedFileEventTest` | covered |
| `getUserConfiguration` cases | `types.RocketFilesParamsTest` | covered |
| `FileHandler` cases | `core.FileHandlerTest` | covered |
| `azureStorageName` cases | `types.ConstantsTest` | covered |
| local URLs with and without `ROCKET_FILES_LOCAL_PORT` | `local.RocketFilesLibraryTest#presigned_urls_point_at_the_local_server` | covered |
| local `list` and ENOENT | `local.RocketFilesLibraryTest` | covered |
| PUT/GET round trip, response JSON, folders created, overwrite | `LocalFilesServerTest`, `e2e.*` | covered |
| watcher: dispatch, hidden, other directories, deletions | `FsWatchTest` | covered |
| `getBlobSasUrl`: prefix, `se - st` 86 or 87 s, `sp`, `sv`, `spr` | `BlobSasSpecificationVectorsTest` | fixed `33c2b3788` (87 s window added) |
| `generateFunctionsCode` text and `Invalid containerName: "Bad". Must match /^[a-z0-9_-]+$/.` | `AzureRocketSynthTest#the_blob_trigger_functions_are_those_the_typescript_functions_js_registers`, `#names_are_validated_with_the_typescript_messages` | differs: `function.json` instead of the text; names, paths, connections and messages equal |
| Azure synth resources and long-name error | `AzureRocketSynthTest` | covered |

## 5. Porting notes and hazards (spec §6)

| # | Note | Java | Status |
| --- | --- | --- | --- |
| 1 | dynamic `require` of the provider library | `core.RocketFilesProviders`, `RocketProviderPackageType` (npm names kept) | covered (`FileHandlerTest#the_provider_library_must_exist`) |
| 2 | decorator registration | `RocketFilesConcepts` | differs (§3.1) |
| 3 | md5 of JavaScript JSON text | `UploadedFileEvent` with the framework's `JsonText.stringify` | covered (`UploadedFileEventTest#the_md5_uses_javascript_json_text`) |
| 4 | minimatch semantics | `Minimatch`, `BraceExpansion` | covered (differential corpora) |
| 5 | synchronous throw vs rejection, `Promise.all` | `FileHandler`, `Promises.runAll` | covered |
| 6 | Express routing | framework `Router` + `FileController` | covered; regex characters inherited (§3.7) |
| 7 | recursive `fs.watch` | `FsWatch` | covered; overflow fixed `b633079cc` |
| 8 | `process.env` port channel | `EnvironmentVariables` override | covered |
| 9 | Azure SDK replaced by REST; 86 400 ms kept | `azure.storage.*` | differs (README); 86.4 s covered |
| 10 | Java functions instead of JavaScript | `function.json` on `rocketBlob` | differs |
| 11 | cdktn tokens | literal names, references, deterministic ids | differs; collisions fixed `875d227d2` |
| 12 | app settings spread order | `TerraformFunctionApp` (`LinkedHashMap`, later keys win) | covered (attribute walk) |
| 13 | in-place mutation of user input | copies | differs |
| 14 | `ctime`, `strcmp` order | `ListHandler` | covered |
| 15 | `path.parse` / `path.join` | `NodePath` | covered (`types.NodePathTest`, Node-computed) |
| 16 | security hazards | traversal refused (400); `deleteFile` takes plain file names only; `https,http` SAS and no local auth kept | differs (traversal) / covered |

## 6. TypeScript sources

| TypeScript file | Java | Status |
| --- | --- | --- |
| `types/src/index.ts` | package `…fileuploads.types` | covered |
| `types/src/rocket-files-params.ts` | `RocketFilesParams`, `RocketFilesUserConfiguration`, `RocketFilesConfiguration`, `RocketProviderPackageType` | covered |
| `types/src/rocket-files-provider-library.ts` | `RocketFilesProviderLibrary` (+ `deleteFile`, `RocketFilesProvider`) | covered |
| `types/src/rocket-file-uploaded-library.ts` | `RocketFileUploadedLibrary` | covered |
| `types/src/rocket-files-blob-uploaded.ts` | `RocketFilesBlobUploaded` | covered |
| `types/src/uploaded-file-event.ts` | `UploadedFileEvent` | covered |
| `types/src/uploaded-file-entity.ts` | `UploadedFileEntity` | covered |
| `types/src/list-item.ts` | `ListItem`, `ListItemProperties` | covered |
| `types/src/constants.ts` | `Constants` | covered |
| `types/src/utils.ts` | `Utils`, `Minimatch`, `BraceExpansion` | covered |
| `core/src/index.ts` | `BoosterRocketFiles` | covered |
| `core/src/file-handler.ts` | `FileHandler` | covered |
| `core/src/file-uploaded.ts` | `core.FileUploaded` | covered |
| `local/src/index.ts` | `local.RocketFilesLibrary` | covered |
| `local/src/handlers/presigned-get.ts` | `local.handlers.PresignedGet` | covered |
| `local/src/handlers/presigned-put.ts` | `local.handlers.PresignedPut` | covered |
| `local/src/handlers/list.ts` | `local.handlers.ListHandler` | fixed `d332b4ffe` |
| `local/src/file-uploaded.ts` | `local.FileUploaded` (`LocalBindingData` is a type only) | covered |
| `local-infrastructure/src/index.ts` | `local.infrastructure.Rocket`, `LocalRocketFiles` | covered |
| `local-infrastructure/src/infra.ts` | `Infra` | covered |
| `local-infrastructure/src/fs-watch.ts` | `FsWatch` | fixed `b633079cc` |
| `local-infrastructure/src/controllers/file-controller.ts` | `controllers.FileController` | covered (`APIResult` failure variant never produced, as in TypeScript) |
| `local-infrastructure/src/http.ts` | the framework's `Http.requestFailed` (the same code; `HttpCodes` unused) | covered |
| `azure/src/index.ts` | `azure.RocketFilesLibrary` | covered |
| `azure/src/handlers/blob-service.ts` | `azure.handlers.BlobService`, `azure.storage.*` | covered |
| `azure/src/handlers/presigned-get.ts` | `azure.handlers.PresignedGet` | covered |
| `azure/src/handlers/presigned-put.ts` | `azure.handlers.PresignedPut` | covered |
| `azure/src/handlers/list.ts` | `azure.handlers.ListHandler` | covered |
| `azure/src/file-uploaded.ts` | `azure.FileUploaded` | covered |
| `azure-infrastructure/src/index.ts` | `azure.infrastructure.Rocket`, `AzureRocketFiles` | covered |
| `azure-infrastructure/src/helper.ts` | `Helper` | covered |
| `azure-infrastructure/src/functions/functions.ts` | `functions.Functions` | covered |
| `azure-infrastructure/src/functions/rocket-files-file-uploaded-function.ts` | `functions.RocketFilesFileUploadedFunction` | differs (`function.json`) |
| `azure-infrastructure/src/synth/synth.ts` | `synth.Synth` | covered |
| `azure-infrastructure/src/synth/terraform-storage-account.ts` | `synth.TerraformStorageAccount` | covered |
| `azure-infrastructure/src/synth/terraform-storage-container.ts` | `synth.TerraformStorageContainer`, `synth.ConstructIds` | covered (ids: §3.9) |
| `azure-infrastructure/src/synth/terraform-function-app.ts` | `synth.TerraformFunctionApp` | differs (Java worker) |
| `azure-infrastructure/src/synth/terraform-role-assignment.ts` | `synth.TerraformRoleAssignment` | covered (ids: §3.9) |

Not ported (n/a): the AWS provider and its presigned POST forms (documentation only, not in the TypeScript code); the
documentation's TODOs (storage deletion on unmount, optional events, deletion events).

## Not verifiable without Azure

That Azure accepts the generated SAS tokens and Terraform, and which binding data the Java worker hands to
`BoosterAzureFunctions.rocketBlob` for a real blob trigger.
