package ph.codeia.fist.sample;

/*
 * This file is a part of the fist project.
 */

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;
import java.util.concurrent.TimeoutException;

import ph.codeia.fist.AndroidMealy;
import ph.codeia.fist.R;
import ph.codeia.fist.content.Loadable;
import ph.codeia.fist.content.Loader;
import ph.codeia.fist.mealy.Mi;

public class MiLoadableContent extends AppCompatActivity implements Loadable.Ui<String> {

    private Scope my;
    private Mi.Actor<Loadable<String>, Loadable.Ui<String>> screen;
    private TextView message;
    private Button refresh;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        my = (Scope) getLastCustomNonConfigurationInstance();
        if (my == null) {
            my = new Scope();
        }
        screen = Mi.bind(my.content, this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_strawman);
        message = (TextView) findViewById(R.id.the_message);
        refresh = (Button) findViewById(R.id.do_refresh);
        refresh.setOnClickListener(_v -> screen.exec(my.loader.load()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        screen.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        screen.stop();
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return my;
    }

    @Override
    public boolean canFetch(Event from, String current) {
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
        screen.exec(my.loader.load());
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
            screen.exec(my.loader.reset());
        }
    }

    void tell(String tpl, Object... fmtArgs) {
        Toast.makeText(this, String.format(tpl, fmtArgs), Toast.LENGTH_SHORT).show();
    }

    private static class Scope {
        final Random rng = new Random();
        final Loader<String> loader = () -> {
            Thread.sleep(10_000);
            if (rng.nextBoolean()) {
                return "Lorem ipsum dolor sit amet";
            }
            else {
                return null;
            }
        };
        final Mi.Runner<Loadable<String>, Loadable.Ui<String>> content =
                new AndroidMealy<>(new Loadable<>());
    }
}
