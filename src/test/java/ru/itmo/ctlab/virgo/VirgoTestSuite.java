package ru.itmo.ctlab.virgo;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import ru.itmo.ctlab.virgo.gmwcs.GMWCSTest;
import ru.itmo.ctlab.virgo.sgmwcs.SGMWCSTest;

/**
 * Created by Nikolay Poperechnyi on 30.08.20.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({GMWCSTest.class, SGMWCSTest.class})
public class VirgoTestSuite {
}
