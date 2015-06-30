package ru.kupchinolabs.lat;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
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

    private static class AsyncResultHandler implements Handler<AsyncResult<List<String>>> {

        private final HttpServerResponse response;

        public AsyncResultHandler(HttpServerResponse response) {
            this.response = response;
        }

        @Override
        public void handle(AsyncResult<List<String>> result) {
            if (result.succeeded()) {
                final List<String> list = result.result();
                Collections.sort(list);
                response.end(String.valueOf(list));
                //TODO convert to json
                //TODO fix stuff like Ñ€ÐµÐ¿ÐµÑ€Ñ‚ÑƒÐ°Ñ€.doc
                //TODO add dir listener to publish dir changes via eventbus, remove after some time or on all websocket client closed
            } else {
                response.setStatusCode(400).end(result.cause().getMessage());
            }
        }
    }
}
