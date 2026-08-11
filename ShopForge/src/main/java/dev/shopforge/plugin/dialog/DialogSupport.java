package dev.shopforge.plugin.dialog;

import dev.shopforge.plugin.ShopForge;
import dev.shopforge.plugin.gui.ShopGuiManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.logging.Level;

/**
 * Optional native "Dialog" menu support. Minecraft 1.21.6 added Dialogs as a
 * client-rendered UI (separate from chest inventories); Paper exposed them
 * to plugins starting with 1.21.7. This class is the ONLY place in ShopForge
 * that references io.papermc.paper.dialog classes.
 * <p>
 * Every method here is only ever called by the rest of the plugin after
 * checking {@link #isSupported()} first. On plain Spigot, or on Paper older
 * than 1.21.7, that check returns false and none of the Dialog-referencing
 * methods below are ever invoked - so the JVM never has to resolve those
 * classes, and the plugin falls back to the normal inventory GUI / chat
 * prompt / click-twice confirmation instead. This mirrors the same safe
 * "check before touching optional classes" pattern CoinForge uses for its
 * Vault soft-dependency.
 */
public final class DialogSupport {

    private static Boolean supported;

    private DialogSupport() {
    }

    public static boolean isSupported() {
        if (supported == null) {
            try {
                Class.forName("io.papermc.paper.dialog.Dialog");
                Class<?> dialogLike = Class.forName("net.kyori.adventure.dialog.DialogLike");
                Player.class.getMethod("showDialog", dialogLike);
                supported = true;
            } catch (Throwable t) {
                supported = false;
            }
        }
        return supported;
    }

    /** Shows a native text-input dialog for shop search, replacing the chat-prompt fallback. */
    public static void openSearchDialog(ShopForge plugin, ShopGuiManager guiManager, Player player) {
        try {
            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(Component.text("Search Shop"))
                            .body(Collections.singletonList(DialogBody.plainMessage(Component.text("Type an item name to search for."))))
                            .inputs(Collections.singletonList(DialogInput.text("query", Component.text("Item name")).build()))
                            .build())
                    .type(DialogType.confirmation(
                            ActionButton.builder(Component.text("Search", NamedTextColor.GREEN))
                                    .action(DialogAction.customClick((view, audience) -> {
                                        String query = view.getText("query");
                                        if (query != null && !query.trim().isEmpty() && audience instanceof Player) {
                                            guiManager.openSearchResults((Player) audience, query.trim(), 0);
                                        }
                                    }, ClickCallback.Options.builder().build()))
                                    .build(),
                            ActionButton.builder(Component.text("Cancel", NamedTextColor.RED))
                                    .action(DialogAction.customClick((view, audience) -> {
                                        // no-op: just closes the dialog
                                    }, ClickCallback.Options.builder().build()))
                                    .build()
                    ))
            );
            player.showDialog(dialog);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to show the search dialog, disabling native dialogs for this session.", t);
            supported = false;
        }
    }

    /** Shows a native Yes/No confirmation dialog, replacing the click-twice-to-confirm fallback. */
    public static void openConfirmDialog(ShopForge plugin, Player player, String title, String detail, Runnable onConfirm) {
        try {
            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(DialogBase.builder(Component.text(title))
                            .body(Collections.singletonList(DialogBody.plainMessage(Component.text(detail))))
                            .build())
                    .type(DialogType.confirmation(
                            ActionButton.builder(Component.text("Confirm", NamedTextColor.GREEN))
                                    .action(DialogAction.customClick((view, audience) -> onConfirm.run(),
                                            ClickCallback.Options.builder().build()))
                                    .build(),
                            ActionButton.builder(Component.text("Cancel", NamedTextColor.RED))
                                    .action(DialogAction.customClick((view, audience) -> {
                                        // no-op: just closes the dialog, purchase does not happen
                                    }, ClickCallback.Options.builder().build()))
                                    .build()
                    ))
            );
            player.showDialog(dialog);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Failed to show the confirmation dialog.", t);
        }
    }
}
