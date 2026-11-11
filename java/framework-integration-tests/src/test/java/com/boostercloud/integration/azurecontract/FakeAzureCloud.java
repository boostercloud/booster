package com.boostercloud.integration.azurecontract;

import com.boostercloud.framework.common.Retrier;
import com.boostercloud.framework.common.RunCommandError;
import com.boostercloud.framework.provider.azure.infrastructure.azure.HttpTransport;
import com.boostercloud.framework.provider.azure.infrastructure.process.Command;
import com.boostercloud.framework.provider.azure.infrastructure.process.CommandRunner;
import com.boostercloud.framework.types.BoosterJson;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * The outside world of a deploy and a nuke, faked and <b>stateful</b>, for the Azure infrastructure's injectable
 * {@link CommandRunner} ({@code terraform}, {@code az}), {@link HttpTransport} (Entra ID, Resource Manager, Kudu, the
 * function apps) and {@link Retrier.Sleeper}: nothing is run and nothing leaves the JVM, but the answers follow what
 * the commands and calls would have done to a subscription, so the flows can be checked end to end:
 *
 * <ul>
 *   <li>{@code terraform init} and {@code plan} answer nothing; {@code terraform apply} "creates" what the synthesized
 *       {@code cdk.tf.json} of its working directory declares (resource groups, function apps, Web PubSub services);
 *   <li>{@code az group show --name <rg>} answers the resource group, or fails like {@code az} with
 *       {@code (ResourceGroupNotFound)};
 *   <li>Entra ID issues a token for the service principal; Resource Manager calls need it;
 *   <li>the publishing credentials of an existing function app, the Kudu {@code zipDeploy} with those credentials (the
 *       package is kept), the cold-start GraphQL request, the host keys ({@code webpubsub_extension} appears once a
 *       package declaring a {@code webPubSubTrigger} was uploaded and the host was started, as the Functions runtime
 *       does), the Web PubSub hub update, and the deletion of a resource group as a long-running operation.
 * </ul>
 *
 * Every command, request and wait is recorded; anything the fake does not know is answered with an error and recorded
 * in {@link #failures()}.
 */
final class FakeAzureCloud implements CommandRunner, HttpTransport, Retrier.Sleeper {

  static final String ARM = "https://management.azure.com";
  static final String TOKEN = "fake-arm-token";
  private static final Pattern PUBLISHING_CREDENTIALS = Pattern.compile(
      "POST " + Pattern.quote(ARM) + "/subscriptions/([^/]+)/resourceGroups/([^/]+)/providers/Microsoft\\.Web/sites/"
          + "([^/]+)/config/publishingcredentials/list\\?api-version=2023-12-01");
  private static final Pattern HOST_KEYS = Pattern.compile(
      "POST " + Pattern.quote(ARM) + "/subscriptions/([^/]+)/resourceGroups/([^/]+)/providers/Microsoft\\.Web/sites/"
          + "([^/]+)/host/default/listkeys\\?api-version=2023-12-01");
  private static final Pattern HUB = Pattern.compile(
      "PUT " + Pattern.quote(ARM) + "/subscriptions/([^/]+)/resourceGroups/([^/]+)/providers/"
          + "Microsoft\\.SignalRService/webPubSub/([^/]+)/hubs/([^/?]+)\\?api-version=2023-02-01");
  private static final Pattern DELETE_GROUP = Pattern.compile(
      "DELETE " + Pattern.quote(ARM) + "/subscriptions/([^/]+)/resourcegroups/([^/?]+)\\?api-version=2021-04-01");
  private static final Pattern OPERATION = Pattern.compile(
      "GET " + Pattern.quote(ARM) + "/subscriptions/([^/]+)/operationresults/([^/?]+)\\?api-version=2021-04-01");
  private static final Pattern ZIP_DEPLOY = Pattern.compile(
      "POST https://([^.]+)\\.scm\\.azurewebsites\\.net/api/zipDeploy\\?isAsync=true");
  private static final Pattern COLD_START = Pattern.compile("POST https://([^.]+)\\.azurewebsites\\.net/api/graphql");

  /**
   * An upload to Kudu.
   *
   * @param functionApp the function app
   * @param authorization the {@code Authorization} header
   * @param body the package
   */
  record Upload(String functionApp, String authorization, Object body) {}

  private final String tenantId;
  private final String subscriptionId;
  private final List<Command> commands = new ArrayList<>();
  private final List<Request> requests = new ArrayList<>();
  private final List<Long> sleeps = new ArrayList<>();
  private final List<String> failures = new ArrayList<>();
  private final List<Upload> uploads = new ArrayList<>();
  private final Map<String, String> resourceGroups = new LinkedHashMap<>();
  private final Set<String> functionApps = new LinkedHashSet<>();
  private final Set<String> webPubSubServices = new LinkedHashSet<>();
  private final Set<String> startedHosts = new LinkedHashSet<>();
  private final Set<String> webPubSubTriggerApps = new LinkedHashSet<>();
  private final Map<String, Object> hubs = new LinkedHashMap<>();
  private final Map<String, String> pendingDeletions = new LinkedHashMap<>();
  private String failingCommandPrefix;
  private String failingCommandStderr;
  private int operations;

  /**
   * @param tenantId the Entra ID tenant of the service principal
   * @param subscriptionId the subscription
   */
  FakeAzureCloud(String tenantId, String subscriptionId) {
    this.tenantId = tenantId;
    this.subscriptionId = subscriptionId;
  }

  /**
   * Makes the commands whose command line starts with {@code prefix} fail with {@code stderr}.
   *
   * @param prefix a command line prefix
   * @param stderr the standard error
   * @return this cloud
   */
  synchronized FakeAzureCloud failing(String prefix, String stderr) {
    this.failingCommandPrefix = prefix;
    this.failingCommandStderr = stderr;
    return this;
  }

  /**
   * @return the commands run, in order
   */
  synchronized List<Command> commands() {
    return List.copyOf(commands);
  }

  /**
   * @return the HTTP requests sent, in order
   */
  synchronized List<Request> requests() {
    return List.copyOf(requests);
  }

  /**
   * @return {@code <METHOD> <url>} of every request except the Entra ID token requests
   */
  synchronized List<String> calls() {
    return requests.stream().filter(request -> !request.uri().getHost().equals("login.microsoftonline.com"))
        .map(request -> request.method() + " " + request.uri()).toList();
  }

  /**
   * @return the waits of the polling loops, in order
   */
  synchronized List<Long> sleeps() {
    return List.copyOf(sleeps);
  }

  /**
   * @return what the fake refused or did not know
   */
  synchronized List<String> failures() {
    return List.copyOf(failures);
  }

  /**
   * @return the packages uploaded to Kudu, in order
   */
  synchronized List<Upload> uploads() {
    return List.copyOf(uploads);
  }

  /**
   * @return the hub definitions sent to Resource Manager by {@code <service>/<hub>}
   */
  synchronized Map<String, Object> hubs() {
    return Map.copyOf(hubs);
  }

  /**
   * @return the resource groups that exist, with their location
   */
  synchronized Map<String, String> resourceGroups() {
    return Map.copyOf(resourceGroups);
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Commands

  @Override
  public synchronized String run(Command command) {
    commands.add(command);
    String commandLine = command.commandLine();
    if (failingCommandPrefix != null && commandLine.startsWith(failingCommandPrefix)) {
      throw new RunCommandError(1, "", failingCommandStderr);
    }
    List<String> arguments = command.arguments();
    if (arguments.size() >= 2 && arguments.getFirst().equals("terraform")) {
      switch (arguments.get(1)) {
        case "init", "plan", "validate" -> {
          return "";
        }
        case "apply" -> {
          apply(command.workingDirectory());
          return "";
        }
        default -> {
          // fall through to the failure below
        }
      }
    }
    Matcher groupShow = Pattern.compile("az group show --name (\\S+)").matcher(commandLine);
    if (groupShow.matches()) {
      String name = groupShow.group(1);
      if (!resourceGroups.containsKey(name)) {
        throw new RunCommandError(3, "", "ERROR: (ResourceGroupNotFound) Resource group '" + name
            + "' could not be found.\nCode: ResourceGroupNotFound\nMessage: Resource group '" + name
            + "' could not be found.");
      }
      Map<String, Object> group = new LinkedHashMap<>();
      group.put("id", "/subscriptions/" + subscriptionId + "/resourceGroups/" + name);
      group.put("location", resourceGroups.get(name).toLowerCase(java.util.Locale.ROOT).replace(" ", ""));
      group.put("managedBy", null);
      group.put("name", name);
      group.put("properties", Map.of("provisioningState", "Succeeded"));
      group.put("tags", null);
      group.put("type", "Microsoft.Resources/resourceGroups");
      return BoosterJson.toJson(group);
    }
    failures.add("Unknown command: " + commandLine);
    throw new RunCommandError(127, "", "fake cloud: unknown command " + commandLine);
  }

  @SuppressWarnings("unchecked")
  private void apply(Path stackDirectory) {
    Path stack = stackDirectory.resolve("cdk.tf.json");
    Map<String, Object> json;
    try {
      json = BoosterJson.parseObject(Files.readString(stack, StandardCharsets.UTF_8));
    } catch (IOException e) {
      failures.add("terraform apply without a stack: " + stack);
      throw new RunCommandError(1, "", "Error: No configuration files");
    }
    Map<String, Object> resources = (Map<String, Object>) json.getOrDefault("resource", Map.of());
    ((Map<String, Object>) resources.getOrDefault("azurerm_resource_group", Map.of())).values().forEach(group ->
        resourceGroups.put(String.valueOf(((Map<String, Object>) group).get("name")),
            String.valueOf(((Map<String, Object>) group).get("location"))));
    ((Map<String, Object>) resources.getOrDefault("azurerm_windows_function_app", Map.of())).values().forEach(app ->
        functionApps.add(String.valueOf(((Map<String, Object>) app).get("name"))));
    ((Map<String, Object>) resources.getOrDefault("azurerm_web_pubsub", Map.of())).values().forEach(service ->
        webPubSubServices.add(String.valueOf(((Map<String, Object>) service).get("name"))));
  }

  // ---------------------------------------------------------------------------------------------------------------
  // HTTP

  @Override
  public synchronized Response send(Request request) {
    requests.add(request);
    String call = request.method() + " " + request.uri();
    if (call.equals("POST https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token")) {
      return token(request);
    }
    if (request.uri().getHost().equals("management.azure.com")
        && !request.header("Authorization").orElse("").equals("Bearer " + TOKEN)) {
      return fail(call, 401, "{\"error\":{\"code\":\"AuthenticationFailed\",\"message\":\"Missing bearer token\"}}");
    }
    Matcher matcher = PUBLISHING_CREDENTIALS.matcher(call);
    if (matcher.matches() && known(matcher.group(1), matcher.group(2)) && functionApps.contains(matcher.group(3))) {
      String app = matcher.group(3);
      return Response.of(200, "{\"name\":\"" + app + "\",\"type\":\"Microsoft.Web/sites/config\",\"properties\":"
          + "{\"publishingUserName\":\"$" + app + "\",\"publishingPassword\":\"pw-" + app + "\"}}");
    }
    matcher = ZIP_DEPLOY.matcher(call);
    if (matcher.matches() && functionApps.contains(matcher.group(1))) {
      return zipDeploy(matcher.group(1), request);
    }
    matcher = COLD_START.matcher(call);
    if (matcher.matches() && functionApps.contains(matcher.group(1))) {
      startedHosts.add(matcher.group(1));
      return Response.of(200, "{\"data\":{\"__typename\":\"Query\"}}");
    }
    matcher = HOST_KEYS.matcher(call);
    if (matcher.matches() && known(matcher.group(1), matcher.group(2)) && functionApps.contains(matcher.group(3))) {
      String app = matcher.group(3);
      boolean keyCreated = startedHosts.contains(app) && webPubSubTriggerApps.contains(app);
      return Response.of(200, "{\"masterKey\":\"master-" + app + "\",\"functionKeys\":{\"default\":\"default-" + app
          + "\"},\"systemKeys\":" + (keyCreated ? "{\"webpubsub_extension\":\"extension-key-" + app + "\"}" : "{}")
          + "}");
    }
    matcher = HUB.matcher(call);
    if (matcher.matches() && known(matcher.group(1), matcher.group(2)) && webPubSubServices.contains(matcher.group(3))) {
      Object body = BoosterJson.parse(request.bodyText());
      hubs.put(matcher.group(3) + "/" + matcher.group(4), body);
      Map<String, Object> properties = new LinkedHashMap<>(asMap(asMap(body).get("properties")));
      properties.put("provisioningState", "Succeeded");
      return Response.of(200, BoosterJson.toJson(Map.of("name", matcher.group(4), "properties", properties)));
    }
    matcher = DELETE_GROUP.matcher(call);
    if (matcher.matches() && matcher.group(1).equals(subscriptionId)) {
      String group = matcher.group(2);
      if (!resourceGroups.containsKey(group)) {
        return Response.of(404, "{\"error\":{\"code\":\"ResourceGroupNotFound\",\"message\":\"Resource group '" + group
            + "' could not be found.\"}}");
      }
      String operation = "delete-" + (++operations);
      pendingDeletions.put(operation, group);
      return Response.of(202, "", "Location", ARM + "/subscriptions/" + subscriptionId + "/operationresults/"
          + operation + "?api-version=2021-04-01", "Retry-After", "15");
    }
    matcher = OPERATION.matcher(call);
    if (matcher.matches() && pendingDeletions.containsKey(matcher.group(2))) {
      resourceGroups.remove(pendingDeletions.remove(matcher.group(2)));
      functionApps.clear();
      webPubSubServices.clear();
      return Response.of(200, "");
    }
    return fail(call, 404, "{\"error\":{\"code\":\"NotFound\",\"message\":\"The fake cloud has no " + call + "\"}}");
  }

  private Response token(Request request) {
    Map<String, String> form = new LinkedHashMap<>();
    for (String pair : request.bodyText().split("&")) {
      int equals = pair.indexOf('=');
      form.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
          URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
    }
    if (!"client_credentials".equals(form.get("grant_type")) || form.get("client_id") == null
        || form.get("client_secret") == null || !(ARM + "/.default").equals(form.get("scope"))) {
      return fail("POST token", 400, "{\"error\":\"invalid_request\"}");
    }
    return Response.of(200, "{\"token_type\":\"Bearer\",\"expires_in\":3599,\"access_token\":\"" + TOKEN + "\"}");
  }

  private Response zipDeploy(String app, Request request) {
    String expected = "Basic " + Base64.getEncoder().encodeToString(("$" + app + ":pw-" + app)
        .getBytes(StandardCharsets.UTF_8));
    String authorization = request.header("Authorization").orElse("");
    if (!authorization.equals(expected)) {
      return fail("zipDeploy " + app, 401, "");
    }
    uploads.add(new Upload(app, authorization, request.body()));
    if (request.body() instanceof Path zip && declaresWebPubSubTrigger(zip)) {
      webPubSubTriggerApps.add(app);
    }
    return Response.of(202, "");
  }

  private static boolean declaresWebPubSubTrigger(Path zip) {
    try (ZipFile file = new ZipFile(zip.toFile())) {
      return file.stream().filter(entry -> entry.getName().endsWith("/function.json")).anyMatch(entry -> {
        try {
          return new String(file.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8)
              .contains("\"webPubSubTrigger\"");
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private boolean known(String subscription, String group) {
    return subscription.equals(subscriptionId) && resourceGroups.containsKey(group);
  }

  private Response fail(String call, int status, String body) {
    failures.add(call + " -> " + status);
    return Response.of(status, body);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Waits

  @Override
  public synchronized void sleep(long millis) {
    sleeps.add(millis);
  }
}
