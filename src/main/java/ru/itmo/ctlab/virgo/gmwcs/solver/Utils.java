package ru.itmo.ctlab.virgo.gmwcs.solver;


import ru.itmo.ctlab.virgo.gmwcs.graph.Edge;
import ru.itmo.ctlab.virgo.gmwcs.graph.Elem;
import ru.itmo.ctlab.virgo.gmwcs.graph.Graph;
import ru.itmo.ctlab.virgo.gmwcs.graph.Node;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;


public class Utils {
    public static double sum(List<Elem> elems) {
        if (elems == null) {
            return 0;
        }
        double res = 0;
        for (Elem elem : elems) {
            res += elem.getWeight();
        }
        return res;
    }

    /*private static String dotColor(Elem elem, List<Elem> expected, List<Elem> actual) {
        if (actual != null && expected.contains(elem) && actual.contains(elem)) {
            return "YELLOW";
        }
        if (expected.contains(elem)) {
            return "GREEN";
        }
        if (actual != null && actual.contains(elem)) {
            return "RED";
        }
        return "BLACK";
    }

    public static void toXdot(Graph graph, List<Elem> expected, List<Elem> actual) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec("xdot");
        try (PrintWriter os = new PrintWriter(process.getOutputStream())) {
            os.println("graph test {");
            for (Node node : graph.vertexSet()) {
                os.print(node.getNum() + " [label = \"" + node.getNum() + ", " + node.getWeight() + "\" ");
                os.println("color=" + dotColor(node, expected, actual) + "]");
            }
            for (Edge edge : graph.edgeSet()) {
                Node from = graph.getEdgeSource(edge);
                Node to = graph.getEdgeTarget(edge);
                os.print(from.getNum() + "--" + to.getNum() + "[label = \"" + edge.getNum() + ", " +
                        edge.getWeight() + "\" ");
                os.println("color=" + dotColor(edge, expected, actual) + "]");
            }
            os.println("}");
            os.flush();
        }
        try {
            process.waitFor();
        } catch (InterruptedException ignored) {
        }
    }*/
}
