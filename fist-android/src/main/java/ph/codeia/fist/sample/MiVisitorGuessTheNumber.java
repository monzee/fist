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

import ph.codeia.fist.AndroidMealy;
import ph.codeia.fist.Effects;
import ph.codeia.fist.R;
import ph.codeia.fist.Mi;

@SuppressLint({"DefaultLocale", "SetTextI18n"})
public class MiVisitorGuessTheNumber extends AppCompatActivity implements Ui {

    private Mi.Runner<Game<Ui>, Ui> game;
    private Mi.Actor<Game<Ui>, Ui> screen;
    private TextView message;
    private EditText input;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        //noinspection unchecked
        game = (Mi.Runner<Game<Ui>, Ui>) getLastCustomNonConfigurationInstance();
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
            screen.exec(Player.guess(Integer.parseInt(text)));
            return game.project(Game::isNotDone);
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
    public void tell(String message, Object... fmtArgs) {
        Toast.makeText(this, String.format(message, fmtArgs), Toast.LENGTH_SHORT)
                .show();
    }

    @Override
    public void clearInput() {
        input.setText(null);
    }

    @Override
    public Mi<Game<Ui>, Ui> begin(int maxTries) {
        message.setText(String.format(""
                + "Choose a number from 1-100 inclusive.%n"
                + "You have %d tries to get it right.",
                maxTries
        ));
        return null;
    }

    @Override
    public Mi<Game<Ui>, Ui> playing(int guess, int secret, int triesLeft, int maxTries) {
        String tries = triesLeft == 1 ? "try" : "tries";
        String relation = guess < secret ? "low" : "high";
        message.setText(String.format(
                "%d is too %s; %d %s remaining",
                guess, relation, triesLeft, tries
        ));
        return null;
    }

    @Override
    public Mi<Game<Ui>, Ui> won() {
        message.setText("Correct!");
        return null;
    }

    @Override
    public Mi<Game<Ui>, Ui> lost(int secret) {
        message.setText(String.format("Game over! The number was %d.", secret));
        return null;
    }
}

interface Game<C> {
    Mi<Game<C>, C> match(State<C> state);

    static <C> Game<C> begin(int maxTries) {
        return game -> game.begin(maxTries);
    }

    default boolean isNotDone() {
        return new State<C>() {
            boolean result = false;

            {
                match(this);
            }

            @Override
            public Mi<Game<C>, C> playing(int guess, int secret, int triesLeft, int maxTries) {
                result = true;
                return null;
            }
        }.result;
    }

    interface State<C> {
        default Mi<Game<C>, C> begin(int maxTries) {
            return otherwise();
        }

        default Mi<Game<C>, C> playing(int guess, int secret, int triesLeft, int maxTries) {
            return otherwise();
        }

        default Mi<Game<C>, C> lost(int secret) {
            return otherwise();
        }

        default Mi<Game<C>, C> won() {
            return otherwise();
        }

        default Mi<Game<C>, C> otherwise() {
            return Mi.raise(new IllegalStateException());
        }
    }
}

interface Ui extends Effects<Game<Ui>>, Game.State<Ui> {
    void tell(String message, Object... fmtArgs);

    void clearInput();

    @Override
    default void onEnter(Game<Ui> game) {
        game.match(this);
    }
}

interface Player {
    Random RNG = new Random();

    static Mi.Action<Game<Ui>, Ui> newGame(int maxTries) {
        return (_g, view) -> {
            view.tell("Starting new game in 10 seconds");
            return Mi.async(() -> {
                Thread.sleep(10_000);
                return (_fg, futureView) -> {
                    futureView.clearInput();
                    return Mi.enter(Game.begin(maxTries));
                };
            });
        };
    }

    static Mi.Action<Game<Ui>, Ui> guess(int n) {
        return (game, view) -> game.match(new Game.State<Ui>() {
            @Override
            public Mi<Game<Ui>, Ui> begin(int maxTries) {
                view.clearInput();
                return playing(n, RNG.nextInt(100) + 1, maxTries, maxTries);
            }

            @Override
            public Mi<Game<Ui>, Ui> playing(
                    int guess, int secret,
                    int triesLeft, int maxTries
            ) {
                if (n == secret) {
                    return newGame(maxTries).after(Mi.enter(Game.State::won));
                }
                if (triesLeft == 1) {
                    return newGame(maxTries).after(Mi.enter(s -> s.lost(secret)));
                }
                view.clearInput();
                return Mi.enter(s -> s.playing(n, secret, triesLeft - 1, maxTries));
            }

            @Override
            public Mi<Game<Ui>, Ui> otherwise() {
                view.tell("Wait for the next game.");
                return Mi.noop();
            }
        });
    }
}
