package ru.itmo.ctlab.virgo.sgmwcs.solver;

import ru.itmo.ctlab.virgo.SolverException;
import ru.itmo.ctlab.virgo.sgmwcs.Signals;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Edge;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Graph;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Node;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Unit;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Nikolay Poperechnyi on 06.05.20.
 */
public class Postprocessor {

    private final int logLevel;
    private Graph g;
    private Signals s;

    private List<Unit> solution;

    public Postprocessor(Graph g, Signals s, List<Unit> sol, int logLevel) {
        this.solution = sol;
        this.s = s;
        this.g = g;
        this.logLevel = logLevel;
    }

    public List<Unit> minimize(double eps) throws SolverException {
        Set<Double> weights = new HashSet<>();
        Set<Integer> sets = new HashSet<>();
        Set<Node> toRemove = new HashSet<>();
        for (Unit u : solution) {
            weights.add(s.weight(u));
            sets.addAll(s.unitSets(u));
        }
        for (Node r : g.vertexSet()) {
            if (!sets.containsAll(s.unitSets(r))) {
                toRemove.add(r);
            }
        }
        for (Node r : toRemove) {
            Set<Edge> es = g.edgesOf(r);
            for (Edge e : es) {
                s.remove(e);
            }
            g.removeVertex(r);
            s.remove(r);
        }
        s.addEdgePenalties(-eps / g.edgeSet().size());
        ComponentSolver solver = new ComponentSolver(25, 0);
        solver.setPreprocessingLevel(0);
        solver.setThreadsNum(4);
        solver.setLogLevel(logLevel);
        List<Unit> res = solver.solve(g, s);
        return res;
    }

    //double computeEdgePenalty() {
    //
    //}
}


