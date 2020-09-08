# virgo-solver

This is a common solver for SGMWCS and GMWCS problems.

[![Build Status](https://travis-ci.org/ctlab/virgo-solver.svg?branch=master)](https://travis-ci.org/ctlab/virgo-solver) [![codecov](https://codecov.io/gh/ctlab/virgo-solver/branch/master/graph/badge.svg)](https://codecov.io/gh/ctlab/virgo-solver)

See [releases](https://github.com/ctlab/virgo-solver/releases) to get built jar files.

# Problem
The solver solves two versions of Maximum Weighted Connected Subgraph problem:
Generalized Maximum Weight Connected Subgraph Problem (GMWCS) and
Signal-Generalized Maximum Weight Connected Subgraph Problem (SGMWCS). The latter one is an extension the first one.
Input of SGMWCS problem is graph with node and edge weights (positive or negative).
Some of the nodes or edges are grouped into a signal so that each node/edge in the signal has the same score.
The goal is to find a connected subgraph with a maximal weight, considered nodes/edges in a signal group are counted maximum one time.
GMWCS problem is a special case of SGMWCS problem when each nodes and edges are not grouped so there is no signal file present.

# Quick start without CPLEX
You can run approximate virgo-solver without extra dependencies installed. Only Java (≥ 11) is required.
Approximate solver for SGMWCS problem:

    java -cp virgo-solver.jar Main -n examples/readme/nodes -e examples/readme/edges -s examples/readme/signals -mst

Approximate solver for GMWCS problem:

    java -cp virgo-solver.jar Main -n examples/gmwcs-5/nodes -e examples/gmwcs-5/edges -mst

# Quick start with CPLEX
To use exact virgo-solver CPLEX (≥ 12.63) is required.

To run virgo-solver you should set jvm parameter java.library.path to directory of CPLEX binaries and set parameter
-classpath to program jar and cplex.jar like in the example below.

    java -Djava.library.path=/path/to/cplex/bin/x86-64_linux/ -cp /path/to/cplex/lib/cplex.jar:virgo-solver.jar ru.itmo.ctlab.virgo.Main -e examples/gmwcs-5/edges -n examples/gmwcs-5/nodes -type gmwcs -l 2 -m 4

To solve sgmwcs problem using virgo-solver:

    java -Djava.library.path=/path/to/cplex/bin/x86-64_linux/ -cp /path/to/cplex/lib/cplex.jar:virgo-solver.jar ru.itmo.ctlab.virgo.Main -e examples/readme/edges -n examples/readme/nodes -s examples/readme/signals -type sgmwcs -l 2 -m 4

# Format and examples
To solve MWCS or GMWCS problem you need to pass `edges` and `nodes` arguments to solver.
MWCS problem is special case of GMWCS problem when all edges have weight 0 (see `examples/bionet`).

## GMWCS format

Node file(node_name  node_weight):

    1   -3.0
    2   -5.0
    3   0.0
    4   2.0
    5   1.0

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


Positive vertices and edges with the same weight share common signal.

![Example](/gmwcs_sample.png?raw=true "Sample")

Red edges and blue nodes in graph below - solution.

![Example](/gmwcs_sample_solved.png?raw=true "Solution")

# Building from sources

Get source using git or svn using the web URL:

    https://github.com/ctlab/virgo-solver.git

Then you should install concert library of CPLEX.
It's located in "cplex/lib" directory from CPLEX STUDIO root path.
For example,

    mvn install:install-file -Dfile=/opt/ibm/ILOG/CPLEX_Studio1263/cplex/lib/cplex.jar -DgroupId=com.ibm -DartifactId=cplex -Dversion=12.6.3 -Dpackaging=jar

After that you can build the project using maven:

    mvn install -DskipTests=true

And jar file with name "virgo-solver.jar" will appear in the "target" directory
