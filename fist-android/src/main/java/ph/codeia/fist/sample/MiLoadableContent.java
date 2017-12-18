package ph.codeia.fist.sample;

/*
 * This file is a part of the fist project.
 */

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;
import java.util.concurrent.TimeoutException;

import ph.codeia.fist.AndroidTroupe;
import ph.codeia.fist.AndroidFst;
import ph.codeia.fist.Fst;
import ph.codeia.fist.R;
import ph.codeia.fist.content.Loadable;
import ph.codeia.fist.content.Loader;

public class MiLoadableContent extends Fragment implements Loadable.Ui<String>
{
    private static final Random RNG = new Random();

    private static final Loader<String> LOADER = () -> {
        Thread.sleep((RNG.nextInt(5) + 2) * 1_000);
        if (RNG.nextBoolean()) {
            return "Lorem ipsum dolor sit amet";
        }
        else {
            return null;
        }
    };

    private Fst.Actor<Loadable<String>, Loadable.Ui<String>> ui;
    private TextView message;
    private Button refresh;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        ui = AndroidTroupe.of(this).bind(
                Loadable.of(new AndroidFst.Builder().timeout(5_000)),
                this
        );
    }

    @Nullable
    @Override
    public View onCreateView(
            LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        View root = inflater.inflate(R.layout.activity_strawman, container, false);
        message = root.findViewById(R.id.the_message);
        refresh = root.findViewById(R.id.do_refresh);
        refresh.setOnClickListener(_v -> ui.exec(LOADER.load()));
        return root;
    }

    @Override
    public boolean shouldFetch(Event from, String current) {
        switch (from) {
        case INIT:
            tell("Hello, world!");
            break;
        case LOAD:
            tell("loading...");
            break;
        case REFRESH:
            tell("reloading...");
            break;
        }
        return true;
    }

    @Override
    public void didFetch(boolean isEmpty) {
        if (isEmpty) {
            tell("got nothing!");
        }
    }

    @Override
    public void begin() {
        ui.exec(LOADER.load());
    }

    @Override
    public void loading() {
        message.setText("please wait");
        refresh.setText("Load");
        refresh.setEnabled(false);
    }

    @Override
    public void loaded(String content) {
        message.setText(content);
        refresh.setText("Refresh");
        refresh.setEnabled(true);
    }

    @Override
    public void refreshing(String oldContent) {
        loaded(oldContent);
        refresh.setEnabled(false);
    }

    @Override
    public void empty() {
        message.setText("?");
        refresh.setText("Load");
        refresh.setEnabled(true);
    }

    @Override
    public void handle(Throwable e) {
        if (e instanceof TimeoutException) {
            tell("Unable to fetch in time.");
            ui.exec(Loadable::reset);
        }
        else {
            throw new RuntimeException(e);
        }
    }

    void tell(String tpl, Object... fmtArgs) {
        Toast.makeText(getContext(), String.format(tpl, fmtArgs), Toast.LENGTH_SHORT)
                .show();
    }
}
