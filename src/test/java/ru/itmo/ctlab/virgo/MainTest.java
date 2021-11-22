package ru.itmo.ctlab.virgo;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Nikolay Poperechnyi on 07.09.20.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MainTest {
    private static String SGMWCS_TEMPLATE = "-m 4 -e src/test/resources/test-%s/sgmwcs/edges.txt " +
                    "-n src/test/resources/test-%s/sgmwcs/nodes.txt -s src/test/resources/test-%s/sgmwcs/signals.txt -type sgmwcs -l %s";


    @Test
    public void test_main() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        for (Integer test: Stream.of(1, 2, 3).collect(Collectors.toList())) {
            String argline = String.format(SGMWCS_TEMPLATE, test, test, test, test - 1);
            Main.main(argline.split(" "));
            String[] out = baos.toString().split("\n");
            double sgmwcs = Double.parseDouble(out[out.length - 1]);
            argline = String.format("-m 4 -e src/test/resources/test-%d/gmwcs/edges.txt -n src/test/resources/test-%d/gmwcs/nodes.txt -type gmwcs -l %d", test, test, test - 1);
            Main.main(argline.split(" "));
            out = baos.toString().split("\n");
            double gmwcs = Double.parseDouble(out[out.length - 1]);
            Assert.assertEquals(sgmwcs, gmwcs, 0.0001);
        }
    }

    @Test
    public void test_separator() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        String argline = "-m 4 -e src/test/resources/test-separator/edges -n src/test/resources/test-separator/nodes -s src/test/resources/test-separator/signals -type sgmwcs -p 0.0001";
        Main.main(argline.split(" "));
        String[] out = baos.toString().split("\n");
        double res = Double.parseDouble(out[out.length - 1]);
        Assert.assertTrue(res > 270.576);


    }
}
