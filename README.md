[![Build Status](https://travis-ci.org/ctlab/virgo-solver.svg?branch=master)](https://travis-ci.org/ctlab/virgo-solver) [![codecov](https://codecov.io/gh/ctlab/virgo-solver/branch/master/graph/badge.svg)](https://codecov.io/gh/ctlab/virgo-solver)

# Virgo Solver

Virgo Solver is a Java program for solving maximum weight connected subgraph (MWCS) problem and its variants.
The solver supports two modes of execution: exact mode that uses CPLEX library to solve the instances to optimality and non-exact,
which uses heuristics to find relatively good solutions.

The supported MWCS variants are:
* classic (simple) MWCS, where only vertices are weighted;
* generalized MWCS (GMWCS), where both vertices and edges are weighted;
* signal generalized MWCS (SGMWCS), where both vertices and edges are marked with weighted “signals”, and a weight of a subgraph is calculated as a sum of weights of its unique signals.


# Quick start

First, open a terminal and make sure you have Java runtime environment (version ≥ 11) available, wich can be checked by running a command `java -version`.

Download the JAR file from latest release at [releases](https://github.com/ctlab/virgo-solver/releases) web-page. 
This file includes all the necessary runtime dependencies.

Downlad the zip-archive with examples... and unpack into the same directory.

Next, you can run Virgo and solve an example GMWCS instance with the following command:

```
java -jar virgo-solver.jar -t gmwcs -n examples/gmwcs-5/nodes -e examples/gmwcs-5/edges -mst
```

Here we used the following arguments:
* `-jar virgo-solver.jar` specifies full path to the JAR-file with the solver;
* `-t gmwcs` specifies the type of the problem that a GMWCS instance will be provided;
* `-n examples/gmwcs-5/nodes` and `-e examples/gmwcs-5/edges` specify full paths to the nodes and edges of the instance;
* `-mst` flag tells to use minimum spanning tree heuristic (MST) which make the solver run in non-exact mode without requirement for CPLEX library.

After the solver has finished running you see a score of the found submodule in terminal stdout.
The solution files are located in `examples/gmwcs-5` files. This can be changed by
adding `-o` flag. For instance `-o solutions/gmwcs-5` will place solutions files in this directory.

Similarly, an SGMWCS instance can be solved:

```
java -jar virgo-solver.jar -t sgmwcs -n examples/readme/nodes -e examples/readme/edges -s examples/readme/signals -mst
```

Here, we changed the value of `-t` parameter to `sgmwcs` and added `-s` parameter with a path to the instance signal weights.

If the cplex library is available, Virgo can e run in the exact mode.

To use exact virgo-solver CPLEX (≥ 12.63) is required.

To run virgo-solver you should set jvm parameter java.library.path to directory of CPLEX binaries and set parameter
-classpath to program jar and cplex.jar like in the example below.

    java -Djava.library.path=/path/to/cplex/bin/x86-64_linux/ -cp /path/to/cplex/lib/cplex.jar:virgo-solver.jar ru.itmo.ctlab.virgo.Main -e examples/gmwcs-5/edges -n examples/gmwcs-5/nodes -type gmwcs -l 2 -m 4

To solve sgmwcs problem using virgo-solver:

    java -Djava.library.path=/path/to/cplex/bin/x86-64_linux/ -cp /path/to/cplex/lib/cplex.jar:virgo-solver.jar ru.itmo.ctlab.virgo.Main -e examples/readme/edges -n examples/readme/nodes -s examples/readme/signals -type sgmwcs -l 2 -m 4

# Supported formats 

For all the problems the input files are described by tab-separated (TSV) files.
The program supports three formats of Maximum Weighted Connected Subgraph problem:
Maximum Weight Connected Subgraph Problem (MWCS),
Generalized Maximum Weight Connected Subgraph Problem (GMWCS) and
Signal-Generalized Maximum Weight Connected Subgraph Problem (SGMWCS).

To solve MWCS or GMWCS problem you need to pass `edges` and `nodes` arguments to solver.
MWCS problem is special case of GMWCS problem when all edges have weight 0.

## MWCS

MWCS instance is a node-weighted graph. The problem is to find a connected subgraph with
maximum total sum of node weights.

The node weights are described in a simple TSV file. For example (`examples/mwcs-5`):

Node file(node_name  node_weight):

    1   5.5
    2   -2.0
    3   -5.0
    4   3.0
    5   -1.0


The edges of the graph are listed in adjacency list file.

Edge file(edge_from edge_to):
    1   2
    1   3
    2   3
    2   4
    4   5
    1   5

Blue nodes in graph below - solution.

![Sample](/mwcs_solved.png?raw=true "Solution")

Output consists of `nodes.out` and `edges.out` files. If a node/edge does not
belong to solution then `n/a` will be printed instead of it's weight.

Solution node file(node_name node_weight)

    1	5.5
    2	n/a
    3	n/a
    4	3.0
    5	-1.0
    #subnet node score	7.5

Solution edge file(edge_source edge_target edge_weight)
    1	2	n/a
    1	3	n/a
    2	3	n/a
    2	4	n/a
    4	5	0.0
    1	5	0.0
    #subnet edge score	0.0

## GMWCS

GMWCS instance is a node and edge-weighted graph. The problem is to find a
maximum weight connected subgraph. Unlike MWCS solution, this graph may not necessarily
be a tree. See `examples/gmwcs-5`.

Node file(node_name  node_weight):

    1   -3.0
    2   -5.0
    3   0.0
    4   2.0
    5   1.0

The only difference from MWCS format: edges have weight column.

Edge file(edge_from edge_to edge_weight):

    1   2   4.0
    1   3   7.0
    2   3   5.0
    3   4   1.0
    4   5   -2.0
    1   5   -1.5

![Sample](/sample.png?raw=true "Sample")

Red units in graph below - solution.

![Sample](/sample_solved.png?raw=true "Solution")

## SGMWCS

Input of SGMWCS problem is graph with node and edge weights (positive or negative).
Some of the nodes or edges are grouped into a signal so that each node/edge in the signal has the same score.
The goal is to find a connected subgraph with a maximal weight, considered nodes/edges in a signal group are counted maximum one time.
GMWCS problem is a special case of SGMWCS problem when each nodes and edges are not grouped so there is no signal file present.

Instead of weight each node and edge has set of signals. Each signal gets it weight from `signals` file. See `examples/readme`.

Node file(node_name  [signal...]):

    1   S7
    2   S9
    3   S3
    4   S10
    5   S11
    6   S2
    7   S2
    8   S8
    9   S3
    10  S2
    11  S1
    12  S5

Edge file(edge_from  edge_to  [signal...]):

	1	2	S3
	2	3	S1
	3	4	S13
	3	6	S16
	6	7	S12
	5	7	S14
	4	7	S4
	4	8	S4
	1	8	S15
	8	9	S4
	9	10	S1
	1	11	S6
    11	12	S13

Signal file(signal  weight)

    S1  2
    S2  10
    S3  5
    S4  5.5
    S5  -1
    S6  -5
    S7  15
    S8  -10
    S9  -6
    S10 -7
    S11 12
    S12 4
    S13 3
    S14 7
    S15 -1
    S16 1

Positive vertices and edges with the same weight share common signal. Common signals of negative
weights are disallowed.

![Example](/gmwcs_sample.png?raw=true "Sample")

Red edges and blue nodes in graph below - solution.

![Example](/gmwcs_sample_solved.png?raw=true "Solution")

# Building from sources

Then you should install CPLEX  ???.

After CPLEX is installed, put the provided jar package into the local maven repository using the following command (replace 12.6.3 with ...):

    mvn install:install-file -Dfile=/opt/ibm/ILOG/CPLEX_Studio1263/cplex/lib/cplex.jar -DgroupId=com.ibm -DartifactId=cplex -Dversion=12.6.3 -Dpackaging=jar


Get the source code by cloning git reposiroty `https://github.com/ctlab/virgo-solver.git` or by downloadeding a zip-file ` .. zip`.

    
From the source root directory run maven to build Virgo:

    mvn install -DskipTests=true

If the build is successfull a jar file `virgo-solver.jar` will appear in the `target` directory.

