package ru.itmo.ctlab.virgo.gmwcs.graph;

import ru.itmo.ctlab.virgo.Pair;

import java.io.*;
import java.text.ParseException;
import java.util.*;

public class SimpleIO implements GraphIO {
    private File nodeIn;
    private File nodeOut;
    private File edgeIn;
    private File edgeOut;
    private List<String> nodeList;
    private List<Pair<String, String>> edgeList;
    private Map<String, Node> nodeMap;
    private Map<String, Map<String, Edge>> edgeMap;
    private boolean mwcsIO;

    public SimpleIO(File nodeIn, File nodeOut, File edgeIn, File edgeOut) {
        this.nodeIn = nodeIn;
        this.edgeOut = edgeOut;
        this.edgeIn = edgeIn;
        this.nodeOut = nodeOut;
        nodeMap = new LinkedHashMap<>();
        nodeList = new ArrayList<>();
        edgeList = new ArrayList<>();
        edgeMap = new LinkedHashMap<>();
    }

    public void mwcs() {
        this.mwcsIO = true;

    }

    @Override
    public Graph read() throws FileNotFoundException, ParseException {
        try (Scanner nodes = new Scanner(new BufferedReader(new FileReader(nodeIn)));
             Scanner edges = new Scanner(new BufferedReader(new FileReader(edgeIn)))) {
            Graph graph = new Graph();
            parseNodes(nodes, graph);
            parseEdges(edges, graph);
            return graph;
        }
    }

    private void parseNodes(Scanner nodes, Graph graph) throws ParseException {
        int lnum = 0;
        while (nodes.hasNextLine()) {
            lnum++;
            String line = nodes.nextLine();
            if (line.startsWith("#")) {
                continue;
            }
            StringTokenizer tokenizer = new StringTokenizer(line);
            if (!tokenizer.hasMoreTokens()) {
                continue;
            }
            String node = tokenizer.nextToken();
            nodeList.add(node);
            if (!tokenizer.hasMoreTokens()) {
                throw new ParseException("Expected weight of node in line", lnum);
            }
            String weightStr = tokenizer.nextToken();
            try {
                double weight = Double.parseDouble(weightStr);
                Node vertex = new Node(lnum, weight);
                nodeMap.put(node, vertex);
                graph.addVertex(vertex);

            } catch (NumberFormatException e) {
                throw new ParseException("Expected floating point value of node weight in line", lnum);
            }
        }
    }

    private void parseEdges(Scanner edges, Graph graph) throws ParseException {
        int lnum = 0;
        while (edges.hasNextLine()) {
            lnum++;
            String line = edges.nextLine();
            if (line.startsWith("#")) {
                continue;
            }
            StringTokenizer tokenizer = new StringTokenizer(line);
            if (!tokenizer.hasMoreTokens()) {
                continue;
            }
            String first = tokenizer.nextToken();
            if (!tokenizer.hasMoreTokens()) {
                throw new ParseException("Expected name of second node in edge list in line", lnum);
            }
            String second = tokenizer.nextToken();
            if (!mwcsIO && !tokenizer.hasMoreTokens()) {
                throw new ParseException("Expected weight of edge in line", lnum);
            }
            try {
                double weight;
                if (mwcsIO)  weight = 0;
                else weight = Double.parseDouble(tokenizer.nextToken());
                if (!nodeMap.containsKey(first) || !nodeMap.containsKey(second)) {
                    throw new ParseException("There's no such vertex in edge list in line", lnum);
                }
                Edge edge = new Edge(lnum, weight);
                Node from = nodeMap.get(first);
                Node to = nodeMap.get(second);
                graph.addEdge(from, to, edge);
                edgeList.add(new Pair<>(first, second));
                if (!edgeMap.containsKey(first)) {
                    edgeMap.put(first, new LinkedHashMap<>());
                }
                edgeMap.get(first).put(second, edge);
            } catch (NumberFormatException e) {
                throw new ParseException("Expected floating point value of edge in line", lnum);
            }
        }
    }

    @Override
    public void write(List<Elem> elems) throws IOException {
        if (elems == null) {
            elems = new ArrayList<>();
        }
        Set<Elem> elemSet = new LinkedHashSet<>(elems);
        writeNodes(elemSet);
        writeEdges(elemSet);
    }

    private void writeEdges(Set<Elem> elems) throws IOException {
        double sum = 0.0;
        edgeOut.createNewFile();
        try (Writer writer = new BufferedWriter(new FileWriter(edgeOut))) {
            for (Pair<String, String> p : edgeList) {
                Edge edge = edgeMap.get(p.first).get(p.second);
                writer.write(p.first + "\t" + p.second + "\t" + (elems.contains(edge) ? edge.getWeight() : "n/a"));
                writer.write("\n");
                if (elems.contains(edge)) {
                    sum += edge.getWeight();
                }
            }
            writer.write("#subnet edge score\t" + sum);
        }
    }

    private void writeNodes(Set<Elem> elems) throws IOException {
        double sum = 0.0;
        nodeOut.createNewFile();
        try (Writer writer = new BufferedWriter(new FileWriter(nodeOut))) {
            for (String name : nodeList) {
                Node node = nodeMap.get(name);
                if (elems.contains(node)) {
                    sum += node.getWeight();
                }
                writer.write(name + "\t" + (elems.contains(node) ? node.getWeight() : "n/a"));
                writer.write("\n");
            }
            writer.write("#subnet node score\t" + sum);
        }
    }

    @Override
    public Node nodeByName(String name) {
        return nodeMap.get(name);
    }
}
