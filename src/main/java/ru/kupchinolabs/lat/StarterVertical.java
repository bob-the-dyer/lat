package ru.kupchinolabs.lat;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.example.util.Runner;
import io.vertx.ext.web.Router;

public class StarterVertical extends AbstractVerticle {

    // Convenience method so you can run it in your IDE
    public static void main(String[] args) {
        Runner.runExample(StarterVertical.class);
    }

    @Override
    public void start() throws Exception {
        vertx.deployVerticle(new HelloWorldVertical());
    }
}
