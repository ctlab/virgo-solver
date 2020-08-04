# virgo-solver

This is a common solver for SGMWCS and GMWCS problems.

# Dependencies

The program requires CPLEX (≥ 12.63) to be installed on your computer.

Running
=======

To run the gmwcs solver you should set jvm parameter java.library.path to directory of CPLEX binaries and set parameter
-classpath to program jar and cplex.jar like in the example below.

    java -Djava.library.path=/path/to/cplex/bin/x86-64_linux/ -cp /path/to/cplex/lib/cplex.jar:sgmwcs-solver.jar ru.itmo.ctlab.virgo.Main -e edges -n nodes -type gmwcs -l 2 -m 4

To run the sgmwcs solver:

    java -Djava.library.path=/path/to/cplex/bin/x86-64_linux/ -cp /path/to/cplex/lib/cplex.jar:sgmwcs-solver.jar ru.itmo.ctlab.virgo.Main -e edges -n nodes -type sgmwcs -l 2 -m 4



