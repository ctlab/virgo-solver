package ru.itmo.ctlab.virgo.gmwcs.solver;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ru.itmo.ctlab.gmwcs.solver.D;
import ru.itmo.ctlab.gmwcs.solver.TreeSolverKt;
import ru.itmo.ctlab.virgo.Pair;
import ru.itmo.ctlab.virgo.TimeLimit;
import ru.itmo.ctlab.virgo.gmwcs.graph.*;
import ru.itmo.ctlab.virgo.SolverException;

import java.util.*;
import java.util.stream.Collectors;

public class RLTSolver extends IloVarHolder implements RootedSolver {
    public static final double EPS = 0.01;
    private IloCplex cplex;
    private Map<Node, IloNumVar> y;
    private Map<Edge, IloNumVar> w;
    private Map<IloNumVar, Double> mstWeights;
    private Map<Edge, Pair<IloNumVar, IloNumVar>> x;
    private Map<Node, IloNumVar> d;
    private Map<Node, IloNumVar> x0;
    private TimeLimit tl;
    private int threads;
    private boolean suppressOutput;
    private Graph graph;
    private double minimum;
    private Node root;
    private boolean isSolvedToOptimality;
    private int maxToAddCuts;
    private int considerCuts;

    public RLTSolver() {
        tl = new TimeLimit(Double.POSITIVE_INFINITY);
        threads = 1;
        this.minimum = -Double.MAX_VALUE;
        maxToAddCuts = considerCuts = Integer.MAX_VALUE;
    }

    public void setMaxToAddCuts(int num) {
        maxToAddCuts = num;
    }

    public void setConsideringCuts(int num) {
        considerCuts = num;
    }

    public void setTimeLimit(TimeLimit tl) {
        this.tl = tl;
    }

