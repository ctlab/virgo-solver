package ru.itmo.ctlab.gmwcs

import ru.itmo.ctlab.gmwcs.solver.preprocessing.*
import ru.itmo.ctlab.virgo.gmwcs.graph.Elem
import ru.itmo.ctlab.virgo.gmwcs.graph.Graph
import ru.itmo.ctlab.virgo.gmwcs.graph.SimpleIO
import java.io.File
import java.io.FileReader
import java.util.*

/**
 * Created by Nikolay Poperechnyi on 19/10/2017.
 */


typealias NamedReductions = kotlin.Pair<String, List<ReductionSequence<out Elem>>>

data class NamedGraph(val graph: Graph, val name: String)

fun benchmark(graphs: Array<NamedGraph>, preprocessingList: List<NamedReductions>) {
    graphs.forEach {
        benchmarkGraph(it, preprocessingList)
    }
}

private fun benchmarkGraph(graph: NamedGraph, reductions: List<NamedReductions>) {
    println("Benchmarks for graph ${graph.name}")
    for (red in reductions) {
        benchmarkGraphPreprocessing(graph.graph.subgraph(graph.graph.vertexSet()), red)
    }
}

private fun benchmarkGraphPreprocessing(graph: Graph, preprocessing: NamedReductions) {
    val benchName = preprocessing.first
    println("--------$benchName--------")
    val sizeBefore = Pair(graph.vertexSet().size, graph.edgeSet().size)
    val timeStart = System.currentTimeMillis()
    Preprocessor(graph, preprocessing.second).preprocess()
    val timeDelta = (System.currentTimeMillis() - timeStart).toDouble() / 1000
    val sizeAfter = Pair(graph.vertexSet().size, graph.edgeSet().size)
    val delta = Pair(sizeBefore.first - sizeAfter.first
            , sizeBefore.second - sizeAfter.second)
    println("---------Results---------")
    println("Time: $timeDelta\nRemoved nodes ${delta.first}, edges: ${delta.second}") // Todo
}

val reductions = mapOf(
        "mergeNeg" to listOf(mergeNeg),
        "mergePos" to listOf(mergePos),
        "npv" to listOf(negV),
        "npe" to listOf(negE),
        "cns" to listOf(cns),
        "nvk" to listOf(nvk)
).toList()

fun main(args: Array<String>) {
    val nodeFile = File(args[0])
    val edgeFile = File(args[1])
    val rulesFile = File(args[2])
    val graphIO = SimpleIO(nodeFile, File(nodeFile.toString() + ".out"),
            edgeFile, File(edgeFile.toString() + ".out"))
    val graph = graphIO.read()
    benchmarkGraph(NamedGraph(graph, "test"), reductions)

    /* setThreads(4)
     FileReader(rulesFile).forEachLine {
         if (!it.contains('#')) {
             val p = Pair(it, it.split(" ").mapNotNull { reductions[it] })
             tests.add(p)
         }
     }
     benchmarkGraph(NamedGraph(graph, "$nodeFile\t$edgeFile"), tests)
     for (test in tests) {
         benchmarkGraphPreprocessing(graph.subgraph(graph.vertexSet()), test)
     } */
}