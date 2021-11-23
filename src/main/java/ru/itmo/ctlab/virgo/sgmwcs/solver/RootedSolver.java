package ru.itmo.ctlab.virgo.sgmwcs.solver;

import ru.itmo.ctlab.virgo.sgmwcs.graph.Node;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Unit;

import java.util.Collection;

public interface RootedSolver extends Solver {
    void setRoot(Node root);

    void setInitialSolution(Collection<Unit> sol);
}
