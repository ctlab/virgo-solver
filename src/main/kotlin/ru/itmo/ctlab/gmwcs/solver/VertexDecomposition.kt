package ru.itmo.ctlab.gmwcs.solver

import ru.itmo.ctlab.virgo.gmwcs.graph.Edge
import ru.itmo.ctlab.virgo.gmwcs.graph.Graph
import ru.itmo.ctlab.virgo.gmwcs.graph.Node
import ru.itmo.ctlab.virgo.gmwcs.graph.Elem
import java.util.*

/**
 * Created by Nikolay Poperechnyi on 19.04.18.
 */


data class Component(val elems: SortedSet<Elem>, val negativeUb: Double)


fun decomposition(g: Graph, r: Node): List<Component> {
    val vis = mutableSetOf<Elem>()
    val components = mutableListOf<Component>()
    components.add(DFS(g, r, vis).getComponent())
    for (node in g.vertexSet()) {
        if (vis.contains(node)) {
            continue
        } else {
            components.add(DFS(g, node, vis).getComponent())
        }
    }
    return components.toList()
}

fun compUb(g: Graph, r: Node): Double {
    return decomposition(g, r).sumByDouble {
        it.negativeUb + it.elems.sumByDouble { it.weight }
    }
}

class DFS(val g: Graph, val root: Node, private val visited: MutableSet<Elem>) {

    fun getComponent(): Component {
        val negUb = runDfs(root, visited, Double.NEGATIVE_INFINITY)
        return Component(visited.toSortedSet(), negUb)
    }

    private fun runDfs(cur: Node, vis: MutableSet<Elem>, bestBndValue: Double): Double {
        var ret = bestBndValue
        vis.add(cur)
        for (v in g.neighborListOf(cur)
                .filter { !vis.contains(it) }) {
            val weightSum = v.weight + g.getEdge(v, cur).weight
            ret = if (weightSum >= 0) {
                maxOf(runDfs(v, vis, ret), ret)
            } else {
                maxOf(weightSum, ret)
            }
        }
        return ret
    }
}

class Dijkstra(val graph: Graph, val rootNodes: Set<Node>, val posEdges: Set<Edge>) {

    private val n = graph.vertexSet().maxBy { it.num }!!.num + 1

    private val d = DoubleArray(n, { Double.MAX_VALUE })

    //private val s = HashMap<Elem, Region>()

    fun findRegions(comps: List<Component>) {
        val queue = PriorityQueue<Node>(
                { n1, n2 -> (d[n1.num] - d[n2.num]).compareTo(0) }
        )
        for (node in rootNodes) {
            d[node.num] = maxOf(0.0, -node.weight)
            queue.add(node)
        }
        while (!queue.isEmpty()) {
            val cur = queue.poll()
            for (e in graph.edgesOf(cur)) {
                val n = graph.opposite(cur, e)
                val dnew = d[cur.num] - e.weight - n.weight
                if (d[n.num] > dnew) {
                    d[n.num] = dnew
                    queue.add(n)
                }
            }
        }
    }

}
/*
fun getRegions(graph: Graph, solution: List<Elem>): Set<Region>? {
    val centers = mutableSetOf<Node>()
    val posEdges = mutableSetOf<Edge>()
    for (elem in solution) {
        if (elem.weight > 0) {
            if (elem is Edge) {
                posEdges.add(elem)
            }
            if (elem is Node) {
                centers.add(elem)
            }
        }
    }
    for (e in posEdges) {
        val u = graph.getEdgeSource(e)
        val v = graph.getEdgeTarget(e)
        if (u.weight < 0) {
            centers.add(u)
        }
        if (v.weight < 0) {
            centers.add(v)
        }
    }
}

*/
