package ru.itmo.ctlab.gmwcs.solver.preprocessing

import ru.itmo.ctlab.virgo.gmwcs.graph.Edge
import ru.itmo.ctlab.virgo.gmwcs.graph.Elem
import ru.itmo.ctlab.virgo.gmwcs.graph.Graph
import ru.itmo.ctlab.virgo.gmwcs.graph.Node
import ru.itmo.ctlab.virgo.SolverException
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Created by Nikolay Poperechnyi on 03/10/2017.
 */

private var logLevel = 1

private var threads: Int = 1

fun setThreads(n: Int) {
    if (n <= 0) throw SolverException("Preprocessor num threads < 0")
    threads = n
}

fun Graph.getAdjacent(e: Edge) = Pair(this.getEdgeSource(e), this.getEdgeTarget(e))

typealias EdgeSet = Set<Edge>
typealias MutableEdgeSet = MutableSet<Edge>
typealias NodeSet = Set<Node>
typealias MutableNodeSet = MutableSet<Node>

typealias Step<T> = (Graph, MutableSet<T>) -> Set<T>
typealias Reduction<T> = (Graph, Set<T>) -> Int

private fun <T> powerset(left: Collection<T>, acc: Set<Set<T>> = setOf(emptySet())): Set<Set<T>> = when {
    left.isEmpty() -> acc
    else -> powerset(left.drop(1), acc + acc.map { it + left.first() })
}

class ReductionSequence<T : Elem>(private val step: Step<T>,
                                  private val reduction: Reduction<T>) {
    fun apply(graph: Graph):Int {
        val res = step(graph, mutableSetOf())
        return reduction(graph, res)
    }
}

typealias Reductions = List<ReductionSequence<out Elem>>

val mergeNeg = ReductionSequence(::mergeNegative, ::logNodes)

val mergePos = ReductionSequence(::mergePositive, { _, n -> n.size })

val negV = ReductionSequence(::negativeVertices, ::logAndRemoveNodes)

val negE = ReductionSequence(::negativeEdges, ::logAndRemoveEdges)

val cns = ReductionSequence(::cns, ::logAndRemoveNodes)

val nvk = ReductionSequence(
        { graph, toRemove -> negativeVertices(4, graph, toRemove) }
        , ::logAndRemoveNodes
)

val isolated = ReductionSequence(
        { graph, toRemove -> isolatedVertices(graph, toRemove) }
        , ::logAndRemoveNodes
)
val leaves = ReductionSequence(
        { graph, toRemove -> l(graph, toRemove) }
        , ::logAndRemoveNodes
)

val allSteps: Reductions = listOf(isolated, mergeNeg, mergePos, leaves, cns, negE, nvk)

fun isolatedVertices(graph: Graph, toRemove: MutableNodeSet = mutableSetOf()): NodeSet {
    return graph.vertexSet().filterTo(toRemove, { it.weight <= 0 && graph.degreeOf(it) == 0 })
}

fun l(graph: Graph, toRemove: MutableNodeSet = mutableSetOf()): NodeSet {
    val prim = graph.vertexSet().max()
    for (n in graph.vertexSet().filter { graph.degreeOf(it) == 1}) {
        if (n == prim || graph.edgesOf(n).size > 1)
            continue
        val e = graph.edgesOf(n).iterator().next()
        val opposite = graph.opposite(n, e)
        if (n.weight + e.weight >= 0) {
            opposite.absorb(n)
            opposite.absorb(e)
            toRemove.add(n)
        } else if (n.weight + e.weight <= 0) {
            toRemove.add(n)
        }
    }
    return toRemove
}

fun mergeNegative(graph: Graph, toRemove: MutableNodeSet = mutableSetOf()): NodeSet {
    for (v in graph.vertexSet().toList()) {
        if (v.weight > 0 || graph.degreeOf(v) != 2) {
            continue
        }
        val edges = graph.edgesOf(v).toTypedArray()
        if (edges[0].weight > 0 || edges[1].weight > 0)
            continue
        val l = graph.opposite(v, edges[0])
        val r = graph.opposite(v, edges[1])
        toRemove.add(v) //TODO: 2 nodes 1 edge invariant broken here
        graph.removeVertex(v)
        if (l != r) {
            edges[0].absorb(v)
            edges[0].absorb(edges[1])
            graph.addEdge(l, r, edges[0])
        }
    }
    return toRemove
}

fun mergePositive(graph: Graph, toRemove: MutableNodeSet = mutableSetOf()): NodeSet {
    for (edge in graph.edgeSet().toList()) {
        if (!graph.containsEdge(edge))
            continue
        val (from, to) = graph.getAdjacent(edge)
        val ew = edge.weight
        if (from == to) {
            merge(graph, edge, from)
        } else if (ew >= 0 && ew + from.weight >= 0 && ew + to.weight >= 0) {
            merge(graph, edge, from, to)
        }
    }
    return toRemove
}

