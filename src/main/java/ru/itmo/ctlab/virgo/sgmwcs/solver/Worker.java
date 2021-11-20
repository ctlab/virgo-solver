package ru.itmo.ctlab.virgo.sgmwcs.solver;

import ru.itmo.ctlab.virgo.SolverException;
import ru.itmo.ctlab.virgo.sgmwcs.Signals;
import ru.itmo.ctlab.virgo.TimeLimit;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Graph;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Node;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Unit;

import java.util.*;
import java.util.stream.Collectors;

public class Worker implements Runnable {
    private final Signals signals;
    private final Graph graph;
    private final RootedSolver solver;
    private final Node root;
    private List<Unit> result = new ArrayList<>();
    private boolean isSolvedToOptimality;
    private long startTime;

    public Worker(Graph graph, Node root, Signals signals, RootedSolver solver, long time) {
        this.solver = solver;
        this.graph = graph;
        this.signals = signals;
        this.root = root;
        isSolvedToOptimality = false;
        startTime = time;
    }

    @Override
    public void run() {
        Set<Node> vertexSet = graph.vertexSet();
        if (vertexSet.size() <= 1) {
            result = vertexSet.stream().filter(n -> signals.weight(n) >= 0).collect(Collectors.toList());
            return;
        }
        final Node treeRoot = Optional.ofNullable(root).orElse(
                vertexSet.stream().max(Comparator.comparing(signals::weight)).orElseThrow());
        List<Unit> sol = new ArrayList<>(new Dijkstra(graph, signals)
                .greedyHeuristic(treeRoot, new ArrayList<>()));
        sol.add(treeRoot);
        sol = Unit.extractAbsorbed(sol)
                .stream().filter(graph::containsUnit)
                .collect(Collectors.toList());
        if (solver != null) try {
            double tl = solver.getTimeLimit().getRemainingTime() - (System.currentTimeMillis() - startTime) / 1000.0;
            if (tl <= 0) {
                isSolvedToOptimality = false;
                return;
            }
            solver.setRoot(root);
            solver.setTimeLimit(new TimeLimit(Math.max(tl, 0.0)));
            double tlb = signals.sum(sol);
            double plb = solver.getLB().get();
            if (tlb >= plb) {
                System.out.println("heuristic found lb " + tlb);
                solver.setInitialSolution(sol);
                solver.getLB().compareAndSet(plb, tlb);
            }
            sol = solver.solve(graph, signals);
            isSolvedToOptimality = solver.isSolvedToOptimality();
        } catch (SolverException e) {
            result = null;
            return;
        }
        if (signals.sum(sol) > signals.sum(result)) {
            result = sol;
        }
    }

    public List<Unit> getResult() {
        return result;
    }

    public boolean isSolvedToOptimality() {
        return isSolvedToOptimality;
    }

}