    public void setThreadsNum(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException();
        }
        this.threads = threads;
    }

    public void setRoot(Node root) {
        this.root = root;
    }

    public void initMstWeights() {
        mstWeights = new HashMap<>();
        for (Edge e : graph.edgeSet()) {
            mstWeights.put(w.get(e), e.getWeight() >= 0 ? 1.0 : 0);
        }
        for (Node n : graph.vertexSet()) {
            mstWeights.put(y.get(n), n.getWeight() >= 0 ? 1.0 : 0);
        }
    }

    @Override
    public List<Elem> solve(Graph graph) throws SolverException {
        try {
            cplex = new IloCplex();
            setCplexLog();
            this.graph = graph;
            initVariables();
            addConstraints();
            addObjective();
            maxSizeConstraints();
            initMstWeights();
            long timeBefore = System.currentTimeMillis();
            if (root == null) {
                breakRootSymmetry();
            } else {
                tighten();
            }
            breakTreeSymmetries();
            tuning(cplex);
            if (!suppressOutput) {
                cplex.use(new LogCallback());
            }
            if (graph.vertexSet().size() >= 25) {
                cplex.use(new MstCallback(0));
                tryMst(this);
            }
            boolean solFound = cplex.solve();
            tl.spend(Math.min(tl.getRemainingTime(),
                    (System.currentTimeMillis() - timeBefore) / 1000.0));
            if (solFound) {
                return getResult();
            }
            return Collections.emptyList();
        } catch (IloException e) {
            throw new SolverException(e.getMessage());
        } finally {
            cplex.end();
        }
    }

    private void setCplexLog() {
        if (suppressOutput) {
            cplex.setOut(null);
            cplex.setWarning(null);
        }
    }

    @Override
    protected void setSolution(IloNumVar[] v, double[] d) throws IloException {
        cplex.addMIPStart(v, d);
    }

    @Override
    protected double getValue(IloNumVar v) throws IloException {
        return mstWeights.get(v);
    }

    private class MstCallback extends IloCplex.HeuristicCallback {
        private int counter;
        private IloVarHolder hld;

        MstCallback(int counter) {
            super();
            this.counter = counter;
            hld = new IloVarHolder() {
                @Override
                protected void setSolution(IloNumVar[] v, double[] d) throws IloException {
                    MstCallback.this.setSolution(v, d);
                }

                @Override
                protected double getValue(IloNumVar v) throws IloException {
                    return MstCallback.this.getValue(v);
                }
            };
        }

        protected void main() throws IloException {
            if (counter % 1000 == 0 && counter / 1000 < 10) {
                tryMst(this.hld);
            }
            counter++;
        }

        @Override
        protected MstCallback clone() {
            return new MstCallback(counter);
        }
    }

    private CplexSolution tryMstSolution(Graph tree, Node root,
                                         Set<Node> mstSol) {
        CplexSolution solution = new CplexSolution();
        final Set<Edge> unvisitedEdges = new HashSet<>(this.graph.edgeSet());
        final Set<Node> unvisitedNodes = new HashSet<>(this.graph.vertexSet());
        final Deque<Node> deque = new ArrayDeque<>();
        deque.add(root);
        Map<Node, Integer> ds = new HashMap<>();
        ds.put(root, 0);
        Set<Node> visitedNodes = new HashSet<>();
        Set<Edge> visitedEdges = new HashSet<>();
        visitedNodes.add(root);
        mstSol.remove(root);
        while (!deque.isEmpty()) {
            final Node cur = deque.pollFirst();
            solution.addVariable(x0, cur, cur == root ? 1 : 0);
            solution.addVariable(y, cur, 1);
            List<Node> neighbors = tree.neighborListOf(cur)
                    .stream().filter(node -> mstSol.contains(node) ||
                            isGoodNode(node, tree.getEdge(cur, node), visitedNodes)
                    ).collect(Collectors.toList());
            visitedNodes.addAll(neighbors);
            mstSol.removeAll(neighbors);
            for (Node node : neighbors) {
                Edge e = tree.getEdge(node, cur);
                unvisitedEdges.remove(e);
                visitedEdges.add(e);
                solution.addVariable(w, e, 1.0);
                deque.add(node);
            }
        }
        unvisitedNodes.removeAll(visitedNodes);
        for (Edge e : new ArrayList<>(unvisitedEdges)) {
            Node u = graph.getEdgeSource(e), v = graph.getEdgeTarget(e);
            IloNumVar from = getX(e, u), to = getX(e, v);
            if (visitedNodes.contains(u)
                    && visitedNodes.contains(v) && e.getWeight() >= 0) {
                unvisitedEdges.remove(e);
                visitedEdges.add(e);
            } else {
                solution.addNullVariables(w.get(e), from, to);
            }
        }
        deque.add(root);
        visitedNodes.remove(root);
        while (!deque.isEmpty()) {
            final Node cur = deque.poll();
            List<Node> neighbors = new ArrayList<>();
            for (Edge e : graph.edgesOf(cur).stream().filter(visitedEdges::contains)
                    .collect(Collectors.toList())) {
                neighbors.add(graph.opposite(cur, e));
            }
            for (Node node : neighbors) {
                if (!visitedNodes.contains(node))
                    continue;
                deque.add(node);
                visitedNodes.remove(node);
                Edge e = graph.getEdge(node, cur);
                solution.addVariable(getX(e, node), 1);
                solution.addVariable(getX(e, cur), 0);
                visitedEdges.remove(e);
                ds.put(node, ds.get(cur) + 1);
            }
        }
        for (Edge e : visitedEdges) {
            Node u = graph.getEdgeSource(e), v = graph.getEdgeTarget(e);
            IloNumVar from = getX(e, u), to = getX(e, v);
            solution.addVariable(w, e, 1);
            solution.addNullVariables(from, to);
        }
        assert visitedNodes.isEmpty();
        for (Map.Entry<Node, Integer> nd : ds.entrySet()) {
            solution.addVariable(d, nd.getKey(), nd.getValue());
        }
        for (Node node : unvisitedNodes) {
            solution.addNullVariables(x0.get(node), d.get(node), y.get(node));
        }
        return solution;
    }

    private boolean isGoodNode(Node node, Edge edge, Set<Node> visited) {
        return node.getWeight() + edge.getWeight() >= 0 && !visited.contains(node);
    }

    private void tryMst(IloVarHolder hld) throws IloException {
        Map<Edge, Double> ews = hld.buildVarGraph(graph, this.y, this.w);
        D solution = null;
        Graph gr = null;
        for (Set<Node> set : graph.connectedSets()) {
            if (set.isEmpty()) continue;
            final Node root = Optional.ofNullable(this.root).orElse(
                    set.stream().max(
                            Comparator.comparingDouble(Node::getWeight)
                    ).get() // Assuming that connected set is not empty
            );
            Graph g = graph.subgraph(set);
            if (!g.containsVertex(root)) {
                continue;
            }
            MSTSolver mst = new MSTSolver(g, ews, root);
            mst.solve();
            g = graph.subgraph(g.vertexSet(), new HashSet<>(mst.getEdges()));
            D sol = TreeSolverKt.solve(g, root, null);
            if (solution == null || solution.getBestD() < sol.getBestD()) {
                solution = sol;
                gr = g;
            }
        }
        if (solution != null) {
            final double best = solution.getWithRootD();
            if (best > 0) {
                CplexSolution sol = tryMstSolution(gr, solution.getRoot(),
                        solution.getWithRoot());
                hld.setSolution(sol.variables(), sol.values());
            }
        }
    }

    private void breakTreeSymmetries() throws IloException {
        int n = graph.vertexSet().size();
        for (Edge e : graph.edgeSet()) {
            Node from = graph.getEdgeSource(e);
            Node to = graph.getEdgeTarget(e);
            cplex.addLe(cplex.sum(d.get(from), cplex.prod(n - 1, w.get(e))), cplex.sum(n, d.get(to)));
            cplex.addLe(cplex.sum(d.get(to), cplex.prod(n - 1, w.get(e))), cplex.sum(n, d.get(from)));
        }
    }

    private void tighten() throws IloException {
        Blocks blocks = new Blocks(graph);
        Separator separator = new Separator(y, w, cplex, graph);
        separator.setMaxToAdd(maxToAddCuts);
        separator.setMinToConsider(considerCuts);
        if (blocks.cutpoints().contains(root)) {
            for (Set<Node> component : blocks.incidentBlocks(root)) {
                dfs(root, component, true, blocks, separator);
            }
        } else {
            dfs(root, blocks.componentOf(root), true, blocks, separator);
        }
        cplex.use(separator);
    }

    private void dfs(Node root, Set<Node> component, boolean fake, Blocks blocks, Separator separator) throws IloException {
        separator.addComponent(graph.subgraph(component), root);
        if (!fake) {
            for (Node node : component) {
                cplex.addLe(cplex.diff(y.get(node), y.get(root)), 0);
            }
        }
        for (Edge e : graph.edgesOf(root)) {
            if (!component.contains(graph.opposite(root, e))) {
                continue;
            }
            cplex.addEq(getX(e, root), 0);
        }
        for (Node cp : blocks.cutpointsOf(component)) {
            if (root != cp) {
                for (Set<Node> comp : blocks.incidentBlocks(cp)) {
                    if (comp != component) {
                        dfs(cp, comp, false, blocks, separator);
                    }
                }
            }
        }
    }

    public boolean isSolvedToOptimality() {
        return isSolvedToOptimality;
    }

    private List<Elem> getResult() throws IloException {
        isSolvedToOptimality = false;
        List<Elem> result = new ArrayList<>();
        for (Node node : graph.vertexSet()) {
            if (cplex.getValue(y.get(node)) > EPS) {
                result.add(node);
            }
        }
        for (Edge edge : graph.edgeSet()) {
            if (cplex.getValue(w.get(edge)) > EPS) {
                result.add(edge);
            }
        }
        if (cplex.getStatus() == IloCplex.Status.Optimal) {
            isSolvedToOptimality = true;
        }
        return result;
    }

    private void initVariables() throws IloException {
        y = new LinkedHashMap<>();
        w = new LinkedHashMap<>();
        d = new LinkedHashMap<>();
        x = new LinkedHashMap<>();
        x0 = new LinkedHashMap<>();
        for (Node node : graph.vertexSet()) {
            String nodeName = Integer.toString(node.getNum() + 1);
            d.put(node, cplex.numVar(0, Double.MAX_VALUE, "d" + nodeName));
            y.put(node, cplex.boolVar("y" + nodeName));
            x0.put(node, cplex.boolVar("x_0_" + (node.getNum() + 1)));
        }
        for (Edge edge : graph.edgeSet()) {
            Node from = graph.getEdgeSource(edge);
            Node to = graph.getEdgeTarget(edge);
            String edgeName = (from.getNum() + 1) + "_" + (to.getNum() + 1);
            w.put(edge, cplex.boolVar("w_" + edgeName));
            IloNumVar in = cplex.boolVar("x_" + edgeName + "_in");
            IloNumVar out = cplex.boolVar("x_" + edgeName + "_out");
            x.put(edge, new Pair<>(in, out));
        }
    }

    private void tuning(IloCplex cplex) throws IloException {
        cplex.setParam(IloCplex.BooleanParam.PreInd, true);
        cplex.setParam(IloCplex.IntParam.Threads, threads);
        cplex.setParam(IloCplex.IntParam.ParallelMode,
                IloCplex.ParallelMode.Opportunistic);
        cplex.setParam(IloCplex.DoubleParam.EpRHS, 1.0e-4);
        cplex.setParam(IloCplex.DoubleParam.EpInt, 1.0e-4);
        cplex.setParam(IloCplex.IntParam.MIPOrdType, 3);
        if (tl.getRemainingTime() <= 0) {
            cplex.setParam(IloCplex.DoubleParam.TiLim, EPS);
        } else if (tl.getRemainingTime() != Double.POSITIVE_INFINITY) {
            cplex.setParam(IloCplex.DoubleParam.TiLim, tl.getRemainingTime());
        }
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
        IloNumVar sum = cplex.numVar(0, n, "prSum");
        cplex.addEq(sum, cplex.sum(terms));
        for (int i = 0; i < n; i++) {
            cplex.addGe(sum, rs[i]);
        }
    }

    private void addObjective() throws IloException {
        Map<Elem, IloNumVar> summands = new LinkedHashMap<>();
        Set<Elem> toConsider = new LinkedHashSet<>();
        toConsider.addAll(graph.vertexSet());
        toConsider.addAll(graph.edgeSet());
        for (Elem elem : toConsider) {
            summands.put(elem, getVar(elem));
        }
        IloNumExpr sum = unitScalProd(summands.keySet(), summands);
        cplex.addGe(sum, minimum);
        cplex.addMaximize(sum);
    }

    private IloNumVar getVar(Elem elem) {
        return elem instanceof Node ? y.get(elem) : w.get(elem);
    }

    @Override
    public void suppressOutput() {
        suppressOutput = true;
    }

    private void addConstraints() throws IloException {
        sumConstraints();
        otherConstraints();
        distanceConstraints();
    }

    private void distanceConstraints() throws IloException {
        int n = graph.vertexSet().size();
        for (Node v : graph.vertexSet()) {
            cplex.addLe(d.get(v), cplex.diff(n, cplex.prod(n, x0.get(v))));
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
        cplex.addGe(cplex.sum(n, d.get(to)), cplex.sum(d.get(from), cplex.prod(n + 1, z)));
        cplex.addLe(cplex.sum(d.get(to), cplex.prod(n - 1, z)), cplex.sum(d.get(from), n));
    }

    private void maxSizeConstraints() throws IloException {
        for (Node v : graph.vertexSet()) {
            for (Node u : graph.neighborListOf(v)) {
                if (u.getWeight() >= 0) {
                    Edge e = graph.getEdge(v, u);
                    if (e != null && e.getWeight() >= 0) {
                        cplex.addLe(y.get(v), w.get(e));
                    }
                }
            }
        }
    }

    private void otherConstraints() throws IloException {
        // (36), (39)
        for (Edge edge : graph.edgeSet()) {
            Pair<IloNumVar, IloNumVar> arcs = x.get(edge);
            Node from = graph.getEdgeSource(edge);
            Node to = graph.getEdgeTarget(edge);
            cplex.addLe(cplex.sum(arcs.first, arcs.second), w.get(edge));
            cplex.addLe(w.get(edge), y.get(from));
            cplex.addLe(w.get(edge), y.get(to));
        }
    }

    private void sumConstraints() throws IloException {
        // (31)
        cplex.addLe(cplex.sum(graph.vertexSet().stream().map(x -> x0.get(x)).toArray(IloNumVar[]::new)), 1);
        if (root != null) {
            cplex.addEq(x0.get(root), 1);
        }
        // (32)
        for (Node node : graph.vertexSet()) {
            Set<Edge> edges = graph.edgesOf(node);
            IloNumVar xSum[] = new IloNumVar[edges.size() + 1];
            int i = 0;
            for (Edge edge : edges) {
                xSum[i++] = getX(edge, node);
            }
            xSum[xSum.length - 1] = x0.get(node);
            cplex.addEq(cplex.sum(xSum), y.get(node));
        }
    }

    private IloNumVar getX(Edge e, Node to) {
        if (graph.getEdgeSource(e) == to) {
            return x.get(e).first;
        } else {
            return x.get(e).second;
        }
    }

    private IloLinearNumExpr unitScalProd(Set<? extends Elem> units, Map<? extends Elem, IloNumVar> vars) throws IloException {
        int n = units.size();
        double[] coef = new double[n];
        IloNumVar[] variables = new IloNumVar[n];
        int i = 0;
        for (Elem elem : units) {
            coef[i] = elem.getWeight();
            variables[i++] = vars.get(elem);
        }
        return cplex.scalProd(coef, variables);
    }

    public void setLB(double lb) {
        this.minimum = lb;
    }

    private class LogCallback extends IloCplex.IncumbentCallback {
        @Override
        protected void main() throws IloException {
            if (this.getSolutionSource() == 118) { //118 is UserSolution source
                System.out.println("MST Heuristic solution");
                System.out.println("Value " + this.getIncumbentObjValue());
            }
        }
    }
}
