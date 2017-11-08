package ph.codeia.fistdemo;

/*
 * This file is a part of the fist project.
 */

import ph.codeia.fist.moore.Mu;

class Counter {
    int n = 0;

    Mu<Counter> succ() {
        n++;
        return Mu.reenter();
    }

    Mu<Counter> pred() {
        n--;
        return Mu.reenter();
    }

    @Override
    public String toString() {
        return Integer.toString(n);
    }
}
