package ru.kupchinolabs.lat;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.file.FileProps;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.net.URLDecoder;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class RestServer extends AbstractVerticle {

    private final static Logger log = Logger.getLogger(RestServer.class.getName());

    @Override
    public void start() throws Exception {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.get("/api/list/").handler(this::handleUserHome);
        router.get("/api/list/:path").handler(this::handleListByPath);
        vertx.createHttpServer().requestHandler(router::accept).listen(8080);
        //TODO add security, tls, eventbus
    }

    private void handleUserHome(RoutingContext routingContext) {
        final String path = System.getProperty("user.home");
        handle(routingContext, path);
    }

    private void handleListByPath(RoutingContext routingContext) {
        String path = URLDecoder.decode(routingContext.request().getParam("path"));
        handle(routingContext, path);
    }

    private void handle(RoutingContext routingContext, String path) {
        HttpServerResponse response = routingContext.response();
        vertx.fileSystem().readDir(path, new AsyncResultHandler(response));
    }

    private class AsyncResultHandler implements Handler<AsyncResult<List<String>>> {

        private final HttpServerResponse response;

        public AsyncResultHandler(HttpServerResponse response) {
            this.response = response;
        }

        @Override
        public void handle(AsyncResult<List<String>> result) {
            if (result.succeeded()) {
                final List<String> list = result.result();
                Collections.sort(list);
                final JsonArray array = new JsonArray();
                JsonObject object = new JsonObject().put("list", array);
                for (String path : list) {
                    final FileProps fileProps = vertx.fileSystem().propsBlocking(path);
                    //TODO convert times to timestamps/dates
                    array.add(new JsonObject()
                                    .put("path", path)
                                    .put("creationTime", fileProps.creationTime())
                                    .put("isDirectory", fileProps.isDirectory())
                                    .put("isRegularFile", fileProps.isRegularFile())
                                    .put("isSymbolicLink", fileProps.isSymbolicLink())
                                    .put("lastAccessTime", fileProps.lastAccessTime())
                                    .put("lastModifiedTime", fileProps.lastModifiedTime())
                                    .put("sizeInBytes", fileProps.size())
                    );
                }
                response.end(object.encodePrettily());
                //TODO fix stuff like Ñ€ÐµÐ¿ÐµÑ€Ñ‚ÑƒÐ°Ñ€.doc
                //TODO add dir listener to publish dir changes via eventbus, remove after some time or on all websocket client closed
            } else {
                response.setStatusCode(400).end(result.cause().getMessage());
            }
        }
    }
}
