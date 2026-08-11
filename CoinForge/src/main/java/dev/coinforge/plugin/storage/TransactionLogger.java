package dev.coinforge.plugin.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

/** Simple append-only audit log for pay/admin/exchange/interest actions, toggled by log-transactions in config.yml. */
public class TransactionLogger {

    private final JavaPlugin plugin;
    private final File logFile;
    private final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private boolean enabled;

    public TransactionLogger(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "transactions.log");
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void log(String line) {
        if (!enabled) return;
        File parent = logFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
            writer.println("[" + format.format(new Date()) + "] " + line);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not write to transactions.log", e);
        }
    }
}
