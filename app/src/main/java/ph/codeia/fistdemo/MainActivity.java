package ph.codeia.fistdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import ph.codeia.fist.moore.BlockingMoore;
import ph.codeia.fist.moore.Mu;

public class MainActivity extends AppCompatActivity {

    private static class Counter {
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

    private static class Scope {
        Mu.Runner<Counter> counter = new BlockingMoore<>(new Counter());
    }

    private Scope my;
    private Mu.Actor<Counter> view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        my = (Scope) getLastCustomNonConfigurationInstance();
        if (my == null) my = new Scope();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TextView counter = (TextView) findViewById(R.id.counter);
        findViewById(R.id.plus).setOnClickListener(_v -> view.exec(Counter::succ));
        findViewById(R.id.minus).setOnClickListener(_v -> view.exec(Counter::pred));
        view = Mu.bind(my.counter, state -> counter.setText(state.toString()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.stop();
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return my;
    }
}
