package io.github.sob1234509876.oc.engine;

import io.github.sob1234509876.oc.engine.api.Engine;
import io.github.sob1234509876.oc.engine.event.OcTickUpdateEvent;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Timer;
import java.util.TimerTask;

@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcEngine implements Engine {

    @NonNull
    ApplicationEventPublisher applicationEventPublisher;

    boolean started;

    int msu;

    @NonNull
    Timer updater = new Timer();

    @Override
    public void start() {

        if (started)
            throw new IllegalStateException("Already started");

        started = true;

        updater.scheduleAtFixedRate(
                new TimerTask() {
                    @Override
                    public void run() {
                        applicationEventPublisher.publishEvent(new OcTickUpdateEvent());
                    }
                },
                0,
                msu);
    }
}
