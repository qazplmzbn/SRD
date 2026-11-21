package com.vis4srd.research;

import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.util.concurrent.*;

@Component
@ConditionalOnProperty(name="app.research.experiments.worker-enabled",havingValue="true",matchIfMissing=true)
public class ResearchExperimentWorker {
    private final ResearchExperimentService service;
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r -> {var thread=new Thread(r,"research-experiments");thread.setDaemon(true);return thread;});
    public ResearchExperimentWorker(ResearchExperimentService service) {this.service=service;}
    @EventListener(ApplicationReadyEvent.class) public void start() {
        service.interruptUnfinished();
        executor.scheduleWithFixedDelay(() -> {try {service.runNext();}catch(Exception e) {LoggerFactory.getLogger(getClass()).error("Research experiment worker unavailable");}},1,2,TimeUnit.SECONDS);
    }
    @PreDestroy public void stop() {executor.shutdownNow();}
}
