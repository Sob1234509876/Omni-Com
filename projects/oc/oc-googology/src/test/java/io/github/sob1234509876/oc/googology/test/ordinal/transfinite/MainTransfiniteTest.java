package io.github.sob1234509876.oc.googology.test.ordinal.transfinite;

import io.github.sob1234509876.oc.googology.fs.mountain.MountainExpander;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.List;

@Slf4j
public class MainTransfiniteTest {

    @Test
    public void testMountain() {
        // p(W_w*W+W_w)
        var owoPOw = new MountainExpander.MountainGraph();
        owoPOw.setValues(new LinkedList<>(List.of(
                new LinkedList<>(List.of(1, 4, 3, 7, 9, 7)))));

        MountainExpander.expand(owoPOw,
                5,
                true,
                true,
                false);

        Assertions.assertEquals(List.of(1, 4, 3, 7, 9, 6, 11, 13, 10, 16, 18, 15, 22, 24, 21, 29, 31), owoPOw.getValues()
                .get(0));

        // FBLO = p((W_w)^2)
        var fblo = new MountainExpander.MountainGraph();
        fblo.setValues(new LinkedList<>(List.of(
                new LinkedList<>(List.of(1, 4, 6, 4))
        )));

        MountainExpander.expand(fblo,
                5,
                true,
                true,
                false);

        Assertions.assertEquals(List.of(1, 4, 6, 3, 7, 10, 6, 11, 15, 10, 16, 21, 15, 22, 28),
                fblo.getValues()
                        .get(0));
    }

}
