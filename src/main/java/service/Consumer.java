package service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;

/**
 * Created by Anton on 25.02.2017.
 */
public class Consumer implements Runnable{
    private BlockingQueue<InnerWatchEvent> createQueue;
    private FileSystemWatchingService service;
    private boolean isStoped;

    Consumer(BlockingQueue<InnerWatchEvent> createQueue, FileSystemWatchingService service){
        this.createQueue = createQueue;
        this.service = service;
    }

    void adaptEvent(List<WatchEvent<?>> events, Path dir){
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
                        //System.out.println("RENAME from "+child+" to "+nextChild);
                        service.runEventListeners(new FileSystemWatchEvent(FileSystemWatchEvent.Type.RENAME, child, nextChild));
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
                        service.walkAndRegisterDirectories(child);
                    }
                } catch (IOException x) {
                    // do something useful
                }
            }
        }
    }



    @Override
    public void run() {

        while (!isStoped){
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
                            //System.out.println("move from "+innerWatchEvent.path +" to "+e.path);
                            service.runEventListeners(new FileSystemWatchEvent(FileSystemWatchEvent.Type.MOVE, innerWatchEvent.path, e.path));
                            createQueue.remove(e);
                            createQueue.remove(innerWatchEvent);
                            done[0] = true;
                        }
                    });
                    if (!done[0]) {
                        //System.out.println("delete "+innerWatchEvent.path);
                        service.runEventListeners(new FileSystemWatchEvent(FileSystemWatchEvent.Type.DELETE, innerWatchEvent.path));
                        done[0] = false;
                    }
                } else if(event.kind().equals(ENTRY_CREATE)){
                    createQueue.forEach(e -> {
                        WatchEvent<Path> ev = e.event;
                        if (ev.kind().equals(ENTRY_DELETE) && ev.context().getFileName().equals(name)){
                            //System.out.println("move from "+e.path +" to "+innerWatchEvent.path);
                            service.runEventListeners(new FileSystemWatchEvent(FileSystemWatchEvent.Type.MOVE, e.path, innerWatchEvent.path));
                            createQueue.remove(e);
                            createQueue.remove(innerWatchEvent);
                        }
                    });
                    if (!done[0]) {
                        //System.out.println("create "+innerWatchEvent.pathFrom);
                        service.runEventListeners(new FileSystemWatchEvent(FileSystemWatchEvent.Type.CREATE, innerWatchEvent.path));
                        done[0] = false;
                    }
                }
            }
        }
    }
}