private fun merge(graph: Graph, e: Edge, n: Node) {
    if (e.weight > 0) {
        n.absorb(e)
    }
    graph.removeEdge(e)
}

private fun merge(graph: Graph, e: Edge, l: Node, r: Node) {
    if (!listOf(l, r).containsAll(graph.getAdjacent(e).toList()))
        throw IllegalArgumentException()
    assert(l != r)
    contract(graph, e)
}

private fun contract(graph: Graph, e: Edge) {
    val (main, aux) = graph.getAdjacent(e)
    val auxEdges = graph.edgesOf(aux)
    auxEdges.remove(e)
    for (edge in auxEdges) {
        val opposite = graph.opposite(aux, edge)
        val m = graph.getEdge(main, opposite)
        graph.removeEdge(edge)
        if (m == null) {
            if (opposite == main) {
                if (edge.weight >= 0) {
                    main.absorb(edge)
                }
                continue
            }
            graph.addEdge(main, opposite, edge)
        } else if (edge.weight >= 0 && m.weight >= 0) {
            m.absorb(edge)
        } else if (m.weight <= edge.weight) {
            assert(m != edge)
            graph.removeEdge(m)
            graph.addEdge(main, opposite, edge)
        }
    }
    graph.removeVertex(aux)
    main.absorb(aux)
    main.absorb(e)
}

fun negativeVertices(graph: Graph,
                     toRemove: MutableNodeSet = mutableSetOf()): NodeSet {
    graph.vertexSet().filterTo(toRemove, { vertexTest(graph, it) })
    return toRemove
}

private fun vertexTest(graph: Graph, v: Node): Boolean {
    return if (v.weight <= 0
            && graph.neighborListOf(v).size == 2
            && graph.edgesOf(v).all { it.weight <= 0 }) {
        val neighbors = graph.neighborListOf(v)
        val n1 = neighbors[0]
        val n2 = neighbors[1]
        Dijkstra(graph, n1).negativeVertex(n2, v)
    } else {
        false
    }
}

fun cns(graph: Graph, toRemove: MutableNodeSet = mutableSetOf()): NodeSet {
    graph.vertexSet()
            .forEach {
                if (!toRemove.contains(it))
                    cnsTest(graph, it, toRemove)
            }
    return toRemove
}

private fun cnsTest(graph: Graph, v: Node, toRemove: MutableNodeSet) {
    val (w, wSum, wNeighbors) = constructW(graph, v, toRemove)
    for (u in w) {
        for (cand in graph.neighborListOf(u).asSequence().filter { !w.contains(it) }) {
            val bestSum = cand.weight + graph.edgesOf(cand)
                    .sumByDouble { Math.max(it.weight, 0.0) }
            if (bestSum >= 0) continue
            val candN = graph.neighborListOf(cand).filter { !w.contains(it) }
            if (wNeighbors.containsAll(candN) && bestSum < wSum) {
                toRemove.add(cand)
            }
        }
    }
}

private data class ConnectedComponent(val w: MutableNodeSet,
                                      val sum: Double,
                                      val wNeighbors: MutableNodeSet)

private fun constructW(graph: Graph, n: Node,
                       toRemove: MutableNodeSet): ConnectedComponent {
    var wSum = minOf(n.weight, 0.0)
    val w = mutableSetOf(n)
    for (i in 1..2) //todo: test another W size
        for (v in w.toTypedArray()) {
            for (u in graph.neighborListOf(v)
                    .filter { !toRemove.contains(it) && !w.contains(it) }) {
                val edge = graph.getEdge(u, v)
                val weightSum = edge.weight + u.weight
                if (weightSum >= 0) {
                    wSum += minOf(edge.weight, 0.0) + minOf(u.weight, 0.0)
                    w.add(u)
                }
            }
        }
    val wNeighbors = mutableSetOf<Node>()
    for (u in w) {
        val nbs = graph.neighborListOf(u)
        for (nb in nbs) {
            if (!w.contains(nb) && !toRemove.contains(nb)) {
                wNeighbors.add(nb)
                wSum += minOf(graph.getEdge(nb, u).weight, 0.0)
            }
        }
    }
    return ConnectedComponent(w, wSum, wNeighbors)
}

fun negativeEdges(graph: Graph, toRemove: MutableEdgeSet = mutableSetOf()): EdgeSet {
    val executor = if (threads == 1) Executors.newSingleThreadExecutor()
    else Executors.newFixedThreadPool(threads)
    graph.subgraph(graph.vertexSet())
    val acu = ConcurrentSkipListSet<Edge>()
    graph.vertexSet().forEach { n ->
        executor.submit {
            val neighs = graph.edgesOf(n)
                    .filter { it.num < n.num && it.weight <= 0 && !acu.contains(it)}
                    .map { graph.opposite(n, it) }.toSet()
            val res = if (!neighs.isEmpty())
                Dijkstra(graph, n).negativeEdges(neighs).toSet()
            else emptySet()
            acu.addAll(res)
        }
    }
    executor.shutdown()
    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
    toRemove.addAll(acu)
    return toRemove
}

