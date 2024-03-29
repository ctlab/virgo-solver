package ru.itmo.ctlab.virgo.sgmwcs.solver;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloObjective;
import ilog.cplex.IloCplex;
import ru.itmo.ctlab.virgo.Pair;
import ru.itmo.ctlab.virgo.SolverException;
import ru.itmo.ctlab.virgo.TimeLimit;
import ru.itmo.ctlab.virgo.sgmwcs.Signals;
import ru.itmo.ctlab.virgo.sgmwcs.graph.*;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static ilog.cplex.IloCplex.*;

public class RLTSolver implements RootedSolver {
    private static final double EPS = 1e-9;
    private double MIPGap;
    private IloCplex cplex;
    private Map<Node, IloNumVar> y;
    private Map<Edge, IloNumVar> w;
    private Map<Edge, Pair<IloNumVar, IloNumVar>> x;
    private Map<Node, IloNumVar> d;
    private Map<Node, IloNumVar> x0;
    private Map<Integer, IloNumVar> s;
    private Collection<Unit> initialSolution;
    private TimeLimit tl;
    private final int threads;
    private int logLevel;
    private Graph graph;
    private Signals signals;
    private Node root;
    private boolean isSolvedToOptimality;
    private final int maxToAddCuts;
    private final int considerCuts;
    private AtomicDouble lb;
    private boolean isLBShared;
    private IloNumVar sum;
    private boolean solutionIsTree;

    public RLTSolver(double MIPGap) {
        this.MIPGap = MIPGap;
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
        threads = 1;
        maxToAddCuts = considerCuts = Integer.MAX_VALUE;
    }


    public void setInitialSolution(Collection<Unit> solution) {
        this.initialSolution = solution;
    }

    @Override
    public TimeLimit getTimeLimit() {
        return tl;
    }

    public void setTimeLimit(TimeLimit tl) {
        this.tl = tl;
    }

    public void setRoot(Node root) {
        this.root = root;
    }

    @Override
    public List<Unit> solve(Graph g, Signals s) throws SolverException {
        this.graph = g;
        this.signals = s;
        try {
            isSolvedToOptimality = false;
            if (!isLBShared) {
                lb = new AtomicDouble(Double.NEGATIVE_INFINITY);
            }
            cplex = new IloCplex();
            setCplexLog();
            initVariables();
            addConstraints();
            addObjective(signals);
            maxSizeConstraints(signals);
            if (root == null) {
                breakRootSymmetry();
            } else {
                tighten();
            }
            if (solutionIsTree) {
                treeConstraints();
            }
            breakTreeSymmetries();
            tuning(cplex);
            // if (graph.edgeSet().size() >= 1)
            //  cplex.use(new MSTCallback());
            if (initialSolution != null) {
                CplexSolution sol = constructMstSolution(initialSolution);
                if (sol != null) {
                    boolean applied = sol.apply((vars, vals) -> {
                                try {
                                    cplex.addMIPStart(vars, vals, MIPStartEffort.Repair);
                                    return true;
                                } catch (IloException e) {
                                    return false;
                                }
                            }
                    );
                    if (!applied) {
                        throw new SolverException("MST Heuristic not applied");
                    }
                }
            }
            // for model and starts;  uncomment to debug
            /*
            List<IloConstraint> constraints = new ArrayList<>();
            for (Iterator it = cplex.rangeIterator(); it.hasNext(); ) {
                Object c = it.next();
                constraints.add((IloRange) c);
            }
            double[] zeros = new double[constraints.size()];
            Arrays.fill(zeros, 0);
            if (cplex.refineMIPStartConflict(0, constraints.toArray(new IloConstraint[0]), zeros)) {
                System.out.println("Conflict refined");
                cplex.writeMIPStarts("../starts.mst");
            } else System.out.println("Conflict not refined");
            cplex.exportModel("../model.lp"); */
            boolean solFound = cplex.solve();
            if (cplex.getCplexStatus() != CplexStatus.AbortTimeLim) {
                isSolvedToOptimality = true;
            }
            if (solFound) {
                return getResult();
            } else if (initialSolution != null) {
                return new ArrayList<>(initialSolution);
            }
            return Collections.emptyList();
        } catch (IloException e) {
            throw new SolverException(e.getMessage());
        } finally {
            if (cplex != null) {
                cplex.end();
            }
        }
    }

