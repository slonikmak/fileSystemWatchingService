package service;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * Created by Anton on 25.02.2017.
 */
class InnerWatchEvent {
    Path path;
    WatchEvent<Path> event;

    InnerWatchEvent(Path path, WatchEvent<Path> event) {
        this.path = path;
        this.event = event;
    }

}
