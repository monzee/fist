package ph.codeia.fist.sample;

/*
 * This file is a part of the fist project.
 */

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;
import java.util.concurrent.Callable;

import ph.codeia.fist.AndroidMealy;
import ph.codeia.fist.R;
import ph.codeia.fist.mealy.Mi;

@SuppressLint({"DefaultLocale", "SetTextI18n"})
public class MiGuessTheNumber extends AppCompatActivity implements Ui {

    private Mi.Runner<GameModel<Ui>, Ui> game;
    private Mi.Actor<GameModel<Ui>, Ui> screen;
    private TextView message;
    private EditText input;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        //noinspection unchecked
        game = (Mi.Runner<GameModel<Ui>, Ui>) getLastCustomNonConfigurationInstance();
        if (game == null) {
            game = new AndroidMealy<>(Game.begin(6));
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guessing_game);
        message = (TextView) findViewById(R.id.message);
        input = (EditText) findViewById(R.id.guess);
        input.setOnEditorActionListener((textView, i, keyEvent) -> {
            String text = textView.getText().toString();
            if (keyEvent != null || text.isEmpty()) return false;
            screen.exec(Game.guess(Integer.parseInt(text)));
            return game.inspect(GameModel::isNotDone);
        });
        screen = Mi.bind(game, this);
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
        return game;
    }

    @Override
    public void say(String message, Object... fmtArgs) {
        Toast.makeText(
                this,
                String.format(message, fmtArgs),
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void clear() {
        input.setText(null);
    }

    @Override
    public Mi<GameModel<Ui>, Ui> begin(int maxTries) {
        message.setText(String.format(""
                + "Choose a number from 1-100 inclusive.%n"
                + "You have %d tries to get it right.", maxTries));
        return null;
    }

    @Override
    public Mi<GameModel<Ui>, Ui> playing(int guess, int secret, int triesLeft, int maxTries) {
        String tries = triesLeft == 1 ? "try" : "tries";
        String relation = guess < secret ? "low" : "high";
        message.setText(String.format(
                "%d is too %s; %d %s remaining",
                guess, relation, triesLeft, tries));
        return null;
    }

    @Override
    public Mi<GameModel<Ui>, Ui> won() {
        message.setText("Correct!");
        return null;
    }

    @Override
    public Mi<GameModel<Ui>, Ui> lost(int secret) {
        message.setText(String.format("Game over! The number was %d.", secret));
        return null;
    }
}

interface GameModel<C> {
    Mi<GameModel<C>, C> match(State<C> state);

    default boolean isNotDone() {
        return new State<C>() {
            boolean result = false;
            {
                match(this);
            }

            @Override
            public Mi<GameModel<C>, C> playing(int guess, int secret, int triesLeft, int maxTries) {
                result = true;
                return null;
            }
        }.result;
    }

    interface State<C> {
        default Mi<GameModel<C>, C> begin(int maxTries) {
            return otherwise();
        }

        default Mi<GameModel<C>, C> playing(int guess, int secret, int triesLeft, int maxTries) {
            return otherwise();
        }

        default Mi<GameModel<C>, C> lost(int secret) {
            return otherwise();
        }

        default Mi<GameModel<C>, C> won() {
            return otherwise();
        }

        default Mi<GameModel<C>, C> otherwise() {
            return Mi.raise(new IllegalStateException());
        }
    }
}

interface Ui extends Mi.Effects<GameModel<Ui>>, GameModel.State<Ui> {
    void say(String message, Object... fmtArgs);
    void clear();

    @Override
    default void onEnter(GameModel<Ui> game) {
        game.match(this);
    }
}

interface Game {
    Random RNG = new Random();

    static <C> GameModel<C> begin(int maxTries) {
        return game -> game.begin(maxTries);
    }

    static Mi.Action<GameModel<Ui>, Ui> newGame(int maxTries) {
        return (_g, view) -> {
            view.say("Starting new game in 10 seconds");
            return Mi.reduce(() -> {
                Thread.sleep(10_000);
                return (_fg, futureView) -> {
                    futureView.clear();
                    return Mi.enter(begin(maxTries));
                };
            });
        };
    }

    static Mi.Action<GameModel<Ui>, Ui> guess(int n) {
        return (game, view) -> game.match(new GameModel.State<Ui>() {
            @Override
            public Mi<GameModel<Ui>, Ui> begin(int maxTries) {
                view.clear();
                return playing(n, RNG.nextInt(100) + 1, maxTries, maxTries);
            }

            @Override
            public Mi<GameModel<Ui>, Ui> playing(
                    int guess, int secret,
                    int triesLeft, int maxTries
            ) {
                if (n == secret) {
                    return newGame(maxTries).after(Mi.enter(GameModel.State::won));
                }
                if (triesLeft == 1) {
                    return newGame(maxTries).after(Mi.enter(s -> s.lost(secret)));
                }
                view.clear();
                return Mi.enter(s -> s.playing(n, secret, triesLeft - 1, maxTries));
            }

            @Override
            public Mi<GameModel<Ui>, Ui> otherwise() {
                view.say("Wait for the next game.");
                return Mi.noop();
            }
        });
    }
}
