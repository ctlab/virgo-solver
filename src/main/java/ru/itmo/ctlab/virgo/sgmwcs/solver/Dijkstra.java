package ru.itmo.ctlab.virgo.sgmwcs.solver;

import ru.itmo.ctlab.virgo.sgmwcs.Signals;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Edge;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Graph;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Node;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Unit;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

class Dijkstra {
    private final Graph graph;
    private final Signals signals;
    private Map<Node, Double> d;
    private Map<Unit, Set<Integer>> p;
    private Map<Node, Set<Edge>> path;
    private Set<Node> dests;

    private Set<Integer> currentSignals;

    private double currentWeight() {
        double w = 0;
        for (int sig: currentSignals) {
            w -= Math.min(0, signals.weight(sig));
        }
        return w;
    }

    private double weight(Node n) {
        return d.getOrDefault(n, Double.MAX_VALUE);
    }

    /**
     * Constructs Dijkstra algorithm instance provided {@link Graph} and
     * {@link Signals}. The distance between two nodes <code>u</code>
     * and <code>v</code> is a modulus of sum of negative sigs on path
     * u -> v.
     */
    Dijkstra(Graph graph, Signals signals) {
        this.graph = graph;
        this.signals = signals;
        this.dests = new HashSet<>();
    }

    /**
     * Calculates distances from {@link Node} <code>u</code> to nodes in {@link Graph}
     * w.r.t. {@link Signals} instance passed to {@link #Dijkstra(Graph, Signals)}
     *
     * @param u The start node. Distance of u -> u is considered as 0.
     */
    public void solve(Node u) {
        d = new HashMap<>();
        p = new HashMap<>();
        PriorityQueue<Node> q = new PriorityQueue<>(Comparator.comparingDouble(this::weight));
        currentSignals = new HashSet<>();
        q.add(u);
        d.put(u, 0.0);
        p.put(u, new HashSet<>(signals.positiveUnitSets(u)));
        Node cur;
        List<Integer> negE, negN;
        path = new HashMap<>();
        List<Integer> addedE = new ArrayList<>(), addedN = new ArrayList<>();
        Set<Node> visitedDests = new HashSet<>();
        path.put(u, Collections.emptySet());
        while ((cur = q.poll()) != null) {
            if (visitedDests.contains(cur))
                continue;
            if (dests.contains(cur)
                    && visitedDests.add(cur)
                    && visitedDests.containsAll(dests)) {
                break;
            }
            currentSignals = p.getOrDefault(cur, new HashSet<>());
            double cw;
            for (Node node : graph.neighborListOf(cur)) {
                cw = currentWeight();
                negN = signals.unitSets(node);
                double sumN = 0;
                for (int i : negN) {
                    if (currentSignals.add(i)) {
                        addedN.add(i);
                        if (signals.weight(i) < 0) {
                            sumN -= signals.weight(i);
                        }
                    }
                }
                cw += sumN;
                for (Edge edge : graph.getAllEdges(node, cur)) {
                    negE = signals.unitSets(edge);
                    double sumE = 0;
                    for (int i : negE) {
                        if (currentSignals.add(i)) {
                            addedE.add(i);
                            if (signals.weight(i) < 0) {
                                sumE -= signals.weight(i);
                            }
                        }
                    }
                    cw += sumE;
                    if (cw < weight(node)) {
                        q.remove(node);
                        d.put(node, cw);
                        p.put(node, new HashSet<>(currentSignals));
                        q.add(node);
                        path.putIfAbsent(node, new HashSet<>(path.get(cur)));
                        graph.getAllEdges(node, cur).forEach(path.get(node)::remove);
                        path.get(node).add(edge);
                    }
                    addedE.forEach(currentSignals::remove);
                    addedE.clear();
                    cw -= sumE;
                }
                addedN.forEach(currentSignals::remove);
                addedN.clear();
            }
        }
    }

    /**
     * Tests NP2 reduction rule which holds if the degree of {@linkplain Node} <code>u</code>
     * is 2, the shortest distance between it's neighbours is less than the sum of negative sigs
     * of <code>u</code> and its adjacent edges and none of them contains positive sigs.
     *
     * @param u Candidate {@linkplain Node} for removal
     * @return <code>true</code> if <code>u</code> can be removed from {@link Graph}
     */
    boolean solveNP(Node u) {
        List<Node> nbors = graph.neighborListOf(u);
        if (nbors.size() != 2) return false;
        Node v_1 = nbors.get(0), v_2 = nbors.get(1);
        this.dests.add(v_2);
        solve(v_1);
        Set<Integer> neg = new HashSet<>(signals.negativeUnitSets(u));
        neg.addAll(signals.negativeUnitSets(graph.edgesOf(u)));
        if (p.get(v_2).containsAll(neg)) return false;
        Set<Integer> pos = new HashSet<>(signals.positiveUnitSets(u));
        pos.addAll(signals.positiveUnitSets(graph.edgesOf(u)));
        pos.removeAll(signals.positiveUnitSets(v_1, v_2));
        return p.get(v_2).containsAll(pos) || -(signals.sum(graph.edgesOf(u)) + signals.weight(u)) > d.get(v_2);
//                && signals.weightSum(signals.filter(p.get(v_2), s -> signals.set(s).size() == 1))
        //               >= signals.minSum(u) + signals.minSum(graph.edgesOf(u));

    }