fun negativeVertices(k: Int, graph: Graph, toRemove: MutableNodeSet): NodeSet {
    if (k < 2) throw IllegalArgumentException("k must be >= 2")
    if (k == 2) {
        return negativeVertices(graph, toRemove)
    }
    graph.vertexSet().toSet().filterTo(toRemove) {
        it.weight <= 0 && 3 <= graph.degreeOf(it)
                && graph.degreeOf(it) <= k
                && nvkTest(graph, it)
    }
    return toRemove
}

fun nvkTest(graph: Graph, v: Node): Boolean {
    val edgesWs = graph.edgesOf(v).asSequence().map { it.weight }
    val maxW = edgesWs.max()!!
    val weight = v.weight + edgesWs.map { maxOf(0.0, it) }.sum() + //vertex weight + good edges sum
            minOf(maxW, 0.0) // if all edges are negative, take the best possible
    return if (weight >= 0) false
    else {
        val delta = graph.neighborListOf(v).toSet()
        val edges = delta.map { Pair(it, graph.getAllEdges(v, it)) }
        graph.removeVertex(v)
        val delete = nvkPredicate(graph, weight, delta)
        graph.addVertex(v)
        edges.forEach { (n, es) ->
            es.forEach {
                graph.addEdge(n, v, it)
            }
        }
        delete
    }
}

private fun nvkPredicate(graph: Graph, weight: Double, delta: NodeSet): Boolean {
    val ds = delta.map {
        Pair(it, Dijkstra(graph, it).negativeDistances(delta))
    }.toSet()
    val powerset = powerset(ds).map { it.toMap() }
            .map {
                it.mapValues { (_, v) ->
                    v.filterKeys { k -> it.containsKey(k) }
                }
            }
    return powerset.all { it.size < 2 || MST(it).solve() > weight }
}

private fun dfsC(v: Node, g: Graph, visited: MutableMap<Node, Int>
                 , p: List<Pair<Edge, Node>> = emptyList()): Boolean {
    if (visited.getOrDefault(v, 0) == 2) {
        return false
    }
    visited[v] = 1
    for ((e, u) in g.edgesOf(v)
            .filter { it.weight >= 0 }
            .map { Pair(it, g.opposite(v, it)) }) {
        val ev = Pair(e, v)
        if (!p.isEmpty() && p.last().second != u
                && visited.getOrDefault(u, 0) == 1) {
            System.err.println(p.plus(ev).dropWhile { it.second != u }
                    .joinToString(separator = "-")
                    { "${it.second}-${it.first}" })
            return true
        } else {
            if (!visited.contains(u) && dfsC(u, g, visited, p.plus(ev))) {
                return true
            }
        }
    }
    visited[v] = 2
    return false
}

fun findPosCycles(graph: Graph): Boolean {
    val g = graph.subgraph(graph.vertexSet())
    val vis = mutableMapOf<Node, Int>()
    var res = 0
    for (v in g.vertexSet().filter { it.weight >= 0 }) {
        res += if (dfsC(v, graph, vis)) 1 else 0
        vis.replaceAll { _, _ -> 2 }
    }
//    val res = g.vertexSet().filter { it.weight >= 0 }.sumBy { if (dfsC(it, graph, vis)) 1 else 0 }
    System.err.println("\nfound $res cycles")
    return res > 0
}


private fun logEdges(graph: Graph, edges: EdgeSet): Int {
    if (logLevel > 0) {
        println("${edges.size} edges to remove")
    }
    return edges.size
}

private fun logNodes(graph: Graph, nodes: NodeSet): Int {
    if (logLevel > 0) {
        println("${nodes.size} nodes to remove")
    }
    return nodes.size
}

private fun logAndRemoveEdges(graph: Graph, edges: EdgeSet): Int {
    logEdges(graph, edges)
    edges.forEach { graph.removeEdge(it) }
    return edges.size
}

private fun logAndRemoveNodes(graph: Graph, nodes: NodeSet): Int {
    logNodes(graph, nodes)
    nodes.forEach { graph.removeVertex(it) }
    return nodes.size
}

fun preprocess(graph: Graph) {
    Preprocessor(graph).preprocess()
}

class Preprocessor(val graph: Graph,
                   private val reductions: Reductions = allSteps) {
    fun preprocess() {
        var sum = 0
        do {
            sum = 0
            for (red in reductions) {
                sum += red.apply(graph)
            }
        } while (sum > 0)
    }
}