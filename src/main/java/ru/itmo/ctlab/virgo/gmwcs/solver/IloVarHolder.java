package ru.itmo.ctlab.virgo.gmwcs.solver;

import ilog.concert.IloException;
import ilog.concert.IloNumVar;
import ru.itmo.ctlab.virgo.gmwcs.graph.Edge;
import ru.itmo.ctlab.virgo.gmwcs.graph.Graph;
import ru.itmo.ctlab.virgo.gmwcs.graph.Node;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Nikolay Poperechnyi on 21.02.18.
 */
public abstract class IloVarHolder {

    protected abstract void setSolution(IloNumVar[] v, double[] d) throws IloException;

    protected abstract double getValue(IloNumVar v) throws IloException;

    public Map<Edge, Double> buildVarGraph(Graph graph,
                                           Map<Node, IloNumVar> y,
                                           Map<Edge, IloNumVar> w) throws IloException {
        Map<Edge, Double> result = new HashMap<>();
        for (Edge e : graph.edgeSet()) {
            Node u = graph.getEdgeSource(e);
            Node v = graph.getEdgeTarget(e);
            double uw = getValue(y.get(u));
            double vw = getValue(y.get(v));
            double ew = getValue(w.get(e));
            result.put(e, 3 - uw - vw - ew);
        }
        return result;
    }


}
