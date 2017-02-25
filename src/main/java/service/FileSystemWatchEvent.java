package service;

import java.nio.file.Path;

/**
 * Created by Anton on 25.02.2017.
 */
public class FileSystemWatchEvent {
    public enum Type{
        CREATE, DELETE, MOVE, RENAME, UPDATE
    }
    private Type type;
    private Path pathFrom;
    private Path pathTo;

    public FileSystemWatchEvent(Type type, Path pathFrom) {
        this.type = type;
        this.pathFrom = pathFrom;
        this.pathTo = null;
    }

    public FileSystemWatchEvent(Type type, Path pathFrom, Path pathTo) {
        this.type = type;
        this.pathFrom = pathFrom;
        this.pathTo = pathTo;
    }

    public Type getType() {
        return type;
    }

    public Path getPathFrom() {
        return pathFrom;
    }

    public Path getPathTo() {
        return pathTo;
    }

    @Override
    public String toString() {
        return "FileSystemWatchEvent{" +
                "type=" + type +
                ", pathFrom=" + pathFrom +
                ", pathTo=" + pathTo +
                '}';
    }
}
