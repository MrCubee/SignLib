package fr.mrcubee.sign.gui;

import fr.mrcubee.bukkit.Packets;
import fr.mrcubee.bukkit.events.PacketReceiveEvent;
import fr.mrcubee.bukkit.packet.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public class SignGUi implements Listener {

    private final Plugin plugin;
    private final GenericListenerManager manager;
    private final Map<Player, Consumer<String[]>> consumerMap;

    protected SignGUi(Plugin plugin) {
        this.plugin = plugin;
        this.consumerMap = new WeakHashMap<Player, Consumer<String[]>>();
        this.manager = GenericListenerManager.create("SignGui");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void removeSign(Player player, Location location) {
        GenericPacketPlayOutBlockChange blockChange;
        Block block;

        if (player == null || location == null)
            return;
        blockChange = GenericPacketPlayOutBlockChange.create();
        if (blockChange == null)
            return;
        location.setWorld(player.getWorld());
        block = location.getBlock();
        blockChange.setLocation(location);
        blockChange.setBlock(block.getType(), block.getData());
        blockChange.sendPlayer(player);
    }

    @EventHandler
    public void packetReceived(PacketReceiveEvent event) {
        GenericPacketPlayInUpdateSign updateSign;
        Consumer<String[]> consumer;
        String[] lines;

        if (event.getListenerManager() != this.manager || event.getPacket().getPacket() != Packets.PLAY_IN_UPDATE_SIGN
        || !this.manager.containPlayer(event.getSender()))
            return;
        event.setCancelled(true);
        this.manager.removePlayer(event.getSender());
        updateSign = (GenericPacketPlayInUpdateSign) event.getPacket();
        removeSign(event.getSender(), updateSign.getLocation());
        consumer = this.consumerMap.remove(event.getSender());
        if (consumer == null)
            return;
        lines = updateSign.getLines();
        if (lines == null)
            return;
        Bukkit.getScheduler().runTask(this.plugin, () -> consumer.accept(lines));
    }

    private boolean openSign(Player player, String[] lines) {
        GenericPacketPlayOutBlockChange blockChange;
        GenericPacketPlayOutUpdateSign updateSign;
        GenericPacketPlayOutOpenSignEditor openSignEditor;
        Location location;

        if (player == null || lines == null || this.manager == null)
            return false;
        blockChange = GenericPacketPlayOutBlockChange.create();
        updateSign = GenericPacketPlayOutUpdateSign.create();
        openSignEditor = GenericPacketPlayOutOpenSignEditor.create();
        location = player.getLocation();
        location.setY(player.getWorld().getMaxHeight() - 1);

        if (!blockChange.setLocation(location)
        || !blockChange.setBlock(Material.WALL_SIGN, 0)
        || !updateSign.setLocation(location) || !updateSign.setLines(lines)
        || !openSignEditor.setLocation(location))
            return false;

        if (!blockChange.sendPlayer(player)
        || !updateSign.sendPlayer(player)
        || !openSignEditor.sendPlayer(player))
            return false;
        if (!this.manager.addPlayer(player))
            player.sendMessage("Error to add self in manager list.");
        return true;
    }

    public boolean open(Player player, Consumer<String[]> consumer, String... lines) {
        if (consumer == null)
            return false;
        else if (!openSign(player, lines))
            return false;
        this.consumerMap.put(player, consumer);
        return true;
    }

    public static SignGUi create(Plugin plugin) {
        if (plugin == null)
            return null;
        return new SignGUi(plugin);
    }
}
