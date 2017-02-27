package service;

import javafx.util.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Class for transform native event to FileSystemWatchEvent
 * it is necessary to receive RENAME and MOVE events
 * because native events is only CREATE, DELETE and MODIFY
 *
 * Depending on the file system RENAME and MOVE events is DELETE+RENAME native events
 *
 */
public class Consumer implements Runnable{

    private CopyOnWriteArrayList<Pair<Path, List<WatchEvent<?>>>> newQueue = new CopyOnWriteArrayList<>();
    private BlockingQueue<Pair<Path, List<WatchEvent<?>>>> queue;
    private BlockingQueue<Pair<Path, WatchEvent<?>>> bufferQueue = new LinkedBlockingQueue<>();
    private FileSystemWatchingService service;

    Consumer(BlockingQueue<Pair<Path, List<WatchEvent<?>>>> queue, FileSystemWatchingService service) {
        this.queue = queue;
        this.service = service;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (queue.size() > 0) {
                new Thread(() -> {

                    newQueue.addAll(queue);
                    queue.removeAll(newQueue);

                    for (Pair<Path, List<WatchEvent<?>>> pair : newQueue) {

                        List<WatchEvent<?>> events = pair.getValue();
                        Path dir = pair.getKey();

                        for (int j = 0; j < events.size(); j++) {
                            WatchEvent<?> event = events.get(j);

                            @SuppressWarnings("rawtypes")
                            WatchEvent.Kind kind = event.kind();
                            // Context for directory entry event is the file name of entry
                            @SuppressWarnings("unchecked")
                            Path name = ((WatchEvent<Path>) event).context();
                            Path child = dir.resolve(name);
                            // print out event
                            //System.out.format("%s: %s\n", event.kind().name(), child);

                            if (((WatchEvent<Path>) event).kind().equals(ENTRY_MODIFY)) {
                                //System.out.println(event.kind());
                                service.runEvents(new FileSystemWatchEvent(FileSystemWatchEvent.Type.MODIFY, child));
                            } else {
                                if (events.size() > 1) {
                                    if (j < events.size() - 1) {
                                        @SuppressWarnings("unchecked")
                                        WatchEvent<Path> nextEvent = (WatchEvent<Path>) events.get(j + 1);
                                        WatchEvent.Kind nextKind = nextEvent.kind();
                                        Path nextName = nextEvent.context();
                                        Path nextChild = dir.resolve(nextName);
                                        if (kind.name().equals("ENTRY_DELETE") && nextKind.name().equals("ENTRY_CREATE") &&
                                                child.getParent().equals(nextChild.getParent())) {
                                            //System.out.println("RENAME from "+child+" to "+nextChild);
                                            service.runEvents(new FileSystemWatchEvent(FileSystemWatchEvent.Type.RENAME, child, nextChild));
                                            service.replaceKey(child, nextChild);
                                            j++;



                                            continue;
                                        }
                                    }
                                    bufferQueue.add(new Pair<>(dir, event));
                                } else {
                                    bufferQueue.add(new Pair<>(dir, event));
                                }
                            }


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

                    checkOtherEvents(bufferQueue);
                    newQueue.clear();

                }).start();
            }

        }
    }


    private void checkOtherEvents(BlockingQueue<Pair<Path, WatchEvent<?>>> queue) {
        List<Pair<Path, WatchEvent<?>>> doneList = new ArrayList<>();
        for (Pair<Path, WatchEvent<?>> p : queue) {
            if (p != null && !doneList.contains(p)) {
                @SuppressWarnings("unchecked")
                WatchEvent<Path> event = (WatchEvent<Path>) p.getValue();
                Path name = event.context();
                final boolean[] done = {false};

                WatchEvent.Kind<Path> from = event.kind();
                WatchEvent.Kind<Path> to = from.equals(ENTRY_DELETE)?ENTRY_CREATE:ENTRY_DELETE;

                queue.forEach(e -> {
                    if (!doneList.contains(e)){
                        WatchEvent<Path> ev = (WatchEvent<Path>) e.getValue();
                        Path newName = ev.context();
                        if (ev.kind().equals(to) && newName.equals(name)) {
                            if (from.equals(ENTRY_DELETE)) {
                                //System.out.println("move from " + p.getKey().resolve(name) + " to " + p.getKey().resolve(name));
                                service.runEvents(new FileSystemWatchEvent(FileSystemWatchEvent.Type.MOVE, p.getKey().resolve(name), e.getKey().resolve(name)));
                            }
                            else {
                                //System.out.println("move from " + e.getKey().resolve(newName) + " to " + p.getKey().resolve(name));
                                service.runEvents(new FileSystemWatchEvent(FileSystemWatchEvent.Type.MOVE, e.getKey().resolve(name), p.getKey().resolve(name)));
                            }
                            queue.remove(e);
                            doneList.add(e);
                            done[0] = true;

                        }
                    }

                });

                if (!done[0]) {
                    //System.out.println(from.toString() + p.getKey().resolve(name));
                    FileSystemWatchEvent.Type eventType = from.equals(ENTRY_CREATE)?FileSystemWatchEvent.Type.CREATE:FileSystemWatchEvent.Type.DELETE;

                    /*if (from == ENTRY_CREATE) {
                        try {
                            if (Files.isDirectory(p.getKey().resolve(name))) {
                                service.walkAndRegisterDirectories(p.getKey().resolve(name));
                            }
                        } catch (IOException x) {
                            // do something useful
                        }
                    }*/

                    service.runEvents(new FileSystemWatchEvent(eventType, p.getKey().resolve(name)));
                    done[0] = false;
                }


            }
            queue.remove(p);
            doneList.add(p);
        }
        //queue.clear();
    }
}
