package ru.spb.kupchinolabs.lat;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.FileProps;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.sockjs.BridgeOptions;
import io.vertx.ext.web.handler.sockjs.PermittedOptions;
import io.vertx.ext.web.handler.sockjs.SockJSHandler;

import java.io.File;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CatalogServiceVertical extends AbstractVerticle {

    private final static Logger log = Logger.getLogger(CatalogServiceVertical.class.getName());

    @Override
    public void start() throws Exception {
        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());

        router.get("/api/catalog/list/").handler(this::handleListUserHome);
        router.get("/api/catalog/list/:path").handler(this::handleListByPath);
        router.put("/api/catalog/watch/:path").handler(this::handleWatchPath);
        router.delete("/api/catalog/watch/:path").handler(this::handleUnwatchPath);

        SockJSHandler sockJSHandler = SockJSHandler.create(vertx);
        BridgeOptions options = new BridgeOptions()
                .addOutboundPermitted(new PermittedOptions().setAddress(Constants.DIR_WATCH_NOTIFY_ADDRESS));
        sockJSHandler.bridge(options);

        router.route("/eventbus/*").handler(sockJSHandler);

        router.route().handler(StaticHandler.create());

        vertx.createHttpServer().requestHandler(router::accept).listen(8080);
        //TODO add security, tls
    }

    private void handleListUserHome(RoutingContext routingContext) {
        final String path = System.getProperty("user.home");
        handle(routingContext, path);
    }

    private void handleListByPath(RoutingContext routingContext) {
        String path = URLDecoder.decode(routingContext.request().getParam("path"));
        handle(routingContext, path);
    }

    private void handle(RoutingContext routingContext, String path) {
        HttpServerResponse response = routingContext.response();
        log.log(Level.INFO, "starting reading dir {0}", path);
        vertx.fileSystem().readDir(path, new ReadDirResultHandler(response, path));
    }

    private void handleWatchPath(RoutingContext routingContext) {
        String path = routingContext.request().getParam("path");
        vertx.eventBus().send(Constants.DIR_WATCH_REGISTER_ADDRESS, URLDecoder.decode(path), new WatcherResultHandler(routingContext));
    }

    private void handleUnwatchPath(RoutingContext routingContext) {
        String path = routingContext.request().getParam("path");
        vertx.eventBus().send(Constants.DIR_WATCH_UNREGISTER_ADDRESS, URLDecoder.decode(path), new WatcherResultHandler(routingContext));
    }

    private static class WatcherResultHandler implements Handler<AsyncResult<Message<Object>>> {
        private final RoutingContext routingContext;

        public WatcherResultHandler(RoutingContext routingContext) {
            this.routingContext = routingContext;
        }

        @Override
        public void handle(AsyncResult<Message<Object>> res) {
            if (res.succeeded()) {
                routingContext.response()
                        .putHeader("content-type", "text/plain")
                        .setStatusCode(200)
                        .end(res.result().body().toString());

            } else {
                routingContext.response()
                        .putHeader("content-type", "text/plain")
                        .setStatusCode(400)
                        .end(res.cause().getMessage());
            }
        }
    }

    private class ReadDirResultHandler implements Handler<AsyncResult<List<String>>> {

        private final HttpServerResponse response;
        private final String path;

        public ReadDirResultHandler(HttpServerResponse response, String path) {
            this.response = response;
            this.path = path;
        }

        @Override
        public void handle(AsyncResult<List<String>> result) {
            if (result.succeeded()) {
                log.log(Level.INFO, "handling success reading of dir {0}", path);
                final JsonObject[] json = new JsonObject[1];
                vertx.executeBlocking(future -> {
                    final List<String> list = result.result();
                    ////////////////////
                    // TODO there could be dirs with huge amount of files, like C:\Windows\winsxs, need to deal with it somehow
                    ////////////////////
                    Collections.sort(list);
                    final JsonArray array = new JsonArray();
                    Path parentPath = Paths.get(path).getParent();
                    final String parent = parentPath != null ? parentPath.toString() : "";
                    json[0] = new JsonObject()
                            .put("dir", path)
                            .put("parent", parent)
                            .put("contents", array);
                    for (String path : list) {
                        FileProps fileProps = null;
                        try {
                            fileProps = vertx.fileSystem().propsBlocking(path);
                        } catch (Exception e) {
                            log.log(Level.INFO, "Error while getting properties for {0}: {1}", new String[]{path, e.getMessage()});
                        }
                        final Path fileNamePath = Paths.get(path).getFileName();
                        final String name = fileNamePath != null ? fileNamePath.toString() : Paths.get(path).toString();
                        final boolean hidden = Paths.get(path).toFile().isHidden();
                        array.add(new JsonObject()
                                        .put("name", name)
                                        .put("path", path)
                                        .put("isDir", fileProps != null ? fileProps.isDirectory() : false)
                                        .put("isFile", fileProps != null ? fileProps.isRegularFile() : false)
                                        .put("isSymLink", fileProps != null ? fileProps.isSymbolicLink() : false)
                                        .put("isOther", fileProps != null ? fileProps.isOther() : false)
                                        .put("isHidden", hidden)
                                        .put("modified", fileProps != null ? fileProps.lastModifiedTime() : "unknown")
                                        .put("size", fileProps != null ? fileProps.size() : "unknown")
                        );
                    }
                    future.complete();
                }, res -> {
                    if (res.succeeded()) {
                        response.putHeader("content-type", "application/json")
                                .setStatusCode(200)
                                .end(json[0].encodePrettily());
                    } else {
                        response.putHeader("content-type", "text/plain")
                                .setStatusCode(400)
                                .end("Failed to prepare response for " + path + ": " + res.cause());
                    }
                });
            } else {
                /////////////////////
                // This is workaround for windows: some folders like My Documents have isDirectory=true
                // however return listFiles = null, vert.x fails with NPE, not nice
                /////////////////////
                final File file = Paths.get(path).toFile();
                boolean isBadDir = file.isDirectory() && file.listFiles() == null;

                log.log(Level.SEVERE, "handling failure reading of dir {0}:badDir={1}:{2}",
                        new String[]{path, String.valueOf(isBadDir), result.cause().toString()});
                response.putHeader("content-type", "text/plain")
                        .setStatusCode(400)
                        .end("Failed to read '" + path + "' contents: " + (isBadDir ? " strange folder ..." : result.cause()));
            }
        }
    }
}
