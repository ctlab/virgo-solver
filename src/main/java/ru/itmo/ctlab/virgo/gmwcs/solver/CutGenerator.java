package ru.itmo.ctlab.virgo.gmwcs.solver;

import ru.itmo.ctlab.virgo.Pair;
import ru.itmo.ctlab.virgo.gmwcs.graph.Edge;
import ru.itmo.ctlab.virgo.gmwcs.graph.Graph;
import ru.itmo.ctlab.virgo.gmwcs.graph.Node;
import ru.itmo.ctlab.virgo.gmwcs.graph.flow.EdmondsKarp;
import ru.itmo.ctlab.virgo.gmwcs.graph.flow.MaxFlow;

import java.util.*;

public class CutGenerator {
    private MaxFlow maxFlow;
    private Map<Node, Integer> nodes;
    private Node root;
    private Map<Edge, Pair<Integer, Integer>> edges;
    private List<Node> backLink;
    private Map<Node, Double> weights;
    private Graph graph;

    public CutGenerator(Graph graph, Node root) {
        int i = 0;
        weights = new HashMap<>();
        backLink = new ArrayList<>();
        nodes = new HashMap<>();
        edges = new HashMap<>();
        for (Node node : graph.vertexSet()) {
            nodes.put(node, i++);
            backLink.add(node);
        }
        maxFlow = new EdmondsKarp(graph.vertexSet().size());
        for (Edge e : graph.edgeSet()) {
            Node v = graph.getEdgeSource(e);
            Node u = graph.getEdgeTarget(e);
            maxFlow.addEdge(nodes.get(v), nodes.get(u));
            edges.put(e, new Pair<>(nodes.get(v), nodes.get(u)));
        }
        this.root = root;
        this.graph = graph;
    }

    public void setCapacity(Edge e, double capacity) {
        Pair<Integer, Integer> edge = edges.get(e);
        maxFlow.setCapacity(edge.first, edge.second, capacity);
        maxFlow.setCapacity(edge.second, edge.first, capacity);
    }

    public void setVertexCapacity(Node v, double capacity) {
        weights.put(v, capacity);
    }

    public List<Edge> findCut(Node v) {
        List<Pair<Integer, Integer>> cut = maxFlow.computeMinCut(nodes.get(root), nodes.get(v), weights.get(v));
        if (cut == null) {
            return null;
        }
        List<Edge> result = new ArrayList<>();
        for (Pair<Integer, Integer> p : cut) {
            result.addAll(graph.getAllEdges(backLink.get(p.first), backLink.get(p.second)));
        }
        return result;
    }

    public Set<Node> getNodes() {
        return nodes.keySet();
    }

    public Set<Edge> getEdges() {
        return edges.keySet();
    }

    public Node getRoot() {
        return root;
    }
}
