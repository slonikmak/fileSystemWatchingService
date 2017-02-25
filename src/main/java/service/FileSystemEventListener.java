package service;

/**
 * Interface fo external event listeners
 */
@FunctionalInterface
public interface FileSystemEventListener {
    /**
     *
     * @param event FileSystemWatchEvent
     */
    void processEvent(FileSystemWatchEvent event);
}
