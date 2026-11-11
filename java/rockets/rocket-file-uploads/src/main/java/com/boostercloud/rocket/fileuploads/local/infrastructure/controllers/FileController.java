package com.boostercloud.rocket.fileuploads.local.infrastructure.controllers;

import com.boostercloud.framework.provider.local.infrastructure.Http;
import com.boostercloud.framework.provider.local.infrastructure.web.HttpRequest;
import com.boostercloud.framework.provider.local.infrastructure.web.HttpResponse;
import com.boostercloud.framework.provider.local.infrastructure.web.NextFunction;
import com.boostercloud.framework.provider.local.infrastructure.web.Router;
import com.boostercloud.framework.types.errors.InvalidParameterError;
import com.boostercloud.framework.types.errors.NotFoundError;
import com.boostercloud.rocket.fileuploads.local.LocalStorage;
import com.boostercloud.rocket.fileuploads.local.infrastructure.FsWatch;
import com.boostercloud.rocket.fileuploads.types.NodePath;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Port of {@code controllers/file-controller.ts}: the routes that emulate presigned URLs on the local server, mounted
 * by {@code Infra} at {@code /<storage>/<container>}:
 *
 * <ul>
 *   <li>{@code PUT /<directory>/:fileName}: writes the raw request body to
 *       {@code <project>/<storage>/<container>/<url directory>/<fileName>} (directories created as needed, an existing
 *       file truncated) and answers {@code 200}
 *       {@code {"status":"success","result":{"message":"File uploaded","data":{"name","mimeType":"unknown","size":0}}}};
 *   <li>{@code GET /<directory>/:fileName}: sends the file as an attachment ({@code res.download}).
 * </ul>
 *
 * <p>As in TypeScript the directory pattern is an Express route path (not a glob: {@code *} matches anything, even
 * several segments), the directory part of the target comes from the raw request URL and the file name is the decoded
 * route parameter; nothing is authenticated. A failed upload answers {@code requestFailed} (status of the error class,
 * {@code {"title","reason"}}) and passes the error on. Java hardening: a target outside the container directory (for
 * instance a file name {@code ..%2F..%2Fx}) is refused with {@code 400 Invalid Parameter Error}. Java-only: with the
 * storage's watcher, every written file is notified before the answer ({@link FsWatch#changed(Path)}).
 */
public final class FileController {

  /** {@code DEFAULT_MIME_TYPE}: the local provider always answers {@code unknown}. */
  public static final String DEFAULT_MIME_TYPE = "unknown";
  /** {@code DEFAULT_FILE_SIZE}: the local provider always answers {@code 0}. */
  public static final int DEFAULT_FILE_SIZE = 0;

  private final Router router = new Router();
  private final String storageName;
  private final String containerName;
  private final String directory;
  private final Path path;
  private final FsWatch watch;

  /**
   * A controller whose uploads are left to the file watcher (TypeScript's constructor).
   *
   * @param projectDirectory the project directory
   * @param storageName the storage
   * @param containerName the container
   * @param directory the directory pattern (an Express route path)
   */
  public FileController(Path projectDirectory, String storageName, String containerName, String directory) {
    this(projectDirectory, storageName, containerName, directory, null);
  }

  /**
   * Java-only: a controller that also notifies every upload to the storage's watcher before answering
   * ({@link FsWatch#changed(Path)}), so an acknowledged upload is notified even if the server stops before the watch
   * service reports it.
   *
   * @param projectDirectory the project directory
   * @param storageName the storage
   * @param containerName the container
   * @param directory the directory pattern (an Express route path)
   * @param watch the watcher of the storage's container, or {@code null}
   */
  public FileController(Path projectDirectory, String storageName, String containerName, String directory,
      FsWatch watch) {
    this.storageName = storageName;
    this.containerName = containerName;
    this.directory = directory;
    this.router.put("/" + directory + "/:fileName", this::uploadFile);
    this.router.get("/" + directory + "/:fileName", this::getFile);
    this.path = LocalStorage.containerDirectory(projectDirectory, storageName, containerName);
    this.watch = watch;
  }

  /**
   * @return the router with the two routes
   */
  public Router router() {
    return router;
  }

  /**
   * @return the storage
   */
  public String storageName() {
    return storageName;
  }

  /**
   * @return the container
   */
  public String containerName() {
    return containerName;
  }

  /**
   * @return the directory pattern
   */
  public String directory() {
    return directory;
  }

  /**
   * {@code getFile(req, res, next)}: {@code res.download(filePath)}. A missing file fails with Node's {@code ENOENT}
   * message (answered by the server's error handler with status 500), a file name starting with a dot answers
   * {@code 404 Not Found} ({@code send}'s dot-file rule) and a directory falls through to the next route.
   *
   * @param req the request
   * @param res the response
   * @param next the next handler
   */
  public void getFile(HttpRequest req, HttpResponse res, NextFunction next) {
    Path expectedPath = getExpectedPath(req);
    String fileName = req.param("fileName");
    Path filePath = target(expectedPath, fileName);
    Path name = filePath.getFileName();
    if (name != null && name.toString().startsWith(".")) {
      throw new NotFoundError("Not Found");
    }
    if (Files.isDirectory(filePath)) {
      next.next();
      return;
    }
    res.download(filePath);
  }

  /**
   * {@code uploadFile(req, res, next)}.
   *
   * @param req the request
   * @param res the response
   * @param next the next handler
   */
  public void uploadFile(HttpRequest req, HttpResponse res, NextFunction next) {
    try {
      Path expectedPath = getExpectedPath(req);
      String fileName = req.param("fileName");
      Path filePath = target(expectedPath, fileName);
      if (!Files.exists(expectedPath)) {
        try {
          Files.createDirectories(expectedPath);
        } catch (IOException e) {
          throw LocalStorage.nodeError(e, "mkdir", expectedPath);
        }
      }
      try (InputStream body = req.inputStream();
          OutputStream writeStream = Files.newOutputStream(filePath, StandardOpenOption.CREATE,
              StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
        body.transferTo(writeStream);
      } catch (IOException e) {
        throw LocalStorage.nodeError(e, "open", filePath);
      }
      if (watch != null) {
        watch.changed(filePath);
      }
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("name", fileName);
      data.put("mimeType", DEFAULT_MIME_TYPE);
      data.put("size", DEFAULT_FILE_SIZE);
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("message", "File uploaded");
      result.put("data", data);
      Map<String, Object> apiResult = new LinkedHashMap<>();
      apiResult.put("status", "success");
      apiResult.put("result", result);
      res.send(apiResult);
    } catch (RuntimeException e) {
      Http.requestFailed(e, res);
      next.next(e);
    }
  }

  /** {@code path.join(this._path, path.parse(req.url).dir)}: the raw URL (query string included), not decoded. */
  private Path getExpectedPath(HttpRequest req) {
    Path expected = Path.of(NodePath.join(path.toString(), NodePath.parseDir(req.url())));
    if (!expected.normalize().startsWith(path.normalize())) {
      throw new InvalidParameterError("Invalid file path " + req.url());
    }
    return expected;
  }

  /** {@code path.join(expectedPath, fileName)}, refused when it leaves the container directory. */
  private Path target(Path expectedPath, String fileName) {
    Path filePath = Path.of(NodePath.join(expectedPath.toString(), fileName));
    Path normalized = filePath.normalize();
    if (!normalized.startsWith(path.normalize()) || normalized.equals(path.normalize())) {
      throw new InvalidParameterError("Invalid file name " + fileName);
    }
    return filePath;
  }
}
