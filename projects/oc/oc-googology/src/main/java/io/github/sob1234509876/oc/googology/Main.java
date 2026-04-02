package io.github.sob1234509876.oc.googology;

import io.github.sob1234509876.oc.googology.ordinal.finite.function.KnuthArrowOrdinal;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.util.List;

@Slf4j
@ComponentScan
@SpringBootApplication
public class Main {
    public static void main(@NonNull String @NonNull [] args) {
        SpringApplication.run(Main.class, args);

        var ttt = new KnuthArrowOrdinal();
        ttt.getKnuthArrows()
                .add(new KnuthArrowOrdinal.KnuthArrow(3, 3));
        ttt.setValue(3);

        var g1 = new KnuthArrowOrdinal();
        g1.getKnuthArrows()
                .add(new KnuthArrowOrdinal.KnuthArrow(4, 3));
        g1.setValue(3);

        var ttt_ttt = new KnuthArrowOrdinal();
        ttt_ttt.getKnuthArrows()
                .add(List.of(new KnuthArrowOrdinal.KnuthArrow(3, 3),
                        new KnuthArrowOrdinal.KnuthArrow(3, 3)));
        ttt_ttt.setValue(3);

        log.info("{} cmp {}: {}", ttt, g1, ttt.compareTo(g1));
        log.info("{} cmp {}: {}", ttt, ttt_ttt, ttt.compareTo(ttt_ttt));
        log.info("{} cmp {}: {}", ttt_ttt, g1, ttt_ttt.compareTo(g1));
    }
}
