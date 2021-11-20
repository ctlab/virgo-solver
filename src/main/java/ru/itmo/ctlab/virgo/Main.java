package ru.itmo.ctlab.virgo;

import ilog.cplex.IloCplex;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import ru.itmo.ctlab.gmwcs.solver.TreeSolverKt;
import ru.itmo.ctlab.virgo.gmwcs.graph.Elem;
import ru.itmo.ctlab.virgo.gmwcs.graph.SimpleIO;
import ru.itmo.ctlab.virgo.gmwcs.solver.BicomponentSolver;
import ru.itmo.ctlab.virgo.sgmwcs.Signals;
import ru.itmo.ctlab.virgo.sgmwcs.graph.*;
import ru.itmo.ctlab.virgo.sgmwcs.solver.ComponentSolver;
import ru.itmo.ctlab.virgo.sgmwcs.solver.Utils;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;

public class Main {
    public static final String VERSION = "0.1.3";

    private static void checkCplex() {
        PrintStream stdout = System.out;
        try {
            System.setOut(System.err);
            Class c = Class.forName("ilog.cplex.IloCplex");
            c.getConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            System.err.println("CPLEX jar file couldn't be found. ");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("CPLEX cannot be initialized.");
            System.exit(1);
        } finally {
            System.setOut(stdout);
            System.out.println("aaa");
        }
    }

