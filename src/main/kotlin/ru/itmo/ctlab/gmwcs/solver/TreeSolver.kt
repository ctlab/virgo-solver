package ru.itmo.ctlab.gmwcs.solver

import ru.itmo.ctlab.gmwcs.solver.preprocessing.preprocess
import ru.itmo.ctlab.virgo.gmwcs.graph.Edge
import ru.itmo.ctlab.virgo.gmwcs.graph.Elem
import ru.itmo.ctlab.virgo.gmwcs.graph.Graph
import ru.itmo.ctlab.virgo.gmwcs.graph.Node
import ru.itmo.ctlab.virgo.gmwcs.solver.MSTSolver
import java.util.*
import kotlin.math.exp

/**
 * Created by Nikolay Poperechnyi on 18/01/2018.
 */


data class D(val root: Node?,
             val best: Set<Elem>,
             val withRoot: Set<Elem>,
             val bestD: Double,
             val withRootD: Double
)

fun solve(g: Graph, root: Node, parent: Node?): D {
    val children = if (parent == null) g.neighborListOf(root)
    else g.neighborListOf(root).minus(parent)
    val withRoot = mutableSetOf<Elem>(root)
    val solutions = mutableSetOf<D>()
    var withRootD = root.weight
    val emptySol = D(root, emptySet(), withRoot.toSet(), 0.0, root.weight)
    if (children.isEmpty()) {
        return if (root.weight < 0) emptySol
        else D(root, withRoot, withRoot, root.weight, root.weight)
    }
    for (e in g.edgesOf(root)) {
        val opp = g.opposite(root, e)
        if (parent != null && opp == parent) continue
        assert(opp != root)
        val sub = solve(g, opp, root)
        if (sub.bestD > 0) {
            solutions.add(sub)
        }
        if (sub.withRootD + e.weight >= 0) {
            withRoot.addAll(sub.withRoot)
            withRoot.add(e)
            withRootD += sub.withRootD + e.weight
        }
    }
    solutions.add(emptySol)
    val bestSub = solutions.maxBy { it.bestD }!!
    val bestSol = if (bestSub.bestD > withRootD) bestSub.best
    else withRoot
    return D(root, bestSol, withRoot, maxOf(bestSub.bestD, withRootD), withRootD)
}

fun mergeEdges(g: Graph) {
    for (u in g.vertexSet()) {
        for (v in g.neighborListOf(u)) {
            if (u == v) {
                g.removeEdge(g.getEdge(u, v))
                continue
            }
            if (u.num > v.num) continue
            val e = g.getAllEdges(u, v).maxBy { it.weight }
            g.getAllEdges(u, v).forEach { g.removeEdge(it) }
            g.addEdge(u, v, e)
        }
    }
}

fun mapWeight(w: Double): Double {
    return 1.0/(1+exp(w))
}

fun solveComponents(g: Graph): Set<Elem> {
    preprocess(g)
    val components = g.connectedSets()
    val gs = components.map { g.subgraph(it) }
    return gs.map { solve(it) }
            .maxBy { it.sumByDouble { it.weight } }
            .orEmpty()
}

fun solve(g: Graph): Set<Elem> {
    val random = Random(1337)
    mergeEdges(g)
    var res: D? = null
    for (i in 0..10) {
        val r = g.vertexSet().toList()[random.nextInt(g.vertexSet().size)]
        val weights = g.edgeSet().map { it -> Pair(it, mapWeight(it.weight)) }.toMap()
        val mst = MSTSolver(g, weights, r)
        mst.solve()
        val sol = solve(g.subgraph(g.vertexSet(), mst.edges.toSet()), r, null)
        if (res == null || sol.bestD > res.bestD)
            res = sol
    }
    return g.subgraph(
            res!!.best.filterIsInstanceTo(mutableSetOf()),
            res.best.filterIsInstanceTo(mutableSetOf())).elemSet()
}


fun main(args: Array<String>) {
    val g = Graph()
    val nodes = arrayOf(Node(1, -5.0),
            Node(2, -1.0),
            Node(3, -2.0))
    val edges = arrayOf(Edge(1, 1.0), Edge(2, -1.0))
    nodes.forEach { g.addVertex(it) }
    edges.forEach { g.addEdge(nodes[it.num - 1], nodes[it.num], it) }
    print(solve(g, nodes[1], null).withRootD)
}
