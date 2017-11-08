package ph.codeia.fistdemo;

/*
 * This file is a part of the fist project.
 */

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import ph.codeia.fist.BlockingFst;
import ph.codeia.fist.Effects;
import ph.codeia.fist.Fst;
import ph.codeia.fist.StatefulFragment;

public class CounterFragment extends StatefulFragment<Counter, Effects<Counter>> {
    private TextView count;

    @Override
    protected Fst.Actor<Counter, Effects<Counter>> initialState() {
        return new BlockingFst<>(new Counter()).bind(state -> count.setText(state.toString()));
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState
    ) {
        View root = inflater.inflate(R.layout.fragment_counter, container, false);
        count = (TextView) root.findViewById(R.id.counter);
        root.findViewById(R.id.plus).setOnClickListener(_v -> exec(Counter::succ));
        root.findViewById(R.id.minus).setOnClickListener(_v -> exec(Counter::pred));
        return root;
    }
}
