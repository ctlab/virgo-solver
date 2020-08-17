package ru.itmo.ctlab.virgo.sgmwcs.graph;

import ru.itmo.ctlab.virgo.sgmwcs.Signals;

import java.io.*;
import java.text.ParseException;
import java.util.*;

public class GraphIO {
    private File nodeIn;
    private File edgeIn;
    private File signalIn;
    private Map<String, Node> nodeNames;
    private Map<Unit, String> unitMap;
    private Signals signals;
    private Map<String, Integer> signalNames;

    private String inf = "inf"; // Representation of infinite-weight signal
    private String nodeOut;
    private String edgeOut;

    public GraphIO(File nodeIn, File edgeIn, File signalIn, String outDir) {
        this.nodeIn = nodeIn;
        this.edgeIn = edgeIn;
        this.signalIn = signalIn;
        this.nodeOut = outDir + "/" + nodeIn + ".out";
        this.edgeOut = outDir + "/" + edgeIn + ".out";
        signals = new Signals();
        nodeNames = new LinkedHashMap<>();
        unitMap = new HashMap<>();
        signalNames = new HashMap<>();
    }

    public Graph read() throws IOException, ParseException {
        try (LineNumberReader nodes = new LineNumberReader(new FileReader(nodeIn));
             LineNumberReader edges = new LineNumberReader(new FileReader(edgeIn));
             LineNumberReader signalsReader = new LineNumberReader(new FileReader(signalIn))) {
            Graph graph = new Graph();
            parseNodes(nodes, graph);
            parseEdges(edges, graph);
            parseSignals(signalsReader);
            return graph;
        }
    }

    private void parseNodes(LineNumberReader reader, Graph graph) throws ParseException, IOException {
        String line;
        int cnt = 1;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#")) {
                continue;
            }
            StringTokenizer tokenizer = new StringTokenizer(line);
            if (!tokenizer.hasMoreTokens()) {
                continue;
            }
            String node = tokenizer.nextToken();
            try {
                Node vertex = new Node(cnt++);
                if (nodeNames.containsKey(node)) {
                    throw new ParseException("Duplicate node " + node, 0);
                }
                nodeNames.put(node, vertex);
                graph.addVertex(vertex);
                processSignals(vertex, tokenizer);
                unitMap.put(vertex, node);
            } catch (ParseException e) {
                throw new ParseException(e.getMessage() + "node file, line", reader.getLineNumber());
            }
        }
    }

    private void parseEdges(LineNumberReader reader, Graph graph) throws ParseException, IOException {
        int cnt = 1;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#")) {
                continue;
            }
            StringTokenizer tokenizer = new StringTokenizer(line);
            if (!tokenizer.hasMoreTokens()) {
                continue;
            }
            String first = tokenizer.nextToken();
            if (!tokenizer.hasMoreTokens()) {
                throw new ParseException("Wrong edge format at line", reader.getLineNumber());
            }
            String second = tokenizer.nextToken();
            try {
                if (!nodeNames.containsKey(first) || !nodeNames.containsKey(second)) {
                    throw new ParseException("There's no such vertex in edge list at line", reader.getLineNumber());
                }
                Edge edge = new Edge(cnt++);
                Node from = nodeNames.get(first);
                Node to = nodeNames.get(second);
                graph.addEdge(from, to, edge);
                unitMap.put(edge, first + "\t" + second);
                processSignals(edge, tokenizer);
            } catch (ParseException e) {
                throw new ParseException(e.getMessage() + "edge file, line", reader.getLineNumber());
            }
        }
    }

    private void processSignals(Unit unit, StringTokenizer tokenizer) throws ParseException {
        List<String> tokens = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            tokens.add(tokenizer.nextToken());
        }
        if (!tokens.isEmpty()) {
            for (String token : tokens) {
                if (signalNames.containsKey(token)) {
                    int signal = signalNames.get(token);
                    signals.add(unit, signal);
                } else {
                    signalNames.put(token, signals.addAndSetWeight(unit, 0.0));
                }
            }
        } else {
            throw new ParseException("Expected signal name: ", 0);
        }
    }


    private void parseSignals(LineNumberReader reader) throws IOException, ParseException {
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                StringTokenizer tokenizer = new StringTokenizer(line);
                if (!tokenizer.hasMoreTokens()) {
                    continue;
                }
                String signal = tokenizer.nextToken();
                if (!tokenizer.hasMoreTokens()) {
                    throw new ParseException(
                            "Expected weight of signal at line ",
                            reader.getLineNumber());
                }
                String w = tokenizer.nextToken();
                double weight = w.equals(inf) ? Double.POSITIVE_INFINITY : Double.parseDouble(w);

                if (signalNames.containsKey(signal)) {
                    int set = signalNames.get(signal);
                    signals.setWeight(set, weight);
                    if (weight < 0 && signals.set(set).size() > 1) {
                        throw new ParseException(
                                "Repeating negative signal at line ",
                                reader.getLineNumber());

                    }
                }
            }
        } catch (NumberFormatException e) {
            throw new ParseException("Wrong format of weight of signal at line", reader.getLineNumber());
        }
    }

    public void write(List<Unit> units) throws IOException {
        if (units == null) {
            units = new ArrayList<>();
        }
        try (PrintWriter nodeWriter = new PrintWriter(nodeOut);
             PrintWriter edgeWriter = new PrintWriter(edgeOut)) {
            for (Unit unit : units) {
                if (!unitMap.containsKey(unit)) {
                    throw new IllegalStateException();
                }
                if (unit instanceof Node) {
                    nodeWriter.println(unitMap.get(unit));
                } else {
                    edgeWriter.println(unitMap.get(unit));
                }
            }
        }
    }

    public Signals getSignals() {
        return signals;
    }
}