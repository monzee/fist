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

import java.util.Random;

import ph.codeia.fist.AndroidRunner;
import ph.codeia.fist.R;
import ph.codeia.fist.moore.Cmd;

public class GuessTheNumber extends AppCompatActivity {

    private Game scoped;
    private Cmd.Actor<Game> screen;
    private TextView message;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        scoped = (Game) getLastCustomNonConfigurationInstance();
        if (scoped == null) scoped = new Game();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_guessing_game);
        screen = Cmd.bind(new AndroidRunner<>(scoped), this::render);
        message = (TextView) findViewById(R.id.message);
        EditText input = (EditText) findViewById(R.id.guess);
        input.setOnEditorActionListener((textView, i, keyEvent) -> {
            String text = textView.getText().toString();
            if (text.isEmpty()) return false;
            screen.exec(Game.guess(Integer.parseInt(text)));
            textView.setText(null);
            return true;
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
        return scoped;
    }

    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    void render(Game game) {
        switch (game.result) {
            case BEGIN:
                message.setText(String.format(""
                        + "Choose a number from 1-100 inclusive.%n"
                        + "You have %d tries to get it right.", game.triesLeft));
                break;
            case WON:
                message.setText("Correct! Starting a new game in 10 seconds...");
                break;
            case LOST:
                message.setText(String.format("Game over! The number was %d.", game.secret));
                break;
            case LOW:
                message.setText(String.format(
                        "%d is too low; %d tries remaining.",
                        game.guess, game.triesLeft));
                break;
            case HIGH:
                message.setText(String.format(
                        "%d is too high; %d tries remaining.",
                        game.guess, game.triesLeft));
                break;
        }
    }

    private static class Game {
        enum Result {BEGIN, LOW, HIGH, WON, LOST}
        private static final Random RNG = new Random();

        Result result = Result.BEGIN;
        int secret = RNG.nextInt(100) + 1;
        int triesLeft = 5;
        int guess;

        static Cmd.Action<Game> newGame() {
            return state -> Cmd.enter(new Game());
        }

        static Cmd.Action<Game> guess(int n) {
            return state -> {
                switch (state.result) {
                    case LOST:  // fallthrough
                    case WON:
                        return Cmd.noop();
                    default:
                        if (state.triesLeft-- < 1) {
                            state.result = Result.LOST;
                            return Cmd.reenter();
                        }
                        if (n == state.secret) {
                            state.result = Result.WON;
                            return Cmd.<Game>reenter().then(() -> {
                                Thread.sleep(10_000);
                                return newGame();
                            });
                        }
                        if (n < state.secret) {
                            state.result = Result.LOW;
                        }
                        else {
                            state.result = Result.HIGH;
                        }
                        state.guess = n;
                        return Cmd.reenter();
                }
            };
        }
    }
}
