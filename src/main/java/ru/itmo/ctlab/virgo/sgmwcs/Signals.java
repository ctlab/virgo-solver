package ru.itmo.ctlab.virgo.sgmwcs;

import ru.itmo.ctlab.virgo.SolverException;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Edge;
import ru.itmo.ctlab.virgo.sgmwcs.graph.Unit;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Signals {
    private final List<Set<Unit>> sets;
    private final Map<Unit, List<Integer>> unitsSets;
    private final List<OptionalDouble> weights;

    public Signals() {
        sets = new ArrayList<>();
        unitsSets = new HashMap<>();
        weights = new ArrayList<>();
    }

    public Signals(Signals signals, Set<Unit> subset) {
        this();
        for (Unit unit : subset) {
            unitsSets.put(unit, new ArrayList<>());
        }
        int j = 0;
        for (int i = 0; i < signals.size(); i++) {
            Set<Unit> set = new HashSet<>();
            for (Unit unit : signals.set(i)) {
                if (subset.contains(unit)) {
                    set.add(unit);
                    unitsSets.get(unit).add(j);
                }
            }
            if (!set.isEmpty()) {
                sets.add(set);
                weights.add(OptionalDouble.of(signals.weight(i)));
                j++;
            }
        }
    }

    public int size() {
        return sets.size();
    }

    public double weight(int num) {
        assert weights.get(num).isPresent();
        return weights.get(num).getAsDouble();
    }

    public Stream<Integer> filter(Set<Integer> sets, Predicate<Integer> pred) {
        return sets.stream().filter(pred);
    }

    public double minSum(Collection<? extends Unit> units) {
        return minSum(units.toArray(new Unit[0]));
    }

    public double maxSum(Collection<? extends Unit> units) {
        return maxSum(units.toArray(new Unit[0]));
    }

    public double maxSum(Unit... units) {
//         return sumByPredicate(units, s -> weight(s) > 0);
        return sum(units);
    }

    public double minSum(Unit... units) {
        return sumByPredicate(units, s -> (set(s).size() == 1) || (weight(s) < 0));
    }


    private double sumByPredicate(Unit[] units, Predicate<Integer> pred) {
        return unitSets(Arrays.stream(units), true)
                .filter(pred)
                .mapToDouble(this::weight)
                .sum();
    }

    public double weight(Unit unit) {
        return unitsSets.get(unit).stream().mapToDouble(this::weight).sum();
    }

    public void join(Unit what, Unit with) {
        List<Integer> x = unitsSets.get(what);
        List<Integer> main = unitsSets.get(with);
        int i = 0, j = 0;
        List<Integer> result = new ArrayList<>();
        while (i != x.size() || j != main.size()) {
            int set;
            if (!(j == main.size()) && (i == x.size() || main.get(j) < x.get(i))) {
                set = main.get(j);
                ++j;
            } else {
                set = x.get(i);
                sets.get(set).remove(what);
                sets.get(set).add(with);
                ++i;
            }
            if (result.isEmpty() || result.get(result.size() - 1) != set) {
                result.add(set);
            }
        }
        unitsSets.put(with, result);
        result = new ArrayList<>(new HashSet<>(unitsSets.get(with)));
        unitsSets.put(with, result);
        unitsSets.remove(what);
    }

    public Map<Unit, List<Integer>> unitSets() {
        Map<Unit, List<Integer>> result = new HashMap<>();
        for (Unit unit : unitsSets.keySet()) {
            result.put(unit, unitsSets.get(unit));
        }
        return result;
    }

    public double sum(Unit... units) {
        return weightSum(unitSets(units));
    }

    public double sum(Collection<? extends Unit> units) {
       return weightSum(unitSets(units));
    }

    public double weightSum(Stream<Integer> sets) {
        return sets.distinct().mapToDouble(this::weight).sum();
    }

    public double weightSum(Collection<Integer> sets) {
        return weightSum(sets.stream());
    }

    public boolean bijection(Unit unit) {
        List<Integer> ss = unitSets(unit);
        return ss.stream().allMatch(s -> set(s).size() == 1);
    }

    public Set<Integer> positiveUnitSets(Unit... units) {
        return positiveUnitSets(Arrays.asList(units));
    }

    public Set<Integer> positiveUnitSets(Collection<? extends Unit> units) {
        return unitSets(units.stream(), true).filter(u -> weight(u) > 0).collect(Collectors.toSet());
    }

    public Set<Integer> positiveUnitSets(Unit unit) {
        return positiveUnitSets(Collections.singletonList(unit));
    }

    public List<Integer> positiveUnitSets(Collection<? extends Unit> units, boolean distinct) {
        return unitSets(units.stream(), distinct).filter(s -> weight(s) > 0).collect(Collectors.toList());
    }

    public Set<Integer> negativeUnitSets(Unit... units) {
        return negativeUnitSets(Arrays.asList(units));
    }

    public Set<Integer> negativeUnitSets(Collection<? extends Unit> units) {
        return unitSets(units.stream(), true).filter(u -> weight(u) < 0).collect(Collectors.toSet());
    }

    public Set<Integer> negativeUnitSets(Unit unit) {
        return negativeUnitSets(Collections.singletonList(unit));
    }

    public List<Integer> negativeUnitSets(Collection<? extends Unit> units, boolean distinct) {
        return unitSets(units.stream(), distinct).filter(u -> weight(u) < 0).collect(Collectors.toList());
    }

    public Set<Integer> unitSets(Unit... units) {
        return unitSets(Arrays.stream(units), true).collect(Collectors.toSet());
    }

    public Set<Integer> unitSets(Collection<? extends Unit> units) {
        return unitSets(units.toArray(new Unit[0]));
    }

    public List<Integer> unitSets(Unit unit) {
        return Collections.unmodifiableList(unitsSets.get(unit));
    }

    public List<Integer> unitSets(Collection<? extends Unit> units, boolean distinct) {
        return unitSets(units.stream(), distinct).collect(Collectors.toList());
    }

    public Stream<Integer> unitSets(Stream<? extends Unit> units, boolean distinct) {
        final Stream<Integer> res = units.map(this::unitSets).flatMap(Collection::stream);
        return distinct ? res.distinct() : res;
    }

    public List<Unit> set(int num) {
        return new ArrayList<>(sets.get(num));
    }

    public void add(Unit unit, int signalTo) {
        sets.get(signalTo).add(unit);
        ensureLink(unit, signalTo);
    }

    private int add(Unit unit) {
        Set<Unit> s = new HashSet<>();
        s.add(unit);
        sets.add(s);
        weights.add(OptionalDouble.empty());
        int num = sets.size() - 1;
        ensureLink(unit, num);
        return num;
    }

    public void remove(Unit unit, int from) {
        sets.get(from).remove(unit);
        unitsSets.get(unit).remove(from);
    }

    public boolean canReplace(Set<Unit> w, Set<Unit> who, Set<Unit> whom) {
        double wsum = maxSum(w);
        w.retainAll(whom);
        w.addAll(who);
        double replSum = maxSum(w);
        return replSum >= wsum;
    }

    public void remove(Unit unit) {
        List<Integer> us = unitsSets.get(unit);
        for (int s: us) {
            sets.get(s).remove(unit);
        }
        unitsSets.remove(unit);
    }

    public int addAndSetWeight(Unit unit, Double weight) {
        int num = add(unit);
        setWeight(num, weight);
        return num;
    }

    /***
     *
     * @param penalty negative value of edge penalty
     */
    public void addEdgePenalties(double penalty) throws SolverException {
        if (penalty > 0) throw new SolverException("expected negative penalty");
        for (Unit unit : unitSets().keySet()) {
            if (unit instanceof Edge) {
                if (bijection(unit)) {
                    int signal = unitSets(unit).get(0);
                    setWeight(signal, weight(signal) + penalty);
                } else {
                    appendSignalToUnit(unit, penalty);
                }
            }
        }
    }

    private void appendSignalToUnit(Unit unit, double weight) {
        int sig = addSignal(weight);
        add(unit, sig);
    }

    public int addSignal(double weight) {
        sets.add(new HashSet<>());
        weights.add(OptionalDouble.of(weight));
        return weights.size() - 1;
    }

    public void setWeight(int set, double weight) {
        weights.set(set, OptionalDouble.of(weight));
    }

    private void ensureLink(Unit unit, int signal) {
        if (unitsSets.containsKey(unit)) {
            unitsSets.get(unit).add(signal);
        } else {
            List<Integer> l = new ArrayList<>();
            unitsSets.put(unit, l);
            l.add(signal);
        }
    }
}
