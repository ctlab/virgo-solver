package ru.itmo.ctlab.virgo.gmwcs.solver;


import ru.itmo.ctlab.gmwcs.solver.preprocessing.PreprocessorKt;
import ru.itmo.ctlab.virgo.SolverException;
import ru.itmo.ctlab.virgo.Pair;
import ru.itmo.ctlab.virgo.TimeLimit;
import ru.itmo.ctlab.virgo.gmwcs.graph.*;

import java.util.*;

import static ru.itmo.ctlab.gmwcs.solver.preprocessing.PreprocessorKt.preprocess;

public class BicomponentSolver implements Solver {
    private TimeLimit rooted;
    private TimeLimit biggest;
    private TimeLimit unrooted;
    private RLTSolver solver;
    private boolean isSolvedToOptimality;
    private double lb;
    private boolean silence;
    private int threadsNum;

    public BicomponentSolver() {

        rooted = new TimeLimit(Double.POSITIVE_INFINITY);
        unrooted = biggest = rooted;
        this.solver = new RLTSolver();
        lb = 0;
        // suppressOutput();
    }

    public void setRootedTL(TimeLimit tl) {
        this.rooted = tl;
    }

    public void setUnrootedTL(TimeLimit tl) {
        this.unrooted = tl;
    }

    public void setTLForBiggest(TimeLimit tl) {
        this.biggest = tl;
    }

    public List<Elem> solve(Graph graph) throws SolverException {
        Graph g = graph;
        graph = graph.subgraph(graph.vertexSet());
        preprocess(graph);
        System.out.print("Preprocessing deleted " + (g.vertexSet().size() - graph.vertexSet().size()) + " nodes ");
        System.out.println("and " + (g.edgeSet().size() - graph.edgeSet().size()) + " edges.");
        isSolvedToOptimality = true;
        solver.setLB(-Double.MAX_VALUE);
        if (graph.vertexSet().size() == 0) {
            return null;
        }
        long timeBefore = System.currentTimeMillis();
        Decomposition decomposition = new Decomposition(graph);
        double duration = (System.currentTimeMillis() - timeBefore) / 1000.0;
        if (!silence) {
            System.out.println("Graph decomposing takes " + duration + " seconds.");
        }
        List<Elem> bestBiggest = solveBiggest(graph, decomposition);
        List<Elem> bestUnrooted = extract(solveUnrooted(graph, decomposition));
        graph.vertexSet().forEach(Node::clear);
        graph.edgeSet().forEach(Edge::clear);
        List<Elem> best = Utils.sum(bestBiggest) > Utils.sum(bestUnrooted) ? bestBiggest : bestUnrooted;
        solver.setLB(-Double.MAX_VALUE);
        if (Utils.sum(best) < 0) {
            return null;
        }
        return best;
    }

    private List<Elem> extract(List<Elem> sol) {
        List<Elem> res = new ArrayList<>();
        for (Elem u : sol) {
            res.addAll(u.getAbsorbed());
            res.add(u);
        }
        return res;
    }

    @Override
    public void setTimeLimit(TimeLimit tl) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSolvedToOptimality() {
        return isSolvedToOptimality;
    }

    @Override
    public void suppressOutput() {
        solver.suppressOutput();
        silence = true;
    }

    @Override
    public void setLB(double lb) {
        this.lb = lb;
    }

    private Node getRoot(Graph graph) {
        Set<Node> rootCandidates = new LinkedHashSet<>();
        for (int i = -1; i < graph.vertexSet().size(); i++) {
            rootCandidates.add(new Node(i, 0.0));
        }
        graph.vertexSet().forEach(v -> rootCandidates.removeAll(v.getAbsorbed()));
        rootCandidates.removeAll(graph.vertexSet());
        return rootCandidates.iterator().next();
    }

    private List<Elem> solveBiggest(Graph graph, Decomposition decomposition) throws SolverException {
        Graph tree = new Graph();
        Map<Elem, List<Elem>> history = new HashMap<>();
        graph.vertexSet().forEach(v -> history.put(v, v.getAbsorbed()));
        graph.edgeSet().forEach(e -> history.put(e, e.getAbsorbed()));
        Node root = getRoot(graph);
        tree.addVertex(root);
        Map<Elem, Node> itsCutpoints = new LinkedHashMap<>();
        for (Pair<Set<Node>, Node> p : decomposition.getRootedComponents()) {
            for (Node node : p.first) {
                for (Edge edge : graph.edgesOf(node)) {
                    itsCutpoints.put(edge, p.second);
                }
                itsCutpoints.put(node, p.second);
            }
            tree.addGraph(graph.subgraph(p.first));
            addAsChild(tree, p.first, p.second, root);
        }
        solver.setRoot(root);
        List<Elem> rootedRes = solve(tree, rooted);
        solver.setRoot(null);
        Graph main = graph.subgraph(decomposition.getBiggestComponent());
        if (rootedRes != null) {
            rootedRes.stream().filter(unit -> unit != root).forEach(unit -> {
                Node cutpoint = itsCutpoints.get(unit);
                cutpoint.absorb(unit);
            });
        }
        solver.setLB(lb);
        List<Elem> solution = solve(main, biggest);
        List<Elem> result = new ArrayList<>(solution);
        solver.setLB(Utils.sum(result));
        solution.forEach(u -> result.addAll(u.getAbsorbed()));
        repairCutpoints(history);
        return result;
    }

    private void repairCutpoints(Map<Elem, List<Elem>> history) {
        history.keySet().forEach(Elem::clear);
        for (Elem u : history.keySet()) {
            for (Elem a : history.get(u)) {
                u.absorb(a);
            }
        }
    }

    private void addAsChild(Graph tree, Set<Node> component, Node cp, Node root) {
        for (Node neighbour : tree.neighborListOf(cp)) {
            if (!component.contains(neighbour)) {
                continue;
            }
            Edge edge = tree.getEdge(cp, neighbour);
            tree.removeEdge(edge);
            tree.addEdge(root, neighbour, edge);
        }
        tree.removeVertex(cp);
    }

    private List<Elem> solveUnrooted(Graph graph, Decomposition decomposition) throws SolverException {
        Set<Node> union = new LinkedHashSet<>();
        decomposition.getUnrootedComponents().forEach(union::addAll);
        return solve(graph.subgraph(union), unrooted);
    }

    private List<Elem> solve(Graph graph, TimeLimit tl) throws SolverException {
        solver.setTimeLimit(tl);
        List<Elem> result = solver.solve(graph);
        if (!solver.isSolvedToOptimality()) {
            isSolvedToOptimality = false;
        }
        return result;
    }

    public void setThreadsNum(int threadsNum) {
        solver.setThreadsNum(threadsNum);
        PreprocessorKt.setThreads(threadsNum);
        this.threadsNum = threadsNum;
    }
}
