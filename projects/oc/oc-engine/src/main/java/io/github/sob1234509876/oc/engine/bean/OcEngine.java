package io.github.sob1234509876.oc.engine.bean;

import io.github.sob1234509876.oc.engine.AbstractOcParent;
import io.github.sob1234509876.oc.engine.api.Engine;
import io.github.sob1234509876.oc.engine.event.OcExecuteUpdateEventFactory;
import lombok.*;

import java.util.Timer;
import java.util.TimerTask;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@RequiredArgsConstructor
public class OcEngine extends AbstractOcParent implements Engine {

    @NonNull
    OcExecuteUpdateEventFactory ocExecuteUpdateEventFactory;

    @NonNull
    OcContext context;

    @NonNull
    OcResourceManager resourceManager;

    @NonNull
    OcEngineEventBusCollection eventBusCollection;

    @NonNull
    Timer schedule;

    int msu;

    boolean started;

    @Override
    public void start() {
        schedule.scheduleAtFixedRate(new TimerTask() {
                                         @Override
                                         public void run() {
                                             update();
                                         }
                                     },
                0,
                getMsu());
    }

    @Override
    public void update() {
        eventBusCollection.getUpdateEventBus()
                .post(ocExecuteUpdateEventFactory.create());
    }
}
