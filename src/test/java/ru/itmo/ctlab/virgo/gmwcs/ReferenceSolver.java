package ru.itmo.ctlab.virgo.gmwcs;

import ru.itmo.ctlab.virgo.gmwcs.graph.Edge;
import ru.itmo.ctlab.virgo.gmwcs.graph.Elem;
import ru.itmo.ctlab.virgo.gmwcs.graph.Graph;
import ru.itmo.ctlab.virgo.gmwcs.graph.Node;

import java.util.*;

import static ru.itmo.ctlab.virgo.gmwcs.solver.Utils.sum;

public class ReferenceSolver {
    public List<Elem> solve(Graph graph, List<Node> roots) {
        for (Node root : roots) {
            if (!graph.containsVertex(root)) {
                throw new IllegalArgumentException();
            }
        }
        if (graph.edgeSet().size() > 31) {
            throw new IllegalArgumentException();
        }
        List<Elem> maxSet = Collections.emptyList();
        double max = roots.isEmpty() ? 0 : -Double.MAX_VALUE;
        // Isolated vertices
        for (Node node : graph.vertexSet()) {
            if ((roots.isEmpty() || (roots.size() == 1 && roots.get(0) == node)) && node.getWeight() > max) {
                max = node.getWeight();
                maxSet = new ArrayList<>();
                maxSet.add(node);
            }
        }
        Edge[] edges = graph.edgeSet().toArray(new Edge[0]);
        int m = edges.length;
        for (int i = 0; i < (1 << m); i++) {
            Set<Edge> currEdges = new LinkedHashSet<>();
            for (int j = 0; j < m; j++) {
                if ((i & (1 << j)) != 0) {
                    currEdges.add(edges[j]);
                }
            }

            Graph subgraph = graph.subgraph(graph.vertexSet(), currEdges);
            for (Set<Node> component : subgraph.connectedSets()) {
                if (component.size() == 1) {
                    subgraph.removeVertex(component.iterator().next());
                }
            }
            List<Set<Node>> connectedSets = subgraph.connectedSets();
            if (connectedSets.size() == 1) {
                Set<Node> res = connectedSets.iterator().next();
                boolean containsRoots = true;
                for (Node root : roots) {
                    if (!res.contains(root)) {
                        containsRoots = false;
                        break;
                    }
                }
                double candidate = sum(new ArrayList<>(res)) + sum(new ArrayList<>(currEdges));
                if (containsRoots && candidate > max) {
                    max = candidate;
                    maxSet = new ArrayList<>();
                    maxSet.addAll(res);
                    maxSet.addAll(currEdges);
                }
            }
        }
        return maxSet;
    }
}