    private void setCplexLog() {
        if (logLevel < 2) {
            cplex.setOut(null);
            cplex.setWarning(null);
        }
    }


    private void breakTreeSymmetries() throws IloException {
        int n = graph.vertexSet().size();
        for (Edge e : graph.edgeSet()) {
            Node from = graph.getEdgeSource(e);
            Node to = graph.getEdgeTarget(e);
            cplex.addLe(cplex.sum(d.get(from), cplex.prod(n - 1, w.get(e))), cplex.sum(n, d.get(to)), "brt" + n);
            cplex.addLe(cplex.sum(d.get(to), cplex.prod(n - 1, w.get(e))), cplex.sum(n, d.get(from)), "brt" + n);
        }
    }

    private void tighten() throws IloException {
        Blocks blocks = new Blocks(graph);
        if (!blocks.cutpoints().contains(root)) {
            return;
        }
        Separator separator = new Separator(y, w, cplex, graph, sum, lb);
        separator.setMaxToAdd(maxToAddCuts);
        separator.setMinToConsider(considerCuts);
        for (Set<Node> component : blocks.incidentBlocks(root)) {
            dfs(root, component, true, blocks, separator);
        }
        cplex.use(separator);
    }

    private void dfs(Node root, Set<Node> component, boolean fake, Blocks bs, Separator separator) throws IloException {
        separator.addComponent(graph.subgraph(component), root);
        if (!fake) {
            for (Node node : component) {
                cplex.addLe(cplex.diff(y.get(node), y.get(root)), 0, "dfs" + node.getNum());
            }
        }
        for (Edge e : graph.edgesOf(root)) {
            if (!component.contains(graph.getOppositeVertex(root, e))) {
                continue;
            }
            cplex.addEq(getX(e, root), 0, "edge_" + e.getNum() + "root_" + root.getNum());
        }
        for (Node cp : bs.cutpointsOf(component)) {
            if (root != cp) {
                for (Set<Node> comp : bs.incidentBlocks(cp)) {
                    if (comp != component) {
                        dfs(cp, comp, false, bs, separator);
                    }
                }
            }
        }
    }

    public boolean isSolvedToOptimality() {
        return isSolvedToOptimality;
    }

    private List<Unit> getResult() throws IloException {
        List<Unit> result = new ArrayList<>();
        for (Node node : graph.vertexSet()) {
            if (cplex.getValue(y.get(node)) > 0.5) {
                result.add(node);
            }
        }
        for (Edge edge : graph.edgeSet()) {
            if (cplex.getValue(w.get(edge)) > 0.5) {
                result.add(edge);
            }
        }
        return result;
    }

    private void initVariables() throws IloException {
        y = new LinkedHashMap<>();
        w = new LinkedHashMap<>();
        d = new LinkedHashMap<>();
        x = new LinkedHashMap<>();
        x0 = new LinkedHashMap<>();
        s = new LinkedHashMap<>();
        for (Node node : graph.vertexSet()) {
            String nodeName = Integer.toString(node.getNum() + 1);
            d.put(node, cplex.numVar(0, Double.MAX_VALUE, "d" + nodeName));
            y.put(node, cplex.boolVar("y" + nodeName));
            x0.put(node, cplex.boolVar("x_0_" + (node.getNum() + 1)));
        }
        Set<String> usedEdges = new HashSet<>();
        for (Edge edge : graph.edgeSet()) {
            Node from = graph.getEdgeSource(edge);
            Node to = graph.getEdgeTarget(edge);
            int num = 0;
            String edgeName;
            do {
                edgeName = (from.getNum() + 1) + "_" + (to.getNum() + 1) + "_" + num;
                num++;
            } while (usedEdges.contains(edgeName));
            usedEdges.add(edgeName);
            w.put(edge, cplex.boolVar("w_" + edgeName));
            IloNumVar in = cplex.boolVar("x_" + edgeName + "_in");
            IloNumVar out = cplex.boolVar("x_" + edgeName + "_out");
            x.put(edge, new Pair<>(in, out));
        }
    }

