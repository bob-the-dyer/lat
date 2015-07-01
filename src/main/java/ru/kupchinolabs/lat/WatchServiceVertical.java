package ru.kupchinolabs.lat;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.Message;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.file.StandardWatchEventKinds.*;
import static ru.kupchinolabs.lat.Constants.*;

public class WatchServiceVertical extends AbstractVerticle {

    private final static Logger log = Logger.getLogger(WatchServiceVertical.class.getName());

    ConcurrentMap<String, WatchService> watchers = new ConcurrentHashMap<>();

    @Override
    public void start() throws Exception {
        vertx.eventBus().consumer(DIR_WATCH_REGISTER_ADDRESS, this::handleRegister);
        vertx.eventBus().consumer(DIR_WATCH_UNREGISTER_ADDRESS, this::handleUnregister);
        vertx.setPeriodic(2000, this::pollWatcherEvents);
    }

    private void handleRegister(Message<Object> message) {
        final String pathAsString = message.body().toString();
        if (watchers.get(pathAsString) == null) {
            final Path path = new File(pathAsString).toPath();
            try {
                WatchService watcher = FileSystems.getDefault().newWatchService();
                path.register(watcher,
                        ENTRY_CREATE,
                        ENTRY_DELETE,
                        ENTRY_MODIFY);
                watchers.putIfAbsent(pathAsString, watcher);
                log.log(Level.INFO, "registered watcher for {0}", pathAsString);
                message.reply("watcher registered, dir " + pathAsString);
            } catch (IOException e) {
                log.log(Level.WARNING, "registering watcher for {0} failed", pathAsString);
                message.fail(-1, "registering watcher failed, dir " + pathAsString);
            }
        } else {
            log.log(Level.INFO, "watcher already exists, dir {0}", pathAsString);
            message.reply("watcher already exists, dir " + pathAsString);
        }
    }

    private void handleUnregister(Message<Object> message) {
        final String pathAsString = message.body().toString();
        final WatchService watcher = watchers.get(pathAsString);
        if (watcher != null) {
            watchers.remove(pathAsString);
            try {
                watcher.close();
                log.log(Level.INFO, "unregistered watcher for {0}", pathAsString);
                message.reply("watcher unregistered, dir " + pathAsString);
            } catch (IOException e) {
                log.log(Level.WARNING, "unregistering watcher for {0} failed", pathAsString);
                message.fail(-1, e.getMessage());
            }
        } else {
            log.log(Level.INFO, "watcher is not registered, dir {0}", pathAsString);
            message.reply("watcher is not registered, dir " + pathAsString);
        }
    }

    private void pollWatcherEvents(Long timerId) {
        for (String watcherDir : watchers.keySet()) {
            final WatchService watcher = watchers.get(watcherDir);
            WatchKey key = watcher.poll();
            if (key != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    // OVERFLOW event can
                    // occur regardless if events
                    // are lost or discarded.
                    if (kind == OVERFLOW) {
                        continue;
                    }
                    log.log(Level.INFO, "watcher event {0} on {1}, path {2}, ",
                            new String[]{kind.name(), watcherDir, event.context().toString()});
                    vertx.eventBus().publish(DIR_WATCH_NOTIFY_ADDRESS, watcherDir);
                }
                // Reset the key -- this step is critical if you want to
                // receive further watch events.  If the key is no longer valid,
                // the directory is inaccessible
                if (!key.reset()) {
                    log.log(Level.INFO, "dir {0} is inaccessible, removing watcher", watcherDir);
                    watchers.remove(watcherDir);
                }
            }
        }
    }
}
