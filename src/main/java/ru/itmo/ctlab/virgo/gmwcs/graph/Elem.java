package ru.itmo.ctlab.virgo.gmwcs.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class Elem implements Comparable<Elem> {
    protected int num;
    protected double weight;
    protected List<Elem> absorbed;

    public Elem(int num, double weight) {
        this.num = num;
        this.weight = weight;
        absorbed = new ArrayList<>();
    }

    public void absorb(Elem elem) {
        for (Elem u : elem.getAbsorbed()) {
            absorbed.add(u);
            weight += u.weight;
        }
        elem.clear();
        absorbed.add(elem);
        weight += elem.weight;
    }

    public void clear() {
        for (Elem elem : absorbed) {
            weight -= elem.getWeight();
        }
        absorbed.clear();
    }

    public List<Elem> getAbsorbed() {
        return new ArrayList<>(absorbed);
    }

    @Override
    public int hashCode() {
        return num;
    }

    public int getNum() {
        return num;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    @Override
    public boolean equals(Object o) {
        return (o.getClass() == getClass() && num == ((Elem) o).num);
    }

    @Override
    public int compareTo(Elem u) {
        if (u.weight != weight) {
            return Double.compare(u.weight, weight);
        }
        return Integer.compare(u.getNum(), num);
    }

    public static List<Elem> extract(Collection<Elem> elems) {
        List<Elem> res = new ArrayList<>();
        for (Elem u : elems) {
            res.addAll(u.getAbsorbed());
            res.add(u);
        }
        return res;
    }
}