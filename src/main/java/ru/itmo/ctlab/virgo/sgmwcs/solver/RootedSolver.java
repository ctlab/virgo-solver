package ru.itmo.ctlab.virgo.sgmwcs.solver;

import ru.itmo.ctlab.virgo.sgmwcs.graph.Node;

public interface RootedSolver extends Solver {
    void setRoot(Node root);

    void setSolIsTree(boolean solutionIsTree);
}
