package dk.ku.di.dms.vms.emitter.adapter;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.function.Consumer;

public class VmsLogReader {

    private final String logPath;
    private long lastKnownPosition = 0;
    private boolean running = true;

    public VmsLogReader(String logPath) {
        this.logPath = logPath;
    }

    /**
     * core read logic
     * @param callback read each new line of log，give it to callback（VmsDecoder）
     */
    public void readStream(Consumer<String> callback) {
        RandomAccessFile reader = null;
        try {
            File logFile = new File(logPath);

            while (!logFile.exists()) {
                System.out.println("Waiting for log file: " + logPath);
                Thread.sleep(1000);
            }

            reader = new RandomAccessFile(logFile, "r");

            while (running) {
                long currentLength = logFile.length();

                // if log file rotation
                if (currentLength < lastKnownPosition) {
                    System.out.println("Log file rotated or truncated. Resetting file descriptor...");

                    lastKnownPosition = 0;

                    if (reader != null) {
                        try { reader.close(); } catch (Exception ignored) {}
                    }

                    reader = new RandomAccessFile(logFile, "r");

                } else if (currentLength > lastKnownPosition) {
                    // new data
                    reader.seek(lastKnownPosition);
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.trim().isEmpty()) {
                            callback.accept(line);
                        }
                    }
                    lastKnownPosition = reader.getFilePointer();
                } else {
                    Thread.sleep(50);
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading log stream: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (Exception ignored) {}
            }
        }
    }

    public void stop() {
        this.running = false;
    }
}