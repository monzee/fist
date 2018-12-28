package ph.codeia.fist.sample;

/*
 * This file is a part of the fist project.
 */

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import ph.codeia.fist.R;

public class PlainAsync extends AppCompatActivity {

    private static final ExecutorService WORKERS = Executors.newCachedThreadPool();
    private static final Random RNG = new Random();

    private enum State { BEGIN, LOADING, LOADED, NOTHING, REFRESHING }

    private static class Scope {
        Future<String> pendingFetch;
        State state = State.BEGIN;
        String text;
    }

    private Scope my;
    private AsyncTask<Void, Exception, String> pendingJoin;
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
        refresh.setOnClickListener(_v -> load());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (my.pendingFetch != null) {
            join(my.pendingFetch);
        }
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (pendingJoin != null) {
            pendingJoin.cancel(true);
        }
    }

    void render() {
        switch (my.state) {
            case BEGIN:
                load();
                break;
            case LOADING:
                message.setText("please wait...");
                refresh.setText("Load");
                refresh.setEnabled(false);
                break;
            case NOTHING:
                message.setText("?");
                refresh.setText("Load");
                refresh.setEnabled(true);
                break;
            case LOADED:
                message.setText(my.text);
                refresh.setText("Refresh");
                refresh.setEnabled(true);
                break;
            case REFRESHING:
                message.setText(my.text);
                refresh.setText("Refresh");
                refresh.setEnabled(false);
                break;
        }
    }

    void load() {
        switch (my.state) {
            case BEGIN:
                tell("Hello, world!");
                my.state = State.LOADING;
                break;
            case NOTHING:
                tell("loading...");
                my.state = State.LOADING;
                break;
            case LOADED:
                tell("refreshing...");
                my.state = State.REFRESHING;
                break;
            default:
                return;
        }
        render();
        join(WORKERS.submit(() -> {
            Thread.sleep(10_000);
            if (RNG.nextBoolean()) {
                return "Lorem ipsum dolor sit amet";
            }
            else {
                return null;
            }
        }));
    }

    @SuppressLint("StaticFieldLeak")
    void join(Future<String> task) {
        my.pendingFetch = task;
        pendingJoin = new AsyncTask<Void, Exception, String>() {
            @Override
            protected String doInBackground(Void... voids) {
                try {
                    String result = task.get(60, TimeUnit.SECONDS);
                    my.pendingFetch = null;
                    return result;
                }
                catch (InterruptedException ignored) {
                }
                catch (ExecutionException | TimeoutException e) {
                    task.cancel(true);
                    my.pendingFetch = null;
                    publishProgress(e);
                    cancel(false);
                }
                finally {
                    pendingJoin = null;
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(Exception... values) {
                my.state = State.NOTHING;
                tell(values[0].toString());
                render();
            }

            @Override
            protected void onPostExecute(String result) {
                my.text = result;
                if (result == null) {
                    tell("got nothing!");
                    my.state = State.NOTHING;
                }
                else {
                    my.state = State.LOADED;
                }
                render();
            }
        };
        pendingJoin.execute();
    }

    void tell(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

}
