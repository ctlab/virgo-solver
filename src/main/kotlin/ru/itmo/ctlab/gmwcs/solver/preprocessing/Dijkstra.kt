package ru.itmo.ctlab.gmwcs.solver.preprocessing


import ru.itmo.ctlab.virgo.gmwcs.graph.Edge
import ru.itmo.ctlab.virgo.gmwcs.graph.Elem
import ru.itmo.ctlab.virgo.gmwcs.graph.Node
import ru.itmo.ctlab.virgo.gmwcs.graph.Graph
import java.util.*
import kotlin.math.abs

/**
 * Created by Nikolay Poperechnyi on 04/10/2017.
 */
class Dijkstra(private val graph: Graph, private val from: Node) {

    private val s = from.num

    private val n = graph.vertexSet().maxBy { it.num }!!.num + 1

    private val visited = BooleanArray(n) { false }

    private var d = DoubleArray(n) { Double.MAX_VALUE }

    private fun solve(neighbors: Set<Node>) {
        if (d[s] != Double.MAX_VALUE) return
        val queue = PriorityQueue<Node> { n1, n2 -> (d[n1.num] - d[n2.num]).compareTo(0) }
        d[s] = 0.0
        queue.add(from)
        while (queue.isNotEmpty()) {
            val cur = queue.poll()
            if (visited[cur.num])
                continue
            visited[cur.num] = true
            // Stop searching if shortest paths are found
            if (neighbors.contains(cur) && neighbors.all { visited[it.num] })
                break
            for (adj in graph.neighborListOf(cur).filter { !visited[it.num] }) {
                // 0 for positive, -weight for negative
                val e = graph.getEdge(cur, adj)
                val ew = p(e, adj)
                val w = d[cur.num] + ew
                if (d[adj.num] > w) {
                    d[adj.num] = w
                    queue.add(adj)
                }
            }
        }
    }

    fun negativeDistances(neighbors: NodeSet): Map<Node, Double> =
            distances(neighbors).mapValues { -it.value }

    private fun distances(neighbors: Set<Node>): Map<Node, Double> {
        solve(neighbors)
        return neighbors.associateWith { d[it.num] + p(from) }
    }

    fun negativeEdges(neighbors: Set<Node>): List<Edge> {
        solve(neighbors)
        return graph.edgesOf(from).filter {
            val end = graph.opposite(from, it)
            it.weight < 0 && !almostEquals(d[end.num] - p(it), p(end))
            // it.weight <= 0 && d[end] < -it.weight
        }
    }

    fun negativeVertex(dest: Node, candidate: Node): Boolean {
        solve(setOf(dest))
        // test is passed if candidate for removal is not in the solution
        val candPathW = p(graph.getEdge(from, candidate),
                graph.getEdge(candidate, dest),
                candidate)
        return !almostEquals(d[dest.num] - p(dest), candPathW)
    }

    private fun almostEquals(a: Double, b: Double): Boolean {
        return abs(a - b) < 1e-10
    }

    private fun p(vararg e: Elem): Double {
        return -e.sumByDouble { minOf(it.weight, 0.0) }
    }
}