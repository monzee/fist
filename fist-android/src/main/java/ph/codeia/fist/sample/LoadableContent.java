package ph.codeia.fist.sample;

/*
 * This file is a part of the fist project.
 */

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;

import ph.codeia.fist.AndroidMachine;
import ph.codeia.fist.Fst;
import ph.codeia.fist.R;
import ph.codeia.fist.content.ContentLoader;
import ph.codeia.fist.content.ContentView;
import ph.codeia.fist.content.LoadEvent;
import ph.codeia.fist.content.Loadable;

public class LoadableContent extends AppCompatActivity implements ContentView<String> {

    private static class Scope {
        final Random rng = new Random();
        final ContentLoader<String> controller = new ContentLoader<String>() {
            @Override
            @Nullable
            @WorkerThread
            protected String onFetch() throws InterruptedException {
                Thread.sleep(10_000);
                if (rng.nextBoolean()) {
                    return "Lorem ipsum dolor sit amet";
                }
                else {
                    return null;
                }
            }
        };
        final Fst.Machine<Loadable<String>, ContentView<String>, LoadEvent<String>> screen =
                new AndroidMachine<>(Loadable::render, controller);
    }

    private Scope my;
    private TextView message;
    private Button refresh;

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return my;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        my = (Scope) getLastCustomNonConfigurationInstance();
        if (my == null) {
            my = new Scope();
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_strawman);
        message = (TextView) findViewById(R.id.the_message);
        refresh = (Button) findViewById(R.id.do_refresh);
        refresh.setOnClickListener(_v -> my.screen.exec(this, my.controller.load()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        my.screen.start(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        my.screen.stop();
    }

    @Override
    public boolean willFetch(Event from, String current) {
        switch (from) {
            case BEGIN:
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
    public Fst<Loadable<String>, LoadEvent<String>> loading() {
        message.setText("please wait");
        refresh.setText("Load");
        refresh.setEnabled(false);
        return Fst.noop();
    }

    @Override
    public Fst<Loadable<String>, LoadEvent<String>> loaded(String content) {
        message.setText(content);
        refresh.setText("Refresh");
        refresh.setEnabled(true);
        return Fst.noop();
    }

    @Override
    public Fst<Loadable<String>, LoadEvent<String>> empty() {
        message.setText("?");
        refresh.setText("Load");
        refresh.setEnabled(true);
        return Fst.noop();
    }

    @Override
    public Fst<Loadable<String>, LoadEvent<String>> refreshing(String oldContent) {
        loaded(oldContent);
        refresh.setEnabled(false);
        return Fst.noop();
    }

    @Override
    public void handle(Throwable error) {
        tell(error.toString());
        my.screen.exec(this, my.controller.reset());
    }

    private void tell(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
