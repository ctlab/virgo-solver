package ru.itmo.ctlab.virgo.sgmwcs.solver;

import ru.itmo.ctlab.virgo.SolverException;
import ru.itmo.ctlab.virgo.sgmwcs.Signals;
import ru.itmo.ctlab.virgo.TimeLimit;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Edge;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Graph;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Node;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Unit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface Solver {
    List<Unit> solve(Graph graph, Signals signals) throws SolverException;

    boolean isSolvedToOptimality();

    TimeLimit getTimeLimit();

    void setTimeLimit(TimeLimit tl);

    void setLogLevel(int logLevel);

    void setLB(AtomicDouble lb);

    AtomicDouble getLB();

}
