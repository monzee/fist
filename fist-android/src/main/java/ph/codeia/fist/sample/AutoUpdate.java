package ph.codeia.fist.sample;

/*
 * This file is a part of the fist project.
 */

import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;
import android.widget.TextView;

import ph.codeia.fist.AndroidMachine;
import ph.codeia.fist.Fst;
import ph.codeia.fist.R;

public class AutoUpdate extends AppCompatActivity implements Periodic.View {
    private static class Scope {
        final Periodic.Presenter actions = new Periodic.Presenter();
        final Fst.Machine<Periodic.Model, Periodic.View, Periodic> screen =
                new AndroidMachine<>(actions, new Periodic.Model());
    }

    private Scope my;
    private TextView message;
    private Button toggle;

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
        toggle = (Button) findViewById(R.id.do_refresh);
        toggle.setOnClickListener(_v -> my.screen.exec(this, my.actions.toggle()));
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
    public void render(boolean active, int last) {
        message.setText("" + last);
        if (active) {
            message.setTextColor(Color.CYAN);
            toggle.setText("Stop");
        }
        else {
            message.setTextColor(Color.RED);
            toggle.setText("Start");
        }
    }

    @Override
    public void say(String s) {
        message.setText(s);
        message.setTextColor(Color.WHITE);
    }

    @Override
    public void handle(Throwable error) {
        throw new RuntimeException(error);
    }
}


interface Periodic extends Fst.Action<Periodic.Model, Periodic.View, Periodic> {
    class Model {
        private boolean running = false;
        private int last = 0;
    }

    interface View extends Fst.ErrorHandler {
        void render(boolean active, int last);
        void say(String message);
    }

    class Presenter implements Periodic {
        @Override
        public Fst<Model, Periodic> apply(Model state, View actor) {
            actor.render(state.running, state.last);
            if (!state.running) return Fst.noop();
            else return Fst.stream(ch -> {
                while (true) {
                    boolean running = ch.send(m -> {
                        if (m.running) {
                            m.last++;
                        }
                        return m;
                    }).running;
                    if (running) Thread.sleep(100);
                    else break;
                }
            }, (m, v) -> {
                v.render(m.running, m.last);
                return Fst.noop();
            });
        }

        Periodic toggle() {
            return (m, v) -> {
                m.running = !m.running;
                return Fst.reenter();
            };
        }

        Periodic pause() {
            return (m, v) -> {
                m.running = false;
                return Fst.reenter();
            };
        }
    }
}

