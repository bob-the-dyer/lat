package ru.spb.kupchinolabs.lat;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.example.util.Runner;

import java.util.logging.Level;
import java.util.logging.Logger;

public class StarterVertical extends AbstractVerticle {

    private final static Logger log = Logger.getLogger(StarterVertical.class.getName());

    // Convenience method so you can run it in your IDE
    public static void main(String[] args) {
        Runner.runExample(StarterVertical.class);
    }

    @Override
    public void start() throws Exception {
        log.info("starting to deploy verticals");
        final AsyncResultHandler completionHandler = new AsyncResultHandler();
        if (should("deploywebservice")) {
            vertx.deployVerticle(CatalogServiceVertical.class.getName(), completionHandler);
        }
        if (should("deploywatchservice")) {
            vertx.deployVerticle(WatchServiceVertical.class.getName(), completionHandler);
        }
    }

    private boolean should(String key) {
        return config().getBoolean(key) == null || config().getBoolean(key);
    }

    private class AsyncResultHandler implements Handler<AsyncResult<String>> {
        @Override
        public void handle(AsyncResult<String> event) {
            if (event.succeeded()) {
                log.info("deploy succeeded - " + event.result());
            } else {
                log.log(Level.SEVERE, "deploy failed: " + event.cause().getMessage());
                vertx.close();
            }
        }
    }
}
