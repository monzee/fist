package ph.codeia.fistdemo;

/*
 * This file is a part of the fist project.
 */

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import ph.codeia.fist.AndroidTroupe;
import ph.codeia.fist.BlockingFst;
import ph.codeia.fist.Fst;
import ph.codeia.fist.Mu;

public class Counter extends Fragment {
    private Fst.Actor<Integer, ?> ui;
    private TextView count;

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        ui = AndroidTroupe.of(this).bind(
                new BlockingFst<>(0),
                n -> count.setText(Integer.toString(n))
        );
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState
    ) {
        View root = inflater.inflate(R.layout.fragment_counter, container, false);
        count = root.findViewById(R.id.counter);
        root.findViewById(R.id.plus).setOnClickListener(_v -> ui.exec(Counter::succ));
        root.findViewById(R.id.minus).setOnClickListener(_v -> ui.exec(Counter::pred));
        return root;
    }

    static Mu<Integer> succ(int n) {
        return Mu.enter(n + 1);
    }

    static Mu<Integer> pred(int n) {
        return Mu.enter(n - 1);
    }
}
