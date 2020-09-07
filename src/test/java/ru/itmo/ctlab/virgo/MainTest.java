package ru.itmo.ctlab.virgo;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import ru.itmo.ctlab.virgo.gmwcs.graph.SimpleIO;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.List;

/**
 * Created by Nikolay Poperechnyi on 07.09.20.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MainTest {
    @Test
    public void test_main() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        for (var test: List.of(1, 2)) {
            var argline = String.format("-m 4 -e src/test/resources/test-%d/sgmwcs/edges.txt " +
                    "-n src/test/resources/test-%d/sgmwcs/nodes.txt -s src/test/resources/test-%d/sgmwcs/signals.txt -type sgmwcs -l 0", test, test, test);
            Main.main(argline.split(" "));
            var out = baos.toString().split("\n");
            var sgmwcs = Double.parseDouble(out[out.length - 1]);
            argline = String.format("-m 4 -e src/test/resources/test-%d/gmwcs/edges.txt -n src/test/resources/test-%d/gmwcs/nodes.txt -type gmwcs -l 0", test, test);
            Main.main(argline.split(" "));
            out = baos.toString().split("\n");
            var gmwcs = Double.parseDouble(out[out.length - 1]);
            Assert.assertEquals(sgmwcs, gmwcs, 0.0001);
        }
    }
}
