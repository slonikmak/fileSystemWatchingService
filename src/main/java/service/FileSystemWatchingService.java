package service;

import javafx.util.Pair;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * File System Watching Service
 * Generate events CREATE, MODIFY, MOVE, RENAME, DELETE
 * <p>
 * Java native service {@link WatchService} generates only CREATE, DELETE and MODIFY events
 * For example, wen we rename a file, WatchService generate DELETE and CREATE events
 * <p>
 * In this service native events added in queue and {@link Consumer} processing their
 * Consumer thread watch the queue and find matches and generate new events,
 * then runs {@link FileSystemEventListener}
 */
public class FileSystemWatchingService {

    //List of Event Listeners
    private List<FileSystemEventListener> listeners = new ArrayList<>();
    //Native Java Watch service
    private final WatchService watcher;
    //Map for storage of keys and related pathways
    private final Map<WatchKey, Path> keys;

    //flag for stopping service
    private boolean isAlive = true;

    //Map for storage events and related path
    //when an event occurs in some folder Watch service add them to related key
    //and then events from key and related path add to this queue
    BlockingQueue<Pair<Path, List<WatchEvent<?>>>> queue = new LinkedBlockingQueue<>();

    /**
     * Creates a WatchService and registers the given directory
     *
     * @param dir root directory
     * @throws IOException
     */
    public FileSystemWatchingService(Path dir) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<>();

        new Thread(new Consumer(queue, this)).start();

        walkAndRegisterDirectories(dir);
    }

    /**
     * Register the given directory with the WatchService; This function will be called by FileVisitor
     *
     * @param dir dir for watch
     * @throws IOException
     */
    private void registerDirectory(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the WatchService.
     *
     * @param start dir with sub-directories
     * @throws IOException
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
        while (isAlive){

            // wait for key to be signalled
            //multiple events packed into key
            WatchKey key;
            try {
                key = watcher.take();
            } catch (InterruptedException x) {
                return;
            }

            //Get the path associated with the key
            Path dir = keys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }


            //get the events list
            List<WatchEvent<?>> events = key.pollEvents();

            //create pair with path and events
            Pair<Path, List<WatchEvent<?>>> eventsSet = new Pair<>(dir, events);

            //add pair to the shared with Consumer queue
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

    /**
     * replacement path associated key when you rename a folder
     * @param from old path
     * @param to new path
     */
    void replaceKey(Path from, Path to) {
        Map.Entry<WatchKey, Path> result = removeKey(from);
        if (result!=null) keys.put(result.getKey(), to);
    }

    /**
     * remove key from keys collection
     * @param path path associated key
     * @return
     */
    Map.Entry<WatchKey, Path> removeKey(Path path){
        Map.Entry<WatchKey, Path> result = null;
        for (Map.Entry<WatchKey, Path> entry :
                keys.entrySet()) {
            if (path.equals(entry.getValue())) {
                result = entry;
                break;
            }
        }
        keys.remove(result.getKey());
        return result;
    }

    /**
     * add event listeners
     *
     * @param listener event listener
     */
    public void addEventListeners(FileSystemEventListener listener) {
        listeners.add(listener);
    }

    /**
     * launch event handlers
     *
     * @param event
     */
    public void runEvents(FileSystemWatchEvent event) {
        listeners.forEach(l -> l.processEvent(event));
    }

    /**
     * stop service
     */
    public void stop(){
        isAlive = false;
    }

    public static void main(String[] args) throws IOException {
        Path dir = Paths.get("c:/temp");

        FileSystemWatchingService service = new FileSystemWatchingService(dir);
        service.addEventListeners(System.out::println);
        service.processEvents();
    }
}
