package ru.itmo.ctlab.virgo.sgmwcs.solver;

import ru.itmo.ctlab.virgo.SolverException;
import ru.itmo.ctlab.virgo.TimeLimit;
import ru.itmo.ctlab.virgo.sgmwcs.Signals;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Graph;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Unit;

import java.util.List;

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

    public List<Unit> minimize(double eps, TimeLimit tl) throws SolverException {
        Graph toMinimizeG = g.subgraph(solution);
        Signals toMinimizeS = new Signals(s, toMinimizeG.units());
        toMinimizeS.addEdgePenalties(-eps / Math.max(toMinimizeG.edgeSet().size(), 1));
        ComponentSolver solver = new ComponentSolver(150, 0);
        solver.setPreprocessingLevel(2);
        solver.setThreadsNum(4);
        solver.setLogLevel(logLevel);
        solver.setTimeLimit(tl);
        return solver.solve(toMinimizeG, toMinimizeS);
    }
}
