package ru.itmo.ctlab.virgo.sgmwcs;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import ru.itmo.ctlab.virgo.SolverException;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Edge;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Graph;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Node;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Unit;
import ru.itmo.ctlab.virgo.sgmwcs.solver.*;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

import static ru.itmo.ctlab.virgo.sgmwcs.solver.Utils.copy;
import static ru.itmo.ctlab.virgo.sgmwcs.solver.Utils.sum;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SGMWCSTest {
    private static final int SEED = 20160309;
    private static final int TESTS_PER_SIZE = 300;
    private static final int MAX_SIZE = 15;
    private static final int RANDOM_TESTS = 2200;
    private static final int RLT_MAX_SIZE = 100;

    static {
        try {
            new IloCplex();
        } catch (UnsatisfiedLinkError e) {
            System.exit(1);
        } catch (IloException ignored) {
        }
    }

    private final List<TestCase> tests;
    private final ComponentSolver solver;
    private final ReferenceSolver referenceSolver;
    private final RLTSolver rltSolver;
    private final Random random;

    public SGMWCSTest() {
        random = new Random(SEED);
        solver = new ComponentSolver(3, 0);
        solver.setPreprocessingLevel(2);
        solver.setLogLevel(0);
        tests = new ArrayList<>();
        referenceSolver = new ReferenceSolver();
        rltSolver = new RLTSolver(1e-9);
        makeConnectedGraphs(1, MAX_SIZE);
        makeUnconnectedGraphs();
    }

    @Test
    public void test_copy() {
        int allTests = MAX_SIZE * TESTS_PER_SIZE;
        for (int i = 0; i < allTests; i++) {
            TestCase test = tests.get(i);
            Graph graph = new Graph();
            Signals signals = new Signals();
            copy(test.graph(), test.signals(), graph, signals);
            double[] nodesPrev = test.graph().vertexSet().stream()
                    .mapToDouble(signals::weight).sorted().toArray();
            double[] nodesNew = graph.vertexSet().stream()
                    .mapToDouble(signals::weight).sorted().toArray();
            Assert.assertArrayEquals("Node weights must be equal", nodesPrev, nodesNew, 0.00001);

            double[] edgesPrev = test.graph().edgeSet().stream()
                    .mapToDouble(signals::weight).sorted().toArray();
            double[] edgesNew = graph.edgeSet().stream()
                    .mapToDouble(signals::weight).sorted().toArray();
            Assert.assertArrayEquals("Edge weights must be equal", edgesPrev, edgesNew, 0.00001);

            Assert.assertEquals(test.signals().size(), signals.size());
            for (int j = 0; j < signals.size(); ++j) {
                List<Unit> newUnits = signals.set(j);
                newUnits.sort(Comparator.comparingInt(Unit::getNum));
                List<Unit> oldUnits = test.signals().set(j);
                oldUnits.sort(Comparator.comparingInt(Unit::getNum));
                for (int num = 0; num < newUnits.size(); ++num) {
                    Unit nu = newUnits.get(num), ou = oldUnits.get(num);
                    Assert.assertNotSame(nu, ou);
                    Assert.assertEquals(nu.getNum(), ou.getNum());
                    Assert.assertTrue(signals.weight(nu) - signals.weight(ou) < 0.01);
                }
            }
        }
    }

    @Test
    public void test01_empty() throws SolverException {
        Graph graph = new Graph();
        solver.setLogLevel(0);
        List<Unit> res = solver.solve(graph, new Signals());
        if (!(res == null || res.isEmpty())) {
            Assert.fail("An empty graph can't contain non-empty subgraph");
        }
    }

    @Test
    public void test02_connected() {
        solver.setThreadsNum(1);
        int allTests = MAX_SIZE * TESTS_PER_SIZE;
        for (int i = 0; i < allTests; i++) {
            System.err.println("Test " + i);
            TestCase test = tests.get(i);
            check(test, i, referenceSolver);
        }
        System.out.println();
    }

    @Test
    public void test03_random() {
        int allTests = MAX_SIZE * TESTS_PER_SIZE;
        for (int i = allTests; i < tests.size(); i++) {
            TestCase test = tests.get(i);
            check(test, i, referenceSolver);
        }
        System.out.println();
    }

    @Test
    public void test04_big() {
        tests.clear();
        makeConnectedGraphs(RLT_MAX_SIZE - 1, RLT_MAX_SIZE);
        for (int i = 0; i < tests.size(); i++) {
            TestCase test = tests.get(i);
            Signals s = test.signals();
            for (int addedSigs = 0; addedSigs < 10; addedSigs++) {
                int sig = s.addSignal(random.nextDouble() * random.nextInt(10));
                test.graph().vertexSet().forEach(
                        n -> {
                            if (s.weight(n) >= 0 && random.nextBoolean())
                                s.add(n, sig);
                        }
                );
                test.graph().edgeSet().forEach(
                        e -> {
                            if (s.weight(e) >= 0 && random.nextBoolean())
                                s.add(e, sig);
                        }
                );
            }
            check(test, i, rltSolver);
        }
    }

    @Test
    public void test05_minimization() {
        tests.clear();
        makeConnectedGraphs(RLT_MAX_SIZE, RLT_MAX_SIZE);
        for (int num = 0; num < tests.size(); num++) {
            TestCase test = tests.get(num);
            Signals s = test.signals();
            System.err.println("TEST " + num);
            try {
                ComponentSolver minimizing = new ComponentSolver(3, 0.0001);
                minimizing.setPreprocessingLevel(2);
                minimizing.setLogLevel(0);
                minimizing.setThreadsNum(2);
                ComponentSolver ordinary = new ComponentSolver(3, 0);
                ordinary.setPreprocessingLevel(2);
                ordinary.setLogLevel(0);
                List<Unit> ord = ordinary.solve(test.graph(), s);
                List<Unit> min = minimizing.solve(test.graph(), s);
                double delta = s.sum(ord) - s.sum(min);
                Assert.assertTrue(num +
                                ": difference between minimized and " +
                                "non minimized sols is " + delta
                        , delta <= 0.0001);
            } catch (Exception e) {
                Assert.fail(num + "\n" + e.getMessage());

            }
        }
    }

    @Test
    public void test06_extraEdges() {
        tests.clear();
        makeConnectedGraphs(MAX_SIZE, MAX_SIZE);
        int num = 0;
        try {
            for (; num < TESTS_PER_SIZE; num++) {
                TestCase t = tests.get(num);
                Node[] vs = t.graph().vertexSet().toArray(new Node[0]);
                for (int i = 0; i < 10; i++) {
                    Node n = vs[random.nextInt(vs.length)];
                    // origin for some parallel edge
                    Edge oe = t.graph().edgeSet().iterator().next();
                    // add self loop
                    Edge se = new Edge(t.graph().edgeSet().size() + 1);
                    t.signals().add(se, random.nextInt(t.signals().size()));
                    t.graph().addEdge(n, n, se);
                    // add parallel edge
                    Edge pe = new Edge(t.graph().edgeSet().size() + 1);
                    t.graph().addEdge(t.graph().getEdgeSource(oe),
                            t.graph().getEdgeTarget(oe), pe);
                    t.signals().add(pe, random.nextInt(t.signals().size()));
                }
                new Preprocessor(t.graph(), t.signals()).preprocess(2);
                for (Node u : t.graph().vertexSet()) {
                    for (Edge e : t.graph().edgesOf(u)) {
                        Node v = t.graph().getOppositeVertex(u, e);
                        List<Edge> allEdges = t.graph().getAllEdges(u, v);
                        if (allEdges.size() > 1) {
                            for (Edge tested : allEdges) {
                                Assert.assertTrue(
                                        "Test " + num + " invariant failed; maxSum of" + tested + " = "
                                                + t.signals().maxSum(tested),
                                        t.signals().maxSum(tested) >= 0);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Assert.fail(num + "\n" + e.getMessage());
        }
    }


    private void check(TestCase test, int num, Solver refSolver) {
        List<Unit> expected = null, actual = null;
        try {
            expected = refSolver.solve(test.graph(), test.signals());
            solver.setLogLevel(0);
            solver.setLB(new AtomicDouble(0));
            actual = solver.solve(test.graph(), test.signals());
        } catch (SolverException e) {
            System.out.println();
            Assert.fail(num + "\n" + e.getMessage());
        }
        if (Math.abs(sum(expected, test.signals()) - sum(actual, test.signals())) > 0.1) {
            System.err.println();
            System.err.println("Expected: " + sum(expected, test.signals()) + ", but actual: "
                    + sum(actual, test.signals()));
            reportError(test, expected, num);
            Assert.fail("A test has failed. See *error files.");
            System.exit(1);
        }
    }

    private void reportError(TestCase test, List<Unit> expected, int testNum) {
        try (PrintWriter nodeWriter = new PrintWriter("nodes_" + testNum + ".error");
             PrintWriter edgeWriter = new PrintWriter("edges_" + testNum + ".error");
             PrintWriter signalWriter = new PrintWriter("signals_" + testNum + ".error")) {
            Graph g = test.graph();
            Signals s = test.signals();
            for (Node v : g.vertexSet()) {
                nodeWriter.println(v.getNum() + "\tS" + (s.unitSets(v).get(0) + 1));
            }
            for (Edge e : g.edgeSet()) {
                Node from = g.getEdgeSource(e);
                Node to = g.getEdgeTarget(e);
                edgeWriter.println(from.getNum() + "\t" + to.getNum() + "\tS" + (s.unitSets(e).get(0) + 1));
            }
            reportSignals(test, signalWriter);
            System.err.println("Correct solution(one of): ");
            for (Unit u : expected) {
                if (u instanceof Edge) {
                    Edge e = (Edge) u;
                    System.err.println(g.getEdgeSource(e).getNum() + "\t" + g.getEdgeTarget(e).getNum());
                } else {
                    System.err.println(u.getNum());
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("Writing to *.error file failed.");
        }
    }

    private void reportSignals(TestCase test, PrintWriter signalWriter) {
        Signals signals = test.signals();
        for (int i = 0; i < signals.size(); i++) {
            signalWriter.println("S" + (i + 1) + "\t" + signals.weight(i));
        }
    }

    private void makeConnectedGraphs(int minSize, int maxSize) {
        for (int size = minSize; size <= maxSize; size++) {
            List<Integer> edgesCount = new ArrayList<>();
            for (int i = 0; i < TESTS_PER_SIZE; i++) {
                if (size == 1) {
                    edgesCount.add(0);
                } else {
                    int upper = Math.min((size * (size - 1)) / 2 + 1, maxSize);
                    upper -= size - 1;
                    edgesCount.add(random.nextInt(upper));
                }
            }
            Collections.sort(edgesCount);
            for (int count : edgesCount) {
                Graph graph = new Graph();
                Map<Node, Double> nodes = fillNodes(graph, size);
                List<Integer> seq = new ArrayList<>();
                for (int j = 0; j < size; j++) {
                    seq.add(j);
                }
                Collections.shuffle(seq, random);
                Node[] nodesArray = nodes.keySet().toArray(new Node[0]);
                Arrays.sort(nodesArray);
                Map<Edge, Double> edges = new HashMap<>();
                for (int j = 0; j < size - 1; j++) {
                    double weight = random.nextInt(16) - 8;
                    Edge edge = new Edge(j + 1);
                    graph.addEdge(nodesArray[seq.get(j)], nodesArray[seq.get(j + 1)], edge);
                    edges.put(edge, weight);
                }
                fillEdgesRandomly(graph, count, nodesArray, edges, size);
                Map<Unit, Double> weights = new HashMap<>();
                weights.putAll(nodes);
                weights.putAll(edges);
                tests.add(new TestCase(graph, weights, random));
            }
        }
    }

    private void makeUnconnectedGraphs() {
        for (int i = 0; i < RANDOM_TESTS; i++) {
            int n = random.nextInt(MAX_SIZE) + 1;
            int m = Math.min((n * (n - 1)) / 2, random.nextInt(MAX_SIZE));
            Graph graph = new Graph();
            Map<Node, Double> nodes = fillNodes(graph, n);
            Map<Edge, Double> edges = new HashMap<>();
            Node[] nodesArray = nodes.keySet().toArray(new Node[0]);
            Arrays.sort(nodesArray);
            fillEdgesRandomly(graph, m, nodesArray, edges, 1);
            Map<Unit, Double> weights = new HashMap<>();
            weights.putAll(nodes);
            weights.putAll(edges);
            tests.add(new TestCase(graph, weights, random));
        }
    }

    private Map<Node, Double> fillNodes(Graph graph, int size) {

        Map<Node, Double> nodes = new HashMap<>();
        for (int j = 0; j < size; j++) {
            Node node = new Node(j + 1);
            nodes.put(node, random.nextInt(16) - 8.0);
            graph.addVertex(node);
        }
        return nodes;
    }

    private void fillEdgesRandomly(Graph graph, int count, Node[] nodes, Map<Edge, Double> edges, int offset) {
        int size = graph.vertexSet().size();
        for (int j = 0; j < count; j++) {
            int u = random.nextInt(size);
            int v = random.nextInt(size);
            if (u == v) {
                j--;
                continue;
            }
            double weight = random.nextInt(16) - 8;
            Edge edge = new Edge(offset + j);
            graph.addEdge(nodes[u], nodes[v], edge);
            edges.put(edge, weight);
        }
    }

}
