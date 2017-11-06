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
import ph.codeia.fist.mealy.Mi;

@SuppressLint({"DefaultLocale", "SetTextI18N"})
public class MiEnumGuessTheNumber extends AppCompatActivity implements Play.Ui {

    private Mi.Runner<GuessingGame, Play.Ui> game;
    private Mi.Actor<GuessingGame, Play.Ui> screen;
    private TextView message;
    private EditText input;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        //noinspection unchecked
        game = (Mi.Runner<GuessingGame, Play.Ui>) getLastCustomNonConfigurationInstance();
        if (game == null) {
            game = new AndroidMealy<>(new GuessingGame(6));
        }
        screen = Mi.bind(game, this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guessing_game);
        message = (TextView) findViewById(R.id.message);
        input = (EditText) findViewById(R.id.guess);
        input.setOnEditorActionListener((textView, i, keyEvent) -> {
            String text = textView.getText().toString();
            if (keyEvent != null || text.isEmpty()) return false;
            screen.exec(Play.guess(Integer.parseInt(text)));
            return game.project(GuessingGame::isNotDone);
        });
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
    public void onBegin(int maxTries) {
        message.setText(String.format(""
                + "Choose a number from 1-100 inclusive.%n"
                + "You have %d tries to get it right.",
                maxTries
        ));
    }

    @Override
    public void onLow(int guess, int triesLeft) {
        String tries = triesLeft == 1 ? "try" : "tries";
        message.setText(String.format(
                "%d is too low; %d %s left",
                guess, triesLeft, tries
        ));
    }

    @Override
    public void onHigh(int guess, int triesLeft) {
        String tries = triesLeft == 1 ? "try" : "tries";
        message.setText(String.format(
                "%d is too high; %d %s left",
                guess, triesLeft, tries
        ));
    }

    @Override
    public void onWin() {
        message.setText("Correct!");
    }

    @Override
    public void onLose(int secret) {
        message.setText(String.format("Game over! The number was %d.", secret));
    }
}

class GuessingGame {
    private static final Random RNG = new Random();

    enum State {BEGIN, PLAYING, GAME_OVER}

    final int secret;
    final int maxTries;
    State state = State.BEGIN;
    int guess;
    int triesLeft;

    GuessingGame(int maxTries) {
        secret = RNG.nextInt(100) + 1;
        this.maxTries = maxTries;
        triesLeft = maxTries;
        String s = "123";
    }

    boolean isNotDone() {
        return state != State.GAME_OVER;
    }
}

class Play {
    interface Ui extends Effects<GuessingGame> {
        void tell(String message, Object... fmtArgs);
        void clearInput();
        void onBegin(int maxTries);
        void onLow(int guess, int triesLeft);
        void onHigh(int guess, int triesLeft);
        void onWin();
        void onLose(int secret);

        @Override
        default void onEnter(GuessingGame game) {
            switch (game.state) {
            case BEGIN:
                onBegin(game.maxTries);
                break;
            case PLAYING:
                if (game.guess < game.secret) {
                    onLow(game.guess, game.triesLeft);
                }
                else {
                    onHigh(game.guess, game.triesLeft);
                }
                break;
            case GAME_OVER:
                if (game.guess == game.secret) {
                    onWin();
                }
                else {
                    onLose(game.secret);
                }
                break;
            }
        }
    }

    static Mi.Action<GuessingGame, Ui> newGame(int maxTries) {
        return (_g, ui) -> {
            ui.tell("Starting a new game in 10 seconds.");
            return Mi.async(() -> {
                Thread.sleep(10_000);
                return (_fg, futureUi) -> {
                    futureUi.clearInput();
                    return Mi.enter(new GuessingGame(maxTries));
                };
            });
        };
    }

    static Mi.Action<GuessingGame, Ui> guess(int n) {
        return (game, ui) -> {
            switch (game.state) {
            case BEGIN:
                game.state = GuessingGame.State.PLAYING;
                // fallthrough
            case PLAYING:
                game.guess = n;
                game.triesLeft--;
                if (n == game.secret || game.triesLeft == 0) {
                    game.state = GuessingGame.State.GAME_OVER;
                    return newGame(game.maxTries).after(Mi.reenter());
                }
                else {
                    ui.clearInput();
                    return Mi.reenter();
                }
            case GAME_OVER:
                ui.tell("Wait for the new game to start");
                return Mi.noop();
            }
            return Mi.raise(new IllegalStateException(game.state.name()));
        };
    }
}
