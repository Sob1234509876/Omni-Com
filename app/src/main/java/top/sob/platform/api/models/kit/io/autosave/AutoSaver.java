package top.sob.platform.api.models.kit.io.autosave;

import top.sob.platform.api.models.serialize.Serializer;
import top.sob.platform.proof.Modifiable;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class AutoSaver implements Runnable {

    public static final long DEFAULT_AUTO_SAVE_PERIOD = 30000; // 30 seconds

    private static final AtomicInteger ID = new AtomicInteger(0);

    public static AutoSaver create(File file) {
        try {
            return new AutoSaver(file.toURI().toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Malformed URL :", e);
        }
    }

    private final Timer timer = new Timer("Auto saver " + ID.getAndIncrement(), true);
    private final URL target;
    private final List<Object> objectList = new LinkedList<>();
    private final TimerTask theTask = new TimerTask() {
        @Override
        public void run() {
            AutoSaver.this.run();
        }
    };

    private final long period;
    private final Serializer serializer;

    public AutoSaver(URL target) {
        this(target, DEFAULT_AUTO_SAVE_PERIOD, Serializer.getDefault());
    }

    public AutoSaver(URL target, long period, Serializer serializer) {
        this.target = target;
        this.period = period;
        this.serializer = serializer;
    }

    public URL getTarget() {
        return target;
    }

    @Modifiable
    public List<Object> getObjectList() {
        return objectList;
    }

    @Override
    public void run() {
        try {
            var s = getTarget().openConnection().getOutputStream();
            s.write(getSerializer().serialize(getObjectList()));
        } catch (IOException ignore) {
        }
    }

    public void start() {
        timer.scheduleAtFixedRate(theTask, 0, getPeriod());
    }

    public long getPeriod() {
        return period;
    }

    public Serializer getSerializer() {
        return serializer;
    }
}
