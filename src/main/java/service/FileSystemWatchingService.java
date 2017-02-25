package service;

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
 * Created by Anton on 25.02.2017.
 */
public class FileSystemWatchingService {




    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;
    private final Consumer consumer;
    private final List<FileSystemEventListener> listeners = new ArrayList<>();
    private static BlockingQueue<InnerWatchEvent> createQueue = new LinkedBlockingQueue<>();
    private static boolean isStoped = false;

    /**
     * Creates a WatchService and registers the given directory
     */
    FileSystemWatchingService(Path dir) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<>();
        consumer = new Consumer(createQueue, this);

        walkAndRegisterDirectories(dir);

        new Thread(consumer).start();


    }

    /**
     * Register the given directory with the WatchService; This function will be called by FileVisitor
     */
    private void registerDirectory(Path dir) throws IOException
    {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the WatchService.
     */
    void walkAndRegisterDirectories(final Path start) throws IOException {
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
    void processEvents() {
        for (;;) {
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

            consumer.adaptEvent(events, dir);

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

    public void stopService(){
        isStoped = true;
    }

    public void addEventListner(FileSystemEventListener eventListener){
        listeners.add(eventListener);
    }

    void runEventListeners(FileSystemWatchEvent event){
        for (FileSystemEventListener l :
                listeners) {
            l.processEvent(event);
        }
    }

    public static void main(String[] args) throws IOException {
        //Path dir = Paths.get("/home/anton/Документы/temp");
        Path dir = Paths.get("C:/temp");
        FileSystemWatchingService service = new FileSystemWatchingService(dir);
        service.addEventListner(e-> System.out.println(e));
        service.processEvents();
    }
}