    private void tuning(IloCplex cplex) throws IloException {
        if (isLBShared) {
            cplex.use(new MIPCallback(logLevel == 0));
        }

        cplex.setParam(Param.Emphasis.MIP, 1);
        cplex.setParam(Param.Threads, threads);
        cplex.setParam(Param.Parallel, -1);
        cplex.setParam(Param.MIP.OrderType, 3);
        if (MIPGap == 0) MIPGap = getMipGap();
        cplex.setParam(Param.MIP.Tolerances.AbsMIPGap, MIPGap);
        if (tl.getRemainingTime() <= 0) {
            cplex.setParam(Param.TimeLimit, EPS);
        } else if (tl.getRemainingTime() != Double.POSITIVE_INFINITY) {
            cplex.setParam(Param.TimeLimit, tl.getRemainingTime());
        }
    }

    private double getMipGap() {
        double absMin = Double.POSITIVE_INFINITY;
        for (int i = 0; i < signals.size(); i++) {
            double w = signals.weight(i);
            absMin = Math.min(absMin, Math.abs(w));
        }
        return absMin / 2;
    }

    private void breakRootSymmetry() throws IloException {
        int n = graph.vertexSet().size();
        PriorityQueue<Node> nodes = new PriorityQueue<>(graph.vertexSet());
        int k = n;
        IloNumExpr[] terms = new IloNumExpr[n];
        IloNumExpr[] rs = new IloNumExpr[n];

        while (!nodes.isEmpty()) {
            Node node = nodes.poll();
            terms[k - 1] = cplex.prod(k, x0.get(node));
            rs[k - 1] = cplex.prod(k, y.get(node));
            k--;
        }
        IloNumVar prSum = cplex.numVar(0, n, "prSum");
        cplex.addEq(prSum, cplex.sum(terms), "prSum");
        for (int i = 0; i < n; i++) {
            cplex.addGe(prSum, rs[i], "brs" + k);
        }
    }

