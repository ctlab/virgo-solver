package ru.itmo.ctlab.virgo.sgmwcs.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class Unit implements Comparable<Unit> {
    protected int num;
    protected List<Unit> absorbed;

    public Unit(int num) {
        this.num = num;
        absorbed = new ArrayList<>();
    }

    public Unit(Unit that) {
        this(that.num);
        this.absorbed = that.getAbsorbed();
    }

    public static List<Unit> extractAbsorbed(Collection<Unit> s) {
        if (s == null) {
            return null;
        }
        List<Unit> l = new ArrayList<>(s);
        for (Unit u : s) {
            l.addAll(u.getAbsorbed());
        }
        return l;
    }

    public void absorb(Unit unit) {
        absorb(unit, true);
    }

    public void absorb(Unit unit, boolean clearUnit) {
        absorbed.addAll(unit.getAbsorbed());
        if (clearUnit)
            unit.clear();
        absorbed.add(unit);
    }

    public void setAbsorbed(List<Unit> absorbed) {
        this.absorbed = absorbed;
    }

    public void clear() {
        absorbed.clear();
    }

    public List<Unit> getAbsorbed() {
        return new ArrayList<>(absorbed);
    }

    @Override
    public int hashCode() {
        return num;
    }

    public int getNum() {
        return num;
    }


    @Override
    public boolean equals(Object o) {
        return (o.getClass() == getClass() && num == ((Unit) o).num);
    }

    @Override
    public int compareTo(Unit u) {
        return Integer.compare(u.getNum(), num);
    }


}