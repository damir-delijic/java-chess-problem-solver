package xyz.stockfish.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.niflheim.utils.Settings;
import xyz.stockfish.ChessEngine;
import xyz.stockfish.utils.Option;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Stockfish implements ChessEngine {
    private final Logger Log = LoggerFactory.getLogger(Stockfish.class);
    private final String executablePath = getPath();

    private final BufferedReader input;
    private final BufferedWriter output;
    private final Process process;

    public Stockfish(Option... options) throws StockfishInitException {
        try {
            process = Runtime.getRuntime().exec(executablePath);
            input = new BufferedReader(new InputStreamReader(process.getInputStream()));
            output = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

            for (Option option : options)
                setOption(option.getName(), option.getVal());
        } catch (Exception e) {
            throw new StockfishInitException(e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            sendCommand("quit");
        } finally {
            process.destroy();
            input.close();
            output.close();
        }
    }

    @Override
    public String getBestMove(String fen, int difficulty) {
        waitForReady();
        setOption("Skill Level", difficulty);

        waitForReady();
        sendCommand("position fen " + fen);

        waitForReady();
        sendCommand("go movetime 1000");

        String bestmove = "";
        List<String> response = readResponse("bestmove");

        for (int i = response.size() - 1; i >= 0; i--) {
            String line = response.get(i);
            if (line.startsWith("bestmove")) {
                bestmove = line.substring("bestmove ".length());
                break;
            }
        }

        return bestmove.split("\\s+")[0];
    }

    @Override
    public List<String> getCheckers(String fen) {
        waitForReady();
        sendCommand("position fen " + fen);

        waitForReady();
        sendCommand("d");

        String[] checkers = new String[0];
        List<String> response = readResponse("Checkers:");

        for (int i = response.size() - 1; i >= 0; i--) {
            String line = response.get(i);
            if (line.startsWith("Checkers: ")) {
                checkers = line.substring("Checkers: ".length()).split("\\s+");
                break;
            }
        }

        return Arrays.stream(checkers).filter(e -> e.length() == 2).collect(Collectors.toList());
    }

    @Override
    public String getFen() {
        waitForReady();
        sendCommand("d");

        String fen = "";
        List<String> response = readResponse("Checkers:");

        for (int i = response.size() - 1; i >= 0; i--) {
            String line = response.get(i);
            if (line.startsWith("Fen: ")) {
                fen = line.substring("Fen: ".length());
                break;
            }
        }
        return fen;
    }

    @Override
    public String makeMove(String fen, String pgn) {
        waitForReady();
        sendCommand("position fen " + fen + " moves " + pgn);
        return getFen();
    }

    @Override
    public boolean isAlive() {
        return process.isAlive();
    }

    private List<String> readResponse(String expected) {
        try {
            List<String> lines = new ArrayList<>();
            while (true) {
                String line = input.readLine();
                lines.add(line);

                if (line.startsWith(expected))
                    break;
            }
            return lines;
        } catch (IOException e) {
            throw new StockfishException(e);
        }
    }

    private void sendCommand(String command) {
        try {
            output.write(command + "\n");
            output.flush();
        } catch (IOException e) {
            throw new StockfishException(e);
        }
    }

    private void setOption(String name, int value) {
        sendCommand("setoption name " + name + " value " + value);
    }

    private void waitForReady() {
        sendCommand("isready");
        readResponse("readyok");
    }

    private String getPath() {
        StringBuilder path = new StringBuilder("assets/engines/");

        if (Settings.platform.equalsIgnoreCase("windows")) {
            path.append("stockfish_9_x64");

            switch (Settings.variant) {
                case "BMI2":
                    path.append("_bmi2.exe");
                    break;
                case "POPCNT":
                    path.append("_popcnt.exe");
                    break;
                default:
                case "DEFAULT":
                    path.append(".exe");
                    break;
            }
        } else if (Settings.platform.equalsIgnoreCase("linux")) {
            path.append("./stockfish-9");

            switch (Settings.variant) {
                case "BMI2":
                    path.append("-bmi2");
                    break;
                case "POPCNT":
                    path.append("-popcnt");
                    break;
                default:
                case "DEFAULT":
                    path.append("-popcnt");
                    break;
            }
        } else {
            Log.warn("Error in configuration file, autodetecting platform and loading defaults.");
            return "assets/engines/" + (System.getProperty("os.name").toLowerCase().contains("win") ? "stockfish_9_x63.exe" : "./stockfish-9-64");
        }


        return path.toString();
    }
}
