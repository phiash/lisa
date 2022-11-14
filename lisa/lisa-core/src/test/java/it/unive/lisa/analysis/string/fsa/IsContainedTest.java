package it.unive.lisa.analysis.string.fsa;

import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class IsContainedTest {

	@Test
	public void simpleTest() {
		Set<State> states = new HashSet<>();
		State[] st = new State[2];
		Set<State> states2 = new HashSet<>();
		State[] st2 = new State[2];

		Set<Transition> delta = new HashSet<>();
		Set<Transition> delta2 = new HashSet<>();

		st[0] = new State(true, false);
		st[1] = new State(false, true);
		Collections.addAll(states, st);

		st2[0] = new State(true, false);
		st2[1] = new State(false, true);
		Collections.addAll(states2, st2);

		delta.add(new Transition(st[0], st[1], "a"));
		delta.add(new Transition(st[0], st[1], "b"));

		delta2.add(new Transition(st2[0], st2[1], "a"));

		// a | b
		Automaton a = new Automaton(states, delta);

		// a
		Automaton a2 = new Automaton(states2, delta2);

		assertTrue(a2.isContained(a));
		assertTrue(!a.isContained(a2));
	}

	@Test
	public void test01() {
		Set<State> states = new HashSet<>();
		State[] st = new State[5];
		Set<State> states2 = new HashSet<>();
		State[] st2 = new State[3];
		Set<Transition> delta = new HashSet<>();
		Set<Transition> delta2 = new HashSet<>();
		st[0] = new State(true, false);
		st[1] = new State(false, false);
		st[2] = new State(false, false);
		st[3] = new State(false, true);
		st[4] = new State(false, true);
		Collections.addAll(states, st);

		st2[0] = new State(true, false);
		st2[1] = new State(false, false);
		st2[2] = new State(false, true);
		Collections.addAll(states2, st2);

		delta.add(new Transition(st[0], st[1], "a"));
		delta.add(new Transition(st[0], st[2], "c"));
		delta.add(new Transition(st[1], st[3], "b"));
		delta.add(new Transition(st[2], st[4], "c"));

		delta2.add(new Transition(st2[0], st2[1], "a"));
		delta2.add(new Transition(st2[1], st2[2], "b"));

		// ab | cc
		Automaton a = new Automaton(states, delta);

		// ab
		Automaton a2 = new Automaton(states2, delta2);

		assertTrue(a2.isContained(a));
		assertTrue(!a.isContained(a2));
	}
}