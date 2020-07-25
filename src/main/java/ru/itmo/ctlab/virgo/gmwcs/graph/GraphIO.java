package ru.itmo.ctlab.virgo.gmwcs.graph;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;

public interface GraphIO {
    Graph read() throws IOException, ParseException;

    void write(List<Elem> elems) throws IOException;

    Node nodeByName(String name);
}
