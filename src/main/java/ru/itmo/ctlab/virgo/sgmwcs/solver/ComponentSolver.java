package ru.itmo.ctlab.virgo.sgmwcs.solver;

import ru.itmo.ctlab.virgo.SolverException;
import ru.itmo.ctlab.virgo.sgmwcs.Signals;
import ru.itmo.ctlab.virgo.TimeLimit;
import ru.itmo.ctlab.virgo.sgmwcs.graph.*;

import java.util.*;
import java.util.concurrent.*;

public class ComponentSolver implements Solver {
    private final int threshold;
    private TimeLimit tl;
    private AtomicDouble lb;
    private boolean isSolvedToOptimality;
    private int logLevel;
    private int threads;
    private boolean cplexOff;

    private final double eps;
    private final boolean minimize;

    private int preprocessLevel;
    private Graph g;
    private Signals s;

    private final int[] preprocessedSize = {0, 0};

    long startTime;

    BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

    ExecutorService executor;

    public int preprocessedNodes() {
        return preprocessedSize[0];
    }

    public int preprocessedEdges() {
        return preprocessedSize[1];
    }

    public ComponentSolver(int threshold, double eps) {
        this.threshold = threshold;
        this.minimize = eps > 0;
        this.eps = eps;
        lb = new AtomicDouble(Double.NEGATIVE_INFINITY);
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
        threads = 1;
    }

    @Override
    public List<Unit> solve(Graph graph, Signals signals) throws SolverException {
        this.g = graph;
        this.s = signals;
        Graph g = new Graph();
        Signals s = new Signals();
        int vertexBefore = graph.vertexSet().size(), edgesBefore = graph.edgeSet().size();
        Utils.copy(graph, signals, g, s);
        Set<Unit> units = new HashSet<>(g.vertexSet());
        units.addAll(g.edgeSet());
        new Preprocessor(g, s, threads, logLevel).preprocess(preprocessLevel);
        preprocessedSize[0] = g.vertexSet().size();
        preprocessedSize[1] = g.edgeSet().size();
        if (logLevel > 0) {
            System.out.print("Preprocessing deleted " + (vertexBefore - g.vertexSet().size()) + " nodes ");
            System.out.println("and " + (edgesBefore - g.edgeSet().size()) + " edges.");
        }
        isSolvedToOptimality = false;
        if (g.vertexSet().size() == 0) {
            return null;
        }
        return afterPreprocessing(g, new Signals(s, units));
    }