    /**
     * Tests NPE reduction condition which holds if there exists a path between
     * {@linkplain Node} <code>u</code> and <code>v</code> with weight less than
     * weight of {@linkplain Edge} <code>u - v </code>.
     *
     * @param u         Node with edges to consider
     * @param neighbors neighbors of node u which contain negative edges
     * @return {@linkplain Set} of edges which can be removed.
     */

    Set<Edge> solveNE(Node u, List<Node> neighbors) {
        this.dests = new HashSet<>(neighbors);
        solve(u);
        Set<Edge> res = new HashSet<>();
        neighbors.forEach(n -> {
            List<Edge> edges = graph.getAllEdges(n, u);
            p.get(n).removeAll(signals.unitSets(u, n));
            for (Edge e : edges) {
                if (!p.get(n).containsAll(signals.negativeUnitSets(e)))
                    res.add(e);
            }
        });
        return res;
    }

    /**
     * Tests NPk reduction condition which holds if the { @link NaiveMST} solutions for
     * all subsets of <code>k</code> have less value than <code>p</code>.
     *
     * @param u {@linkplain Node} considered.
     * @param k Adjacent nodes.
     * @return <code>true</code> if condition holds.
     */
    /* unused now due to large time complexity
    boolean solveClique(Node u, Set<Node> k) {
        if (k.size() < 2) return false;
        Map<Node, Map<Node, Double>> distances = new HashMap<>();
        for (Node v : k) {
            solve(v);
            distances.putIfAbsent(v, new HashMap<>());
            Map<Node, Double> cd = distances.get(v);
            for (Node n : k) {
                if (n == v) continue;
                Set<Integer> path = this.p.get(n);
                if (path == null) return false;
                path.addAll(signals.negativeUnitSets(v));
                cd.put(n, -signals.weightSum(signals.filter(path, s -> signals.weight(s) < 0)));
            }
        }
        Set<Set<Node>> subsets = Utils.subsets(k);
        for (Set<Node> subset : subsets) {
            if (subset.size() < 2) continue;
            if (    !signals.positiveUnitSets(subset).containsAll(signals.positiveUnitSets(u))
                    || new NaiveMST(subset, distances).result() + signals.minSum(u) > 0)
                return false;
        }
        return true;
    }
    */

    /**
     * @return distances calculated by {@link #solve(Node)}.
     */
    Map<Node, Double> distances() {
        return d;
    }

    Set<Integer> getPath(Node n) {
        return p.get(n);
    }

    public Set<Unit> greedyHeuristic(Node rt, List<Unit> absorbed) {
        final Node r = new Node(rt);
        Graph graph = new Graph();
        Signals signals = new Signals();
        Utils.copy(this.graph, this.signals, graph, signals);
        List<Node> nodes = new ArrayList<>(graph.vertexSet());
        solve(r);
        ToDoubleFunction<Node> w = n -> {
            Set<Integer> sig = signals.unitSets(path.get(n));
            sig.addAll(signals.unitSets(n, r));
            return signals.weightSum(sig);
        };
        nodes.remove(r);
        Node v = nodes.stream().max(Comparator.comparingDouble(w)).orElse(r);
        if (v == r || w.applyAsDouble(v) <= signals.weight(r)) {
            return new HashSet<>(absorbed);
        }
        Set<Node> pt = path.get(v).stream().map(
                graph::disjointVertices
        ).flatMap(List::stream).collect(Collectors.toSet());
        pt.remove(r);
        Consumer<Unit> absorb = (u) -> {
            r.absorb(u, false);
            signals.join(u, r);
            if (u instanceof Node) {
                Node n = (Node) u;
                for (Edge e : graph.edgesOf(n)) {
                    Node m = graph.getOppositeVertex(n, e);
                    if (!pt.contains(m)) {
                        graph.removeEdge(e);
                        graph.addEdge(r, m, e);
                    }
                }
            }
            graph.removeUnit(u);
        };
        path.get(v).forEach(absorb);
        pt.forEach(absorb);
        return new Dijkstra(graph, signals).greedyHeuristic(r, r.getAbsorbed());
    }
}