    private void addObjective(Signals signals) throws IloException {
        List<Double> ks = new ArrayList<>();
        List<IloNumVar> vs = new ArrayList<>();
        double negSum = 0, min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < signals.size(); i++) {
            double weight = signals.weight(i);
            List<Unit> set = signals.set(i);
            IloNumVar[] vars = set.stream()
                    .map(this::getVar).filter(Objects::nonNull)
                    .toArray(IloNumVar[]::new);


            IloNumExpr vsum = cplex.sum(vars);

            IloNumVar x = cplex.boolVar("s" + i);
            for (Unit u : set) {
                IloNumVar r = getVar(u);
                cplex.addLe(r, x);
            }

            if (vars.length == 0 || weight == 0.0) {
                continue;
            } else if (Double.isInfinite(weight)) {
                // cplex.addEq(x, 1);
                cplex.addLazyConstraint(cplex.range(1, vsum, vars.length, "sig_root" + i));
                weight = 0;
                signals.setWeight(i, 0);
            } else if (weight > 0) {
                min = Math.min(min, weight);
            } else {
                negSum += weight;
            }
            ks.add(weight);
            if (vars.length == 1) {
                vs.add(vars[0]);
                continue;
            }
            s.put(i, x);
            vs.add(x);
            if (weight >= 0) {
                cplex.addLe(x, vsum, "sig_sum_pos" + i);
            } else {
                cplex.addGe(cplex.prod(vars.length, x), vsum, "sig_sum_neg" + i);
            }
        }
        IloObjective sum = cplex.maximize();
        sum.setExpr(cplex.scalProd(ks.stream().mapToDouble(d -> d).toArray(),
                vs.toArray(new IloNumVar[0])));
        this.sum = cplex.numVar(negSum - 1, Double.POSITIVE_INFINITY, "sum");
        cplex.addGe(sum.getExpr(), lb.get(), "lb");
        cplex.addEq(this.sum, sum.getExpr(), "seq");
        cplex.add(sum);
    }

    private IloNumVar getVar(Unit unit) {
        return unit instanceof Node ? y.get(unit) : w.get((Edge) unit);
    }

    @Override
    public void setLogLevel(int logLevel) {
        this.logLevel = logLevel;
    }

    @Override
    public void setLB(AtomicDouble lb) {
        this.lb = lb;
        isLBShared = true;
    }

    private void addConstraints() throws IloException {
        sumConstraints();
        otherConstraints();
        distanceConstraints();
    }

    private void distanceConstraints() throws IloException {
        int n = graph.vertexSet().size();
        for (Node v : graph.vertexSet()) {
            cplex.addLe(d.get(v), cplex.diff(n, cplex.prod(n, x0.get(v))), "dist" + v.getNum());
        }
        for (Edge e : graph.edgeSet()) {
            Node from = graph.getEdgeSource(e);
            Node to = graph.getEdgeTarget(e);
            addEdgeConstraints(e, from, to);
            addEdgeConstraints(e, to, from);
        }
    }

    private void addEdgeConstraints(Edge e, Node from, Node to) throws IloException {
        int n = graph.vertexSet().size();
        IloNumVar z = getX(e, to);
        cplex.addGe(cplex.sum(n, d.get(to)), cplex.sum(d.get(from), cplex.prod(n + 1, z)),
                "edge_constraints" + e.getNum() + "_1");
        cplex.addLe(cplex.sum(d.get(to), cplex.prod(n - 1, z)), cplex.sum(d.get(from), n),
                "edge_constraints" + e.getNum() + "_2");
    }

    private void maxSizeConstraints(Signals signals) throws IloException {
        for (Node v : graph.vertexSet()) {
            for (Node u : graph.neighborListOf(v)) {
                if (signals.minSum(u) >= 0) {
                    Edge e = graph.getAllEdges(v, u)
                            .stream().max(Comparator.comparingDouble(signals::weight)).get();
                    if (signals.minSum(e) >= 0) {
                        for (int sig : signals.unitSets(e)) {
                            cplex.addLe(y.get(v), s.getOrDefault(sig, w.get(e)));
                        }
                    }
                }
            }
        }
    }

    /*
        private void psdConstraints(Signals signals, PSD psd) throws IloException {
            Map<PSD.Center, List<PSD.Path>> centerPaths = psd.centerPaths();
            for (Map.Entry<PSD.Center, List<PSD.Path>> cps : centerPaths.entrySet()) {
                PSD.Center c = cps.getKey();
                List<PSD.Path> paths = cps.getValue();
                Set<Integer> pathSigs = new HashSet<>();
                for (PSD.Path p : paths) {
                    pathSigs.addAll(p.sigs);
                }
                for (int sig : c.sigs) {
                    int k = pathSigs.size();
                    IloNumVar term = s.getOrDefault(sig, getVar(c.elem));
                    IloNumVar[] neg = pathSigs.stream()
                            .flatMap(ss -> signals.set(ss).stream())
                            .map(this::getVar).toArray(IloNumVar[]::new);
                    if (neg.length * k > 0 && term != null) {
                        IloNumExpr e = cplex.diff(cplex.prod(k, term), cplex.sum(neg));
                        IloRange r = cplex.range(0, e, k);
                        cplex.add(r);
                    }
                }
            }

        }
    */
    private void otherConstraints() throws IloException {
        // (36), (39)
        for (Edge edge : graph.edgeSet()) {
            Pair<IloNumVar, IloNumVar> arcs = x.get(edge);
            Node from = graph.getEdgeSource(edge);
            Node to = graph.getEdgeTarget(edge);
            cplex.addLe(cplex.sum(arcs.first, arcs.second), w.get(edge), "arcs" + edge.getNum());
            cplex.addLe(w.get(edge), y.get(from), "edges_from" + edge.getNum());
            cplex.addLe(w.get(edge), y.get(to), "edges_to" + edge.getNum());
        }
    }


    private void sumConstraints() throws IloException {
        // (31)
        cplex.addLe(cplex.sum(graph.vertexSet().stream().map(x -> x0.get(x)).toArray(IloNumVar[]::new)), 1, "sum31");
        if (root != null) {
            cplex.addEq(x0.get(root), 1, "root");
        }
        // (32)
        for (Node node : graph.vertexSet()) {
            Set<Edge> edges = graph.edgesOf(node);
            IloNumVar[] xSum = new IloNumVar[edges.size() + 1];
            int i = 0;
            for (Edge edge : edges) {
                xSum[i++] = getX(edge, node);
            }
            xSum[xSum.length - 1] = x0.get(node);
            cplex.addEq(cplex.sum(xSum), y.get(node), "sum" + node.getNum());
        }
    }

    private void treeConstraints() throws IloException {
        cplex.addEq(cplex.sum(y.values().toArray(new IloNumVar[0])),
                cplex.sum(1, cplex.sum(w.values().toArray(new IloNumVar[0]))),
                "tree"
        );
    }

    private IloNumVar getX(Edge e, Node to) {
        if (graph.getEdgeSource(e) == to) {
            return x.get(e).first;
        } else {
            return x.get(e).second;
        }
    }

    public AtomicDouble getLB() {
        return lb;
    }

    private Set<Unit> applyPrimalHeuristic(Node treeRoot,
                                           Map<Edge, Double> edgeWeights) {
        MSTSolver mst = new MSTSolver(graph, edgeWeights, treeRoot);
        mst.solve();
        Graph tree = graph.edgesSubgraph(mst.getEdges());
        return tree.units();
    }

    private static class CplexSolution {
        private final List<IloNumVar> variables = new ArrayList<>();
        private final List<Double> values = new ArrayList<>();

        IloNumVar[] variables() {
            return variables.toArray(new IloNumVar[0]);
        }

        double[] values() {
            return values.stream().mapToDouble(d -> d).toArray();
        }

        private <U extends Unit> void addVariable(Map<U, IloNumVar> map,
                                                  U unit, double val) {
            addVariable(map.get(unit), val);
        }

        private void addVariable(IloNumVar var, double val) {
            variables.add(var);
            values.add(val);
        }

        private void addNullVariables(IloNumVar... vars) {
            for (IloNumVar var : vars) {
                addVariable(var, 0);
            }
        }

        private boolean apply(BiFunction<IloNumVar[], double[], Boolean> set) {
            double[] vals = new double[values.size()];
            for (int i = 0; i < values.size(); ++i) {
                vals[i] = values.get(i);
            }
            return set.apply(variables.toArray(new IloNumVar[0]), vals);
        }

        private double obj() {
            assert values.size() == variables.size();
            return values.get(values.size() - 1);
        }
    }

    private class MSTSolution {
        private final CplexSolution solution = new CplexSolution();

        private final Node root;
        private final Graph tree;
        private final Deque<Node> deque;
        private final HashMap<Node, Integer> dist;
        private final Set<Unit> mstSol;
        private final Set<Node> visitedNodes;
        private final Set<Edge> visitedEdges;
        private final Set<Node> unvisitedNodes;
        private final Set<Edge> unvisitedEdges;

        MSTSolution(final Graph tree,
                    final Node root,
                    final Collection<Unit> mstSol) {
            this.tree = tree;
            this.root = root;
            this.mstSol = new HashSet<>(mstSol);
            this.deque = new ArrayDeque<>();
            this.dist = new HashMap<>();
            dist.put(root, 0);
            visitedNodes = new HashSet<>();
            visitedEdges = new HashSet<>();
            unvisitedNodes = new HashSet<>(RLTSolver.this.graph.vertexSet());
            unvisitedEdges = new HashSet<>(RLTSolver.this.graph.edgeSet());
            traverseSolution();
            fillSolution();
        }

        public CplexSolution solution() {
            return this.solution;
        }


        private void traverseSolution() {
            deque.add(root);
            this.visitedNodes.add(root);
            mstSol.remove(root);
            while (!deque.isEmpty()) {
                final Node cur = deque.pollFirst();
                solution.addVariable(x0, cur, cur == root ? 1 : 0);
                solution.addVariable(y, cur, 1);
                List<Node> neighbors = tree.neighborListOf(cur)
                        .stream().filter(mstSol::contains)
                        .collect(Collectors.toList());
                visitedNodes.addAll(neighbors);
                neighbors.forEach(mstSol::remove);
                for (Node node : neighbors) {
                    Edge e = tree.getAllEdges(node, cur)
                            .stream().filter(mstSol::contains).findFirst().get();
                    unvisitedEdges.remove(e);
                    visitedEdges.add(e);
                    solution.addVariable(w, e, 1.0);
                    deque.add(node);
                    solution.addVariable(getX(e, node), 1);
                    solution.addVariable(getX(e, cur), 0);
                    dist.put(node, dist.get(cur) + 1);
                }
            }

        }

        private void fillSolution() {
            unvisitedNodes.removeAll(visitedNodes);
            for (Edge e : new ArrayList<>(unvisitedEdges)) {
                Node u = graph.getEdgeSource(e), v = graph.getEdgeTarget(e);
                IloNumVar from = getX(e, u), to = getX(e, v);
                solution.addNullVariables(w.get(e), from, to);
            }
            Set<Unit> solutionUnits = new HashSet<>(visitedNodes);
            solutionUnits.addAll(visitedEdges);
            for (Map.Entry<Node, Integer> nd : dist.entrySet()) {
                solution.addVariable(d, nd.getKey(), nd.getValue());
            }
            for (Node node : unvisitedNodes) {
                solution.addNullVariables(x0.get(node), d.get(node), y.get(node));
            }
            for (int sig = 0; sig < signals.size(); sig++) {
                List<Unit> units = signals.set(sig);
                if (s.containsKey(sig)) {
                    boolean val = units.stream().anyMatch(solutionUnits::contains);
                    solution.addVariable(s.get(sig), val ? 1 : 0);
                }
            }
            solution.addVariable(RLTSolver.this.sum, signals.sum(solutionUnits));
        }
    }

    private CplexSolution constructMstSolution(Collection<Unit> units) {
        if (units.isEmpty()) {
            return null;
        }
        Set<Node> nodes = Utils.nodes(units);
        Node treeRoot = Optional.ofNullable(root)
                .orElse(nodes.stream().min(Comparator.naturalOrder()).get());
        return new MSTSolution(graph.subgraph(units), treeRoot, units).solution();
    }

    private CplexSolution MSTHeuristic(Map<Edge, Double> weights) {
        Node treeRoot = Optional.ofNullable(root)
                .orElse(graph.vertexSet().stream().min(Comparator.naturalOrder()).get());
        Set<Unit> units = applyPrimalHeuristic(treeRoot, weights);
        return constructMstSolution(units);
    }

    private class MIPCallback extends IncumbentCallback {
        private final boolean silence;

        public MIPCallback(boolean silence) {
            this.silence = silence;
        }

        @Override
        protected void main() throws IloException {
            double currLB;
            while (true) {
                currLB = lb.get();
                if (currLB >= getObjValue()) {
                    break;
                }
                if (lb.compareAndSet(currLB, getObjValue()) && !silence) {
                    System.out.println("Found new solution: " + getObjValue());
                }
            }
        }
    }
}
