package ru.itmo.ctlab.virgo.gmwcs.graph;

public class Node extends Elem {

    public Node(int num, double weight) {
        super(num, weight);
    }

    @Override
    public String toString() {
        return "N(" + String.valueOf(num) + ", " + String.valueOf(weight) + ")";
    }
}