    private List<Unit> afterPreprocessing(Graph graph, Signals signals) throws SolverException {
        startTime = System.currentTimeMillis();
        PriorityQueue<Set<Node>> components = getComponents(graph);
        List<Worker> memorized = new ArrayList<>();
        executor = new ThreadPoolExecutor(threads, threads, Long.MAX_VALUE, TimeUnit.NANOSECONDS, queue);
        while (!components.isEmpty()) {
            Set<Node> component = components.poll();
            Graph subgraph = graph.subgraph(component);
            Node root = null;
            double timeRemains = tl.getRemainingTime()
                    - (System.currentTimeMillis() - startTime) / 1000.0;
            if (component.size() >= threshold && timeRemains > 0) {
                root = getRoot(subgraph, new Blocks(subgraph));
                if (root != null) {
                    addComponents(subgraph, root, components);
                }
            }
            addWorker(subgraph, signals, root, memorized);
        }

        executor.shutdown();
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException ignored) {
        }
        return getResult(memorized, graph, signals);
    }

    private List<Unit> getResult(List<Worker> memorized, Graph graph, Signals signals) throws SolverException {
        List<Unit> best = null;
        double bestScore = -Double.MAX_VALUE;
        for (Worker worker : memorized) {
            List<Unit> solution = worker.getResult();
            if (solution == null) {
                throw new SolverException("Worker " + memorized.indexOf(worker) + "failed");
            }
            if (bestScore < Utils.sum(solution, signals)) {
                best = solution;
                bestScore = Utils.sum(solution, signals);
                isSolvedToOptimality = worker.isSolvedToOptimality();
            }
        }
        List<Unit> result = Unit.extractAbsorbed(best);
        graph.vertexSet().forEach(Unit::clear);
        graph.edgeSet().forEach(Unit::clear);
        if (minimize && bestScore > 0) {
            System.out.println("RUNNING MINIMIZATION");
            return new Postprocessor(g, s, result, logLevel).minimize(eps, tl);
        } else return result;
    }

    private Node getRoot(Graph graph, Blocks blocks) {
        Map<Node, Integer> maximum = new HashMap<>();
        if (blocks.cutpoints().isEmpty()) {
            return null;
        }
        Node v = blocks.cutpoints().iterator().next();
        dfs(v, null, blocks, maximum, graph.vertexSet().size());
        if (maximum.isEmpty()) {
            return null;
        }
        Node best = maximum.keySet().iterator().next();
        for (Node u : maximum.keySet()) {
            if (maximum.get(u) < maximum.get(best)) {
                best = u;
            }
        }
        return best;
    }

    private int dfs(Node v, Node p, Blocks blocks, Map<Node, Integer> max, int n) {
        int res = 0;
        for (Set<Node> c : blocks.incidentBlocks(v)) {
            if (c.contains(p)) {
                continue;
            }
            int sum = c.size() - 1;
            for (Node cp : blocks.cutpointsOf(c)) {
                if (cp != v) {
                    sum += dfs(cp, v, blocks, max, n);
                }
            }
            if (!max.containsKey(v) || max.get(v) < sum) {
                max.put(v, sum);
            }
            res += sum;
        }
        int rest = n - res - 1;
        if (!max.containsKey(v) || max.get(v) < rest) {
            max.put(v, rest);
        }
        return res;
    }

    @Override
    public boolean isSolvedToOptimality() {
        return isSolvedToOptimality;
    }

    private void addComponents(Graph graph, Node root,
                               PriorityQueue<Set<Node>> components) {
        graph = graph.subgraph(graph.vertexSet());
        graph.removeVertex(root);
        components.addAll(graph.connectedSets());
    }

    private PriorityQueue<Set<Node>> getComponents(Graph graph) {
        PriorityQueue<Set<Node>> result = new PriorityQueue<>(new SetComparator());
        result.addAll(graph.connectedSets());
        return result;
    }

    @Override
    public TimeLimit getTimeLimit() {
        return tl;
    }

    @Override
    public void setTimeLimit(TimeLimit tl) {
        this.tl = tl;
    }

    @Override
    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
    }

    public void setThreadsNum(int n) {
        if (n < 1) {
            throw new IllegalArgumentException();
        }
        threads = n;
    }

    @Override
    public void setLB(AtomicDouble lb) {
        this.lb = lb;
    }

    @Override
    public AtomicDouble getLB() {
        return lb;
    }


    public void addWorker(Graph subgraph, Signals signals, Node root, List<Worker> memorized) {
        Set<Unit> subset = subgraph.units();
        Signals subSignals = new Signals(signals, subset);
        RootedSolver solver = null;
        if (!this.cplexOff) {
            solver = new RLTSolver(this.minimize ? 0 : 1e-6);
            solver.setLB(lb);
            solver.setTimeLimit(tl);
            solver.setLogLevel(logLevel);
        }
        Worker worker = new Worker(subgraph, root,
                subSignals, solver, startTime);
        executor.execute(worker);
        memorized.add(worker);
    }

    public void setPreprocessingLevel(int preprocessLevel) {
        this.preprocessLevel = preprocessLevel;
    }

    public void setCplexOff(boolean cplexOff) {
        this.cplexOff = cplexOff;
    }

    public static class SetComparator implements Comparator<Set<Node>> {
        @Override
        public int compare(Set<Node> o1, Set<Node> o2) {
            return -Integer.compare(o1.size(), o2.size());
        }
    }
}