    private static OptionSet parseArgs(String args[]) throws IOException {
        OptionParser optionParser = new OptionParser();
        optionParser.allowsUnrecognizedOptions();
        optionParser.acceptsAll(asList("h", "help"), "Print a short help message");
        optionParser.accepts("version");
        OptionSet optionSet = optionParser.parse(args);
        optionParser.acceptsAll(asList("n", "nodes"), "Node list file").withRequiredArg().required();
        optionParser.acceptsAll(asList("e", "edges"), "Edge list file").withRequiredArg().required();
        optionParser.acceptsAll(asList("s", "signals"), "Signals file").withOptionalArg();
        optionParser.acceptsAll(asList("m", "threads"), "Number of threads")
                .withRequiredArg().ofType(Integer.class).defaultsTo(1);
        optionParser.acceptsAll(asList("t", "timelimit"), "Timelimit in seconds (<= 0 - unlimited)")
                .withRequiredArg().ofType(Long.class).defaultsTo(0L);
        optionParser.accepts("type", "One of: SGMWCS, GMWCS")
                .withRequiredArg().ofType(String.class).defaultsTo("sgmwcs");
        optionParser.accepts("c", "Threshold for CPE solver").withRequiredArg().
                ofType(Integer.class).defaultsTo(25);
        optionParser.acceptsAll(asList("p", "epsilon"), "Maximum allowed absolute score error for solver")
                .withRequiredArg().ofType(Double.class).defaultsTo(.0);
        optionParser.acceptsAll(asList("l", "log"), "Log level")
                .withRequiredArg().ofType(Integer.class).defaultsTo(0);
        optionParser.acceptsAll(asList("bm", "benchmark"), "Benchmark output file")
                .withOptionalArg().defaultsTo("");
        optionParser.acceptsAll(asList("pl", "preprocessing-level"), "Disable preprocessing")
                .withOptionalArg().ofType(Integer.class).defaultsTo(2);
        optionParser.acceptsAll(asList("o", "output-dir"), "Solver output directory")
                .withOptionalArg().ofType(String.class);
        optionParser.accepts("mst", "Use primal heuristic only");
        if (optionSet.has("h")) {
            optionParser.printHelpOn(System.out);
            System.exit(0);
        }
        if (optionSet.has("version")) {
            System.out.println("sgmwcs-solver version " + VERSION);
            System.exit(0);
        }
        try {
            optionSet = optionParser.parse(args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.err.println();
            optionParser.printHelpOn(System.err);
            System.exit(1);
        }
        return optionSet;
    }

    public static void main(String[] args) {
        OptionSet optionSet = null;
        try {
            optionSet = parseArgs(args);
        } catch (IOException e) {
            // We can't say anything. Error occurred while printing to stderr.
            System.exit(2);
        }
        long timelimit = (Long) optionSet.valueOf("timelimit");
        int threshold = (Integer) optionSet.valueOf("c");
        TimeLimit tl = new TimeLimit(timelimit <= 0 ? Double.POSITIVE_INFINITY : timelimit);
        int threads = (Integer) optionSet.valueOf("m");
        File nodeFile = new File((String) optionSet.valueOf("nodes"));
        File edgeFile = new File((String) optionSet.valueOf("edges"));
        double edgePenalty = (Double) optionSet.valueOf("p");
        int logLevel = (Integer) optionSet.valueOf("l");
        int preprocessLevel = (Integer) optionSet.valueOf("pl");
        boolean heuristicOnly = optionSet.has("mst");
        if (!heuristicOnly) {
            checkCplex();
        }
        String instanceType = (String) optionSet.valueOf("type");
        String outDir = optionSet.has("o") ? (String) optionSet.valueOf("o") : nodeFile.getAbsoluteFile().getParent();
        String statsFile = outDir + "/stats.tsv";
        try {
            Files.createDirectories(Paths.get(outDir));
        } catch (IOException e) {
            System.err.println("Incorrect output path: " + outDir);
            System.exit(1);
        }

        if (edgePenalty < 0) {
            System.err.println("Edge penalty can't be negative");
            System.exit(1);
        }

        if (instanceType.equals("sgmwcs")) {
            File signalFile = new File((String) optionSet.valueOf("signals"));

            ComponentSolver solver = new ComponentSolver(threshold, edgePenalty);

            solver.setThreadsNum(threads);
            solver.setTimeLimit(tl);
            solver.setLogLevel(logLevel);
            solver.setPreprocessingLevel(preprocessLevel);
            solver.setCplexOff(heuristicOnly);
            GraphIO graphIO = new GraphIO(nodeFile, edgeFile, signalFile, outDir);
            try {
                long before = System.currentTimeMillis();
                Graph graph = graphIO.read();
                System.out.println("Graph with " +
                        graph.edgeSet().size() + " edges and " +
                        graph.vertexSet().size() + " nodes");
                Signals signals = graphIO.getSignals();
                List<Unit> units = solver.solve(graph, signals);
                long now = System.currentTimeMillis();
                if (solver.isSolvedToOptimality()) {
                    System.out.println("SOLVED TO OPTIMALITY");
                }
                double sum = Utils.sum(units, signals);
                long timeConsumed = now - before;
                System.out.println("time:" + timeConsumed);
                System.out.println(sum);
                Set<Edge> edges = new HashSet<>();
                Set<Node> nodes = new HashSet<>();
                if (logLevel >= 1 && units != null) {
                    for (Unit unit : units) {
                        if (unit instanceof Edge) {
                            edges.add((Edge) unit);
                        } else {
                            nodes.add((Node) unit);
                        }
                    }
                    Graph solGraph = graph.subgraph(nodes, edges);
                    if (logLevel == 2)
                        new GraphPrinter(solGraph, signals).toTSV(outDir + "/" + "nodes-sol.tsv", outDir + "/" + "edges-sol.tsv");
                    printStats(solver.isSolvedToOptimality() ? 1 : 0,
                            solver.preprocessedNodes(), solver.preprocessedEdges(),
                            solGraph.vertexSet().size(), solGraph.edgeSet().size(), timeConsumed, statsFile,
                            nodeFile.getAbsolutePath(), edgeFile.getAbsolutePath(), signalFile.getAbsolutePath());
                }
                graphIO.write(units);
            } catch (ParseException e) {
                System.err.println("Couldn't parse input files: " + e.getMessage() + " " + e.getErrorOffset());
                System.exit(1);
            } catch (SolverException e) {
                System.err.println("Error occurred while solving:" + e.getMessage());
                System.exit(1);
            } catch (IOException e) {
                System.err.println("Error occurred while reading/writing input/output files");
                System.exit(1);
            }
        } else if (instanceType.equals("gmwcs") || instanceType.equals("mwcs")) {
            SimpleIO graphIO = new SimpleIO(nodeFile, new File(outDir + "/" + nodeFile.getName() + ".out"),
                    edgeFile, new File(outDir + "/" + edgeFile.getName() + ".out"));
            if (instanceType.equals("mwcs")) {
                graphIO.mwcs();
            }
            try {
                ru.itmo.ctlab.virgo.gmwcs.graph.Graph graph = graphIO.read();
                List<Elem> units;
                boolean toOpt = false;
                int prepNodes = 0, prepEdges = 0;
                if (edgePenalty > 0) {
                    graph.edgeSet().forEach(e -> e.setWeight(e.getWeight() - edgePenalty));
                }
                if (heuristicOnly) {
                    units = new ArrayList<>(TreeSolverKt.solveComponents(graph));
                } else {
                    BicomponentSolver solver = new BicomponentSolver();
                    if (logLevel < 2) {
                        solver.suppressOutput();
                    }
                    solver.setThreadsNum(threads);
                    solver.setUnrootedTL(tl);
                    solver.setRootedTL(tl.subLimit(0.7));
                    solver.setTLForBiggest(tl);
                    units = solver.solve(graph);
                    toOpt = solver.isSolvedToOptimality();
                    prepEdges = solver.preprocessedEdges();
                    prepNodes = solver.preprocessedNodes();
                }
                System.out.println(units.stream().mapToDouble(Elem::getWeight).sum());
                int edgeSize = (int) units
                        .stream()
                        .filter(x -> x instanceof ru.itmo.ctlab.virgo.gmwcs.graph.Edge)
                        .count();
                int nodeSize = units.size() - edgeSize;
                units.forEach(u -> {
                    if (u instanceof ru.itmo.ctlab.virgo.gmwcs.graph.Edge) u.setWeight(u.getWeight() + edgePenalty);
                });
                graphIO.write(units);
                if (logLevel >= 1)
                    printStats(toOpt ? 1 : 0, prepNodes, prepEdges,
                            nodeSize, edgeSize, 0, statsFile,
                            nodeFile.getAbsolutePath(), edgeFile.getAbsolutePath(), "NULL");
            } catch (ParseException e) {
                System.err.println("Couldn't parse input files: " + e.getMessage() + " " + e.getErrorOffset());
                System.exit(1);
            } catch (SolverException e) {
                System.err.println("Error occur while solving:" + e.getMessage());
                System.exit(1);
            } catch (IOException e) {
                System.err.println("Error occurred while reading/writing input/output files");
                System.exit(1);
            }
        }
    }

    private static void printStats(int isOpt, int prepNodes, int prepEdges, int solNodes, int solEdges,
                                   long timeConsumed, String fileName,
                                   String nodesFile, String edgesFile, String signalsFile) {
        try (PrintWriter pw = new PrintWriter(fileName)) {
            String header = "isOpt\tVPrep\tEPrep\ttime\tnodes\tedges\tnodefile\tedgefile\tsigfile\tversion\n";
            String out = isOpt + "\t" + prepNodes + "\t" + prepEdges + "\t" + timeConsumed + "\t" +
                    solNodes + "\t" + solEdges
                    + "\t" + nodesFile + "\t" + edgesFile + "\t" + signalsFile + "\t" + VERSION + "\n";
            pw.write(header);
            pw.write(out);
        } catch (IOException e) {
            System.err.println("Failed to write stats");
        }
    }
}
