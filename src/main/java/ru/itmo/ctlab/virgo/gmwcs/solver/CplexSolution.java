package ru.itmo.ctlab.virgo.gmwcs.solver;

import ilog.concert.IloNumVar;
import ru.itmo.ctlab.virgo.gmwcs.graph.Elem;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Created by Nikolay Poperechnyi on 18.04.18.
 */
public class CplexSolution {
    private List<IloNumVar> variables = new LinkedList<>();
    private List<Double> values = new LinkedList<>();

    public IloNumVar[] variables() {
        return variables.toArray(new IloNumVar[0]);
    }

    public double[] values() {
        return values.stream().mapToDouble(d -> d).toArray();
    }

    <U extends Elem> void addVariable(Map<U, IloNumVar> map,
                                      U elem, double val) {
        addVariable(map.get(elem), val);
    }

    void addVariable(IloNumVar var, double val) {
/*            try {
                cplex.addEq(var, val); // for debug purposes: add
            } catch (IloException e) { // these constraints to check infeasibility of heuristic
                throw new Error("oops");
            } */
        variables.add(var);
        values.add(val);
    }

    void addNullVariables(IloNumVar... vars) {
        for (IloNumVar var : vars) {
            addVariable(var, 0);
        }
    }


    boolean apply(BiFunction<IloNumVar[], double[], Boolean> set) {
        double[] vals = new double[values.size()];
        for (int i = 0; i < values.size(); ++i) {
            vals[i] = values.get(i);
        }
        return set.apply(variables.toArray(new IloNumVar[0]), vals);
    }
}

