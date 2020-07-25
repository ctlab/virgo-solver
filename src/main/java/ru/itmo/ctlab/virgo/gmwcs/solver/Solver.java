package ru.itmo.ctlab.virgo.gmwcs.solver;


import ru.itmo.ctlab.virgo.TimeLimit;
import ru.itmo.ctlab.virgo.gmwcs.graph.Elem;
import ru.itmo.ctlab.virgo.gmwcs.graph.Graph;

import java.util.List;

public interface Solver {
    List<Elem> solve(Graph graph) throws SolverException;

    void setTimeLimit(TimeLimit tl);

    boolean isSolvedToOptimality();

    void suppressOutput();

    void setLB(double lb);
}
