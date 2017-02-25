package service;

/**
 * Created by Anton on 25.02.2017.
 */
@FunctionalInterface
public interface FileSystemEventListener {
    void processEvent(FileSystemWatchEvent event);
}
