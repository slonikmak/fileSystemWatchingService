package service;

import javafx.util.Pair;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * File System Watching Service
 * Generate events CREATE, MODIFY, MOVE, RENAME, DELETE
 */
public class FileSystemWatchingService {

    private List<FileSystemEventListener> listeners = new ArrayList<>();


    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;

    BlockingQueue<Pair<Path, List<WatchEvent<?>>>> queue = new LinkedBlockingQueue<>();

    /**
     * Creates a WatchService and registers the given directory
     */
    FileSystemWatchingService(Path dir) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey, Path>();

        new Thread(new Consumer(queue, this)).start();

        walkAndRegisterDirectories(dir);
    }

    /**
     * Register the given directory with the WatchService; This function will be called by FileVisitor
     */
    private void registerDirectory(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the WatchService.
     */
    public void walkAndRegisterDirectories(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerDirectory(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Process all events for keys queued to the watcher
     */
    private void processEvents() {
        for (; ; ) {

            // wait for key to be signalled
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }

            List<WatchEvent<?>> events = key.pollEvents();

            Pair<Path, List<WatchEvent<?>>> eventsSet = new Pair<>(dir, events);
            queue.add(eventsSet);

            // reset key and remove from set if directory no longer accessible
            boolean valid = key.reset();
            if (!valid) {
                keys.remove(key);

                // all directories are inaccessible
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }

    public void addEventListeners(FileSystemEventListener listener){
        listeners.add(listener);
    }

    public void runEvents(FileSystemWatchEvent event){
        listeners.forEach(l->l.processEvent(event));
    }

    public static void main(String[] args) throws IOException {
        Path dir = Paths.get("c:/temp");

        FileSystemWatchingService service = new FileSystemWatchingService(dir);
        service.addEventListeners(System.out::println);
        service.processEvents();
    }
}
