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

import ph.codeia.fist.R;
import ph.codeia.fist.moore.Sm;

public class StrictMoore extends AppCompatActivity implements Content.View {
    private static class Scope {}

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
    }

    @Override
    public Sm<Content, Content.Event> loading() {
        return Sm.noop();
    }

    @Override
    public Sm<Content, Content.Event> nothing() {
        return Sm.noop();
    }

    @Override
    public Sm<Content, Content.Event> loaded(String text) {
        return Sm.noop();
    }

    @Override
    public Sm<Content, Content.Event> say(String message, Object... fmtArgs) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        return null;
    }


}

interface Content extends Sm.State<Content, Content.View, Content.Event> {

    interface Event extends Sm.Action<Content, Event> {}

    interface View {
        Sm<Content, Event> loading();
        Sm<Content, Event> nothing();
        Sm<Content, Event> loaded(String text);
        Sm<Content, Event> say(String message, Object... fmtArgs);
    }

    abstract class State implements View {
        @Override
        public Sm<Content, Event> say(String message, Object... fmtArgs) {
            return null;
        }
    }

    class Base extends State {
        public Sm<Content, Event> otherwise() {
            return Sm.noop();
        }

        @Override
        public Sm<Content, Event> loading() {
            return otherwise();
        }

        @Override
        public Sm<Content, Event> nothing() {
            return otherwise();
        }

        @Override
        public Sm<Content, Event> loaded(String text) {
            return otherwise();
        }
    }

}

class Controller implements Content {
    @Override
    public Sm<Content, Event> render(View actor) {
        return Sm.seq(
                Sm.peek(v -> v.say("Hello, world!")),
                Sm.async(View::loading, this::fetch)
        );
    }

    Content.Event load() {
        return m -> m.render(new Base() {
            @Override
            public Sm<Content, Event> otherwise() {
                return Sm.noop();
            }

            @Override
            public Sm<Content, Event> nothing() {
                return Sm.seq(
                        Sm.peek(v -> v.say("loading...")),
                        Sm.async(View::loading, () -> fetch())
                );
            }

            @Override
            public Sm<Content, Event> loaded(String text) {
                return Sm.seq(
                        Sm.peek(v -> v.say("reloading...")),
                        Sm.async(View::loading, () -> fetch())
                );
            }
        });
    }

    Content.Event fetch() throws InterruptedException {
        Thread.sleep(1000);
        return m -> Sm.enter(v -> v.loaded("stuff"));
    }
}
