package ru.itmo.ctlab.virgo.gmwcs.solver;


import ru.itmo.ctlab.virgo.gmwcs.graph.Node;

public interface RootedSolver extends Solver {
    void setRoot(Node root);
}
