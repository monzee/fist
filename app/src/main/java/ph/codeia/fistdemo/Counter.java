package ph.codeia.fistdemo;

/*
 * This file is a part of the fist project.
 */

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import ph.codeia.fist.BlockingFst;
import ph.codeia.fist.Fst;
import ph.codeia.fist.LifecycleBinder;
import ph.codeia.fist.Mu;
import ph.codeia.fist.R;

public class Counter extends Fragment {
    private Fst.Binding<Integer, ?> ui;
    private TextView count;

    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        ui = LifecycleBinder.of(this).bind(
                new BlockingFst<>(0),
                n -> count.setText(Integer.toString(n))
        );
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
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
