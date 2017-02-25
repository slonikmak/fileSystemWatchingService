package service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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

    private static class InnerWatchEvent{
        Path path;
        WatchEvent<Path> event;

        public InnerWatchEvent(Path path, WatchEvent<Path> event) {
            this.path = path;
            this.event = event;
        }
    }

    private final WatchService watcher;
    private final Map<WatchKey, Path> keys;
    private BlockingQueue<InnerWatchEvent> createQueue = new LinkedBlockingQueue<>();

    /**
     * Creates a WatchService and registers the given directory
     */
    FileSystemWatchingService(Path dir) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<>();

        walkAndRegisterDirectories(dir);

        new Thread(()->{
            for (;;){
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                InnerWatchEvent innerWatchEvent = createQueue.poll();

                if (innerWatchEvent!=null){
                    WatchEvent<Path> event = innerWatchEvent.event;
                    Path name = event.context().getFileName();
                    final boolean[] done = {false};
                    if (event.kind().equals(ENTRY_DELETE)){
                        createQueue.forEach(e -> {
                            WatchEvent<Path> ev = e.event;
                            if (ev.kind().equals(ENTRY_CREATE) && ev.context().getFileName().equals(name)){
                                System.out.println("move from "+innerWatchEvent.path+" to "+e.path);
                                createQueue.remove(e);
                                createQueue.remove(innerWatchEvent);
                                done[0] = true;
                            }
                        });
                        if (!done[0]) {
                            System.out.println("delete "+innerWatchEvent.path);
                            done[0] = false;
                        }
                    } else if(event.kind().equals(ENTRY_CREATE)){
                        createQueue.forEach(e -> {
                            WatchEvent<Path> ev = e.event;
                            if (ev.kind().equals(ENTRY_DELETE) && ev.context().getFileName().equals(name)){
                                System.out.println("move from "+e.path+" to "+innerWatchEvent.path);
                                createQueue.remove(e);
                                createQueue.remove(innerWatchEvent);
                            }
                        });
                        if (!done[0]) {
                            System.out.println("create "+innerWatchEvent.path);
                            done[0] = false;
                        }
                    }
                }
            }
        }).start();
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
    private void walkAndRegisterDirectories(final Path start) throws IOException {
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

            adaptEvent(events, dir);

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

    public void adaptEvent(List<WatchEvent<?>> events, Path dir){
        for (int i = 0; i < events.size(); i++) {
            @SuppressWarnings("rawtypes")
            WatchEvent<Path> event = (WatchEvent<Path>) events.get(i);

            WatchEvent.Kind kind = event.kind();

            // Context for directory entry event is the file name of entry
            @SuppressWarnings("unchecked")
            Path name = event.context();
            Path child = dir.resolve(name);

            if (events.size()>1){
                if (i<events.size()-1){
                    @SuppressWarnings("rawtypes")
                    WatchEvent<Path> nextEvent = (WatchEvent<Path>) events.get(i+1);
                    WatchEvent.Kind nextKind = nextEvent.kind();
                    Path nextName = nextEvent.context();
                    Path nextChild = dir.resolve(nextName);
                    if (kind.name().equals("ENTRY_DELETE") && nextKind.name().equals("ENTRY_CREATE") &&
                            child.getParent().equals(nextChild.getParent())){
                        System.out.println("RENAME from "+child+" to "+nextChild);
                        i++;
                        continue;
                    }
                }
                createQueue.add(new InnerWatchEvent(child, event));
            } else createQueue.add(new InnerWatchEvent(child, event));

            // print out event
            //System.out.format("%s: %s\n", event.kind().name(), child);

            // if directory is created, and watching recursively, then register it and its sub-directories
            if (kind == ENTRY_CREATE) {
                try {
                    if (Files.isDirectory(child)) {
                        walkAndRegisterDirectories(child);
                    }
                } catch (IOException x) {
                    // do something useful
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        //Path dir = Paths.get("/home/anton/Документы/temp");
        Path dir = Paths.get("/home/anton/Документы/temp");
        new FileSystemWatchingService(dir).processEvents();
    }
}
