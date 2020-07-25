package ru.itmo.ctlab.virgo.gmwcs.graph;

public class Edge extends Elem {
    public Edge(int num, double weight) {
        super(num, weight);
    }

    @Override
    public String toString() {
        return "E(" + String.valueOf(num) + ", " + String.valueOf(weight) + ")";
    }
}
