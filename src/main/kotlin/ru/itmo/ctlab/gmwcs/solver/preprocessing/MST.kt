package ru.itmo.ctlab.gmwcs.solver.preprocessing

import ru.itmo.ctlab.virgo.gmwcs.graph.Node

/**
 * Created by Nikolay Poperechnyi on 27/10/2017.
 */
typealias NodeArray = Array<Node>

typealias Distances = Map<Node, Double>

/**
 * Naive MST implementation for NPVk preprocessing.
 * k is expected to be small(5-6) and graph should be a clique.
 * Edge weights are assumed to be negative.
 * @param nodes input graph
 */
class MST(val nodes: Map<Node, Distances>) {
    private var res: Double? = null

    /**
     * @return sum of MST weights (is negative).
     */
    fun solve(): Double {
        if (res != null) {
            return res!!
        }
        res = 0.0
        val entries = nodes.entries
        val (start, neighbors) = entries.first()
        assert(nodes.size > 1, { "MST for non-tree" })
        assert(nodes.size == neighbors.size, { "MST for non-clique" })
        val tree = mutableSetOf(start)
        while (tree.size != nodes.size) {
            var best: Node? = null
            var bestW = 0.0
            for (t in tree) {
                val near = nodes[t]!!
                for ((node, _) in near) {
                    if (tree.contains(node)) {
                        continue
                    }
                    if (best == null || near[node]!! < bestW) {
                        best = node
                        bestW = near[node]!!
                    }
                }
            }
            tree.add(best!!)
            res = res!!.plus(bestW)
        }
        return res!!
    }

}