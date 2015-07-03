package ru.kupchinolabs.lat;

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

import java.io.File;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static ru.kupchinolabs.lat.Constants.DIR_WATCH_REGISTER_ADDRESS;
import static ru.kupchinolabs.lat.Constants.DIR_WATCH_UNREGISTER_ADDRESS;

public class WebServiceVertical extends AbstractVerticle {

    private final static Logger log = Logger.getLogger(WebServiceVertical.class.getName());

    @Override
    public void start() throws Exception {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.get("/api/list/").handler(this::handleListUserHome);
        router.get("/api/list/:path").handler(this::handleListByPath);
        router.get("/api/watch/:path").handler(this::handleWatchPath);
        router.get("/api/unwatch/:path").handler(this::handleUnwatchPath);
        router.route().handler(StaticHandler.create());
        vertx.createHttpServer().requestHandler(router::accept).listen(8080);
        //TODO add security, tls, eventbus
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
        vertx.fileSystem().readDir(path, new ReadDirResultHandler(response, path));
    }

    private void handleWatchPath(RoutingContext routingContext) {
        String path = routingContext.request().getParam("path");
        vertx.eventBus().send(DIR_WATCH_REGISTER_ADDRESS, URLDecoder.decode(path), new WatcherResultHandler(routingContext));
    }

    private void handleUnwatchPath(RoutingContext routingContext) {
        String path = routingContext.request().getParam("path");
        vertx.eventBus().send(DIR_WATCH_UNREGISTER_ADDRESS, URLDecoder.decode(path), new WatcherResultHandler(routingContext));
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
                final AtomicReference<JsonObject> object = new AtomicReference<>(); // just to use in lambda
                vertx.executeBlocking(future -> {
                    final List<String> list = result.result();
                    Collections.sort(list);
                    final JsonArray array = new JsonArray();
                    object.set(new JsonObject()
                                    .put("dir", path)
                                    .put("parent", path.substring(0, path.lastIndexOf(File.separator)))
                                    .put("contents", array)
                    );
                    for (String path : list) {
                        final FileProps fileProps = vertx.fileSystem().propsBlocking(path);
                        //TODO fix stuff like Ñ€ÐµÐ¿ÐµÑ€Ñ‚ÑƒÐ°Ñ€.doc
                        //TODO convert times to timestamps/dates
                        final String[] split = path.split(File.separator);
                        array.add(new JsonObject()
                                        .put("name", split[split.length - 1])
                                        .put("path", path)
                                        .put("isDirectory", fileProps.isDirectory())
                                        .put("isRegularFile", fileProps.isRegularFile())
                                        .put("isSymbolicLink", fileProps.isSymbolicLink())
                                        .put("lastModifiedTime", fileProps.lastModifiedTime())
                                        .put("sizeInBytes", fileProps.size())
                        );
                    }
                    future.complete();
                }, res -> {
                    if (res.succeeded()) {
                        response.putHeader("content-type", "application/json")
                                .setStatusCode(200)
                                .end(object.get().encodePrettily());
                    } else {
                        response.putHeader("content-type", "text/plain")
                                .setStatusCode(400)
                                .end(res.cause().getMessage());
                    }
                });
            } else {
                response.putHeader("content-type", "text/plain/")
                        .setStatusCode(400)
                        .end(result.cause().getMessage());
            }
        }
    }
}
